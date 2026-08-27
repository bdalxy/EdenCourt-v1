package com.bdalxy.edencourt.feature.chat

import com.bdalxy.edencourt.data.local.dao.RelationshipDao
import com.bdalxy.edencourt.data.local.entity.RelationshipEvent

/**
 * 共同内容会话「一起看」：用户主动分享内容 → 角色陪看（T11.16，T12.9 从 ChatViewModel 抽出）。
 *
 * 职责：① 检测用户消息是否为「分享内容」（触发词或超长粘贴）；② 提供陪看模式 prompt 注入片段；
 * ③ AI 回复完成后把「用户分享 + 角色陪看」沉淀为共同经历关系事件（type=WATCH）。
 *
 * 红线：只处理用户**主动发来**的消息，绝不读取设备上正在看什么（前台 App/剪贴板监控=越权）。
 *
 * 状态说明：pendingWatch（本次待沉淀）与 lastWatchEventAt（冷却时间戳）为本类内部状态，
 * 与 ChatViewModel 的生成生命周期联动——发送时 detect() 记录、回复完成 settle() 消费、
 * 生成失败 clear() 作废，避免残留注入到下次普通消息。
 */
class WatchTogetherDetector(
    private val conversationId: Long,
    private val relationshipDao: RelationshipDao,
) {

    /** 一次用户分享 + 角色陪看回复（沉淀为共同经历事件用） */
    private data class WatchShare(
        val topic: String,      // 内容主题（提取自分享语句）
        val userContent: String, // 用户分享的原文（作为讨论要点素材）
    )

    /** 本次待沉淀的共同观看事件（发送时检测到分享 → AI 回复完成后沉淀，T11.16） */
    private var pendingWatch: WatchShare? = null

    /** 最近一次沉淀「共同观看」事件的时间戳（本会话，防同一次陪看刷多事件） */
    private var lastWatchEventAt: Long = 0L

    /** 陪看触发词：明确表达「我在看/分享」意图的短语（命中即视为分享内容） */
    private val watchTriggers = listOf(
        "我在看", "在追", "刚看完", "看完了", "追到", "更新了", "新出了", "出新",
        "分享", "发你", "给你看", "一起看", "这段剧情", "这个剧情", "这段",
        "这部", "这篇", "这个番", "这本小说", "这本漫画", "最新章节", "最新一集",
        "看到第", "看到哪", "看到这里",
    )

    /** 粘贴正文判定阈值：无明确触发词但消息超长（如整段粘贴的小说/剧情文字）也视为分享 */
    private val WATCH_PASTE_MIN_LENGTH = 60

    /** 同一次陪看沉淀冷却（毫秒）：10 分钟内不重复沉淀，避免连发刷多事件 */
    private val WATCH_EVENT_COOLDOWN_MS = 10 * 60 * 1000L

    /**
     * 检测用户消息是否为「分享内容」并记录为待沉淀（发送消息时调用，T11.16）。
     * - 规则 1：命中陪看触发词（我在看 XX / 分享 / 这段剧情…）→ 提取触发词后的内容为主题；
     * - 规则 2：无触发词但文本超长（粘贴整段正文/小说/截图说明）→ 取开头片段为主题。
     * @return 检测到分享则返回主题（供 prompt 注入与事件 topic），否则 null
     */
    fun detect(text: String): String? {
        val share = detectWatchContent(text)?.let { WatchShare(it, text) } ?: return null
        pendingWatch = share
        return share.topic
    }

    /** 陪看模式注入片段（buildPrompt 调用）：未触发分享时返回空串，不改变普通对话 prompt */
    fun injectionText(): String = pendingWatch?.let { buildWatchModeText(it.topic) } ?: ""

    /**
     * T11.16：AI 回复完成后，把本次「用户分享 + 角色陪看」沉淀为「共同经历」关系事件（finishReply 调用）。
     * - 复用 T11.13 关系事件表，type=WATCH（共同观看），convAId=本角色会话、convBId=null（用户侧，无第二会话；T12.10 由可空列表达、非哨兵 0）；
     * - 要点 = 用户分享原文摘要 + 角色陪看回复摘要；
     * - 冷却期内不重复沉淀（同一次陪看不刷多事件），每次新分享会覆盖待沉淀主题。
     */
    suspend fun settle(aiContent: String) {
        val share = pendingWatch ?: return
        pendingWatch = null // 立即消费，避免本次回复只沉淀一次
        val now = System.currentTimeMillis()
        if (now - lastWatchEventAt < WATCH_EVENT_COOLDOWN_MS) return // 冷却期内跳过
        lastWatchEventAt = now
        relationshipDao.insertEvent(
            RelationshipEvent(
                convAId = conversationId,
                convBId = null, // null=用户共同观看（无第二会话，区别于角色间互动，T12.10 非哨兵 0）
                scene = "你分享内容，一起看「${share.topic.take(12)}」",
                topic = share.topic.take(40),
                mood = "一起看",
                points = buildString {
                    append("你分享：").append(share.userContent.replace('\n', ' ').take(60))
                    if (aiContent.isNotBlank()) append("\n").append("角色回应：").append(aiContent.replace('\n', ' ').take(60))
                },
                type = RelationshipEvent.TYPE_WATCH,
                happenedAt = now,
            ),
        )
    }

    /** 生成失败时作废本次待沉淀（markFailed 调用），避免残留注入到下次普通消息 */
    fun clear() {
        pendingWatch = null
    }

    /**
     * 检测用户消息是否为「分享内容」→ 进入陪看模式（T11.16）。
     * - 规则 1：命中陪看触发词（我在看 XX / 分享 / 这段剧情…）→ 提取触发词后的内容为主题；
     * - 规则 2：无触发词但文本超长（粘贴整段正文/小说/截图说明）→ 取开头片段为主题。
     * @return 检测到分享则返回主题（供 prompt 注入与事件 topic），否则 null
     */
    private fun detectWatchContent(text: String): String? {
        val trigger = watchTriggers.firstOrNull { text.contains(it) }
        if (trigger != null) {
            // 规则 1：主题 = 触发词后的内容（如「我在看 海贼王」→「海贼王」）
            val after = text.substringAfter(trigger, "").trim().trimStart('：', ':', '，', ',', ' ')
            return extractWatchTopic(after.ifBlank { text })
        }
        // 规则 2：超长粘贴正文视为分享内容
        if (text.length >= WATCH_PASTE_MIN_LENGTH) return extractWatchTopic(text)
        return null
    }

    /** 提取分享内容主题：取文本前 20 字并去掉结尾标点（用于 prompt/事件 topic 的简洁锚点） */
    private fun extractWatchTopic(text: String): String {
        val head = text.trim().replace('\n', ' ').take(20)
            .trimEnd('。', '！', '？', '，', '；', '…', '!', '?', '.', ' ')
        return head.ifBlank { "你分享的内容" }
    }

    /** 陪看模式注入片段（T11.16）：让角色以陪看口吻回应讨论/吐槽/猜后续 */
    private fun buildWatchModeText(topic: String): String =
        "【陪看模式】用户正在和你分享内容、一起看「$topic」。" +
            "请像朋友一起追番追文那样回应：可以讨论剧情、吐槽角色、分享感想、猜后续发展，" +
            "并自然追问「看到哪了」「看完一定要告诉我结局」之类的跟进话。" +
            "记住你们是在一起看的同伴，不是你在单向解说，要像真人一样有来有回。"
}
