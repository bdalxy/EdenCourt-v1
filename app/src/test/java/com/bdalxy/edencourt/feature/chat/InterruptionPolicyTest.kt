package com.bdalxy.edencourt.feature.chat

import com.bdalxy.edencourt.data.local.entity.MessageStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 流式生成「打断处理」决策纯逻辑单测（G-③ 自动化回归·补聊天核心链路缺口）。
 * 覆盖 T5.10 打断作废（删空占位）、T12.2 打断 flush 内存（不丢最后一批增量）、
 * 以及「已被 finish/失败处理过则不动作」的防误删分支。
 */
class InterruptionPolicyTest {

    // ---------- 生成中 + 无内容 → DELETE ----------

    @Test
    fun `生成中且无任何内容_删除空占位_打断即作废`() {
        assertEquals(
            InterruptionAction.DELETE,
            InterruptionPolicy.decide(MessageStatus.GENERATING, "", ""),
        )
    }

    @Test
    fun `生成中仅思考过程非空_flush完成_保留思考内容`() {
        // 原逻辑：content/reasoning「两者都空」才删；仅 reasoning 非空也走 flush，保留思考过程
        assertEquals(
            InterruptionAction.FLUSH_DONE,
            InterruptionPolicy.decide(MessageStatus.GENERATING, "", "思考中…"),
        )
    }

    // ---------- 生成中 + 有内容 → FLUSH_DONE ----------

    @Test
    fun `生成中已有正文_flush完成_不丢增量`() {
        assertEquals(
            InterruptionAction.FLUSH_DONE,
            InterruptionPolicy.decide(MessageStatus.GENERATING, "你好呀", ""),
        )
    }

    @Test
    fun `生成中正文与思考都有_flush完成`() {
        assertEquals(
            InterruptionAction.FLUSH_DONE,
            InterruptionPolicy.decide(MessageStatus.GENERATING, "你好呀", "思考中…"),
        )
    }

    // ---------- 已非生成中 → SKIP ----------

    @Test
    fun `消息已完成_不动作_避免误改已完成的回复`() {
        assertEquals(
            InterruptionAction.SKIP,
            InterruptionPolicy.decide(MessageStatus.DONE, "完整回复", ""),
        )
    }

    @Test
    fun `消息已失败_不动作`() {
        assertEquals(
            InterruptionAction.SKIP,
            InterruptionPolicy.decide(MessageStatus.FAILED, "", ""),
        )
    }

    @Test
    fun `消息仍为发送中_不动作`() {
        assertEquals(
            InterruptionAction.SKIP,
            InterruptionPolicy.decide(MessageStatus.SENT, "", ""),
        )
    }
}