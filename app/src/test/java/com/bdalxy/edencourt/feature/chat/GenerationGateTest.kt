package com.bdalxy.edencourt.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 代际闸门（P1 竞态修复）决策纯逻辑单测（G-③ 自动化回归·补聊天核心链路缺口）。
 * 覆盖「请求代际 == 当前最新则放行 / 否则作废」与「推进下一个代际号」，
 * 防止重构 streamReply / retry / 回复泵时破坏并发保护。
 */
class GenerationGateTest {

    // ---------- isCurrent（闸门判断） ----------

    @Test
    fun `请求代际等于当前最新_放行`() {
        assertTrue(GenerationGate.isCurrent(requestGeneration = 3L, currentGeneration = 3L))
    }

    @Test
    fun `请求代际落后于当前最新_作废_防旧结果覆盖新结果`() {
        // 期间又发起了更新的生成（代际已推进到 4），旧请求（3）应作废
        assertFalse(GenerationGate.isCurrent(requestGeneration = 3L, currentGeneration = 4L))
    }

    @Test
    fun `请求代际超前于当前最新_作废_不可能态兜底`() {
        // 防御性：请求代际不应超前，超前也视为不一致作废
        assertFalse(GenerationGate.isCurrent(requestGeneration = 5L, currentGeneration = 3L))
    }

    @Test
    fun `初始代际0与请求0_放行`() {
        assertTrue(GenerationGate.isCurrent(requestGeneration = 0L, currentGeneration = 0L))
    }

    // ---------- next（推进代际号） ----------

    @Test
    fun `从0推进到1`() {
        assertEquals(1L, GenerationGate.next(0L))
    }

    @Test
    fun `从N推进到N+1`() {
        assertEquals(4L, GenerationGate.next(3L))
    }

    @Test
    fun `连续推进单调递增`() {
        var current = 0L
        current = GenerationGate.next(current)
        current = GenerationGate.next(current)
        current = GenerationGate.next(current)
        assertEquals(3L, current)
        // 每次推进后的新代际必然能被闸门放行
        assertTrue(GenerationGate.isCurrent(current, current))
        // 旧代际已被覆盖
        assertFalse(GenerationGate.isCurrent(2L, current))
    }
}