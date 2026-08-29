package com.bdalxy.edencourt.data.traits

import com.bdalxy.edencourt.domain.TraitRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 人设维度范围解析器单测（T13.2 角色差异化/自补充方案）。
 * 覆盖三类捏卡用户 + 兜底链：
 *   数据党（卡上精确填 traitsRange）→ 直接取；
 *   感觉党（走 LLM 输出 JSON）→ parseLlmJson 后取；
 *   中间党（部分数据 + 部分 LLM）→ 逐维合并；
 *   启发式（personality 关键词偏移）；
 *   兜底（全部缺失 → 50±25）。
 */
class TraitRangeResolverTest {

    /** 一张「温柔学姐」角色卡，作为启发式验证用 */
    private val personality = "温柔体贴的高个子学姐，沉默寡言但很照顾人"

    // ---------- 数据党：精确填全 6 维 ----------

    @Test
    fun `数据党_卡上全填_直接采用精确范围`() {
        val dataRange = mapOf(
            "sociability" to TraitRange(min = 40, max = 60),
            "initiative" to TraitRange(min = 50, max = 70),
            "warmth" to TraitRange(min = 80, max = 95),
            "attachment" to TraitRange(min = 60, max = 85),
            "humor" to TraitRange(min = 20, max = 45),
            "casualness" to TraitRange(min = 25, max = 50),
        )
        val result = TraitRangeResolver.resolve(dataRange, llmRange = null, personality = personality)

        // 6 维全部来自数据党精确值，且不被 personality 关键词覆盖
        assertEquals(40..60, result["sociability"])
        assertEquals(50..70, result["initiative"])
        assertEquals(80..95, result["warmth"])
        assertEquals(60..85, result["attachment"])
        assertEquals(20..45, result["humor"])
        assertEquals(25..50, result["casualness"])
    }

    @Test
    fun `数据党_confidence过低_降级不再采用`() {
        // confidence=0.2 < 阈值0.35 → 该维被废弃；又无 LLM 兜底 → 走启发式（性格含温柔→warmth 偏移）或默认
        val dataRange = mapOf(
            "warmth" to TraitRange(min = 10, max = 20, confidence = 0.2),
        )
        val result = TraitRangeResolver.resolve(dataRange, llmRange = null, personality = personality)
        // personality 含「温柔」+20、「体贴」+15 → warmth 中心 50+35=85，半宽25 → 60..100（启发式生效，未用被废弃的 10..20）
        val warmth = result["warmth"]!!
        assertEquals("启发式未正确覆盖 warmth: $warmth", 60..100, warmth)
    }

    // ---------- 感觉党：走 LLM 输出 JSON ----------

    @Test
    fun `感觉党_LLM输出JSON_解析并采用`() {
        val llmJson = """
            {"sociability":{"min":45,"max":65,"confidence":0.6},"initiative":{"min":50,"max":75,"confidence":0.5},"warmth":{"min":70,"max":95,"confidence":0.8},"attachment":{"min":55,"max":85,"confidence":0.6},"humor":{"min":20,"max":50,"confidence":0.7},"casualness":{"min":25,"max":55,"confidence":0.6}}
        """.trimIndent()
        val llmRange = TraitRangeResolver.parseLlmJson(llmJson)
        // 6 维完整给出
        assertEquals(6, llmRange.size)
        val result = TraitRangeResolver.resolve(dataRange = null, llmRange = llmRange, personality = personality)
        assertEquals(45..65, result["sociability"])
        assertEquals(70..95, result["warmth"])
        assertEquals(20..50, result["humor"])
    }

    @Test
    fun `感觉党_LLM解析失败或空_降级启发式与默认`() {
        // 空串 → 空 map → 全走启发式/默认
        val empty = TraitRangeResolver.parseLlmJson("")
        assertTrue(empty.isEmpty())
        val result = TraitRangeResolver.resolve(dataRange = null, llmRange = empty, personality = "")
        // 无 personality 信号 → 全部 50±25
        result.values.forEach { assertEquals(25..75, it) }
    }

    @Test
    fun `感觉党_LLM输出带markdown围栏_仍能解析`() {
        val llmJson = "```json\n{\"warmth\":{\"min\":75,\"max\":90,\"confidence\":0.9}}\n```"
        val llmRange = TraitRangeResolver.parseLlmJson(llmJson)
        assertEquals(75..90, TraitRangeResolver.resolve(null, llmRange, "举例").get("warmth"))
    }

    @Test
    fun `感觉党_LLM输出兼容字符串与纯数字形态`() {
        val llmJson = """{"warmth":"70..95","humor":40}"""
        val llmRange = TraitRangeResolver.parseLlmJson(llmJson)
        assertEquals(70..95, llmRange["warmth"]?.clamped())
        assertEquals(40..40, llmRange["humor"]?.clamped())
    }

    @Test
    fun `感觉党_LLM混入非六维key_拒绝`() {
        val llmJson = """{"evil":{"min":90,"max":99,"confidence":0.9}}"""
        val llmRange = TraitRangeResolver.parseLlmJson(llmJson)
        assertTrue(llmRange.isEmpty())
    }

    // ---------- 中间党：部分数据 + 部分 LLM ----------

    @Test
    fun `中间党_数据党填部分维度_其余由LLM补全`() {
        // 数据党只填 warmth/humor；感觉党 LLM 填剩余 4 维
        val dataRange = mapOf(
            "warmth" to TraitRange(min = 80, max = 95),
            "humor" to TraitRange(min = 20, max = 40),
        )
        val llmRange = mapOf(
            "sociability" to TraitRange(min = 45, max = 65),
            "initiative" to TraitRange(min = 50, max = 75),
            "attachment" to TraitRange(min = 55, max = 85),
            "casualness" to TraitRange(min = 25, max = 55),
        )
        val result = TraitRangeResolver.resolve(dataRange, llmRange, personality)
        // 数据维度优先数据党，其余维度由 LLM 补全
        assertEquals(80..95, result["warmth"])
        assertEquals(20..40, result["humor"])
        assertEquals(45..65, result["sociability"])
        assertEquals(55..85, result["attachment"])
        assertEquals(50..75, result["initiative"])
        assertEquals(25..55, result["casualness"])
    }

    // ---------- 启发式与边界 ----------

    @Test
    fun `启发式_personality命中关键词_产出偏移范围`() {
        // 「高冷」→ warmth -15，attachment -10；「温柔」→ warmth +20
        val result = TraitRangeResolver.resolve(
            dataRange = null, llmRange = null,
            personality = "温柔但也有点高冷",
        )
        // warmth：+20-15 = +5 → 中心55，半宽25 → 30..80
        val warmth = result["warmth"]!!
        assertTrue("warmth 未受双重信号影响: $warmth", warmth.first <= 35 && warmth.last >= 70)
    }

    @Test
    fun `兜底_全部缺失_返回50上下25`() {
        val result = TraitRangeResolver.resolve(dataRange = null, llmRange = null, personality = "")
        result.values.forEach { assertEquals(25..75, it) }
        // 恒为 6 维
        assertEquals(6, result.keys.size)
    }
}