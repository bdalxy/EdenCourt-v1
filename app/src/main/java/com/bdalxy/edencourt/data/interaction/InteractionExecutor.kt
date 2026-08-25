package com.bdalxy.edencourt.data.interaction

import android.util.Log
import com.bdalxy.edencourt.data.local.dao.CharacterCardDao
import com.bdalxy.edencourt.data.local.dao.InteractionDao
import com.bdalxy.edencourt.data.local.dao.RelationshipDao
import com.bdalxy.edencourt.data.local.dao.UserIdentityDao
import com.bdalxy.edencourt.data.local.entity.CharacterCardEntity
import com.bdalxy.edencourt.data.local.entity.Conversation
import com.bdalxy.edencourt.data.local.entity.ConversationRelationship
import com.bdalxy.edencourt.data.local.entity.Interaction
import com.bdalxy.edencourt.data.local.entity.InteractionParticipant
import com.bdalxy.edencourt.data.local.entity.InteractionSource
import com.bdalxy.edencourt.data.local.entity.InteractionStatus
import com.bdalxy.edencourt.data.local.entity.InteractionTurn
import com.bdalxy.edencourt.data.local.entity.Message
import com.bdalxy.edencourt.data.local.entity.MessageType
import com.bdalxy.edencourt.data.local.entity.ParticipantRole
import com.bdalxy.edencourt.data.local.entity.RelationStatus
import com.bdalxy.edencourt.data.local.entity.Role
import com.bdalxy.edencourt.data.remote.AiChatService
import com.bdalxy.edencourt.data.remote.ChatMessage
import com.bdalxy.edencourt.data.repository.ChatRepository
import com.bdalxy.edencourt.data.repository.MemoryRepository
import com.bdalxy.edencourt.data.setting.ApiConfig
import com.bdalxy.edencourt.data.setting.ApiConfigStore
import com.bdalxy.edencourt.data.setting.InteractionConfigStore
import com.bdalxy.edencourt.domain.CharacterCardParser
import com.bdalxy.edencourt.domain.PromptBuilder
import kotlinx.coroutines.flow.firstOrNull

/**
 * 角色间互动执行器（v1.2b 互动系统，T8.9）。
 * 让两个会话个体 A、B 进行一场【双向真实对话】（A→B→A→…），而非单问单答。
 * - 状态机逐轮调 LLM：每轮只注入当前说话者「自己的可互动记忆」（该角色共享 + 该会话私有，且排除绝密），
 *   共享事实=双方已经说出口的对话历史本身，天然做到信息隔离（绝密内容双方都不感知，无法外泄）。
 * - 防跑偏：话题锚点（A 开场抛题，每轮携带）、轮次硬上限、历史只取最近几条、指令限定只说当前一句、≤60 字。
 * - 收敛：达到轮次上限或某轮自然结束（空白/结束标记）即停。
 * - 落位（T8.10）：写 interaction 主表 + 参与者表 + 轮次表；双方会话各插一条 INTERACTION_DIGEST 摘要；更新关系表互动计数。
 */
class InteractionExecutor(
    private val chatRepository: ChatRepository,
    private val characterCardDao: CharacterCardDao,
    private val memoryRepository: MemoryRepository,
    private val interactionDao: InteractionDao,
    private val relationshipDao: RelationshipDao,
    private val interactionConfigStore: InteractionConfigStore,
    private val apiConfigStore: ApiConfigStore,
    private val aiChatService: AiChatService,
    private val userIdentityDao: UserIdentityDao,
) {

    /** 一场互动最大台词句数（硬性上限，防死循环；约 2~3 个来回） */
    private val maxTurns = 6

    /** 每轮只带入最近多少句已说出口的话（共享事实，防上下文过长啰嗦） */
    private val historyLimit = 6

    /** 每轮注入说话者的可互动记忆条数上限 */
    private val memoryInjectLimit = 5

    /** 互动输出的应用级互斥：同一时间只允许一场互动执行 */
    @Volatile
    private var running = false

    /** 互动执行结果 */
    data class InteractionOutcome(
        val success: Boolean,
        val interactionId: Long? = null,
        val message: String = "",
    )

    /**
     * 发起一场互动：initiator 主动找 target 双向聊。
     * @param source 互动来源（T11.7）：MANUAL=手动 / AUTO=自动驱动，默认手动；写入互动主表便于区分/去重。
     * @return 成功则带 interactionId；失败返回 message 说明原因（供 UI 文案提示）。
     */
    suspend fun start(
        initiatorConvId: Long,
        targetConvId: Long,
        source: InteractionSource = InteractionSource.MANUAL,
    ): InteractionOutcome {
        // 1. 前置校验：全局开关 / API / 会话存在 / 双方均开放互动 / 非重复执行
        val cfg = interactionConfigStore.config.firstOrNull()
        if (cfg == null || !cfg.enabled) {
            return InteractionOutcome(false, message = "角色互动未开启（可在互动中心打开全局开关）")
        }
        if (initiatorConvId == targetConvId) return InteractionOutcome(false, message = "不能让同一个会话和自己互动")
        val config = apiConfigStore.config.firstOrNull()
        if (config == null || config.apiKey.isBlank()) return InteractionOutcome(false, message = "未配置 API，无法互动")
        val a = chatRepository.getConversation(initiatorConvId) ?: return InteractionOutcome(false, message = "发起会话不存在")
        val b = chatRepository.getConversation(targetConvId) ?: return InteractionOutcome(false, message = "目标会话不存在")
        if (!a.interactionEnabled || !b.interactionEnabled) {
            return InteractionOutcome(false, message = "存在未开放互动的会话（需在会话设置打开「可互动开关」）")
        }
        if (running) return InteractionOutcome(false, message = "已有互动正在执行，请稍等")

        running = true
        try {
            // 互动身份名（区分双方 + 对白历史标签，同卡多会话无备注时附短码）+ 摘要名（进用户会话消息，用自然角色名）
            val aIdentity = resolveIdentityName(a)
            val bIdentity = resolveIdentityName(b)
            val aPlain = resolvePlainName(a)
            val bPlain = resolvePlainName(b)
            val now = System.currentTimeMillis()

            // 同源判定 + 当前好感度：同卡告知双方"来自同一张角色卡"；氛围由好感度分档驱动（T11.6 取代按次数近似）
            val isSameCard = a.characterCardId != null && a.characterCardId == b.characterCardId
            val sameCardName = if (isSameCard) aPlain else ""
            val existingRel = relationshipDao.getBetween(a.id, b.id)
            val affinityScore = existingRel?.affinityScore ?: 0
            // RIFT 为人工负向态，保留不覆盖；其余按好感度分档推导当前关系氛围
            val relationStatus = if (existingRel?.relationStatus == RelationStatus.RIFT.name) {
                RelationStatus.RIFT.name
            } else {
                ConversationRelationship.relationStatusForAffinity(affinityScore)
            }

            // 2. 建主记录 + 两条参与者（未读）
            val interactionId = interactionDao.insertInteraction(
                Interaction(
                    initiatorConvId = a.id,
                    targetConvId = b.id,
                    status = InteractionStatus.RUNNING.name,
                    source = source.name,
                    startedAt = now,
                ),
            )
            interactionDao.insertParticipant(
                InteractionParticipant(interactionId = interactionId, convId = a.id, role = ParticipantRole.INITIATOR.name),
            )
            interactionDao.insertParticipant(
                InteractionParticipant(interactionId = interactionId, convId = b.id, role = ParticipantRole.TARGET.name),
            )

            // 3. 逐轮状态机：A 先行开话抛题，之后 A/B 交替
            val turns = mutableListOf<Pair<String, String>>() // (说话者显示名, 台词)
            var speaker: Conversation = a
            var topicSeed = ""
            var ended = false
            for (i in 0 until maxTurns) {
                if (ended) break
                val line = requestLine(config, a, b, aIdentity, bIdentity, speaker, turns, topicSeed, isOpening = i == 0 && speaker.id == a.id, isSameCard = isSameCard, relationStatus = relationStatus, affinityScore = affinityScore, sameCardName = sameCardName)
                if (line.isBlank()) { ended = true; break }
                if (i == 0 && speaker.id == a.id) topicSeed = line // 第一句 A 的开场即话题锚点
                turns.add(speaker.id.let { if (it == a.id) aIdentity else bIdentity } to line)
                interactionDao.insertTurn(InteractionTurn(interactionId = interactionId, turnIndex = i, speakerConvId = speaker.id, content = line))
                if (line.contains("（话题到此") || line.contains("（结束）")) { ended = true; break }
                speaker = if (speaker.id == a.id) b else a
            }

            // 4. 无任何台词的失败兜底
            if (turns.isEmpty()) {
                interactionDao.updateInteraction(
                    Interaction(
                        id = interactionId, initiatorConvId = a.id, targetConvId = b.id,
                        status = InteractionStatus.FAILED.name, source = source.name, startedAt = now, endedAt = now, turnCount = 0, createdAt = now,
                    ),
                )
                return InteractionOutcome(false, message = "聊不起来，双方没接上")
            }

            // 5. 关系：无则建（按首场好感度定档），有则累计好感度/互动次数/最近时间并递进关系标签
            upsertRelationshipProgress(a.id, b.id, now, turns.size)

            // 6. 参与者摘要（按会话视角）写入：摘要名用自然角色名（不带短码），进用户会话不显机械
            val aSummary = buildDigest(aPlain, bPlain, aIdentity, bIdentity, topicSeed, turns, fromA = true)
            val bSummary = buildDigest(aPlain, bPlain, aIdentity, bIdentity, topicSeed, turns, fromA = false)
            interactionDao.participantFor(interactionId, a.id)?.let { p ->
                interactionDao.updateParticipant(p.copy(summary = aSummary))
            }
            interactionDao.participantFor(interactionId, b.id)?.let { p ->
                interactionDao.updateParticipant(p.copy(summary = bSummary))
            }

            // 7. 收尾主记录 + 双方会话各插一条摘要消息（会话内可感知"有人来过"，不刷屏）
            interactionDao.updateInteraction(
                Interaction(
                    id = interactionId, initiatorConvId = a.id, targetConvId = b.id,
                    status = InteractionStatus.COMPLETED.name, topicSeed = topicSeed, source = source.name,
                    startedAt = now, endedAt = now, turnCount = turns.size, createdAt = now,
                ),
            )
            chatRepository.insertMessage(Message(conversationId = a.id, role = Role.ASSISTANT, type = MessageType.INTERACTION_DIGEST, content = aSummary))
            chatRepository.insertMessage(Message(conversationId = b.id, role = Role.ASSISTANT, type = MessageType.INTERACTION_DIGEST, content = bSummary))

            Log.d(TAG, "互动完成 #$interactionId：A(${a.id}) × B(${b.id}) 共 ${turns.size} 句，话题=${topicSeed.take(20)}")
            return InteractionOutcome(true, interactionId, "互动完成")
        } finally {
            running = false
        }
    }

    /** 请求当前说话者输出下一句台词（只注入说话者自己的记忆 + 共享对白历史） */
    private suspend fun requestLine(
        config: ApiConfig,
        a: Conversation, b: Conversation,
        aName: String, bName: String,
        speaker: Conversation,
        turns: List<Pair<String, String>>,
        topicSeed: String,
        isOpening: Boolean,
        isSameCard: Boolean,
        relationStatus: String,
        affinityScore: Int,
        sameCardName: String,
    ): String {
        val speakerName = if (speaker.id == a.id) aName else bName
        val otherName = if (speaker.id == a.id) bName else aName

        // 说话者人格底座
        val systemPrompt = buildSpeakerSystem(speaker)
        // 说话者自己的可互动记忆（共享 + 本会话私有，排除绝密）
        val memoryFragment = buildMemoryFragment(speaker)
        val rules = buildString {
            append("你现在身处一场【角色间对话】：你正和另一个会话里的一名角色交谈。\n")
            if (isSameCard) {
                // 同卡：明确告知双方来自同一张角色卡，并按关系标签决定初见/熟识氛围
                append("你的身份是「").append(speakerName).append("」，对方是「").append(otherName).append("」。\n")
                append("你们来自同一张角色卡，都是「").append(sameCardName).append("」——彼此最相似、几乎一样的存在。\n")
            } else {
                // 异卡：本来就是不同角色，正常独立对话
                append("你的身份是「").append(speakerName).append("」，对方是「").append(otherName)
                append("」。你们是两个独立的个体，你就是你、对方是对方。\n")
            }
            append(relationTone(relationStatus, isSameCard, affinityScore))
            append("规则：1) 只输出你这一句的台词，不要替对方说话，不要加括号说明或其他解释；\n")
            append("2) 只基于你自己的记忆和已发生的对话回应，不要凭空编造新信息；\n")
            append("3) 对方不想说的事、以及他人绝不可知的秘密，绝不要吐露，也不要去逼问；\n")
            append("4) 一句自然口语，不超过 60 字；\n")
            append("5) 说话用「我」自称，称呼对方用「你」；不要用「另一个XX」之类的说法；按你自己的性格自然地对待你们的关系；\n")
            append("6) 不要复述或模仿对方刚说过的话，按你自己的记忆和性格自然地回应；\n")
            if (isOpening) {
                append("7) 你是开场者：请主动抛出一个自然、你真心想和对方聊的话题（你的见闻/好奇/关心），作为这句台词。\n")
            } else {
                append("7) 若觉得聊得差不多、无话可说，用「（话题到此）」结尾并自然收场；若还有兴致就自然地接下去。\n")
            }
        }
        val finalSystem = buildString {
            append(systemPrompt)
            if (memoryFragment.isNotBlank()) append("\n\n").append(memoryFragment)
            append("\n\n").append(rules)
        }

        // 对话历史 = 双方已说出口的话（共享事实）
        val historyText = turns.takeLast(historyLimit).joinToString("\n") { (name, content) -> "$name：$content" }
        val userPrompt = buildString {
            append("以下是这场互动到目前为止的对白（共有事实）：\n")
            if (historyText.isBlank()) append("（还没有任何对白）\n")
            else append(historyText).append("\n")
            if (topicSeed.isNotBlank()) append("\n本次互动的话题点：").append(topicSeed).append("\n")
            append("\n现在轮到你（").append(speakerName).append("）发言。只输出你这一句台词：")
        }

        val messages = listOf(
            ChatMessage(role = "system", content = finalSystem),
            ChatMessage(role = "user", content = userPrompt),
        )
        return runCatching { aiChatService.completeChat(messages, config) }
            .getOrElse { e ->
                Log.w(TAG, "互动某轮失败：${e.javaClass.simpleName}: ${e.message}")
                ""
            }
            .trim()
    }

    /** 构造说话者人格 system（绑定角色卡则用其卡片 system，否则默认助手） */
    private suspend fun buildSpeakerSystem(conversation: Conversation): String {
        // T8.13：用户自设身份随 persona 注入（互动中角色同样按统一称呼对待用户）
        val identityText = userIdentityDao.get()?.let { PromptBuilder.buildUserIdentityPrompt(it) }.orEmpty()
        val cardId = conversation.characterCardId ?: return PromptBuilder.DEFAULT_SYSTEM_PROMPT
        val entity = characterCardDao.getById(cardId)
        val card = entity?.let { runCatching { CharacterCardParser.parse(it.jsonContent) }.getOrNull() }
        return if (card != null) PromptBuilder.buildCardSystemPrompt(card, identityText) else PromptBuilder.DEFAULT_SYSTEM_PROMPT
    }

    /** 说话者自己的可互动记忆片段：该角色共享 + 本会话私有，全部排除绝密（互动中双方都不感知绝密，硬性兜底） */
    private suspend fun buildMemoryFragment(conversation: Conversation): String {
        val cardId = conversation.characterCardId ?: return ""
        val memories = memoryRepository.getInjectableMemories(cardId, conversation.id, memoryInjectLimit)
            .filter { !it.isTopSecret }
        if (memories.isEmpty()) return ""
        return buildString {
            append("[你记得的事]\n")
            memories.forEach { m -> append("- ").append(m.content).append("\n") }
        }.trim()
    }

    /** 互动身份名：会话备注优先，其次角色卡备注/名；同卡多会话个体且无备注时附「·短码尾4位」保证双方可区分 */
    private suspend fun resolveIdentityName(conversation: Conversation): String {
        // ① 用户设置的会话备注最优先（自定义最自然）
        if (conversation.remark.isNotBlank()) return conversation.remark
        // ② 角色卡备注/名
        val cardId = conversation.characterCardId ?: return conversation.title.ifBlank { "会话" }
        val entity = characterCardDao.getById(cardId) ?: return conversation.title.ifBlank { "会话" }
        val base = entity.remark.ifBlank { entity.name }
        // ③ 同卡多会话个体且无备注 → 附短码尾4位，让 LLM 在对白历史标签里分清「哪个小夏说过什么」
        if (conversation.code.isNotBlank() && chatRepository.countConversationsByCard(cardId) > 1) {
            return base + "·" + conversation.code.takeLast(4)
        }
        return base
    }

    /** 自然摘要名：会话备注优先，其次角色卡备注/名（进用户会话消息用，不带短码） */
    private suspend fun resolvePlainName(conversation: Conversation): String {
        if (conversation.remark.isNotBlank()) return conversation.remark
        val cardId = conversation.characterCardId ?: return conversation.title.ifBlank { "会话" }
        val entity = characterCardDao.getById(cardId) ?: return conversation.title.ifBlank { "会话" }
        return entity.remark.ifBlank { entity.name }
    }

    /**
     * 更新关系表（T11.6 好感度数值体系）：
     * - 无关系 → 建：好感度=本场加成，关系标签按好感度定档，次数=1
     * - 有关系 → 累计好感度 += 本场加成，次数+1，标签按新好感度递进，刷新最近互动时间
     */
    private suspend fun upsertRelationshipProgress(aId: Long, bId: Long, now: Long, turnCount: Int) {
        val gain = ConversationRelationship.affinityGain(turnCount)
        val smallId = minOf(aId, bId)
        val bigId = maxOf(aId, bId)
        val existing = relationshipDao.getBetween(aId, bId)
        if (existing == null) {
            relationshipDao.upsert(
                ConversationRelationship(
                    convAId = smallId, convBId = bigId,
                    relationStatus = ConversationRelationship.relationStatusForAffinity(gain),
                    affinityScore = gain,
                    interactionCount = 1, lastInteractionAt = now,
                ),
            )
        } else {
            // 互动后好感度累计、次数+1，按新好感度递进关系标签（供互动中心展示；氛围在 start() 已按当前好感度推导）
            val newScore = existing.affinityScore + gain
            relationshipDao.touchInteraction(existing.id, now, ConversationRelationship.relationStatusForAffinity(newScore), newScore)
        }
    }

    /** 好感度分档 → 互动氛围引导（T11.6：数值分档驱动，取代按次数近似；角色性格已由 persona 注入，这里只定双方关系基调） */
    private fun relationTone(status: String, isSameCard: Boolean, affinityScore: Int): String {
        // RIFT 为人工负向态，优先保留其疏远氛围
        if (status == RelationStatus.RIFT.name) {
            return "你们之间曾有过不快，关系有些疏远：说话时带着一点距离和小心。\n"
        }
        // 好感度感知：让对话内容随数值更细腻（数值越高越亲近自然）
        val tier = ConversationRelationship.affinityTierLabel(affinityScore)
        val base = when (status) {
            RelationStatus.STRANGER.name ->
                if (isSameCard) "这是你们第一次见面：可以自然地惊讶、好奇这份相似，但简单带过即可，很快进入自然的聊天。\n"
                else "你们是第一次见面，彼此还很陌生：自然地寒暄、认识对方。\n"
            RelationStatus.ACQUAINTANCE.name -> "你们已经认识，还算不上很熟：带着一点熟悉和一点客套，自然地聊。\n"
            RelationStatus.FRIENDLY.name -> "你们是老朋友了，彼此熟悉又自在：像老朋友一样轻松地聊。\n"
            RelationStatus.CLOSE.name -> "你们非常亲密、无话不谈：可以聊很私密、很交心的话题。\n"
            else -> "自然地聊就好。\n"
        }
        return buildString {
            append("（你们之间的好感度约 ").append(affinityScore).append("，已到「").append(tier).append("」的关系。）\n")
            append(base)
        }
    }

    /**
     * 双方视角的一句话摘要（模板拼接，避免额外 LLM 调用；digest 插入双方会话）。
     * 注意：turns 里存的是带短码的 identity 名，故匹配对端首句用 identity；展示给用户用自然名（plain）。
     */
    private fun buildDigest(
        aName: String, bName: String,
        aIdentity: String, bIdentity: String,
        topic: String, turns: List<Pair<String, String>>, fromA: Boolean,
    ): String {
        val topicTrimmed = topic.trim().take(40)
        val peerFirstLine = if (fromA) {
            turns.firstOrNull { it.first == bIdentity }?.second?.take(30).orEmpty()
        } else {
            turns.firstOrNull { it.first == aIdentity }?.second.orEmpty().take(30)
        }
        return if (fromA) {
            buildString {
                append("（互动）今天我去找了").append(bName).append("聊：")
                append(if (topicTrimmed.isBlank()) "随便唠了唠" else topicTrimmed)
                if (peerFirstLine.isNotBlank()) append("。").append(bName).append("说：\"").append(peerFirstLine).append("”")
            }
        } else {
            buildString {
                append("（互动）").append(aName).append("今天来找我聊了聊")
                if (topicTrimmed.isNotBlank()) append("「").append(topicTrimmed).append("」")
            }
        }
    }

    private companion object {
        private const val TAG = "InteractionExecutor"
    }
}