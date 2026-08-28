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

    /** 跨会话打听触发词（T8.4 意图识别：命中任一即视为用户触发打听） */
    val CONSULT_TRIGGERS = listOf(
        "去打听", "帮我打听", "打听一下", "去问问", "帮我问",
        "打探", "探探口风", "问问", "去问",
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
        val trigger = CONSULT_TRIGGERS.firstOrNull { text.contains(it) } ?: return null
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

    /** 分词：按中文/英文标点与空白切分（供 T8.1 主题检索定位候选会话） */
    fun splitTerms(question: String): List<String> =
        question.split(Regex("[，。,.！!？?、\\s]+")).filter { it.isNotBlank() }
}