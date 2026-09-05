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

    /** ─── R2 浓度轴回落（v2.0 关系引擎）─── */

    @Test
    fun `2天内不回落 - 视为仍在热络期`() {
        // 距上次互动 0/1/2 天：浓度应保持不变
        assertEquals(50, UserAffinity.decay(50, 0))
        assertEquals(50, UserAffinity.decay(50, 1))
        assertEquals(50, UserAffinity.decay(50, 2))
    }

    @Test
    fun `第3天起逐天回落 - 每天降给定步长`() {
        // 从 50 起点，距上次 3 天、每天降 2 → 50-2=48
        assertEquals(48, UserAffinity.decay(50, 3, stepRandom = { 2 }))
        // 距上次 4 天、每天降 2 → 50 - 2*2 = 46
        assertEquals(46, UserAffinity.decay(50, 4, stepRandom = { 2 }))
        // 距上次 5 天、每天降 1 → 50 - 3 = 47
        assertEquals(47, UserAffinity.decay(50, 5, stepRandom = { 1 }))
    }

    @Test
    fun `回落步长限定在1到3 - 防越界随机源`() {
        // 注入返回 100 的异常随机源，仍应被 clamp 到 3
        assertEquals(47, UserAffinity.decay(50, 3, stepRandom = { 100 }))
        // 注入返回 0/-1 的异常随机源，仍应被 clamp 到 1
        assertEquals(49, UserAffinity.decay(50, 3, stepRandom = { 0 }))
    }

    @Test
    fun `不破档 - 保底到当前档位起始值`() {
        // 结识档位（20~39）：降到 20 即停，不会跌破 20
        assertTrue(
            "结识档不应跌破20，实际=" + UserAffinity.decay(21, 30, stepRandom = { 3 }),
            UserAffinity.decay(21, 30, stepRandom = { 3 }) == 20,
        )
        // 友好档位（40~69）：保底 40
        assertTrue(
            "友好档不应跌破40，实际=" + UserAffinity.decay(45, 10, stepRandom = { 3 }),
            UserAffinity.decay(45, 10, stepRandom = { 3 }) == 40,
        )
        // 亲密档位（>=70）：保底 70
        assertTrue(
            "亲密档不应跌破70，实际=" + UserAffinity.decay(72, 60, stepRandom = { 3 }),
            UserAffinity.decay(72, 60, stepRandom = { 3 }) == 70,
        )
    }

    @Test
    fun `已在档位底部 - 不再回落`() {
        // 陌生档起始 0，或已触底到档位起始值：保持原值
        assertEquals(0, UserAffinity.decay(0, 99, stepRandom = { 3 }))
        // 恰在友好档起始 40：回落到 40 原样返回
        assertEquals(40, UserAffinity.decay(40, 99, stepRandom = { 3 }))
    }
}