package com.bdalxy.edencourt.feature.chat

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bdalxy.edencourt.EdenCourtApp
import com.bdalxy.edencourt.data.local.dao.CharacterCardDao
import com.bdalxy.edencourt.data.local.entity.Message
import com.bdalxy.edencourt.data.local.entity.MessageStatus
import com.bdalxy.edencourt.data.local.entity.Role
import com.bdalxy.edencourt.data.remote.AiChatService
import com.bdalxy.edencourt.data.remote.ApiErrorKind
import com.bdalxy.edencourt.data.remote.ApiException
import com.bdalxy.edencourt.data.remote.ChatMessage
import com.bdalxy.edencourt.data.repository.ChatRepository
import com.bdalxy.edencourt.data.setting.ApiConfigStore
import com.bdalxy.edencourt.domain.CharacterCard
import com.bdalxy.edencourt.domain.CharacterCardParser
import com.bdalxy.edencourt.domain.PromptBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * 聊天页 ViewModel（T2.7 + T3.7 + T4.6）。
 * - 按 conversationId 观察该会话消息流与标题
 * - T3.7 起：发送消息后调用远程 API 流式生成，逐段写入 Room 实现「逐字显示」
 * - T4.6 起：system prompt 由会话绑定的真实角色卡生成；空会话欢迎语用角色开场白
 */
class ChatViewModel(
    private val conversationId: Long,
    private val repository: ChatRepository,
    private val characterCardDao: CharacterCardDao,
    private val apiConfigStore: ApiConfigStore,
    private val aiChatService: AiChatService,
) : ViewModel() {

    /** 该会话的消息流（按创建时间正序，数据库变化自动刷新） */
    val messages: StateFlow<List<Message>> =
        repository.observeMessages(conversationId)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

    /** 会话标题（顶部栏显示） */
    val conversationTitle: StateFlow<String?> =
        repository.observeConversation(conversationId)
            .map { it?.title }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = null,
            )

    /** 是否正在生成 AI 回复（UI 据此切换 停止/发送 按钮） */
    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    /** 失败消息错误提示：messageId -> 用户可读文案（UI 显示在失败气泡下方） */
    private val _errorMessages = MutableStateFlow<Map<Long, String>>(emptyMap())
    val errorMessages: StateFlow<Map<Long, String>> = _errorMessages.asStateFlow()

    /** 当前流式生成任务（用于停止） */
    private var generatingJob: Job? = null

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

    /** 发送用户消息：入库 + 流式请求 AI 回复（T2.8 基础上扩展 T3.7/T4.6） */
    fun sendMessage(text: String) {
        val content = text.trim()
        if (content.isEmpty() || _isGenerating.value) return
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
            // 4. 组装 Prompt（system 用会话绑定的角色卡）并开始流式生成
            streamReply(PromptBuilder.build(history, content, systemPrompt = currentSystemPrompt()))
        }
    }

    /** 停止生成：取消任务，已生成内容保留并标记完成（不视为失败） */
    fun stopGenerating() {
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
            streamReply(PromptBuilder.build(newHistory, userMsg.content, systemPrompt = currentSystemPrompt()))
        }
    }

    /** 流式生成：插入 AI 占位消息，逐段追加内容到 Room（驱动逐字显示）；含 429 退避重试 */
    private suspend fun streamReply(promptMessages: List<ChatMessage>) {
        if (_isGenerating.value) return
        _isGenerating.value = true
        // AI 占位消息（空内容 + 生成中状态，UI 显示「正在思考…」）
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
        generatingJob = viewModelScope.launch {
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
                // 用户主动停止：保留已生成内容，标记完成
                val current = repository.getMessage(aiId)
                if (current != null && current.status == MessageStatus.GENERATING) {
                    repository.updateMessage(current.copy(status = MessageStatus.DONE))
                }
            } finally {
                _isGenerating.value = false
            }
        }
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
        _errorMessages.update { it + (aiId to text) }
        val current = repository.getMessage(aiId)
        if (current != null && current.status == MessageStatus.GENERATING) {
            repository.updateMessage(current.copy(status = MessageStatus.FAILED))
        }
    }

    /** 正常结束生成：写入最终内容并标记 DONE */
    private suspend fun finishReply(aiId: Long, finalContent: String) {
        val current = repository.getMessage(aiId) ?: return
        repository.updateMessage(current.copy(content = finalContent, status = MessageStatus.DONE))
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

    companion object {
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
                    )
                }
            }
    }
}
