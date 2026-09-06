package com.bdalxy.edencourt.feature.chat

import com.bdalxy.edencourt.data.local.entity.Conversation

/**
 * 跨会话打听「意图识别」纯逻辑（v1.2，T8.4 抽离，供 JVM 单测）。
 *
 * 背景：ChatSideEffects.detectConsult 内联了「触发词命中 → 点名定位 → 主题剥离」的解析，
 * 依赖 characterCardDao（取角色名表），解析本身无副作用却无法单测。此处把解析抽为纯函数：
 *  - [identify]：给定文本 + 会话列表 + 角色名→卡id 映射，返回打听请求或 null（未触发）
 *  - [splitTerms]：中文/常见标点分词（供主题检索用）
 *
 * 触发词（[CONSULT_TRIGGERS]）与 ChatSideEffects 保持同一份，避免两处漂移。
 * 函数不触达 DAO/网络，输入→输出确定，可 JVM 断言。
 */
data class ConsultRequest(
    val targetConversationId: Long?,
    val question: String,
)

object ConsultParser {

    /**
     * 跨会话打听触发词（T8.4 意图识别：命中任一即视为用户触发打听）。
     * T12.x P1 修复：剔除「问问」「去问」等日常口语高频词——「我想问问你最近怎么样」这类
     * 对着当前角色随口一问的句子，会被误判成「去打听别的角色」，触发跨会话改写与转述。
     * 保留的是明确表达「向第三方打听」的委托词（去打听/帮我打听/打听一下/帮我问/打探/探探口风）。
     */
    val CONSULT_TRIGGERS = listOf(
        "去打听", "帮我打听", "打听一下", "帮我问",
        "打探", "探探口风",
    )

    /**
     * 解析用户消息是否为「跨会话打听」意图。
     * @param text 用户消息原文
     * @param conversations 当前全部会话（用于在非当前会话中定位被点名的角色）
     * @param conversationId 当前会话 id（打听目标必须是「别的」会话）
     * @param cardNameById 角色卡 id → 显示名（备注优先，无备注用卡名）映射
     * @return 命中触发词则返回打听请求；未命中返回 null（普通对话）
     */
    fun identify(
        text: String,
        conversations: List<Conversation>,
        conversationId: Long,
        cardNameById: Map<Long, String>,
    ): ConsultRequest? {
        // 命中任一触发词才算打听意图
        if (CONSULT_TRIGGERS.none { text.contains(it) }) return null

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

        // 按长度降序剥离所有命中触发词（长词优先，避免残留短触发词片段），
        // 修正原 firstOrNull 只剥第一个导致「帮我打听一下」等组合词残留的问题
        var question = text
        CONSULT_TRIGGERS.sortedByDescending { it.length }.forEach { trigger ->
            question = question.replace(trigger, "")
        }
        // 剥离口语语气词「一下」（打听祈使句里常见，避免主题残留虚词影响检索）
        question = question.replace("一下", "").trim()

        // 剥离被点名的目标角色名
        matchedName?.let { question = question.replace(it, "").trim() }

        // 剥离当前会话角色名：点名当前角色时不作打听目标，但名字也不该残留在主题里参与检索
        val selfCardId = conversations.firstOrNull { it.id == conversationId }?.characterCardId
        val selfName = selfCardId?.let { cardNameById[it] }
        selfName?.let { question = question.replace(it, "").trim() }

        return ConsultRequest(targetConversationId = targetId, question = question.ifBlank { text })
    }

    /** 分词：按中文/英文标点与空白切分（供 T8.1 主题检索定位候选会话） */
    fun splitTerms(question: String): List<String> =
        question.split(Regex("[，。,.！!？?、\\s]+")).filter { it.isNotBlank() }
}