package com.bdalxy.edencourt.feature.chat

import com.bdalxy.edencourt.data.local.entity.Message
import com.bdalxy.edencourt.data.local.entity.Role

/**
 * 回复泵「攒批决策」纯逻辑（T6.9 攒批核心抽离，供 JVM 单测）。
 *
 * 背景：回复泵（ensureReplyPumpRunning）原本把「选本轮待回复的用户消息 + 合并为一次来信」
 * 的内联在 ChatViewModel 里，无法单测。此对象把这两个**无副作用的决策**抽为纯函数：
 *  1. [selectNewUserMessages]：按「上次已处理游标」选出去程之后的所有用户消息（连发一次取到，实现攒批）
 *  2. [buildBatchInput]：把攒批的多条消息合并为一次「来信」正文（以换行分隔）
 *
 * 二者均不触达 Room/DataStore/网络，输入→输出确定，可用 mock 数据在 JVM 直接断言，
 * 覆盖「单发」「连发攒批」「游标推进后的断点续扫」「跳过 AI/系统消息」等回归场景。
 */
object ReplyPumpCalculator {

    /**
     * 选出本轮应处理的「新用户消息」。
     * @param history 当前会话全部消息（按创建时间正序，id 递增）
     * @param lastProcessedId 上次已处理的用户消息 id；null 表示从未处理（进程重建/首次）
     * @return 位于游标之后的所有 USER 消息（连发会被一次性取到，实现攒批）。
     *         若为 null 游标，则取全部 USER 消息（由调用方保证首个游标已从持久化恢复，
     *         避免把历史全量当新来信——T12.4 已在调用方兜底）。
     */
    fun selectNewUserMessages(history: List<Message>, lastProcessedId: Long?): List<Message> =
        history.filter { msg ->
            msg.role == Role.USER &&
                (lastProcessedId == null || msg.id > lastProcessedId)
        }

    /**
     * 合并多条用户消息为一次「来信」输入（T6.9）。
     * 单发返回原文；连发以换行分隔合并，交 AI 由它按性格决定逐条/综合/分组回应。
     * @param messages 本轮待回复的用户消息（应非空，由调用方保证）
     * @return 合并后的输入正文
     */
    fun buildBatchInput(messages: List<Message>): String =
        messages.joinToString("\n") { it.content }

    /** 返回这批消息中最大 id（游标推进目标）；空列表返回 null 由调用方处理 */
    fun lastId(messages: List<Message>): Long? = messages.maxOfOrNull { it.id }
}