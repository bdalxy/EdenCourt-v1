package com.bdalxy.edencourt.domain

import com.bdalxy.edencourt.data.local.entity.RelationDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RelationDirectionEngine（R3 方向轴纯逻辑）单测。
 * 覆盖：方向等级 rank 映射、结构化输出 JSON 解析（含 markdown 剥壳 + 容错）、方向提示词组装。
 */
class RelationDirectionEngineTest {

    /** ─── rank 映射：与枚举声明顺序解耦 ─── */

    @Test
    fun `方向等级排名与专家建议一致`() {
        assertEquals(0, RelationDirection.NONE.rank())
        assertEquals(1, RelationDirection.FRIEND.rank())
        assertEquals(1, RelationDirection.COMRADE.rank()) // 朋友/同好同级
        assertEquals(2, RelationDirection.AMBIGUOUS.rank())
        assertEquals(3, RelationDirection.INTIMATE.rank()) // 深交 < 恋人
        assertEquals(4, RelationDirection.LOVER.rank())
    }

    /** ─── 结构化输出解析 ─── */

    @Test
    fun `解析纯 JSON 得到完整字段`() {
        val j = RelationDirectionParser.parse(
            """{"suggested_direction":"LOVER","confidence":0.82,"reason":"亲昵称呼增多","signals":["宝贝","只喜欢你"]}""",
        )
        assertNotNull(j)
        j!!
        assertEquals(RelationDirection.LOVER, j.direction)
        assertEquals(0.82f, j.confidence, 0.0001f)
        assertEquals(2, j.signals.size)
    }

    @Test
    fun `解析被 markdown 代码块包裹的 JSON`() {
        val raw = "```json\n{\"suggested_direction\":\"AMBIGUOUS\",\"confidence\":0.7,\"reason\":\"暧昧试探\",\"signals\":[]}\n```"
        val j = RelationDirectionParser.parse(raw)
        assertNotNull(j)
        assertEquals(RelationDirection.AMBIGUOUS, j!!.direction)
    }

    @Test
    fun `非法 JSON 返回 null - 不抛异常`() {
        assertNull(RelationDirectionParser.parse("这不是 JSON"))
        assertNull(RelationDirectionParser.parse(""))
    }

    @Test
    fun `建议方向大小写与首尾空格容错`() {
        val lower = RelationDirectionParser.parse("""{"suggested_direction":"lover","confidence":0.5,"reason":"x","signals":[]}""")
        assertEquals(RelationDirection.LOVER, lower!!.direction)
        val spaced = RelationDirectionParser.parse("""{"suggested_direction":"  friend  ","confidence":0.5,"reason":"x","signals":[]}""")
        assertEquals(RelationDirection.FRIEND, spaced!!.direction)
    }

    @Test
    fun `未知或空建议方向回落 null - 由调用方忽略`() {
        // 中文或无法映射的枚举值 → direction 为 null
        assertNull(RelationDirectionParser.parse("""{"suggested_direction":"恋人","confidence":0.9,"reason":"x","signals":[]}""")!!.direction)
        assertNull(RelationDirectionParser.parse("""{"suggested_direction":"","confidence":0.9,"reason":"x","signals":[]}""")!!.direction)
    }

    /** ─── 方向判断提示词组装 ─── */

    @Test
    fun `提示词含当前方向与上下文片段`() {
        val prompt = buildDirectionPrompt(RelationDirection.FRIEND, "用户：今天好累\nAI：我也是，抱抱")
        assertTrue(prompt.contains("FRIEND"))
        assertTrue(prompt.contains("用户：今天好累"))
        // 提示词强调"慢变量 / 不频繁变化"，防随意跳档
        assertTrue(prompt.contains("慢变量"))
        assertTrue(prompt.contains("signals"))
    }

    /** ─── 候选裁决（四件套阈值）─── */

    @Test
    fun `升档 - 连续2次高置信且冷却满足才切换`() {
        // 候选已命中1次，本次再命中1次 → 连续2次
        val state = DirectionState(RelationDirection.FRIEND, RelationDirection.LOVER, 1, lastDirectionSwitchAt = 0L)
        val d = adjudicateDirection(state, RelationDirection.LOVER, 0.8f, 50, now = 13L * 3600_000)
        assertTrue(d.switched)
        assertEquals(RelationDirection.LOVER, d.newDirection)
        assertEquals(0, d.newCandidateCount)
        assertEquals(13L * 3600_000, d.lastDirectionSwitchAt)
    }

    @Test
    fun `升档 - 置信度不足不切换`() {
        val state = DirectionState(RelationDirection.FRIEND, RelationDirection.LOVER, 1, lastDirectionSwitchAt = null)
        val d = adjudicateDirection(state, RelationDirection.LOVER, 0.6f, 50, now = 100L * 3600_000)
        assertTrue(!d.switched)
        assertEquals(RelationDirection.FRIEND, d.newDirection)
        assertEquals(2, d.newCandidateCount) // 本次仍判到同候选，计数继续累计
    }

    @Test
    fun `升档 - 冷却未到不切换`() {
        // 距上次切换仅 5h（<12h）
        val state = DirectionState(RelationDirection.FRIEND, RelationDirection.LOVER, 1, lastDirectionSwitchAt = 0L)
        val d = adjudicateDirection(state, RelationDirection.LOVER, 0.9f, 50, now = 5L * 3600_000)
        assertTrue(!d.switched)
        assertEquals(RelationDirection.FRIEND, d.newDirection)
    }

    @Test
    fun `降档 - 连续3次且浓度跌破下限且冷却满足才切换`() {
        val state = DirectionState(RelationDirection.LOVER, RelationDirection.FRIEND, 2, lastDirectionSwitchAt = 0L)
        val d = adjudicateDirection(state, RelationDirection.FRIEND, 0.85f, 30, now = 25L * 3600_000)
        assertTrue(d.switched)
        assertEquals(RelationDirection.FRIEND, d.newDirection)
    }

    @Test
    fun `降档 - 浓度未跌破下限不切换`() {
        // 当前 LOVER 下限 70，浓度 80 未跌破
        val state = DirectionState(RelationDirection.LOVER, RelationDirection.FRIEND, 2, lastDirectionSwitchAt = 0L)
        val d = adjudicateDirection(state, RelationDirection.FRIEND, 0.9f, 80, now = 25L * 3600_000)
        assertTrue(!d.switched)
        assertEquals(RelationDirection.LOVER, d.newDirection)
    }

    @Test
    fun `判回当前方向 - 候选计数减一但不低于0`() {
        val state = DirectionState(RelationDirection.FRIEND, RelationDirection.LOVER, 2, lastDirectionSwitchAt = null)
        val d = adjudicateDirection(state, RelationDirection.FRIEND, 0.9f, 50, now = 100L)
        assertEquals(1, d.newCandidateCount)
        assertTrue(!d.switched)
        // 已为 0 时不再减
        val d2 = adjudicateDirection(DirectionState(RelationDirection.FRIEND, RelationDirection.LOVER, 0), RelationDirection.FRIEND, 0.9f, 50, now = 100L)
        assertEquals(0, d2.newCandidateCount)
    }

    @Test
    fun `判到新方向 - 候选重置为1并更新候选`() {
        val state = DirectionState(RelationDirection.FRIEND, RelationDirection.LOVER, 2, lastDirectionSwitchAt = null)
        val d = adjudicateDirection(state, RelationDirection.AMBIGUOUS, 0.9f, 50, now = 100L)
        assertEquals(RelationDirection.AMBIGUOUS, d.newCandidate)
        assertEquals(1, d.newCandidateCount)
        assertTrue(!d.switched)
    }

    @Test
    fun `典型浓度下限映射 - 与方向亲近度成正比`() {
        assertEquals(0, RelationDirection.NONE.typicalAffinityFloor())
        assertEquals(20, RelationDirection.FRIEND.typicalAffinityFloor())
        assertEquals(20, RelationDirection.COMRADE.typicalAffinityFloor())
        assertEquals(40, RelationDirection.AMBIGUOUS.typicalAffinityFloor())
        assertEquals(60, RelationDirection.INTIMATE.typicalAffinityFloor())
        assertEquals(70, RelationDirection.LOVER.typicalAffinityFloor())
    }

    /** ─── 方向注入片段（R4 反哺口吻）─── */

    @Test
    fun `方向注入 - NONE 返回空串不注入`() {
        assertEquals("", buildDirectionFragment(RelationDirection.NONE))
    }

    @Test
    fun `方向注入 - 各方向非空且含方向描述与隐式引导`() {
        val lover = buildDirectionFragment(RelationDirection.LOVER)
        assertTrue(lover.isNotBlank())
        assertTrue(lover.contains("恋人"))
        assertTrue(lover.contains("不要刻意宣告")) // 隐式关系：不主动挑明，靠自然流露
        listOf(RelationDirection.FRIEND, RelationDirection.COMRADE, RelationDirection.AMBIGUOUS, RelationDirection.INTIMATE)
            .forEach { assertTrue(buildDirectionFragment(it).isNotBlank()) }
    }
}