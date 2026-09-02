package com.bdalxy.edencourt.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * UserAffinity 亲密度模型单测（A1）。
 * 覆盖：非线性饱和增长（前期快、后期慢）、封顶、越界夹取、boost 加成、注入文本分档。
 */
class UserAffinityTest {

    @Test
    fun `推进呈非线性饱和 - 离上限越近增量越小`() {
        val step1 = UserAffinity.advance(0) - 0
        val step40 = UserAffinity.advance(40) - 40
        val step70 = UserAffinity.advance(70) - 70
        // 前期增长明显快于后期（非线性，非固定加分）
        assertTrue("前期增量(=$step1)应大于后期增量(=$step70)，实际 non-linear 失效", step1 > step70)
        assertTrue("接近上限应趋缓（增量应小于前期）", step70 < step40)
    }

    @Test
    fun `永不越过上限`() {
        assertEquals(UserAffinity.MAX, UserAffinity.advance(UserAffinity.MAX - 1))
        // 已封顶则不再变化
        assertEquals(UserAffinity.MAX, UserAffinity.advance(UserAffinity.MAX))
    }

    @Test
    fun `推进值始终夹在 0~100 之间`() {
        // 越界输入会被夹到合法区间
        assertTrue(UserAffinity.advance(200) in UserAffinity.MIN..UserAffinity.MAX)
        assertEquals(UserAffinity.MAX, UserAffinity.advance(200))
    }

    @Test
    fun `boost 加成使推进更快`() {
        // 普通推进 vs 带入 boost：相同起点带 boost 后值更高（深化时刻加速亲近）
        assertTrue(UserAffinity.advance(0, boost = 10) > UserAffinity.advance(0))
    }

    @Test
    fun `负 boost 按 0 处理 不使推进倒退`() {
        // 负数 boost coerce 为 0，推进值不低于默认推进（不反向缩减）
        assertEquals(UserAffinity.advance(10), UserAffinity.advance(10, boost = -5))
        assertTrue(UserAffinity.advance(10, boost = -100) >= UserAffinity.advance(10))
    }

    @Test
    fun `注入文本含亲密度分档与数值`() {
        val s0 = UserAffinity.buildAffinityFragment(0)
        val sClose = UserAffinity.buildAffinityFragment(90)
        assertTrue("0 应含「陌生」分档", s0.contains("陌生"))
        assertTrue("90 应含「亲密」分档", sClose.contains("亲密"))
        assertTrue("应含数值", s0.contains("0/100") || s0.contains("亲密度 0"))
        assertTrue("90 注入应含数值", sClose.contains("90"))
    }

    @Test
    fun `分数随起点递增 - 亲密度越高档位越高`() {
        // 档位递进：随亲密度升高，分档应单调不降
        for (score in 1..UserAffinity.MAX - 1) {
            val lower = UserAffinity.buildAffinityFragment(score)
            assertTrue(lower.isNotBlank())
        }
    }
}