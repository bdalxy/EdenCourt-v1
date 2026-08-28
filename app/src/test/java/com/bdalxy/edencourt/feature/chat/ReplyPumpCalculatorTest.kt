package com.bdalxy.edencourt.feature.chat

import com.bdalxy.edencourt.data.local.entity.Message
import com.bdalxy.edencourt.data.local.entity.MessageStatus
import com.bdalxy.edencourt.data.local.entity.Role
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 回复泵「攒批决策」纯逻辑单测（G-③ 自动化回归·补聊天核心链路缺口）。
 * 覆盖 T6.9 攒批合并、T12.4 游标断点续扫、跳过 AI/系统消息、单发/连发等关键分支，
 * 防止今后重构回复泵状态机时回归（该类逻辑曾在无测试情况下多次踩坑）。
 */
class ReplyPumpCalculatorTest {

    /** 构造一条测试消息（便捷 helper：只关心 id / role / content，其余取默认） */
    private fun msg(id: Long, role: Role, content: String): Message =
        Message(id = id, conversationId = 1L, role = role, content = content, status = MessageStatus.DONE)

    // ---------- selectNewUserMessages 分支 ----------

    @Test
    fun `单发一条用户消息时选中该用户消息`() {
        val history = listOf(
            msg(1, Role.USER, "在吗"),
        )
        val result = ReplyPumpCalculator.selectNewUserMessages(history, null)
        assertEquals(listOf(history[0]), result)
    }

    @Test
    fun `游标为null时选中全部用户消息_并跳过AI与系统消息`() {
        val history = listOf(
            msg(1, Role.SYSTEM, "sys"),
            msg(2, Role.USER, "在吗"),
            msg(3, Role.ASSISTANT, "在的"),
            msg(4, Role.USER, "今天天气如何"),
            msg(5, Role.ASSISTANT, "很好"),
        )
        val result = ReplyPumpCalculator.selectNewUserMessages(history, null)
        // 只取 USER 消息，跳过 SYSTEM/AI，且保持时间序
        assertEquals(listOf(2L, 4L), result.map { it.id })
    }

    @Test
    fun `游标推进后只选游标之后的新用户消息_实现断点续扫`() {
        val history = listOf(
            msg(1, Role.USER, "在吗"),
            msg(2, Role.ASSISTANT, "在的"),
            msg(3, Role.USER, "连发第二条"),
            msg(4, Role.USER, "连发第三条"),
        )
        // 游标已处理到 id=1，只应选中 id=3、4 两条（连发攒批）
        val result = ReplyPumpCalculator.selectNewUserMessages(history, 1L)
        assertEquals(listOf(3L, 4L), result.map { it.id })
    }

    @Test
    fun `游标到达全部用户消息之后_返回空列表`() {
        val history = listOf(
            msg(1, Role.USER, "在吗"),
            msg(2, Role.USER, "第二条"),
        )
        val result = ReplyPumpCalculator.selectNewUserMessages(history, 2L)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `空历史返回空列表`() {
        assertTrue(ReplyPumpCalculator.selectNewUserMessages(emptyList(), null).isEmpty())
    }

    // ---------- buildBatchInput 分支 ----------

    @Test
    fun `单条消息合并为原文`() {
        val input = ReplyPumpCalculator.buildBatchInput(listOf(msg(1, Role.USER, "在吗")))
        assertEquals("在吗", input)
    }

    @Test
    fun `连发多条以换行分隔合并`() {
        val messages = listOf(
            msg(1, Role.USER, "在吗"),
            msg(2, Role.USER, "你吃了没"),
            msg(3, Role.USER, "我刚到家"),
        )
        val input = ReplyPumpCalculator.buildBatchInput(messages)
        assertEquals("在吗\n你吃了没\n我刚到家", input)
    }

    // ---------- lastId 分支 ----------

    @Test
    fun `lastId返回最大消息id`() {
        val messages = listOf(msg(1, Role.USER, "a"), msg(5, Role.USER, "b"), msg(3, Role.USER, "c"))
        assertEquals(5L, ReplyPumpCalculator.lastId(messages))
    }

    @Test
    fun `lastId为空列表返回null`() {
        assertEquals(null, ReplyPumpCalculator.lastId(emptyList()))
    }
}