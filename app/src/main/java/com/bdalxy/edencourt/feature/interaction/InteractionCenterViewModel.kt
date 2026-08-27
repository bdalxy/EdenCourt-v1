package com.bdalxy.edencourt.feature.interaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bdalxy.edencourt.EdenCourtApp
import com.bdalxy.edencourt.data.interaction.InteractionExecutor
import com.bdalxy.edencourt.data.local.dao.CharacterCardDao
import com.bdalxy.edencourt.data.local.dao.ConversationDao
import com.bdalxy.edencourt.data.local.dao.InteractionDao
import com.bdalxy.edencourt.data.local.dao.RelationshipDao
import com.bdalxy.edencourt.data.local.entity.Conversation
import com.bdalxy.edencourt.data.local.entity.ConversationRelationship
import com.bdalxy.edencourt.data.local.entity.Interaction
import com.bdalxy.edencourt.data.local.entity.InteractionParticipant
import com.bdalxy.edencourt.data.local.entity.InteractionTurn
import com.bdalxy.edencourt.data.local.entity.ParticipantRole
import com.bdalxy.edencourt.data.setting.InteractionConfigStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 互动中心列表条目（UI 组装：参与双方名 + 摘要 + 未读） */
data class InteractionListItem(
    val interaction: Interaction,
    val title: String,
    val summary: String,
    val unread: Boolean,
)

/** 一场互动的完整详情（互动中心点击行后展示：双方名 + 台词轮次） */
data class InteractionDetail(
    val title: String,
    val participants: Map<Long, String>, // convId -> 显示名
    val turns: List<InteractionTurn>,
)

/** 一对会话的关系一览（互动中心顶部「关系一览」，T11.6：好感度/档位/次数） */
data class RelationshipOverview(
    val title: String,          // "A × B"
    val affinityScore: Int,     // 好感度数值
    val tierLabel: String,      // 中文档位（陌生/相识/友好/亲密）
    val interactionCount: Int,  // 互动次数
)

/** 一条「共同经历事件」的时间线展示（T11.13 关系故事区：场景/主题/氛围/要点/时间） */
data class RelationshipEventOverview(
    val title: String,      // "A × B"；共同观看事件为「A × 你」
    val scene: String,      // 场景（事件驱动=因由「因为…」，否则「日常」；共同观看=分享场景）
    val topic: String,      // 主题（话题锚点）
    val mood: String,       // 氛围档位（陌生/相识/友好/亲密；共同观看=「一起看」）
    val points: String,     // 要点（双方视角摘要）
    val source: String,     // MANUAL / AUTO
    val type: String,       // INTERACTION=角色间互动 / WATCH=用户共同观看（T11.16）
    val happenedAt: Long,   // 发生时间
)

/** 互动自动驱动配置（T11.7：互动中心「自动驱动」设置区展示/修改，改后立即重排当日闹钟） */
data class AutoDriveConfig(
    val autoEnabled: Boolean,
    val windowStartMinute: Int,
    val windowEndMinute: Int,
    val probability: Float,
)

/**
 * 互动中心 ViewModel（v1.2b，T8.11/T8.12）。
 * - 全部互动列表（双方名 + 视角摘要 + 未读红点）
 * - 全局总开关
 * - 手动发起互动：选 A / 选 B，调 InteractionExecutor
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InteractionCenterViewModel(
    private val interactionDao: InteractionDao,
    private val conversationDao: ConversationDao,
    private val characterCardDao: CharacterCardDao,
    private val interactionConfigStore: InteractionConfigStore,
    private val interactionExecutor: InteractionExecutor,
    private val relationshipDao: RelationshipDao,
) : ViewModel() {

    /** 全局总开关 */
    val globalEnabled: StateFlow<Boolean> = interactionConfigStore.config.map { it.enabled }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** 互动自动驱动配置（T11.7：开关/窗口/概率，随配置变化实时刷新） */
    val autoConfig: StateFlow<AutoDriveConfig> = interactionConfigStore.config.map { cfg ->
        AutoDriveConfig(
            autoEnabled = cfg.autoEnabled,
            windowStartMinute = cfg.autoWindowStartMinute,
            windowEndMinute = cfg.autoWindowEndMinute,
            probability = cfg.autoProbability,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        AutoDriveConfig(true, 20 * 60, 22 * 60, 0.3f),
    )

    /** 全部互动（倒序） */
    private val interactions = interactionDao.observeAll()
    private val participants = interactionDao.observeAllParticipants()

    /** 发起/操作提示（一次性，展示后自动清除） */
    val toast = MutableStateFlow<String?>(null)

    /** 当前选中的互动详情（非空时展示详情弹窗） */
    val detail = MutableStateFlow<InteractionDetail?>(null)

    /**
     * 会话显示名解析（用户定稿：有会话备注优先用备注；无备注时用角色名，
     * 同一角色卡有多个会话个体时附带短码尾4位以区分，如「小夏·CB96」）。
     */
    val convNames: StateFlow<Map<Long, String>> = conversationDao.observeAll()
        .flatMapLatest { list ->
            flow {
                // 预统计：每个角色卡下挂了多少个会话，>1 才需要加码区分
                val cardCounts = list.groupingBy { it.characterCardId }.eachCount()
                val map = mutableMapOf<Long, String>()
                for (c in list) {
                    // ① 用户给会话设置的备注最优先（如「学姐A」），自定义最自然
                    if (c.remark.isNotBlank()) {
                        map[c.id] = c.remark
                        continue
                    }
                    // ② 无备注：角色卡名兜底
                    val base = if (c.characterCardId != null) {
                        characterCardDao.getById(c.characterCardId)?.let { it.remark.ifBlank { it.name } }
                    } else {
                        null
                    } ?: c.title.ifBlank { "会话" }
                    // ③ 同卡多会话个体 → 追加「·code尾4位」，身份唯一可辨
                    val extra = if ((cardCounts[c.characterCardId] ?: 0) > 1 && c.code.isNotBlank()) {
                        "·" + c.code.takeLast(4)
                    } else {
                        ""
                    }
                    map[c.id] = base + extra
                }
                emit(map)
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** 全部关系一览（T11.6：好感度数值/档位/互动次数，随互动实时刷新，供互动中心顶部展示） */
    val relationships: StateFlow<List<RelationshipOverview>> = relationshipDao.observeAll()
        .combine(convNames) { rels, names ->
            rels.map { r ->
                RelationshipOverview(
                    title = buildString {
                        append(names[r.convAId] ?: "会话")
                        append(" × ")
                        append(names[r.convBId] ?: "会话")
                    },
                    affinityScore = r.affinityScore,
                    tierLabel = ConversationRelationship.affinityTierLabel(r.affinityScore),
                    interactionCount = r.interactionCount,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 关系故事时间线（T11.13：全部共同经历事件倒序，随互动实时刷新，供互动中心「关系故事」区展示里程碑） */
    val relationshipEvents: StateFlow<List<RelationshipEventOverview>> = relationshipDao.observeAllEvents()
        .combine(convNames) { events, names ->
            events.map { e ->
                RelationshipEventOverview(
                    title = buildString {
                        append(names[e.convAId] ?: "会话")
                        append(" × ")
                        // T11.16：共同观看事件 convBId=null 表示用户侧，显示「你」（T12.10 取消哨兵 0，改可空列）
                        val peerB = e.convBId
                        append(if (peerB == null) "你" else (names[peerB] ?: "会话"))
                    },
                    scene = e.scene,
                    topic = e.topic,
                    mood = e.mood,
                    points = e.points,
                    source = e.source,
                    type = e.type,
                    happenedAt = e.happenedAt,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 组装列表条目（双方名 + 视角摘要 + 未读） */
    val listItems: StateFlow<List<InteractionListItem>> = combine(interactions, participants, convNames) { its, parts, names ->
        val byId = parts.groupBy { it.interactionId }
        its.map { inter ->
            val ps = byId[inter.id].orEmpty()
            val init = ps.firstOrNull { it.role == ParticipantRole.INITIATOR.name }
            val target = ps.firstOrNull { it.role == ParticipantRole.TARGET.name }
            val summary = init?.summary ?: target?.summary ?: ""
            val unread = ps.any { it.readAt == null }
            InteractionListItem(
                interaction = inter,
                title = buildString {
                    append(names[init?.convId] ?: "A")
                    append(" × ")
                    append(names[target?.convId] ?: "B")
                },
                summary = summary,
                unread = unread,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 可选作 A/B 的会话：已开放互动的会话（角色卡绑定且 interactionEnabled=true，新建默认 false） */
    val selectableConversations: StateFlow<List<Conversation>> = conversationDao.observeAll()
        .map { list -> list.filter { it.characterCardId != null && it.interactionEnabled } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 展示一次性提示 */
    private fun toast(msg: String) {
        viewModelScope.launch {
            toast.value = msg
            delay(2500)
            toast.value = null
        }
    }

    /** 切换全局总开关 */
    fun setGlobalEnabled(enabled: Boolean) {
        viewModelScope.launch { interactionConfigStore.setEnabled(enabled) }
    }

    /** T11.7：切换互动自动驱动开关 */
    fun setAutoEnabled(enabled: Boolean) {
        viewModelScope.launch { interactionConfigStore.update { it.copy(autoEnabled = enabled) } }
    }

    /** T11.7：设置自动驱动窗口（起止分钟），结束分钟不低于开始+1（避免空窗口） */
    fun setAutoWindow(startMinute: Int, endMinute: Int) {
        viewModelScope.launch {
            interactionConfigStore.update {
                it.copy(
                    autoWindowStartMinute = startMinute.coerceIn(0, 23 * 60 + 59),
                    autoWindowEndMinute = endMinute.coerceIn(startMinute + 1, 23 * 60 + 59),
                )
            }
        }
    }

    /** T11.7：设置自动驱动概率（0.1~1.0） */
    fun setAutoProbability(probability: Float) {
        viewModelScope.launch {
            interactionConfigStore.update { it.copy(autoProbability = probability.coerceIn(0.1f, 1f)) }
        }
    }

    /** 发起一场互动：A（发起方）→ B（目标） */
    fun startInteraction(initiatorId: Long, targetId: Long) {
        viewModelScope.launch {
            val outcome = interactionExecutor.start(initiatorId, targetId)
            toast(if (outcome.success) "互动完成" else outcome.message)
        }
    }

    /** 加载并展示某场互动的详情（互动中心点击行触发，T8.11 详情） */
    fun openDetail(interaction: Interaction) {
        viewModelScope.launch {
            val ps = interactionDao.participantsFor(interaction.id)
            val turns = interactionDao.turnsFor(interaction.id)
            // 参与者显示名：复用带短码的 convNames（同卡多会话可辨），兜底单次解析
            val names = convNames.value
            val nameMap = ps.associate { it.convId to (names[it.convId] ?: resolveName(it.convId)) }
            val title = buildString {
                append(nameMap[interaction.initiatorConvId] ?: "A")
                append(" × ")
                append(nameMap[interaction.targetConvId] ?: "B")
            }
            detail.value = InteractionDetail(title = title, participants = nameMap, turns = turns)
        }
    }

    /** 关闭详情 */
    fun closeDetail() {
        detail.value = null
    }

    /** 一次性解析会话显示名（会话备注优先，其次角色卡备注/名，兜底标题），复用 convNames 逻辑的 suspend 版 */
    private suspend fun resolveName(convId: Long): String {
        val conv = conversationDao.getById(convId) ?: return "会话"
        // ① 用户设置的会话备注最优先
        if (conv.remark.isNotBlank()) return conv.remark
        // ② 角色卡备注/名
        val cardId = conv.characterCardId
        if (cardId != null) {
            val card = characterCardDao.getById(cardId)
            if (card != null) {
                val name = card.remark.ifBlank { card.name }
                if (name.isNotBlank()) return name
            }
        }
        // ③ 会话标题兜底
        return conv.title.ifBlank { "会话" }
    }

    /** 标记某会话的所有互动已读（进入会话时消除红点） */
    fun markRead(conversationId: Long) {
        viewModelScope.launch {
            interactionDao.listParticipantsForConversation(conversationId)
                .filter { it.readAt == null }
                .forEach { interactionDao.markParticipantRead(it.id, System.currentTimeMillis()) }
        }
    }

    class Factory(private val app: EdenCourtApp) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return InteractionCenterViewModel(
                interactionDao = app.interactionDao,
                conversationDao = app.conversationDao,
                characterCardDao = app.characterCardDao,
                interactionConfigStore = app.interactionConfigStore,
                interactionExecutor = app.interactionExecutor,
                relationshipDao = app.relationshipDao,
            ) as T
        }
    }
}