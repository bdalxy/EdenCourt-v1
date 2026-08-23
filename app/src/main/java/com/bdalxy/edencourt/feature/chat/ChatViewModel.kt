package com.bdalxy.edencourt.feature.chat

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bdalxy.edencourt.EdenCourtApp
import com.bdalxy.edencourt.data.local.dao.CharacterCardDao
import com.bdalxy.edencourt.data.local.entity.CharacterCardEntity
import com.bdalxy.edencourt.data.local.entity.Message
import com.bdalxy.edencourt.data.local.entity.MessageStatus
import com.bdalxy.edencourt.data.local.entity.Role
import com.bdalxy.edencourt.data.remote.AiChatService
import com.bdalxy.edencourt.data.remote.ApiErrorKind
import com.bdalxy.edencourt.data.remote.ApiException
import com.bdalxy.edencourt.data.remote.ChatMessage
import com.bdalxy.edencourt.data.repository.ChatRepository
import com.bdalxy.edencourt.data.repository.MemoryRepository
import com.bdalxy.edencourt.data.setting.ApiConfigStore
import com.bdalxy.edencourt.domain.CharacterCard
import com.bdalxy.edencourt.domain.CharacterCardParser
import com.bdalxy.edencourt.domain.PromptBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * 单条消息的错误提示（T6.1）。
 * - text：用户可读文案（企划书 5.5.3）
 * - kind：错误类型（AUTH 时 UI 提供「去设置」入口）
 */
data class ChatError(
    val text: String,
    val kind: ApiErrorKind,
)

/**
 * 聊天页 ViewModel（T2.7 + T3.7 + T4.6 + T5.10 + T6.1）。
 * - 按 conversationId 观察该会话消息流与标题
 * - T3.7 起：发送消息后调用远程 API 流式生成，逐段写入 Room 实现「逐字显示」
 * - T4.6 起：system prompt 由会话绑定的真实角色卡生成；空会话欢迎语用角色开场白
 * - T5.10 起：生成/连发中可发新消息打断（双向可打断）；取消原语为远期设备操作仲裁层预留
 * - T6.1 起：错误提示可关闭（dismissError）；AUTH 错误携带类型供 UI 跳设置
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    private val conversationId: Long,
    private val repository: ChatRepository,
    private val characterCardDao: CharacterCardDao,
    private val apiConfigStore: ApiConfigStore,
    private val aiChatService: AiChatService,
    private val memoryRepository: MemoryRepository,
) : ViewModel() {

    /** 该会话的消息流（按创建时间正序，数据库变化自动刷新） */
    val messages: StateFlow<List<Message>> =
        repository.observeMessages(conversationId)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

    /**
     * 会话绑定角色卡实体（T6.2A，聊天页顶部显示头像+角色名用）。
     * 绑定角色卡 → 返回卡片（含 avatar/name）；未绑定或卡片被删 → null（顶部显示默认助手）。
     */
    val roleCard: StateFlow<CharacterCardEntity?> =
        repository.observeConversation(conversationId)
            .flatMapLatest { conv ->
                val cardId = conv?.characterCardId
                if (cardId == null) {
                    flowOf(null)
                } else {
                    // 观察角色卡：名称/头像变化（编辑）或删除后自动更新
                    characterCardDao.observeById(cardId)
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = null,
            )

    /** 会话绑定角色名（T6.2A，企划书 5.3.2「顶部=返回+角色名+更多」）。由 roleCard 派生 */
    val roleName: StateFlow<String> =
        roleCard.map { it?.name ?: "默认助手" }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = "默认助手",
            )

    /** 是否正在生成 AI 回复（UI 据此切换 停止/发送 按钮） */
    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    /** 失败消息错误提示：messageId -> 错误信息（文案+类型，T6.1 支持关闭/跳设置） */
    private val _errorMessages = MutableStateFlow<Map<Long, ChatError>>(emptyMap())
    val errorMessages: StateFlow<Map<Long, ChatError>> = _errorMessages.asStateFlow()

    /** 当前流式生成任务（用于停止） */
    private var generatingJob: Job? = null

    /**
     * 生成请求代际序号（P1 竞态修复）。
     * 每次发送消息自增：后发消息代际更大，覆盖先发。
     * streamReply 闸门据此判断「本请求是否已过期」，避免快速连发时
     * 两条消息都卡在挂起点、后一条被先发任务的活跃状态拦截而不触发 AI 回复。
     */
    private var generationCounter = 0L

    init {
        viewModelScope.launch {
            // 空会话时插入欢迎语：绑定角色卡且有开场白则用角色开场白，否则用默认助手欢迎语（T4.6）
            val existing = repository.observeMessages(conversationId).first()
            if (existing.isEmpty()) {
                repository.insertMessage(
                    Message(
                        conversationId = conversationId,
                        role = Role.ASSISTANT,
                        content = boundCard()?.firstMessage?.takeIf { it.isNotBlank() }
                            ?: "你好呀，我是永恒庭院。今天想聊些什么呢？",
                    ),
                )
            }
        }
    }

    /**
     * 发送用户消息：入库 + 流式请求 AI 回复（T2.8 基础上扩展 T3.7/T4.6）。
     * T5.10 起：生成/连发过程中发送视为「打断」——先取消当前回复再处理新消息（双向可打断）。
     */
    fun sendMessage(text: String) {
        val content = text.trim()
        if (content.isEmpty()) return
        // 打断当前 AI 回复（T5.10：真人式双向可打断，已生成内容保留）
        interruptCurrentGeneration()
        // 递增代际：本次发送为最新请求，旧请求若尚未真正启动则直接作废（P1 竞态修复）
        val generation = ++generationCounter
        viewModelScope.launch {
            // 1. 取当前历史（不含本次输入）用于 Prompt 构建
            val history = repository.observeMessages(conversationId).first()
            // 2. 用户消息入库（触发 UI 显示右气泡）
            repository.insertMessage(
                Message(
                    conversationId = conversationId,
                    role = Role.USER,
                    content = content,
                    status = MessageStatus.SENT,
                ),
            )
            // 3. 更新会话摘要（标题/预览/时间）
            updateConversationSummary(content)
            // 4. 组装 Prompt（system 用会话绑定的角色卡 + T5.4 注入相关长期记忆）并开始流式生成
            streamReply(buildPrompt(history, content), generation)
        }
    }

    /** 停止生成：取消任务，已生成内容保留并标记完成（不视为失败） */
    fun stopGenerating() {
        generatingJob?.cancel()
    }

    /**
     * 打断当前 AI 回复（T5.10 双向可打断）。
     * - 取消生成/拆条连发任务：已生成内容保留并标记完成，不视为失败
     * - 作为「取消原语」预留：远期 AI 设备操作仲裁层将以本方法为基座，
     *   把执行队列中待执行的设备操作一并取消（企划书 11.6）
     */
    private fun interruptCurrentGeneration() {
        generatingJob?.cancel()
    }

    /**
     * 重试失败消息（T3.8）：删除失败 AI 消息与其用户消息，重新构建 prompt 再生成。
     * @param failedAiId 失败 AI 消息的 id
     */
    fun retry(failedAiId: Long) {
        if (_isGenerating.value) return
        viewModelScope.launch {
            val history = repository.observeMessages(conversationId).first()
            val failed = history.firstOrNull { it.id == failedAiId } ?: return@launch
            // 失败消息之前最后一条用户消息即本次输入
            val userMsg = history.takeWhile { it.id != failedAiId }.lastOrNull { it.role == Role.USER }
                ?: return@launch
            // 清掉失败状态与错误提示，移除失败对（AI+user）后重新生成
            _errorMessages.update { it - failedAiId }
            repository.deleteMessage(failed)
            val newHistory = history.filter { it.id != failedAiId && it.id != userMsg.id }
            streamReply(buildPrompt(newHistory, userMsg.content), generationCounter)
        }
    }

    /**
     * 流式生成：插入 AI 占位消息，逐段追加内容到 Room（驱动逐字显示）；含 429 退避重试。
     * 并发控制（T5.10 + P1 竞态修复）：以「代际序号」为闸门判断本请求是否已过期——
     * 快速连发时，后发消息代际更大，先发请求即便尚未走完挂起点也视为过期直接作废，
     * 后发请求总能启动；彻底避免「旧请求已启动、新请求被活跃任务拦截而永不回复」的竞态。
     */
    private suspend fun streamReply(promptMessages: List<ChatMessage>, generation: Long) {
        // 代际闸门：本请求已被更新的发送覆盖（generation != 当前最新）则直接作废，不启动生成
        if (generation != generationCounter) return
        _isGenerating.value = true
        // AI 占位消息（空内容 + 生成中状态，UI 显示「对方正在输入…」）
        val aiId = repository.insertMessage(
            Message(
                conversationId = conversationId,
                role = Role.ASSISTANT,
                content = "",
                status = MessageStatus.GENERATING,
            ),
        )
        val accumulated = StringBuilder()
        val accumulatedReasoning = StringBuilder()
        val job = viewModelScope.launch {
            try {
                val config = apiConfigStore.config.first()
                var attempt = 0
                while (true) {
                    try {
                        // 收集增量并逐段写入 Room：正式答案与思考过程分开累积（T3.4 修正）
                        aiChatService.streamChat(promptMessages, config).collect { delta ->
                            if (delta.reasoning.isNotEmpty()) accumulatedReasoning.append(delta.reasoning)
                            if (delta.content.isNotEmpty()) accumulated.append(delta.content)
                            val current = repository.getMessage(aiId) ?: return@collect
                            repository.updateMessage(
                                current.copy(
                                    content = accumulated.toString(),
                                    reasoning = accumulatedReasoning.toString(),
                                ),
                            )
                        }
                        // 正常完成：空输出兜底（企划书 3.6.4）
                        finishReply(aiId, accumulated.toString().ifBlank { "（对方没有回应）" })
                        break
                    } catch (e: ApiException) {
                        // 429 退避重试 1~2 次（企划书 3.6.4）
                        if (e.kind == ApiErrorKind.RATE_LIMIT && attempt < 2) {
                            attempt++
                            delay(1000L * attempt)
                            continue
                        }
                        markFailed(aiId, e.kind)
                        break
                    } catch (e: IOException) {
                        markFailed(aiId, ApiErrorKind.NETWORK)
                        break
                    }
                }
            } catch (e: CancellationException) {
                // 用户主动停止/打断（T5.10）：协程已取消，需用 NonCancellable 包裹才能执行挂起写库
                withContext(NonCancellable) {
                    val current = repository.getMessage(aiId)
                    if (current != null && current.status == MessageStatus.GENERATING) {
                        if (current.content.isBlank()) {
                            // 尚无任何内容：删除空占位，避免残留「对方正在输入…」（打断即作废，T5.10）
                            repository.deleteMessage(current)
                        } else {
                            // 已有部分内容：保留并标记完成（不视为失败）
                            repository.updateMessage(current.copy(status = MessageStatus.DONE))
                        }
                    }
                }
            } finally {
                // 仅当仍是当前最新任务时复位状态（T5.10：避免被取消的旧任务 finally 误关新任务）
                if (generatingJob == coroutineContext[Job]) {
                    _isGenerating.value = false
                }
            }
        }
        generatingJob = job
    }

    /** 失败处理：记录错误提示 + 将 AI 消息标记 FAILED（T3.8） */
    private suspend fun markFailed(aiId: Long, kind: ApiErrorKind) {
        val text = when (kind) {
            ApiErrorKind.NETWORK -> "网络不可用，请检查网络后重试"
            ApiErrorKind.AUTH -> "API Key 无效，请到设置页检查"
            ApiErrorKind.RATE_LIMIT -> "请求过于频繁，请稍后重试"
            ApiErrorKind.TIMEOUT -> "请求超时，请重试"
            ApiErrorKind.SERVER -> "生成失败（服务器错误）"
        }
        _errorMessages.update { it + (aiId to ChatError(text, kind)) }
        val current = repository.getMessage(aiId)
        if (current != null && current.status == MessageStatus.GENERATING) {
            repository.updateMessage(current.copy(status = MessageStatus.FAILED))
        }
    }

    /** 关闭某条失败消息的错误提示（T6.1：错误可关闭且不影响已发消息） */
    fun dismissError(messageId: Long) {
        _errorMessages.update { it - messageId }
    }

    /**
     * 正常结束生成（T5.9 拆条连发）。
     * 流式阶段把完整内容累积在单条占位消息里（UI 逐字/「对方正在输入…」）；
     * 完成后按句拆成最多 3 条短消息连发入库，模拟真人打字节奏。
     */
    private suspend fun finishReply(aiId: Long, finalContent: String) {
        val current = repository.getMessage(aiId) ?: return
        // 记忆提取基于完整回复内容（不受拆条影响，T5.3）
        extractMemoryAsync(current.copy(content = finalContent))

        // 拆条连发：第一条复用占位消息（保持原 id，避免 UI key 突变），后续逐条插入
        val chunks = MessageSplitter.split(finalContent)
        if (chunks.isEmpty()) return
        repository.updateMessage(current.copy(content = chunks.first(), status = MessageStatus.DONE))
        chunks.drop(1).forEach { chunk ->
            // 短间隔模拟真人连续打字发送的节奏（T5.9）
            delay(CHUNK_SEND_DELAY_MS)
            repository.insertMessage(
                Message(
                    conversationId = conversationId,
                    role = Role.ASSISTANT,
                    content = chunk,
                    status = MessageStatus.DONE,
                ),
            )
        }
    }

    /** 异步提取长期记忆：取该条 AI 回复对应的用户消息做输入，失败不影响主流程（T5.3） */
    private fun extractMemoryAsync(aiMessage: Message) {
        viewModelScope.launch {
            val history = repository.observeMessages(conversationId).first()
            val userMsg = history
                .takeWhile { it.id != aiMessage.id }
                .lastOrNull { it.role == Role.USER }
            if (userMsg == null) {
                Log.w(TAG, "记忆提取跳过：未找到对应的用户消息")
                return@launch
            }
            Log.d(TAG, "记忆提取触发：user=[${userMsg.content.take(30)}], ai=[${aiMessage.content.take(30)}]")
            memoryRepository.extractAndStore(userMsg.content, aiMessage.content)
        }
    }

    /** 更新会话摘要：标题取首条用户消息截断，预览与时间跟随 */
    private suspend fun updateConversationSummary(content: String) {
        val conv = repository.getConversation(conversationId) ?: return
        val now = System.currentTimeMillis()
        val preview = content.replace('\n', ' ')
        val isUnnamed = conv.title.isBlank() || conv.title == "新对话"
        repository.updateConversation(
            conv.copy(
                title = if (isUnnamed) preview.take(20) else conv.title,
                lastMessagePreview = preview.take(50),
                lastMessageTime = now,
                updatedAt = now,
            ),
        )
    }

    /**
     * 取会话绑定的角色卡（T4.6）。
     * 未绑定（characterCardId 为空）或卡片被删/解析失败时返回 null（回退默认助手）。
     */
    private suspend fun boundCard(): CharacterCard? {
        val conv = repository.getConversation(conversationId) ?: return null
        val cardId = conv.characterCardId ?: return null
        val entity = characterCardDao.getById(cardId) ?: return null
        return runCatching { CharacterCardParser.parse(entity.jsonContent) }.getOrNull()
    }

    /**
     * 构建当前 system 提示词（T4.6）。
     * 会话绑定角色卡 → 用角色卡构建；否则用默认助手提示词。
     */
    private suspend fun currentSystemPrompt(): String {
        val card = boundCard() ?: return PromptBuilder.DEFAULT_SYSTEM_PROMPT
        return PromptBuilder.buildCardSystemPrompt(card)
    }

    /**
     * 组装完整 Prompt（T5.4 记忆检索注入）。
     * 用用户输入里的词做关键词检索长期记忆（top-K=3），拼成
     * 「[关于用户的长期记忆]- …」片段附加到 system 末尾；检索为空则不影响。
     */
    private suspend fun buildPrompt(history: List<Message>, userInput: String): List<ChatMessage> {
        val memoryFragment = buildLongTermMemoryFragment(userInput)
        return PromptBuilder.build(
            history = history,
            userInput = userInput,
            systemPrompt = currentSystemPrompt(),
            longTermMemories = memoryFragment,
        )
    }

    /** 检索长期记忆并拼装注入片段（T5.4）：无命中返回空串 */
    private suspend fun buildLongTermMemoryFragment(userInput: String): String {
        // 拆词：按空格/标点拆成多个关键词，避免整句 LIKE 匹配永不命中
        val terms = userInput
            .split(Regex("[\\s,.;!?，。！？、；：\"'()\\[\\]{}]+"))
            .map { it.trim() }
            .filter { it.isNotBlank() && it.length >= 2 }
            .distinct()
            .take(MAX_MEMORY_SEARCH_TERMS)
        val memories = memoryRepository.searchByTerms(terms)
            .take(PromptBuilder.DEFAULT_MEMORY_LIMIT)
        if (memories.isEmpty()) return ""
        return buildString {
            append("[关于用户的长期记忆]\n")
            memories.forEach { m ->
                append("- ").append(m.content).append("\n")
            }
        }.trim()
    }

    companion object {
        private const val TAG = "ChatViewModel"
        /** 检索时最多使用的关键词数量（T5.4：控制检索开销） */
        private const val MAX_MEMORY_SEARCH_TERMS = 5
        /** 拆条连发间隔（T5.9：模拟真人连续打字发送的节奏） */
        private const val CHUNK_SEND_DELAY_MS = 500L

        /** 工厂：传入路由参数 conversationId，从 Application 取仓库/角色卡 DAO/配置/网络服务单例 */
        fun factory(conversationId: Long, application: Application): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val app = this[androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as EdenCourtApp
                    ChatViewModel(
                        conversationId = conversationId,
                        repository = app.chatRepository,
                        characterCardDao = app.characterCardDao,
                        apiConfigStore = app.apiConfigStore,
                        aiChatService = app.aiChatService,
                        memoryRepository = app.memoryRepository,
                    )
                }
            }
    }
}
