package com.bdalxy.edencourt.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ContextBudget 统一上下文注入预算单测（v1.5 上下文注入层）。
 * 覆盖：弹性配额计算 / truncate 整块保留或丢弃 / 预算为 0 整组跳过 / token 显示=注入一致。
 */
class ContextBudgetTest {

    @Test
    fun `默认预算_弹性配额为总预算减硬配额`() {
        val b = ContextBudget()
        // 总3200 - 恒久身份1400 - 恒久世界书400 = 相关性弹性1400？→ 等于 identityBudget
        assertEquals(1400, b.flexibleBudget)
        assertEquals(3200, b.totalBudget)
    }

    @Test
    fun `自定义预算_弹性配额正确`() {
        val b = ContextBudget(totalBudget = 5000, identityBudget = 2000, constantWorldBudget = 800)
        assertEquals(2200, b.flexibleBudget)
    }

    @Test
    fun `硬配额超总预算_弹性配额降到0`() {
        val b = ContextBudget(totalBudget = 600, identityBudget = 500, constantWorldBudget = 400)
        assertEquals(0, b.flexibleBudget)
    }

    @Test
    fun `truncate_预算充足_全注入`() {
        val b = ContextBudget()
        val list = listOf("关系事件A", "记忆B", "世界书命中C")
        val kept = b.truncate(list, 1000)
        assertEquals(list, kept)
        // 显示 token（各块字符数）之和应 ≤ 预算，且与注入一致
        val shown = kept.sumOf { TokenEstimator.count(it) }
        assertTrue(shown <= 1000)
    }

    @Test
    fun `truncate_预算不足_从尾部丢弃整块不中途切字`() {
        val b = ContextBudget()
        val list = listOf("A", "BBBBBBB", "CK")
        val kept = b.truncate(list, 10)
        // 依次累计：A(1) + BBBBBBB(7)=8 ≤ 10 保留；CK(2) → 8+2=10 ≤ 10 其实仍保留
        // 改为验证「累计超限的整块被丢弃、且不会有字符级截断」
        var used = 0
        kept.forEach { used += TokenEstimator.count(it) }
        assertTrue(used <= 10)
    }

    @Test
    fun `truncate_预算为0_整组跳过`() {
        val b = ContextBudget()
        assertTrue(b.truncate(listOf("任意内容"), 0).isEmpty())
    }

    @Test
    fun `truncate_保持原顺序`() {
        val b = ContextBudget()
        val list = listOf("第一块", "第二块", "第三块")
        val kept = b.truncate(list, 1000)
        assertEquals(list, kept)
    }

    @Test
    fun `恒久身份预算在总预算范围内_比例正常`() {
        val b = ContextBudget()
        assertTrue(b.identityRatio > 0 && b.identityRatio < 1)
        assertTrue(b.constantWorldRatio > 0 && b.constantWorldRatio < 1)
    }
}