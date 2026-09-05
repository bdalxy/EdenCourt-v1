package com.bdalxy.edencourt.feature.chat

import android.app.Application
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bdalxy.edencourt.EdenCourtApp
import com.bdalxy.edencourt.data.local.dao.CharacterCardDao
import com.bdalxy.edencourt.data.local.dao.ConversationTraitsDao
import com.bdalxy.edencourt.data.local.dao.InteractionDao
import com.bdalxy.edencourt.data.local.dao.MoodDao
import com.bdalxy.edencourt.data.local.dao.RelationshipDao
import com.bdalxy.edencourt.data.local.dao.UserIdentityDao
import com.bdalxy.edencourt.data.local.dao.WorldInfoDao
import com.bdalxy.edencourt.data.local.entity.CharacterCardEntity
import com.bdalxy.edencourt.data.local.entity.ConversationMood
import com.bdalxy.edencourt.data.local.entity.ConversationRelationship
import com.bdalxy.edencourt.data.local.entity.InteractionStatus
import com.bdalxy.edencourt.data.local.entity.MemoryItem
import com.bdalxy.edencourt.data.local.entity.MoodSource
import com.bdalxy.edencourt.data.local.entity.Message
import com.bdalxy.edencourt.data.local.entity.MessageStatus
import com.bdalxy.edencourt.data.local.entity.ParticipantRole
import com.bdalxy.edencourt.data.local.entity.Role
import com.bdalxy.edencourt.data.local.entity.WorldInfoBook
import com.bdalxy.edencourt.data.remote.AiChatService
import com.bdalxy.edencourt.data.remote.ApiErrorKind
import com.bdalxy.edencourt.data.remote.StreamPolicy
import com.bdalxy.edencourt.data.remote.ApiException
import com.bdalxy.edencourt.data.local.entity.Conversation
import com.bdalxy.edencourt.data.local.entity.RelationDirection
import com.bdalxy.edencourt.data.local.entity.ConversationTraits
import com.bdalxy.edencourt.data.remote.ChatMessage
import com.bdalxy.edencourt.data.repository.ChatRepository
import com.bdalxy.edencourt.data.repository.CrossSessionConsultService
import com.bdalxy.edencourt.data.repository.MemoryRepository
import com.bdalxy.edencourt.data.schedule.CharacterSchedule
import com.bdalxy.edencourt.data.schedule.LifeState
import com.bdalxy.edencourt.data.setting.ApiConfigStore
import com.bdalxy.edencourt.data.setting.ReplyCursorStore
import com.bdalxy.edencourt.data.setting.ScheduleConfig
import com.bdalxy.edencourt.data.setting.ScheduleConfigStore
import com.bdalxy.edencourt.domain.CharacterCard
import com.bdalxy.edencourt.domain.CharacterCardParser
import com.bdalxy.edencourt.domain.MoodTag
import com.bdalxy.edencourt.domain.MoodRules
import com.bdalxy.edencourt.domain.PromptBuilder
import com.bdalxy.edencourt.domain.TokenEstimator
import com.bdalxy.edencourt.domain.WorldInfoMatcher
import com.bdalxy.edencourt.domain.DirectionState
import com.bdalxy.edencourt.domain.RelationDirectionParser
import com.bdalxy.edencourt.domain.adjudicateDirection
import com.bdalxy.edencourt.domain.buildDirectionPrompt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
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
import java.time.LocalTime
import java.util.concurrent.atomic.AtomicBoolean

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
 * 聊天页知识库抽屉（KnowledgeDrawer）的展示聚合快照（v1.5 上下文注入层 UI）。
 * 由 refreshKnowledgeDrawer() 一次性拉取，供抽屉展示「角色此刻脑子里装了什么」。
 * token 展示统一走 TokenEstimator（1 字符≈1 token），保证显示 ≈ 注入口径一致（UI 标「估」/「~」）。
 */
data class KnowledgeDrawerState(
    /** 恒久身份（角色卡核心文本 name/性格/背景/说话风格）估算 token */
    val characterTokens: Int,
    /** 世界书行文案：未挂载 / 已挂「书名」 */
    val worldBookLabel: String,
    /** 世界书命中条目 token 合计（浮动态，~值） */
    val worldTokens: Int,
    /** 世界书命中条数（>0 表示正在生效，抽屉绿点） */
    val worldHitCount: Int,
    /** 记忆行文案：如「共享 12 · 私有 5」 */
    val memoryLabel: String,
    /** 记忆 token 合计（浮动态，~值） */
    val memoryTokens: Int,
    /** 亲密度文案：如「63 / 100」 */
    val affinityText: String,
    /** 生活状态文案：如「活跃中」；作息未开启显示「未开启」 */
    val lifeStateText: String,
    /** 当前情绪文案：如「欢喜 · 72」（自主/交互双源合成） */
    val moodText: String,
    /** 本次注入预算已用估算（恒久身份 + 世界书 + 记忆 合计，用于底部分配条） */
    val budgetUsed: Int,
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
    private val replyCursorStore: ReplyCursorStore,
    private val conversationTraitsDao: ConversationTraitsDao,
    private val moodDao: MoodDao,
    private val worldInfoDao: WorldInfoDao,
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
     * 消息列表「展示窗口分页」（G-① 列表懒加载+分页 —— 聊天页侧）。
     * 背景：messages 为全量流（流式/prompt/跨会话暗访均依赖全量，不可截断），
     *      但 UI 一次性渲染数千条 item 会产生重组/测量卡顿。
     * 做法：messages 全量流不变，UI 只展示「最近 windowSize 条」；向上滚动接近窗口顶时
     *      调 loadOlderMessages() 扩大窗口，历史消息按 key 前插且不跳动。
     * 兼容性：新消息/流式更新自动进入窗口尾部（数据源自同一全量流），滚底/交错/暗访全保留。
     */
    /** 初始展示的消息条数（够一屏起，避免长会话首帧全量渲染） */
    private val initialWindowSize = 60

    /** 每次「向上加载更早历史」追加的条数 */
    private val windowPageSize = 40

    /** 当前展示窗口覆盖的条数（从最新往前数） */
    private val _windowSize = MutableStateFlow(initialWindowSize)

    /**
     * 聊天页实际渲染的消息子集 = messages 最近 windowSize 条（时间正序保持）。
     * 空态判断应看全量 messages，这里仅用于渲染与滚底。
     */
    val displayMessages: StateFlow<List<Message>> =
        combine(messages, _windowSize) { all, size ->
            if (all.size <= size) all else all.takeLast(size)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    /** 是否还有更早的历史可向上加载（windowSize < 全量条数） */
    val hasMoreOlder: StateFlow<Boolean> =
        combine(messages, _windowSize) { all, size -> all.size > size }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false,
        )

    /** 向上滚动到顶部附近时调用：扩大展示窗口，载入更早的历史消息 */
    fun loadOlderMessages() {
        // 仅在确实还有更早历史时扩展，避免无效调用刷状态
        if (_windowSize.value < messages.value.size) {
            _windowSize.update { it + windowPageSize }
        }
    }

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

    /** 知识库抽屉展示快照（null=尚未刷新；打开抽屉前调 refreshKnowledgeDrawer() 拉取，见 A2） */
    private val _knowledgeDrawer = MutableStateFlow<KnowledgeDrawerState?>(null)
    val knowledgeDrawer: StateFlow<KnowledgeDrawerState?> = _knowledgeDrawer.asStateFlow()

    /** 全部世界书（供 B1 挂载弹层选择） */
    val worldBooks: StateFlow<List<WorldInfoBook>> =
        worldInfoDao.observeBooks()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

    /** 本会话的「独有人格」（T13.6：聊天页显示印象摘要 + 反差入口；无则 null，兼容默认助手/老会话） */
    val traits: StateFlow<ConversationTraits?> = conversationTraitsDao.observeByConversation(conversationId)
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
    /** 刷新知识库抽屉展示数据（A2：聚合恒久身份/世界书/记忆/关系与状态/预算，显示与注入同 TokenEstimator 口径）。
     *  打开抽屉前调用一次，生成一次快照；数据变化较大时可在抽屉关闭后再次刷新。
     *  注意：构建快照会同步读取 Room DAO（getBook/getEntriesByBook 等为非 suspend），必须在 IO 线程执行，
     *  否则主线程访问数据库触发 Room 的 assertNotMainThread 异常而被 runCatching 吞掉，抽屉仍显示旧值。 */
    fun refreshKnowledgeDrawer() {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { _knowledgeDrawer.value = buildKnowledgeDrawerState() }
            }
                .onFailure { Log.w("ChatViewModel", "refreshKnowledgeDrawer failed", it) }
        }
    }

    /** 提取角色卡「恒久身份」核心文本，用于 token 估算（对齐恒久身份注入片段 ≈ name/性格/背景/说话风格） */
    private fun buildIdentityText(card: CharacterCardEntity?): String {
        if (card == null) return ""
        val cc = runCatching { CharacterCardParser.parse(card.jsonContent) }.getOrNull()
        if (cc == null) return card.name
        return buildString {
            append(cc.name).append("\n")
            append(cc.personality).append("\n")
            append(cc.background).append("\n")
            append(cc.speakingStyle)
        }
    }

    /** 计算本会话角色当前生活状态（作息两级开关都开启才返回具体状态；否则 null）——对齐 ChatPromptAssembler 注入口径 */
    private suspend fun currentLifeState(): LifeState? {
        if (!scheduleEnabled.value) return null
        val conv = conversation.value ?: return null
        if (!conv.lifeEnabled) return null
        val cardEntity = conv.characterCardId?.let { characterCardDao.getById(it) } ?: return null
        val cfg = CharacterSchedule.resolveConfig(scheduleConfigStore.config.first(), cardEntity, conv)
        return CharacterSchedule.computeState(cfg, LocalTime.now())
    }

    /** 计算知识库抽屉展示聚合（一次性快照：读数据库 + 组合当前流值）。
     *  注意：会话信息改为实时从数据库读取（而非内存 conversation.value 流），
     *  避免挂载/卸下后内存流异步发射延迟导致抽屉展示旧的 worldBookId。 */
    private suspend fun buildKnowledgeDrawerState(): KnowledgeDrawerState {
        val conv = repository.getConversation(conversationId)
        val card = roleCard.value
        // —— 恒久身份（角色卡核心文本）估算 ——
        val characterTokens = TokenEstimator.count(buildIdentityText(card))
        // —— 世界书：挂载名 + 命中（扫最近对话历史，对齐注入关键词命中） ——
        var worldBookLabel = "未挂载"
        var worldTokens = 0
        var worldHitCount = 0
        val bookId = conv?.worldBookId
        if (bookId != null) {
            worldInfoDao.getBook(bookId)?.let { book ->
                val entries = worldInfoDao.getEntriesByBook(bookId).filter { it.enabled }
                worldBookLabel = "已挂「${book.name}」"
                val texts = messages.value.takeLast(20).map { it.content }
                val matched = WorldInfoMatcher.matched(entries, texts)
                worldHitCount = matched.size
                worldTokens = matched.sumOf { TokenEstimator.count(it.content) }
            }
        }
        // —— 记忆：共享 + 私有 ——
        val shared = sharedMemories.value
        val priv = privateMemories.value
        val memoryLabel = "共享 ${shared.size} · 私有 ${priv.size}"
        val memoryTokens = (shared + priv).sumOf { TokenEstimator.count(it.content) }
        // —— 关系与状态 ——
        val lifeState = currentLifeState()
        val affinityText = "${conv?.affinityScore ?: 0} / 100"
        val lifeStateText = lifeState?.label ?: "未开启"
        val mood = moodDao.get(conversationId)
        val baseline = MoodRules.baselineFor(lifeState)
        val resolved = MoodRules.resolve(mood, System.currentTimeMillis(), baseline)
        val moodText = "${MoodRules.tagName(resolved.tag)} · ${resolved.intensity}"
        // —— 预算已用估算（恒久身份 + 世界书 + 记忆） ——
        val budgetUsed = characterTokens + worldTokens + memoryTokens
        return KnowledgeDrawerState(
            characterTokens = characterTokens,
            worldBookLabel = worldBookLabel,
            worldTokens = worldTokens,
            worldHitCount = worldHitCount,
            memoryLabel = memoryLabel,
            memoryTokens = memoryTokens,
            affinityText = affinityText,
            lifeStateText = lifeStateText,
            moodText = moodText,
            budgetUsed = budgetUsed,
        )
    }

    /** 挂载/卸下世界书（B1）：更新本会话的 worldBookId；null 表示卸下。挂载变动后刷新知识库抽屉 */
    fun mountWorldBook(bookId: Long?) {
        viewModelScope.launch {
            val conv = repository.getConversation(conversationId) ?: return@launch
            repository.updateConversation(conv.copy(worldBookId = bookId))
            refreshKnowledgeDrawer()
        }
    }

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

    /** 陪看模式检测与事件沉淀（T12.9 拆分：T11.16 逻辑独立成类，瘦身本超载类） */
    private val watchTogether = WatchTogetherDetector(conversationId, relationshipDao)

    /** 跨会话打听副作用执行器（T12.9 拆分：T8.3/8.4 暗访逻辑独立成类） */
    private val chatSideEffects = ChatSideEffects(
        conversationId = conversationId,
        repository = repository,
        characterCardDao = characterCardDao,
        memoryRepository = memoryRepository,
        crossSessionConsultService = crossSessionConsultService,
    )

    /** Prompt 片段构建器（T15.6 拆分：记忆/关系事件/此刻状态/独有人格/system prompt 组装独立成类，纯数据读与组装） */
    private val promptAssembler = ChatPromptAssembler(
        conversationId = conversationId,
        repository = repository,
        characterCardDao = characterCardDao,
        userIdentityDao = userIdentityDao,
        conversationTraitsDao = conversationTraitsDao,
        relationshipDao = relationshipDao,
        memoryRepository = memoryRepository,
        scheduleConfigStore = scheduleConfigStore,
        moodDao = moodDao,
        worldInfoDao = worldInfoDao,
    )

    init {
        viewModelScope.launch {
            // 空会话时插入欢迎语：绑定角色卡且有开场白则用角色开场白，否则用默认助手欢迎语（T4.6）
            val existing = repository.observeMessages(conversationId).first()
            if (existing.isEmpty()) {
                repository.insertMessage(
                    Message(
                        conversationId = conversationId,
                        role = Role.ASSISTANT,
                        content = promptAssembler.boundCard()?.firstMessage?.takeIf { it.isNotBlank() }
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
            // 3. A2：用户言行触发的交互情绪——命中轻量规则则写入当前会话情绪（未命中不改变）
            applyMoodReaction(content)
            // 3. v1.2 用户触发跨会话打听（T8.4）：命中则走旁路打听并原地回传，不走正常回复泵
            val conversations = repository.observeConversations().first()
            val consult = chatSideEffects.detectConsult(content, conversations)
            if (consult != null) {
                chatSideEffects.runConsult(consult, roleName.value) { _isGenerating.value = it }
                return@launch
            }
            // 4. 否则走正常流程：确保回复泵在跑（生成中连发的消息已入库，泵下轮循环自动取到并攒批回复）
            // T11.16：检测用户是否在分享内容（陪看模式），命中则记录待沉淀主题（含原文作讨论要点素材）
            watchTogether.detect(content)
            ensureReplyPumpRunning()
        }
    }

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
                // T12.4：进程重启后首次运行，从持久化游标恢复已处理位置，
                // 避免把历史用户消息全部「当新来信」重复洗澡成一次回复（B1 修复）。
                // 仅在游标为空（首轮）才读库，之后每轮沿用内存值，避免重复 IO。
                if (lastProcessedUserMessageId == null) {
                    lastProcessedUserMessageId = replyCursorStore.get(conversationId)
                }
                // 取当前历史（含刚发送/攒批的用户消息）
                val history = repository.observeMessages(conversationId).first()
                // 本轮待回复：位于上次处理点之后的所有用户消息（连发会一次性取到，实现攒批）
                // T6.9 攒批决策抽离为纯函数（ReplyPumpCalculator），逻辑此处直接复用，行为不变
                val newUserMessages = ReplyPumpCalculator.selectNewUserMessages(history, lastProcessedUserMessageId)
                // 没有新消息，泵退出等待下次发送
                if (newUserMessages.isEmpty()) break
                lastProcessedUserMessageId = newUserMessages.last().id
                // T12.4：游标推进后立即落库，进程重启后可从中断位置续扫（不重复、不丢失）
                replyCursorStore.save(conversationId, lastProcessedUserMessageId!!)
                // 合并为一次「来信」：多条以换行分隔，AI 按性格决定怎么回（T6.9）
                val mergedInput = ReplyPumpCalculator.buildBatchInput(newUserMessages)
                // 推进代际号（先自增成员变量，再作为本请求代际传入，语义同 ++generationCounter）
                generationCounter = GenerationGate.next(generationCounter)
                streamReply(
                    promptMessages = buildPrompt(history, mergedInput, newUserMessages.size),
                    generation = generationCounter,
                )
            }
        }
    }

    /**
     * A2：根据用户输入应用「交互情绪」轻量规则。
     * 命中关键词 → 覆盖当前会话情绪（temp 源、带有效期）；未命中 → 不改（维持既有情绪）。
     * 到期后 ChatPromptAssembler.buildMoodFragment 回落自主基线，本方法不处理回落写库。
     */
    private suspend fun applyMoodReaction(text: String) {
        val reaction = MoodRules.react(text) ?: return
        moodDao.upsert(
            ConversationMood(
                conversationId = conversationId,
                moodTag = reaction.tag.name,
                intensity = reaction.intensity,
                source = MoodSource.REACT.name,
                updatedAt = System.currentTimeMillis(),
                expiresAt = System.currentTimeMillis() + reaction.durationMs,
            ),
        )
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
            // 代际计数自增后再发起重试（B2 修复）：重试视为一次「新的生成」，推进代际，
            // 与 streamReply 的代际闸门语义对齐（避免极端并发下旧请求被误判/旧结果覆盖新结果）
            generationCounter = GenerationGate.next(generationCounter)
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
        // 闸门判断抽离为纯函数 GenerationGate（行为不变，可单测）
        if (!GenerationGate.isCurrent(generation, generationCounter)) return
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
        // T12.2：流式写入节流——记录上次落库时刻（elapsedRealtime 单调时钟，不随系统时间跳变），
        // 距上次 >= 节流阈值（StreamPolicy.DEFAULT_THROTTLE_MS）才触发一次批量写，从「每 token 1次」降到「每~120ms 1次」
        var lastPersistAt = 0L
        // T6.9：同步在当前协程（回复泵/重试协程）内执行，generatingJob 记录当前协程供「停止」取消
        val currentJob = coroutineContext[Job]
        generatingJob = currentJob
        try {
            val config = apiConfigStore.config.first()
            var attempt = 0
            while (true) {
                try {
                    // 收集增量并节流批量写入 Room：正式答案与思考过程分开累积（T3.4 修正）
                    aiChatService.streamChat(promptMessages, config).collect { delta ->
                        if (delta.reasoning.isNotEmpty()) accumulatedReasoning.append(delta.reasoning)
                        if (delta.content.isNotEmpty()) accumulated.append(delta.content)
                        // 等时间节流：达标才落库；未达标仅累积在内存，最终由 finishReply/打断路径 flush
                        // 节流判断抽离为纯函数 StreamPolicy（行为不变，可单测）
                        val now = SystemClock.elapsedRealtime()
                        if (StreamPolicy.shouldPersist(now, lastPersistAt)) {
                            persistAiMessage(aiId, accumulated.toString(), accumulatedReasoning.toString())
                            lastPersistAt = now
                        }
                    }
                    // 正常完成：空输出兜底（企划书 3.6.4）
                    finishReply(aiId, accumulated.toString().ifBlank { "（对方没有回应）" })
                    break
                } catch (e: ApiException) {
                    // 429 退避重试 1~2 次（企划书 3.6.4）；重试策略抽离为纯函数 StreamPolicy（行为不变，可单测）
                    if (StreamPolicy.shouldRetry(e.kind, attempt)) {
                        attempt++
                        delay(StreamPolicy.backoffDelayMs(attempt))
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
            // 用户主动停止/打断（T5.10）：协程已取消，需用 NonCancellable 包裹才能执行挂起写库。
            // T12.2：不再读 DB 旧值（节流会让 DB 落后于内存最新累积），改 flush 内存 accumulated 最新值，
            // 保证打断时最后一批未落库的增量也不丢失。
            withContext(NonCancellable) {
                val latestContent = accumulated.toString()
                val latestReasoning = accumulatedReasoning.toString()
                val current = repository.getMessage(aiId)
                // 打断处理决策抽离为纯函数 InterruptionPolicy（行为不变，可单测）：SKIP/DELETE/FLUSH_DONE
                if (current != null) {
                    when (InterruptionPolicy.decide(current.status, latestContent, latestReasoning)) {
                        // 尚无任何内容：删除空占位，避免残留「正在输入」气泡（打断即作废，T5.10）
                        InterruptionAction.DELETE -> repository.deleteMessage(current)
                        // 已有部分内容：flush 内存最新内容并标记完成（不视为失败）
                        InterruptionAction.FLUSH_DONE -> repository.updateMessage(
                            current.copy(
                                content = latestContent,
                                reasoning = latestReasoning,
                                status = MessageStatus.DONE,
                            ),
                        )
                        // 消息已非生成中（被 finish/失败处理过）：不动它
                        InterruptionAction.SKIP -> Unit
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

    /** 流式写入 helper（T12.2）：读取占位消息并整条更新为最新累积内容（content + reasoning），供节流调用 */
    private suspend fun persistAiMessage(aiId: Long, content: String, reasoning: String) {
        val current = repository.getMessage(aiId) ?: return
        repository.updateMessage(current.copy(content = content, reasoning = reasoning))
    }

    /** 失败处理：记录错误提示 + 将 AI 消息标记 FAILED（T3.8） */
    private suspend fun markFailed(aiId: Long, kind: ApiErrorKind) {
        // T11.16：生成失败则作废本次陪看待沉淀状态，避免残留注入到下次普通消息
        watchTogether.clear()
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
        // T11.16：若本次发送检测到「分享内容」，AI 回复完成后沉淀为「共同经历」关系事件（进关系故事区）
        watchTogether.settle(finalContent)

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
        repository.updateConversation(
            repository.getConversation(conversationId)?.copy(
                lastMessagePreview = content.replace('\n', ' ').take(50),
                lastMessageTime = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
            ) ?: return,
        )
        // A1：AI 成功完成回复视为一次有效对话，自然推进用户↔角色亲密度
        repository.advanceAffinity(conversationId)
        // R3：AI 回复完成后异步判断是否触发方向判断（后台执行，不阻塞本轮回复）
        maybeJudgeDirectionAsync()
    }

    /** R3 方向判断并发锁：防止短时间内多轮触发并发判断、旧结果覆盖新结果 */
    private val directionJudgeLock = AtomicBoolean(false)

    /**
     * R3：AI 回复完成后，判断是否满足方向判断触发条件，满足则后台异步执行。
     * 触发条件（2026-09-05 定稿）：首次 或 距上次判断≥12h 或（距上次≥2min 且 新增≥5条用户消息）。
     * 后台执行、全程吞错，失败不影响主对话（与记忆提取 extractMemoryAsync 同策略）。
     */
    private fun maybeJudgeDirectionAsync() {
        viewModelScope.launch {
            val conv = repository.getConversation(conversationId) ?: return@launch
            val now = System.currentTimeMillis()
            val lastSeen = conv.candidateLastSeen
            val shouldJudge = when {
                lastSeen == null -> true
                now - lastSeen >= JUDGE_PERIOD_MS -> true
                now - lastSeen >= MIN_JUDGE_INTERVAL_MS &&
                    repository.countUserMessagesSince(conversationId, lastSeen) >= JUDGE_MSG_THRESHOLD -> true
                else -> false
            }
            if (!shouldJudge) return@launch
            runDirectionJudge(conv, now)
        }
    }

    /**
     * R3：执行一次方向判断并裁决回写（并发锁内运行）。
     * 流程：取 config → 取最近 30 条消息拼上下文 → 组装方向提示词 → 非流式调 AI → 解析 JSON
     *      → 候选裁决 → 回写候选状态/方向字段。任何失败仅记日志、不更状态、不抛错。
     */
    private suspend fun runDirectionJudge(conv: Conversation, now: Long) {
        if (!directionJudgeLock.compareAndSet(false, true)) return
        try {
            val config = apiConfigStore.config.firstOrNull() ?: return
            if (config.apiKey.isBlank()) return
            val recent = repository.getRecentMessages(conversationId, JUDGE_CONTEXT_LIMIT)
            if (recent.isEmpty()) return
            val context = recent.joinToString("\n") { "${roleLabel(it.role)}：${it.content}" }
            val current = runCatching { RelationDirection.valueOf(conv.relationDirection) }.getOrDefault(RelationDirection.NONE)
            val prompt = buildDirectionPrompt(current, context)
            val raw = aiChatService.completeChat(listOf(ChatMessage("user", prompt)), config)
            val judgment = RelationDirectionParser.parse(raw) ?: return
            val suggestion = judgment.direction ?: return
            val candidate = runCatching { RelationDirection.valueOf(conv.candidateDirection) }.getOrDefault(RelationDirection.NONE)
            val decision = adjudicateDirection(
                state = DirectionState(current, candidate, conv.candidateCount, conv.lastDirectionSwitch),
                suggestion = suggestion,
                confidence = judgment.confidence,
                affinity = conv.affinityScore,
                now = now,
            )
            repository.updateConversation(
                conv.copy(
                    relationDirection = decision.newDirection.name,
                    candidateDirection = decision.newCandidate.name,
                    candidateCount = decision.newCandidateCount,
                    candidateLastSeen = now,
                    lastDirectionSwitch = decision.lastDirectionSwitchAt,
                ),
            )
            if (decision.switched) {
                Log.i(TAG, "R3 方向切换：${current.name} → ${decision.newDirection.name}（${judgment.reason}）")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "R3 方向判断失败（不影响主流程）：${e.message}")
        } finally {
            directionJudgeLock.set(false)
        }
    }

    /** 消息角色 → 中文标签（R3 方向判断上下文拼接用） */
    private fun roleLabel(role: Role): String = when (role) {
        Role.USER -> "用户"
        Role.ASSISTANT -> "AI"
        Role.SYSTEM -> "系统"
    }

    /**
     * 组装完整 Prompt（T5.4 记忆检索注入 + T6.9 攒批提示）。
     * T15.6 拆分：组装逻辑已移入 [ChatPromptAssembler]，此处仅转发并补两个动态参数
     * （作息全局开关、陪看注入文本），行为与拆分前完全一致。
     * @param userBatchCount 本次来信合并的用户消息条数（>1 表示连发攒批，T6.9）
     */
    private suspend fun buildPrompt(history: List<Message>, userInput: String, userBatchCount: Int = 1): List<ChatMessage> =
        promptAssembler.buildPrompt(
            history = history,
            userInput = userInput,
            userBatchCount = userBatchCount,
            scheduleEnabled = scheduleEnabled.value,
            // T11.16：注入「陪看模式」——用户正在分享内容，角色以陪看口吻回应（未触发则为空）
            watchModeText = watchTogether.injectionText(),
        )

    /**
     * T13.6：用户手动编辑本会话的印象摘要 + 标签（generatedBy→'user'）。
     * 用户改后即落库为「用户定义」，后续 LLM 重新生成不会再覆盖（生成器只写非 user 来源，T13.4）。
     * @param blurb 新的印象摘要；@param tags 新标签（逗号分隔的文本）
     */
    fun saveTraitsIdentity(blurb: String, tags: String) {
        viewModelScope.launch {
            runCatching {
                // 标签归一为 JSON 数组（去空白/去重/限 3 个），保证与生成格式一致
                val parsed = tags.split('，', ',').map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(3)
                val json = kotlinx.serialization.json.Json.encodeToString(
                    kotlinx.serialization.json.JsonArray.serializer(),
                    kotlinx.serialization.json.JsonArray(parsed.map { kotlinx.serialization.json.JsonPrimitive(it) }),
                )
                conversationTraitsDao.updateBlurbAndTags(
                    conversationId = conversationId,
                    blurb = blurb.trim(),
                    tags = json,
                    generatedBy = ConversationTraits.GENERATED_BY_USER,
                )
            }.onFailure { e ->
                android.util.Log.w("TraitsEdit", "保存印象摘要失败：${e.message}")
            }
        }
    }

    /**
     * 取当前会话绑定的角色卡 id（用于记忆归属/检索过滤，T6.13）。
     * 未绑定角色卡返回 null（默认助手不注入长期记忆）。
     */
    private suspend fun currentCardId(): Long? =
        repository.getConversation(conversationId)?.characterCardId

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

        /** R3 方向判断：输入上下文条数（专家建议 20~30，取 30） */
        private const val JUDGE_CONTEXT_LIMIT = 30
        /** R3 方向判断：自上次判断以来新增用户消息达此数即触发 */
        private const val JUDGE_MSG_THRESHOLD = 5
        /** R3 方向判断：距上次判断超过此时长必判一次（12h 低频兜底） */
        private const val JUDGE_PERIOD_MS = 12L * 3600_000
        /** R3 方向判断：最小判断间隔（2min 防高频） */
        private const val MIN_JUDGE_INTERVAL_MS = 2L * 60_000

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
                        replyCursorStore = app.replyCursorStore,
                        conversationTraitsDao = app.conversationTraitsDao,
                        moodDao = app.moodDao,
                        worldInfoDao = app.worldInfoDao,
                    )
                }
            }
    }
}
