package com.bdalxy.edencourt.feature.chat

import com.bdalxy.edencourt.data.local.dao.CharacterCardDao
import com.bdalxy.edencourt.data.local.dao.ConversationTraitsDao
import com.bdalxy.edencourt.data.local.dao.RelationshipDao
import com.bdalxy.edencourt.data.local.dao.UserIdentityDao
import com.bdalxy.edencourt.data.local.entity.Conversation
import com.bdalxy.edencourt.data.local.entity.Message
import com.bdalxy.edencourt.data.remote.ChatMessage
import com.bdalxy.edencourt.data.repository.ChatRepository
import com.bdalxy.edencourt.data.repository.MemoryRepository
import com.bdalxy.edencourt.data.schedule.CharacterSchedule
import com.bdalxy.edencourt.data.schedule.LifeState
import com.bdalxy.edencourt.data.setting.ScheduleConfigStore
import com.bdalxy.edencourt.domain.CharacterCard
import com.bdalxy.edencourt.domain.CharacterCardParser
import com.bdalxy.edencourt.domain.PromptBuilder
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
        )
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
        if (!scheduleEnabled) return ""
        val conv = repository.getConversation(conversationId) ?: return ""
        if (!conv.lifeEnabled) return ""
        val cardEntity = conv.characterCardId?.let { characterCardDao.getById(it) } ?: return ""
        val cfg = CharacterSchedule.resolveConfig(scheduleConfigStore.config.first(), cardEntity, conv)
        return when (CharacterSchedule.computeState(cfg, LocalTime.now())) {
            LifeState.SLEEPING -> "你此刻正在睡眠中，被吵醒了会带着睡意和起床气，说话迷迷糊糊、懒洋洋的。"
            LifeState.WAKING -> "你此刻刚起床不久，精神正在恢复，回复带着清晨的清爽与活力。"
            LifeState.ACTIVE -> "你此刻正处于活跃时段，精力充沛，回复干脆利落、不拖沓。"
            LifeState.EVENING -> "你此刻正处在晚间放松时段，语气放松惬意，像忙完一天后的闲聊。"
        }
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