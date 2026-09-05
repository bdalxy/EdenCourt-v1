package com.bdalxy.edencourt.feature.chat

import com.bdalxy.edencourt.data.local.dao.CharacterCardDao
import com.bdalxy.edencourt.data.local.dao.ConversationTraitsDao
import com.bdalxy.edencourt.data.local.dao.MoodDao
import com.bdalxy.edencourt.data.local.dao.RelationshipDao
import com.bdalxy.edencourt.data.local.dao.UserIdentityDao
import com.bdalxy.edencourt.data.local.dao.WorldInfoDao
import com.bdalxy.edencourt.data.local.entity.Conversation
import com.bdalxy.edencourt.data.local.entity.RelationDirection
import com.bdalxy.edencourt.data.local.entity.Message
import com.bdalxy.edencourt.data.local.entity.WorldInfoEntry
import com.bdalxy.edencourt.data.remote.ChatMessage
import com.bdalxy.edencourt.data.repository.ChatRepository
import com.bdalxy.edencourt.data.repository.MemoryRepository
import com.bdalxy.edencourt.data.schedule.CharacterSchedule
import com.bdalxy.edencourt.data.schedule.LifeState
import com.bdalxy.edencourt.data.setting.ScheduleConfigStore
import com.bdalxy.edencourt.domain.CharacterCard
import com.bdalxy.edencourt.domain.CharacterCardParser
import com.bdalxy.edencourt.domain.ContextBudget
import com.bdalxy.edencourt.domain.PromptBuilder
import com.bdalxy.edencourt.domain.UserAffinity
import com.bdalxy.edencourt.domain.MoodRules
import com.bdalxy.edencourt.domain.WorldInfoMatcher
import com.bdalxy.edencourt.domain.buildDirectionFragment
import java.time.LocalTime
import kotlinx.coroutines.flow.first

/**
 * 聊天 Prompt 组装器（T15.6 拆分：把 ChatViewModel 的「Prompt 片段构建」职责抽离为独立类）。
 *
 * 职责：① 解析会话绑定角色卡（boundCard）；② 拼装系统 prompt（含用户自设身份）；
 * ③ 组装各类注入片段——长期记忆、共同经历关系事件、此刻生活状态、会话独有人格；
 * ④ 最终 buildPrompt 注入到 ChatViewModel 的回流生成请求。
 *
 * 与 ChatViewModel 的边界：本类只做「数据读取 + 纯组装 + 返回成品」，不持有任何 UI 状态、
 * 不写入任何数据库；需要的外部开关（作息总开关、陪看注入文本）由调用方以参数传入。
 * 行为与拆分前完全一致，仅变更代码所在类。
 */
class ChatPromptAssembler(
    private val conversationId: Long,
    private val repository: ChatRepository,
    private val characterCardDao: CharacterCardDao,
    private val userIdentityDao: UserIdentityDao,
    private val conversationTraitsDao: ConversationTraitsDao,
    private val relationshipDao: RelationshipDao,
    private val memoryRepository: MemoryRepository,
    private val scheduleConfigStore: ScheduleConfigStore,
    private val moodDao: MoodDao,
    private val worldInfoDao: WorldInfoDao,
) {

    /**
     * 组装完整 Prompt（T5.4 记忆检索注入 + T6.9 攒批提示）。
     * 用用户输入里的词做关键词检索长期记忆（top-K=3），拼成
     * 「[关于用户的长期记忆]- …」片段附加到 system 末尾；检索为空则不影响。
     * @param userBatchCount 本次来信合并的用户消息条数（>1 表示连发攒批，T6.9）
     * @param scheduleEnabled 角色作息功能全局开关（T11.11：两级开关第一级开启才注入此刻状态）
     * @param watchModeText 陪看模式注入文本（T11.16：用户在分享内容时角色以陪看口吻回应；未触发为空串）
     */
    suspend fun buildPrompt(
        history: List<Message>,
        userInput: String,
        userBatchCount: Int,
        scheduleEnabled: Boolean,
        watchModeText: String,
    ): List<ChatMessage> {
        val memoryFragment = buildLongTermMemoryFragment(userInput)
        return PromptBuilder.build(
            history = history,
            userInput = userInput,
            systemPrompt = currentSystemPrompt(),
            longTermMemories = memoryFragment,
            userBatchCount = userBatchCount,
            // T11.11：注入当前角色生活状态，让 AI 按状态调整语气与内容
            lifeStateText = currentLifeStateText(scheduleEnabled),
            // T11.13：注入「共同经历事件」——角色记得自己和其他会话角色共同经历过的事，对话有连续性
            relationshipEventsText = buildRelationshipEventFragment(),
            // T11.16：注入「陪看模式」——用户正在分享内容，角色以陪看口吻回应（未触发则为空）
            watchModeText = watchModeText,
            // T13.5：注入「会话独有人格」——同一张卡的不同会话角色言行差异化（老会话无 traits 为空，兼容旧行为）
            traitsPrompt = buildTraitsFragment(),
            // A1：注入「用户↔角色亲密度」——角色按与用户的亲疏自然调整口吻（0=陌生/高=亲密）
            affinityText = buildAffinityFragment(),
            // R4：注入「关系方向」——角色按关系方向自然调整口吻（方向隐式、不宣告；NONE 为空不注入）
            directionText = directionFragment(),
            // A2：注入「角色此刻情绪」——自主/交互双源合成，反哺语气
            moodText = buildMoodFragment(scheduleEnabled),
            // A3：注入「世界书」——恒定条目(恒常规则) + 命中条目(关键词触发)，
            //    v1.5 上下文注入层《①恒/③恒/⑤弹》交给 PromptBuilder 统一按优先级链与预算组装
            constantWorldInfoText = buildConstantWorldFragment(history, userInput),
            worldInfoText = buildMatchedWorldFragment(history, userInput),
            // v1.5 统一上下文注入预算：恒久身份1400 + 恒久世界书400 + 相关性弹性(总3200-1800)；旧行为兼容可用 null
            budget = ContextBudget(),
        )
    }

    /**
     * A3：读取本会话挂载的世界书条目（未挂载/无条目返回空列表）。
     * 恒定条目与命中条目分离：恒定走「③恒久世界规则」每次都注入；命中走「⑤相关性知识」按关键词触发。
     */
    private suspend fun loadWorldEntries(history: List<Message>, userInput: String): List<WorldInfoEntry> {
        val conv = repository.getConversation(conversationId) ?: return emptyList()
        val bookId = conv.worldBookId ?: return emptyList()
        val entries = worldInfoDao.getEntriesByBook(bookId)
        return entries.filter { it.enabled }
    }

    /**
     * A3：构建「恒定世界书」注入片段（③恒久世界规则）。
     * constant=true 的条目是最核心的世界规则，每次必注入，不随关键词命中浮动（不参与⑤的弹性截断）。
     */
    private suspend fun buildConstantWorldFragment(history: List<Message>, userInput: String): String {
        val constant = loadWorldEntries(history, userInput).filter { it.constant }
        if (constant.isEmpty()) return ""
        return WorldInfoMatcher.buildFragment(constant.sortedBy { it.priority })
    }

    /**
     * A3：构建「世界书命中条目」注入片段（⑤相关性知识，弹性配额）。
     * 非恒定条目靠关键词触发：扫描最近对话与当前输入命中主关键词（含副关键词收窄），
     * 经 priority 排序后拼装注入。预算截断由 PromptBuilder 按统一预算（ContextBudget）负责。
     */
    private suspend fun buildMatchedWorldFragment(history: List<Message>, userInput: String): String {
        val entries = loadWorldEntries(history, userInput).filter { !it.constant }
        if (entries.isEmpty()) return ""
        val texts = history.takeLast(DEFAULT_WORLD_SCAN_HISTORY).map { it.content } + userInput
        val matched = WorldInfoMatcher.matched(entries, texts)
        return WorldInfoMatcher.buildFragment(matched.sortedBy { it.priority })
    }

    /** A3：世界书关键词扫描的历史条数（轻量初版：只扫最近几轮，兼顾命中率与开销） */
    private companion object {
        const val DEFAULT_WORLD_SCAN_HISTORY = 10
    }

    /**
     * A1：构建「用户↔本会话角色亲密度」注入片段。
     * 亲密度存于 conversation.affinityScore，随对话非线性增长；分档复用 affinityTierLabel。
     * 未绑定角色卡（默认助手）或 value=0 时也注入「陌生」分档，让口吻有起点；异常读取返回空串兜底。
     */
    private suspend fun buildAffinityFragment(): String {
        val conv = repository.getConversation(conversationId) ?: return ""
        return UserAffinity.buildAffinityFragment(conv.affinityScore)
    }

    /**
     * R4：构建「关系方向」注入片段（v2.0 关系引擎：方向反哺口吻）。
     * 读取 conversation.relationDirection 转成口吻引导；NONE（未明确）或解析失败返回空串不注入。
     */
    private suspend fun directionFragment(): String {
        val conv = repository.getConversation(conversationId) ?: return ""
        val direction = runCatching { RelationDirection.valueOf(conv.relationDirection) }.getOrNull()
        return direction?.let { buildDirectionFragment(it) }.orEmpty()
    }

    /**
     * 取会话绑定的角色卡（T4.6）。
     * 未绑定（characterCardId 为空）或卡片被删/解析失败时返回 null（回退默认助手）。
     */
    suspend fun boundCard(): CharacterCard? {
        val conv = repository.getConversation(conversationId) ?: return null
        val cardId = conv.characterCardId ?: return null
        val entity = characterCardDao.getById(cardId) ?: return null
        return runCatching { CharacterCardParser.parse(entity.jsonContent) }.getOrNull()
    }

    /** 取当前会话绑定的角色卡 id（用于记忆归属/检索过滤，T6.13）。未绑定返回 null（默认助手不注入长期记忆） */
    suspend fun currentCardId(): Long? =
        repository.getConversation(conversationId)?.characterCardId

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
     * T13.5：读取本会话的「独有人格」并生成注入片段（印象摘要 + 反差对 + 自补充引导）。
     * 无 traits（默认助手/老会话）返回空串，不改变既有 prompt；依恋度阈值触发反差由 PromptBuilder 按
     * 本会话 attachment 数值判断（老会话 attachment=0 < 阈值 65，即反差不外露，与旧行为一致）。
     */
    private suspend fun buildTraitsFragment(): String {
        val traits = conversationTraitsDao.getByConversation(conversationId) ?: return ""
        return PromptBuilder.buildTraitsPrompt(traits)
    }

    /**
     * T11.13：本会话最近参与的「共同经历事件」注入片段（最多 3 条，含与其他会话角色的自动/手动互动）。
     * 让角色记得「我们共同经历过什么」，对话时自然带出，形成关系连续性。
     */
    private suspend fun buildRelationshipEventFragment(): String {
        val events = relationshipDao.recentEventsForConversation(conversationId, 3)
        if (events.isEmpty()) return ""
        return buildString {
            append("【你和朋友们共同经历过的事】\n")
            events.forEach { e ->
                // T12.10：convBId 可空——共同观看（WATCH）事件对方是「你」（无角色会话），其余为另一角色会话
                val peerId = if (e.convAId == conversationId) e.convBId else e.convAId
                val peerName = if (peerId == null) "你" else {
                    repository.getConversation(peerId)?.let { peerDisplayName(it) } ?: return@forEach
                }
                append("- 你和").append(peerName).append("一起")
                if (e.scene.isNotBlank()) append(e.scene)
                if (e.topic.isNotBlank()) append("「").append(e.topic.take(20)).append("」")
                append("。\n")
            }
        }.trim()
    }

    /** T11.13：解析共同经历对方会话的显示名（会话备注优先，其次角色卡备注/名，兜底标题） */
    private suspend fun peerDisplayName(peer: Conversation): String {
        if (peer.remark.isNotBlank()) return peer.remark
        val cardId = peer.characterCardId ?: return peer.title.ifBlank { "朋友" }
        val card = characterCardDao.getById(cardId) ?: return peer.title.ifBlank { "朋友" }
        return card.remark.ifBlank { card.name }
    }

    /**
     * 取当前角色「此刻状态」注入文本（T11.11：状态驱动对话）。
     * 两级开关（全局 + 本会话）都开启才注入，否则状态体系未生效、不给 AI 塞无意义状态；
     * 生效作息按 resolveConfig 会话级解析（会话自定义 > 角色自定义 > 个性派生 > 基础）。
     */
    private suspend fun currentLifeStateText(scheduleEnabled: Boolean): String {
        val state = computeLifeState(scheduleEnabled) ?: return ""
        return when (state) {
            LifeState.SLEEPING -> "你此刻正在睡眠中，被吵醒了会带着睡意和起床气，说话迷迷糊糊、懒洋洋的。"
            LifeState.WAKING -> "你此刻刚起床不久，精神正在恢复，回复带着清晨的清爽与活力。"
            LifeState.ACTIVE -> "你此刻正处于活跃时段，精力充沛，回复干脆利落、不拖沓。"
            LifeState.EVENING -> "你此刻正处在晚间放松时段，语气放松惬意，像忙完一天后的闲聊。"
        }
    }

    /**
     * A2：构建「角色此刻情绪」注入片段（自主 + 交互双源合成）。
     * - 自主基线：作息开启时由生活状态派生（睡/醒/活跃/晚间）；否则归为「平静」。
     * - 交互情绪：用户言行触发的情绪（未过期则以交互为主；已过期回落自主基线）。
     * 合成本逻辑在 MoodRules.resolve（纯函数，可单测）；此处只负责读取与拼装，不写库。
     */
    private suspend fun buildMoodFragment(scheduleEnabled: Boolean): String {
        val mood = moodDao.get(conversationId)
        val baseline = MoodRules.baselineFor(computeLifeState(scheduleEnabled))
        val resolved = MoodRules.resolve(mood, System.currentTimeMillis(), baseline)
        return MoodRules.buildMoodText(resolved.tag, resolved.intensity)
    }

    /**
     * 计算本会话角色当前生活状态（作息两级开关都开启才计算；否则 null）。
     * 供「此刻状态」与「情绪自主基线」复用，避免重复解析逻辑（A2）。
     */
    private suspend fun computeLifeState(scheduleEnabled: Boolean): LifeState? {
        if (!scheduleEnabled) return null
        val conv = repository.getConversation(conversationId) ?: return null
        if (!conv.lifeEnabled) return null
        val cardEntity = conv.characterCardId?.let { characterCardDao.getById(it) } ?: return null
        val cfg = CharacterSchedule.resolveConfig(scheduleConfigStore.config.first(), cardEntity, conv)
        return CharacterSchedule.computeState(cfg, LocalTime.now())
    }

    /**
     * 拼装注入当前角色卡的长期记忆片段（T6.13 按角色卡隔离）。
     * 修复原「用户输入分词 LIKE 检索」：中文整句几乎无法命中记忆里的短句，
     * 导致「AI 竟然不知道用户告知过的事实」。现直接取当前角色卡名下的记忆注入，
     * 既保证「AI 记得」，又做到不同角色卡之间记忆互不可见。
     * @param query 当前用户消息（方案 F v1）：传给检索排序做相关性加权，
     *              让与当前话题相关的记忆优先注入，强化「它记得我说过的 X」。
     */
    private suspend fun buildLongTermMemoryFragment(query: String? = null): String {
        val cardId = currentCardId() ?: return ""
        // T7.5 会话级私有定稿：注入 = 该角色共享记忆 + 本会话自己的私有记忆（跨会话私有不串）
        val memories = memoryRepository.getInjectableMemories(cardId, conversationId, query = query)
        if (memories.isEmpty()) return ""
        return buildString {
            append("[关于用户的长期记忆]\n")
            memories.forEach { m ->
                append("- ").append(m.content).append("\n")
            }
            // 方案 F v2「触发增强」：当前用户消息与某条记忆主题相关时，追加「主动提起」引导，
            // 让角色自然带出记忆（而不只是被动当背景知识），强化「它记得我说过的 X」。
            // 无关时返回 null，不注入引导、完全兼容旧行为。
            val proactive = memoryRepository.findProactiveReference(cardId, conversationId, query)
            if (proactive != null) {
                append("\n【你可以主动提起】")
                    .append(proactive.content)
                    .append("。如果合适，自然地提起这件事，让对话更有连续性；不要生硬地背诵。")
            }
        }.trim()
    }
}