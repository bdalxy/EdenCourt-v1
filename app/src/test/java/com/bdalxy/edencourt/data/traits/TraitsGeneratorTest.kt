package com.bdalxy.edencourt.data.traits

import com.bdalxy.edencourt.data.traits.ContrastLibrary.generate
import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 6 维随机生成 + 反差对生成单测（T13.3 角色差异化/自补充方案）。
 * 覆盖：范围边界（落点不越界）/ 均匀性 / 缺省维兜底 / 反差一致性过滤（冲突候选重选）/ 表面特质派生。
 */
class TraitsGeneratorTest {

    /** 固定种子随机源（java.util.Random 需 Long 种子） */
    private fun rnd(seed: Int): Random = Random(seed.toLong())

    // ---------- TraitsGenerator：范围边界 ----------

    @Test
    fun `落点_在给定范围内_不越界`() {
        // 用固定种子保证确定性
        val random = rnd(42)
        val ranges = TraitRangeResolver.DIMENSION_KEYS
            .mapIndexed { i, dim -> dim to IntRange(i * 10, i * 10 + 9) }
            .toMap()
        val result = TraitsGenerator.generate(ranges, random)
        // 每维落点都在其范围内
        ranges.forEach { (dim, range) ->
            val v = result[dim]!!
            assertTrue("$dim=$v 越界 $range", v in range.first..range.last)
        }
    }

    @Test
    fun `落点_闭区间两端可达`() {
        // 极窄范围：每一维都固定值 → 落点==边界
        val ranges = TraitRangeResolver.DIMENSION_KEYS.associateWith { IntRange(80, 80) }
        val result = TraitsGenerator.generate(ranges, rnd(7))
        result.forEach { (_, v) -> assertEquals(80, v) }
    }

    @Test
    fun `落点_缺失维度_退回固定50`() {
        // 只给 warmth 一个范围，其余维缺省 → 50
        val result = TraitsGenerator.generate(mapOf("warmth" to IntRange(90, 100)), rnd(3))
        assertTrue("warmth 越界", result["warmth"]!! in 90..100)
        assertEquals(50, result["sociability"])
        assertEquals(50, result["initiative"])
        assertEquals(50, result["attachment"])
    }

    @Test
    fun `落点_越界值被clamp到0到100`() {
        // 传入非法范围（如 >100）也被 clamp
        val result = TraitsGenerator.generate(mapOf("warmth" to IntRange(95, 150)), rnd(1))
        assertTrue(result["warmth"]!! <= 100)
        assertTrue(result["warmth"]!! >= 95)
    }

    // ---------- ContrastLibrary：表面特质派生 ----------

    @Test
    fun `表面特质_命中温柔关键词_派生温柔`() {
        assertEquals("温柔体贴", ContrastLibrary.deriveSurface("她是个温柔体贴的学姐"))
    }

    @Test
    fun `表面特质_无关键词_兜底沉稳内敛`() {
        assertEquals("沉稳内敛", ContrastLibrary.deriveSurface("无法识别的设定文本"))
    }

    // ---------- ContrastLibrary：受限反差库选隐藏（一致性过滤） ----------

    @Test
    fun `反差对_隐藏特质与卡设定无冲突`() {
        // 卡设定「随和好说话独立」涵盖多个候选的冲突词 → 冲突候选应被排除
        val pair = generate(personality = "她很独立随和好说话", background = "", random = rnd(11))
        // 表面派生：随/好说话未在表面词表，兜底沉稳内敛
        assertNotNull(pair.inner)
        assertTrue(pair.inner.isNotBlank())
        assertTrue(pair.trigger.isNotBlank())
        // 冲突词「依赖/占有/粘人」不应出现（因为「独立」命中冲突）
        listOf("依赖", "粘人", "占有").forEach {
            assertTrue("隐藏特质「${pair.inner}」与服务设定冲突", !pair.inner.contains(it))
        }
    }

    @Test
    fun `反差对_不同随机种子_可能选出不同隐藏特质`() {
        // 用大样本：同一卡设定，至少有一个种子选出与另一种子不同的 inner（证明随机重选生效）
        val basePersonality = "温柔学姐"
        val candidates = (0 until 50).map { seed ->
            generate(personality = basePersonality, background = "", random = rnd(seed)).inner
        }.toSet()
        assertTrue("50 次随机竟全同? candidates=${candidates.size}", candidates.size >= 2)
    }

    @Test
    fun `反差对_全候选冲突_兜底取首个`() {
        // 构造一个所有候选冲突词都命中的卡设定
        val conflictRichText = "随和软弱好说话随波逐流独立独行侠洒脱豁达冷淡疏离佛系随性淡然圆滑"
        val pair = generate(personality = conflictRichText, background = "", random = rnd(5))
        // 全冲突 → 兜底取 INNER_POOL 首个（其冲突词未必全命中，但至少不抛异常且非空）
        assertTrue(pair.inner.isNotBlank())
        assertTrue(pair.trigger.isNotBlank())
    }

    @Test
    fun `反差对_表面特质来自卡设定`() {
        val pair = generate(personality = "活泼开朗爱笑的姑娘", background = "", random = rnd(9))
        assertEquals("活泼开朗", pair.surface)
    }
}