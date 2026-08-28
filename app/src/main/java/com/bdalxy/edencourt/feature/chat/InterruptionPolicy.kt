package com.bdalxy.edencourt.feature.chat

import com.bdalxy.edencourt.data.local.entity.MessageStatus

/**
 * 流式生成「被打断时如何处理」决策纯逻辑（T5.10 打断 + T12.2 内存 flush 抽离，供 JVM 单测）。
 *
 * 背景：ChatViewModel.streamReply 的 CancellationException 分支内联了「删占位 / flush 完成 / 跳过」
 * 的决策，无法单测。此处把该**无副作用**决策抽为纯函数 [decide]：
 *  - 当前 AI 消息已非「生成中」（已被 finish/失败处理过）→ [InterruptionAction.SKIP]：不动它
 *  - 仍是生成中但无任何内容 → [InterruptionAction.DELETE]：删除空占位，避免残留「正在输入」气泡
 *  - 仍是生成中且已有内容 → [InterruptionAction.FLUSH_DONE]：flush 内存最新值并标记完成（不视为失败）
 *
 * 函数不触达 Room/网络，输入→输出确定，可 JVM 断言；执行侧（delete/update 调用）仍留在调用方。
 */
enum class InterruptionAction {
    /** 不动作：消息已被其他路径处理过，避免误删/误改 */
    SKIP,
    /** 删除空占位消息（打断即作废，T5.10） */
    DELETE,
    /** flush 内存最新内容并标记完成（T12.2 打断时不丢最后一批增量） */
    FLUSH_DONE,
}

object InterruptionPolicy {

    /**
     * 决定打断时对当前 AI 占位消息的处理。
     * @param currentStatus 当前 AI 消息状态（从库中读取的最新值）
     * @param latestContent 内存累积的正式内容（最新，可能领先于 DB）
     * @param latestReasoning 内存累积的思考过程（最新）
     */
    fun decide(
        currentStatus: MessageStatus,
        latestContent: String,
        latestReasoning: String,
    ): InterruptionAction =
        when {
            // 已非生成中（被 finish/失败处理过）：不动作，避免误删已完成的回复
            currentStatus != MessageStatus.GENERATING -> InterruptionAction.SKIP
            // 生成中但无任何内容：删空占位，避免残留「正在输入」气泡
            latestContent.isBlank() && latestReasoning.isBlank() -> InterruptionAction.DELETE
            // 生成中且已有内容：flush 内存最新值并标记完成
            else -> InterruptionAction.FLUSH_DONE
        }
}