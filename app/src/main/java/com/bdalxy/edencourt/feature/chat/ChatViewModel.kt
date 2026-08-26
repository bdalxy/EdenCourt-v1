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
import com.bdalxy.edencourt.data.local.dao.InteractionDao
import com.bdalxy.edencourt.data.local.dao.RelationshipDao
import com.bdalxy.edencourt.data.local.dao.UserIdentityDao
import com.bdalxy.edencourt.data.local.entity.CharacterCardEntity
import com.bdalxy.edencourt.data.local.entity.ConversationRelationship
import com.bdalxy.edencourt.data.local.entity.InteractionStatus
import com.bdalxy.edencourt.data.local.entity.MemoryItem
import com.bdalxy.edencourt.data.local.entity.Message
import com.bdalxy.edencourt.data.local.entity.MessageStatus
import com.bdalxy.edencourt.data.local.entity.ParticipantRole
import com.bdalxy.edencourt.data.local.entity.Role
import com.bdalxy.edencourt.data.remote.AiChatService
import com.bdalxy.edencourt.data.remote.ApiErrorKind
import com.bdalxy.edencourt.data.remote.ApiException
import com.bdalxy.edencourt.data.local.entity.Conversation
import com.bdalxy.edencourt.data.remote.ChatMessage
import com.bdalxy.edencourt.data.schedule.CharacterSchedule
import com.bdalxy.edencourt.data.schedule.LifeState
import com.bdalxy.edencourt.data.schedule.scheduleTrait
import java.time.LocalTime
import com.bdalxy.edencourt.data.repository.ChatRepository
import com.bdalxy.edencourt.data.repository.CrossSessionConsultService
import com.bdalxy.edencourt.data.repository.MemoryRepository
import com.bdalxy.edencourt.data.setting.ApiConfigStore
import com.bdalxy.edencourt.data.setting.ScheduleConfig
import com.bdalxy.edencourt.data.setting.ScheduleConfigStore
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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
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

/** 本会话参与的一场互动（v1.2b T8.11：互动面板「本会话互动记录」条目） */
data class InteractionRecordItem(
    val interactionId: Long,
    val peerName: String,
    val status: String,
    val summary: String,
)

/** 本会话角色参与的一条关系（T11.6 好感度细分：互动面板「我的关系（好感度）」条目） */
data class RelationshipItem(
    val peerName: String,
    val affinityScore: Int,
    val tierLabel: String,
    val interactionCount: Int,
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
    private val crossSessionConsultService: CrossSessionConsultService,
    private val interactionDao: InteractionDao,
    private val relationshipDao: RelationshipDao,
    private val userIdentityDao: UserIdentityDao,
    private val scheduleConfigStore: ScheduleConfigStore,
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

    /** 会话绑定角色名（T6.2A，企划书 5.3.2「顶部=返回+角色名+更多」）。由 roleCard 派生；T6.14 起优先显示备注别名 */
    val roleName: StateFlow<String> =
        roleCard.map { it?.remark?.ifBlank { it.name } ?: "默认助手" }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = "默认助手",
            )

    /** 角色生活作息配置（T11.10：聊天页顶部「此刻状态」按此计算，设置页改动实时联动） */
    val scheduleConfig: StateFlow<ScheduleConfig> = scheduleConfigStore.config
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ScheduleConfig(),
        )

    /** 角色作息功能总开关（T11.10 深化两级开关第一级；默认关闭） */
    val scheduleEnabled: StateFlow<Boolean> = scheduleConfigStore.enabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false,
        )

    /** 本会话实体（T11.10 深化：聊天页顶部作息面板读取会话级开关/自定义作息） */
    val conversation: StateFlow<Conversation?> = repository.observeConversation(conversationId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    /** 本会话的未读互动数（v1.2b T8.11：聊天页顶部「互动」图标红点，进入互动中心后消除） */
    val unreadInteractions: StateFlow<Int> =
        interactionDao.observeForConversation(conversationId)
            .map { parts -> parts.count { it.readAt == null } }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = 0,
            )

    /** 标记本会话全部互动已读（v1.2b T8.11：进入会话/互动中心时消除红点） */
    fun markInteractionsRead() {
        viewModelScope.launch {
            interactionDao.listParticipantsForConversation(conversationId)
                .filter { it.readAt == null }
                .forEach { interactionDao.markParticipantRead(it.id, System.currentTimeMillis()) }
        }
    }

    /** 本会话是否开放角色间互动（v1.2b T8.7/T8.11：互动面板「开放互动开关」的实际状态） */
    val interactionEnabled: StateFlow<Boolean> =
        repository.observeConversation(conversationId)
            .map { it?.interactionEnabled ?: false }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = false,
            )

    /** 本会话的互动记录（v1.2b T8.11：互动面板「本会话互动记录」，含对方名 + 该会话视角摘要） */
    val interactionRecords: StateFlow<List<InteractionRecordItem>> =
        interactionDao.observeForConversation(conversationId)
            .flatMapLatest { parts ->
                // 一次性合成：每条参与行的互动主记录 + 对方会话名
                flow {
                    val items = mutableListOf<InteractionRecordItem>()
                    for (p in parts) {
                        val inter = interactionDao.getInteraction(p.interactionId) ?: continue
                        // 对方 = 互动中除本会话外的参与者会话 id
                        val peerConvId = if (p.convId == inter.initiatorConvId) inter.targetConvId else inter.initiatorConvId
                        val peerName = resolvePeerName(peerConvId, inter.initiatorConvId, inter.targetConvId)
                        items.add(
                            InteractionRecordItem(
                                interactionId = inter.id,
                                peerName = peerName,
                                status = inter.status,
                                summary = p.summary.orEmpty(),
                            ),
                        )
                    }
                    emit(items)
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

    /** 切换本会话互动资格开关（v1.2b T8.7/T8.11：持久化到 conversations.interactionEnabled） */
    fun setInteractionEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val conv = repository.getConversation(conversationId) ?: return@launch
            repository.updateConversation(conv.copy(interactionEnabled = enabled))
        }
    }

    /** 切换本会话角色作息开关（T11.10 深化两级开关第二级：聊天页顶部作息面板） */
    fun setConversationLife(enabled: Boolean) {
        viewModelScope.launch {
            val conv = repository.getConversation(conversationId) ?: return@launch
            repository.updateConversation(
                conv.copy(
                    lifeEnabled = enabled,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    /**
     * 设置/清除本会话角色自定义作息（T11.10 深化：每个会话角色单独调整）。
     * @param cfg 自定义作息（起床+入睡）；传 null 表示清除自定义，恢复跟随角色卡/默认
     */
    fun updateConversationLifeSchedule(cfg: ScheduleConfig?) {
        viewModelScope.launch {
            val conv = repository.getConversation(conversationId) ?: return@launch
            repository.updateConversation(
                conv.copy(
                    scheduleWakeUpMinute = cfg?.wakeUpMinute,
                    scheduleSleepMinute = cfg?.sleepMinute,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    /** 本会话角色参与的全部关系（T11.6 好感度细分：互动面板「我的关系（好感度）」区，随互动实时刷新） */
    val myRelationships: StateFlow<List<RelationshipItem>> =
        relationshipDao.observeForConversation(conversationId)
            .flatMapLatest { rels ->
                flow {
                    val items = rels.map { r ->
                        // 对方 = 关系中除本会话外的会话；好感度档位按数值推导
                        val peerConvId = if (r.convAId == conversationId) r.convBId else r.convAId
                        RelationshipItem(
                            peerName = resolvePeerName(peerConvId, r.convAId, r.convBId),
                            affinityScore = r.affinityScore,
                            tierLabel = ConversationRelationship.affinityTierLabel(r.affinityScore),
                            interactionCount = r.interactionCount,
                        )
                    }
                    emit(items)
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

    /** 解析对方会话显示名（用户定稿：会话备注优先，其次角色卡备注/名，兜底标题） */
    private suspend fun resolvePeerName(peerConvId: Long, initConvId: Long, targetConvId: Long): String {
        val peer = repository.getConversation(peerConvId) ?: return "其他会话"
        // ① 用户给会话设置的备注最优先
        if (peer.remark.isNotBlank()) return peer.remark
        // ② 角色卡备注/名
        val cardId = peer.characterCardId
        if (cardId != null) {
            val card = characterCardDao.getById(cardId)
            if (card != null) {
                val name = card.remark.ifBlank { card.name }
                if (name.isNotBlank()) return name
            }
        }
        // ③ 会话标题兜底
        return peer.title.ifBlank { "其他会话" }
    }

    /** 是否正在生成 AI 回复（UI 据此切换 停止/发送 按钮） */
    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    /** 失败消息错误提示：messageId -> 错误信息（文案+类型，T6.1 支持关闭/跳设置） */
    private val _errorMessages = MutableStateFlow<Map<Long, ChatError>>(emptyMap())
    val errorMessages: StateFlow<Map<Long, ChatError>> = _errorMessages.asStateFlow()

    /** 当前流式生成任务（用于停止）；T6.9 起指向「回复泵」协程 */
    private var generatingJob: Job? = null

    /**
     * T6.9 回复泵任务：串行消费攒批的用户消息（生成中连发不打断、不丢消息）。
     * 泵在 Main 线程同步检查并启动，保证同一时刻只有一个泵在跑（避免连发竞态重复生成）。
     */
    private var replyPumpJob: Job? = null

    /** T6.9 已处理到的最后一条用户消息 id（用于计算下一批待回复的新消息） */
    private var lastProcessedUserMessageId: Long? = null

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
     * 发送用户消息：入库 + 确保回复泵运行（T2.8 基础上扩展 T3.7/T4.6/T6.9）。
     * T6.9 起（真人式攒批）：生成中连发不再打断当前回复，消息先入库，
     * 由回复泵在下轮循环把攒批的消息合并为一次「来信」统一回复——不打断、不丢消息。
     */
    fun sendMessage(text: String) {
        val content = text.trim()
        if (content.isEmpty()) return
        viewModelScope.launch {
            // 1. 用户消息入库（触发 UI 显示右气泡）
            repository.insertMessage(
                Message(
                    conversationId = conversationId,
                    role = Role.USER,
                    content = content,
                    status = MessageStatus.SENT,
                ),
            )
            // 2. 会话标题/时间/排序更新（T6.15：不再用用户内容覆盖预览，预览跟随 AI 最后回复）
            updateConversationOnSend(content)
            // 3. v1.2 用户触发跨会话打听（T8.4）：命中则走旁路打听并原地回传，不走正常回复泵
            val conversations = repository.observeConversations().first()
            val consult = detectConsult(content, conversations)
            if (consult != null) {
                runConsultUntilDone(consult)
                return@launch
            }
            // 4. 否则走正常流程：确保回复泵在跑（生成中连发的消息已入库，泵下轮循环自动取到并攒批回复）
            ensureReplyPumpRunning()
        }
    }

    /** 用户触发跨会话打听的请求（v1.2，T8.4） */
    private data class ConsultRequest(
        val targetConversationId: Long?,
        val question: String,
    )

    /**
     * 检测用户消息是否为「跨会话打听」意图（T8.4，MVP 用关键词触发）：
     * - 命中触发词（去问问/帮我打听/打听一下…）即视为打听；
     * - 若能点名目标角色卡（消息里出现某角色名）→ 直接定位其某会话；
     * - 主题 = 去掉触发词与点名的角色名后的剩余文本。
     */
    private suspend fun detectConsult(text: String, conversations: List<Conversation>): ConsultRequest? {
        val trigger = CONSULT_TRIGGERS.firstOrNull { text.contains(it) } ?: return null
        val cardNameById = characterCardDao.observeAll().first()
            .mapNotNull { it.id to (it.remark.ifBlank { it.name }) }
            .toMap()
        var targetId: Long? = null
        var matchedName: String? = null
        // 在非当前会话中找「被点名角色」的任一会话作为打听目标
        for (conv in conversations) {
            if (conv.id == conversationId) continue
            val cid = conv.characterCardId ?: continue
            val name = cardNameById[cid] ?: continue
            if (text.contains(name)) {
                matchedName = name
                targetId = conv.id
                break
            }
        }
        var question = text.replace(trigger, "").trim()
        matchedName?.let { question = question.replace(it, "").trim() }
        return ConsultRequest(targetConversationId = targetId, question = question.ifBlank { text })
    }

    /**
     * 执行一次用户触发的旁路打听（T8.3 回传 + T8.4 装配）：
     * - 未点名目标时用 T8.1 按主题检索候选会话 B；
     * - 插一条 AI 占位消息（复用现有消息流渲染："我去打听一下…"），完成后原地更新为回传结果，用户全程可见、不打断正文。
     */
    private suspend fun runConsultUntilDone(req: ConsultRequest) {
        var targetId = req.targetConversationId
        if (targetId == null) {
            val candidates = memoryRepository.locateTargetConversationIds(splitTerms(req.question), conversationId)
            targetId = candidates.firstOrNull()
        }
        // AI 占位消息（GENERATING，UI 顶部显示「正在输入…」同款处理）
        val aiId = repository.insertMessage(
            Message(
                conversationId = conversationId,
                role = Role.ASSISTANT,
                content = "（我去打听一下…）",
                status = MessageStatus.GENERATING,
            ),
        )
        _isGenerating.value = true
        try {
            if (targetId == null) {
                updateConsultMessage(aiId, "我去问了一圈，没人知道呀。", MessageStatus.SENT)
                return
            }
            val visitor = roleName.value
            val result = crossSessionConsultService.consult(targetId, req.question, visitor)
            if (result.success) {
                // 回传：正文自然带来源（"我去问了X，X说…"），用户可看到打听到的内容与出处
                updateConsultMessage(aiId, "我去问了${result.sourceName}，${result.sourceName}说：\n${result.answer}", MessageStatus.SENT)
            } else {
                updateConsultMessage(aiId, result.reason ?: "没问到", MessageStatus.SENT)
            }
        } finally {
            _isGenerating.value = false
        }
    }

    /** 把打听占位消息原地更新为最终回传文本（T8.3 结果回传 A） */
    private suspend fun updateConsultMessage(id: Long, content: String, status: MessageStatus) {
        repository.getMessage(id)?.let { repository.updateMessage(it.copy(content = content, status = status)) }
    }

    /** 打听主题拆词（按常见标点/空白切分，供 T8.1 检索） */
    private fun splitTerms(question: String): List<String> =
        question.split(Regex("[，。,.！!？?、\\s]+")).filter { it.isNotBlank() }

    /** 停止生成：取消当前回复泵任务（已生成内容保留并标记完成，不视为失败） */
    fun stopGenerating() {
        generatingJob?.cancel()
    }

    /**
     * 确保「回复泵」在运行（T6.9 攒批综合回复，真人式不秒回）。
     * - 泵串行消费「上次处理点之后」的全部新用户消息：单发则正常回复一条，
     *   连发则把多条合并为一次「来信」交 AI，由 AI 按性格决定逐条/综合/分组回应。
     * - 生成中连发的新消息已入库，泵在下轮循环自动取到，因此不打断、不丢消息。
     * - 本方法在 Main 线程同步检查并启动泵（无挂起点），保证同一时刻只有一个泵在跑。
     */
    private fun ensureReplyPumpRunning() {
        // 泵已在跑：消息已入库，由泵下轮循环统一处理（同步判断，避免连发竞态重复生成）
        if (replyPumpJob?.isActive == true) return
        replyPumpJob = viewModelScope.launch {
            while (true) {
                // 取当前历史（含刚发送/攒批的用户消息）
                val history = repository.observeMessages(conversationId).first()
                // 本轮待回复：位于上次处理点之后的所有用户消息（连发会一次性取到，实现攒批）
                val newUserMessages = history.filter { msg ->
                    msg.role == Role.USER &&
                        (lastProcessedUserMessageId == null || msg.id > lastProcessedUserMessageId!!)
                }
                // 没有新消息，泵退出等待下次发送
                if (newUserMessages.isEmpty()) break
                lastProcessedUserMessageId = newUserMessages.last().id
                // 合并为一次「来信」：多条以换行分隔，AI 按性格决定怎么回（T6.9）
                val mergedInput = newUserMessages.joinToString("\n") { it.content }
                streamReply(
                    promptMessages = buildPrompt(history, mergedInput, newUserMessages.size),
                    generation = ++generationCounter,
                )
            }
        }
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
     * 并发控制（T6.9 重构）：改为在「回复泵」协程内同步执行（不再另起 job），
     * 由泵串行消费攒批消息；generatingJob 记录泵协程供「停止」取消。
     * 保留代际闸门（P1 竞态修复）：本请求已被更新的发送覆盖（generation != 当前最新）则直接作废。
     */
    private suspend fun streamReply(promptMessages: List<ChatMessage>, generation: Long) {
        // 代际闸门：本请求已被更新的发送覆盖（generation != 当前最新）则直接作废，不启动生成
        if (generation != generationCounter) return
        _isGenerating.value = true
        // AI 占位消息（空内容 + 生成中状态，UI 顶部显示「正在输入…」，T6.8）
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
        // T6.9：同步在当前协程（回复泵/重试协程）内执行，generatingJob 记录当前协程供「停止」取消
        val currentJob = coroutineContext[Job]
        generatingJob = currentJob
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
                        // 尚无任何内容：删除空占位，避免残留「正在输入」气泡（打断即作废，T5.10）
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
        // T6.15：AI 完成回复后，会话列表预览跟随最后一条 AI 回复并刷新时间/排序（类微信）
        updateConversationOnReply(finalContent)
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
            memoryRepository.extractAndStore(userMsg.content, aiMessage.content, currentCardId(), conversationId)
        }
    }

    /** 用户发送时刷新会话（T6.15）：标题取首条用户消息，更新时间/触发排序，但不覆盖预览（预览跟随 AI 最后回复） */
    private suspend fun updateConversationOnSend(content: String) {
        val conv = repository.getConversation(conversationId) ?: return
        val now = System.currentTimeMillis()
        val plain = content.replace('\n', ' ')
        val isUnnamed = conv.title.isBlank() || conv.title == "新对话"
        repository.updateConversation(
            conv.copy(
                title = if (isUnnamed) plain.take(20) else conv.title,
                lastMessageTime = now,
                updatedAt = now,
                // 预览保留 AI 上次回复；若尚无预览（首个会话），用本次用户输入兜底
                lastMessagePreview = conv.lastMessagePreview.ifEmpty { plain.take(50) },
            ),
        )
    }

    /** AI 完成回复时更新会话（T6.15）：预览显示 AI 最后一条（类微信），并刷新时间/排序 */
    private suspend fun updateConversationOnReply(content: String) {
        val conv = repository.getConversation(conversationId) ?: return
        val now = System.currentTimeMillis()
        repository.updateConversation(
            conv.copy(
                lastMessagePreview = content.replace('\n', ' ').take(50),
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
        // T8.13：注入用户自设身份（所有角色统一称呼用户）
        val identityText = userIdentityDao.get()?.let { PromptBuilder.buildUserIdentityPrompt(it) }.orEmpty()
        return PromptBuilder.buildCardSystemPrompt(card, identityText)
    }

    /**
     * 组装完整 Prompt（T5.4 记忆检索注入 + T6.9 攒批提示）。
     * 用用户输入里的词做关键词检索长期记忆（top-K=3），拼成
     * 「[关于用户的长期记忆]- …」片段附加到 system 末尾；检索为空则不影响。
     * @param userBatchCount 本次来信合并的用户消息条数（>1 表示连发攒批，T6.9）
     */
    private suspend fun buildPrompt(history: List<Message>, userInput: String, userBatchCount: Int = 1): List<ChatMessage> {
        val memoryFragment = buildLongTermMemoryFragment()
        return PromptBuilder.build(
            history = history,
            userInput = userInput,
            systemPrompt = currentSystemPrompt(),
            longTermMemories = memoryFragment,
            userBatchCount = userBatchCount,
            // T11.11：注入当前角色生活状态，让 AI 按状态调整语气与内容
            lifeStateText = currentLifeStateText(),
        )
    }

    /**
     * 取当前角色「此刻状态」注入文本（T11.11：状态驱动对话）。
     * 两级开关（全局 + 本会话）都开启才注入，否则状态体系未生效、不给 AI 塞无意义状态；
     * 生效作息按 resolveConfig 会话级解析（会话自定义 > 角色自定义 > 个性派生 > 基础）。
     */
    private suspend fun currentLifeStateText(): String {
        if (!scheduleEnabled.value) return ""
        val conv = repository.getConversation(conversationId) ?: return ""
        if (!conv.lifeEnabled) return ""
        val cardEntity = conv.characterCardId?.let { characterCardDao.getById(it) } ?: return ""
        val cfg = CharacterSchedule.resolveConfig(scheduleConfig.value, cardEntity, conv)
        return when (CharacterSchedule.computeState(cfg, LocalTime.now())) {
            LifeState.SLEEPING -> "你此刻正在睡眠中，被吵醒了会带着睡意和起床气，说话迷迷糊糊、懒洋洋的。"
            LifeState.WAKING -> "你此刻刚起床不久，精神正在恢复，回复带着清晨的清爽与活力。"
            LifeState.ACTIVE -> "你此刻正处于活跃时段，精力充沛，回复干脆利落、不拖沓。"
            LifeState.EVENING -> "你此刻正处在晚间放松时段，语气放松惬意，像忙完一天后的闲聊。"
        }
    }

    /**
     * 取当前会话绑定的角色卡 id（用于记忆归属/检索过滤，T6.13）。
     * 未绑定角色卡返回 null（默认助手不注入长期记忆）。
     */
    private suspend fun currentCardId(): Long? =
        repository.getConversation(conversationId)?.characterCardId

    /**
     * 拼装注入当前角色卡的长期记忆片段（T6.13 按角色卡隔离）。
     * 修复原「用户输入分词 LIKE 检索」：中文整句几乎无法命中记忆里的短句，
     * 导致「AI 竟然不知道用户告知过的事实」。现直接取当前角色卡名下的记忆注入，
     * 既保证「AI 记得」，又做到不同角色卡之间记忆互不可见。
     */
    private suspend fun buildLongTermMemoryFragment(): String {
        val cardId = currentCardId() ?: return ""
        // T7.5 会话级私有定稿：注入 = 该角色共享记忆 + 本会话自己的私有记忆（跨会话私有不串）
        val memories = memoryRepository.getInjectableMemories(cardId, conversationId)
        if (memories.isEmpty()) return ""
        return buildString {
            append("[关于用户的长期记忆]\n")
            memories.forEach { m ->
                append("- ").append(m.content).append("\n")
            }
        }.trim()
    }

    // ---------- 会话记忆面板（T7.4 会话级私有：共享 + 私有两区） ----------

    /** 会话记忆面板「共享区」：该角色卡的全部共享记忆 */
    private val _sharedMemories = MutableStateFlow<List<MemoryItem>>(emptyList())
    val sharedMemories: StateFlow<List<MemoryItem>> = _sharedMemories.asStateFlow()

    /** 会话记忆面板「私有区」：仅本会话（sourceConversationId=conversationId）的私有记忆 */
    private val _privateMemories = MutableStateFlow<List<MemoryItem>>(emptyList())
    val privateMemories: StateFlow<List<MemoryItem>> = _privateMemories.asStateFlow()

    /** 打开记忆面板时刷新两区数据（T7.4） */
    fun refreshSessionMemories() {
        viewModelScope.launch {
            val cardId = currentCardId()
            _sharedMemories.value =
                if (cardId != null) memoryRepository.getSharedByCard(cardId) else emptyList()
            _privateMemories.value =
                memoryRepository.getPrivateByConversation(conversationId)
        }
    }

    companion object {
        private const val TAG = "ChatViewModel"
        /** 拆条连发间隔（T5.9：模拟真人连续打字发送的节奏） */
        private const val CHUNK_SEND_DELAY_MS = 500L
        /** 跨会话打听触发词（v1.2，T8.4 意图识别：命中任一即视为用户触发打听） */
        private val CONSULT_TRIGGERS = listOf(
            "去打听", "帮我打听", "打听一下", "去问问", "帮我问",
            "打探", "探探口风", "问问", "去问",
        )

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
                        crossSessionConsultService = app.crossSessionConsultService,
                        interactionDao = app.interactionDao,
                        relationshipDao = app.relationshipDao,
                        userIdentityDao = app.userIdentityDao,
                        scheduleConfigStore = app.scheduleConfigStore,
                    )
                }
            }
    }
}
