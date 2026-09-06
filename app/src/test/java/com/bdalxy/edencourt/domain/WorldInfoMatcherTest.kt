package com.bdalxy.edencourt.domain

import com.bdalxy.edencourt.data.local.entity.WorldInfoEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WorldInfoMatcher 世界书命中/预算/片段单测（A3）。
 * 覆盖：主/副关键词命中与收窄（AND_ANY/AND_ALL）、constant 恒注入、enabled 开关、
 * priority 排序、token 与条数预算截断、片段拼接。
 */
class WorldInfoMatcherTest {

    private fun entry(
        title: String,
        keys: String,
        content: String = title + "内容",
        secondaryKeys: String = "",
        logic: String = "AND_ANY",
        priority: Int = 50,
        constant: Boolean = false,
        enabled: Boolean = true,
    ) = WorldInfoEntry(
        bookId = 1L,
        title = title,
        keys = keys,
        secondaryKeys = secondaryKeys,
        logic = logic,
        content = content,
        priority = priority,
        constant = constant,
        enabled = enabled,
    )

    @Test
    fun `主关键词命中才注入 - 未命中不注入`() {
        val e = entry("魔法", "魔法")
        val hit = WorldInfoMatcher.matched(listOf(e), listOf("他施展了魔法"))
        val miss = WorldInfoMatcher.matched(listOf(e), listOf("今天天气很好"))
        assertEquals(1, hit.size)
        assertTrue(miss.isEmpty())
    }

    @Test
    fun `扫描多段文本任一段命中即触发`() {
        val e = entry("王国", "王国")
        // 命中在扫描文本的某一段即可
        assertEquals(1, WorldInfoMatcher.matched(listOf(e), listOf("无关", "王国的疆域")).size)
    }

    @Test
    fun `constant 恒定注入 - 不依赖关键词`() {
        val e = entry("恒定规则", "绝不出现的关键词", constant = true)
        assertEquals(1, WorldInfoMatcher.matched(listOf(e), listOf("nothing hit")).size)
    }

    @Test
    fun `enabled false 不注入`() {
        val e = entry("禁用", "魔法", enabled = false)
        assertTrue(WorldInfoMatcher.matched(listOf(e), listOf("魔法")).isEmpty())
    }

    @Test
    fun `AND_ANY 主命中后任一副关键词即通过`() {
        val e = entry("王城", "王城", secondaryKeys = "国王, 卫兵", logic = "AND_ANY")
        assertEquals(1, WorldInfoMatcher.matched(listOf(e), listOf("王城 与 国王")).size)
        assertEquals(1, WorldInfoMatcher.matched(listOf(e), listOf("王城 卫兵 巡街")).size)
    }

    @Test
    fun `AND_ANY 副关键词零命中则不注入`() {
        val e = entry("王城", "王城", secondaryKeys = "国王, 卫兵", logic = "AND_ANY")
        assertTrue(WorldInfoMatcher.matched(listOf(e), listOf("王城 只有 平民")).isEmpty())
    }

    @Test
    fun `AND_ALL 须全部副关键词命中`() {
        val e = entry("王城", "王城", secondaryKeys = "国王, 卫兵", logic = "AND_ALL")
        // 全部副关键词都在才注入
        assertEquals(1, WorldInfoMatcher.matched(listOf(e), listOf("王城 国王 卫兵")).size)
        assertTrue(WorldInfoMatcher.matched(listOf(e), listOf("王城 国王")).isEmpty())
    }

    @Test
    fun `priority 升序排列 - 数值小靠前`() {
        val low = entry("核心", "魔法", priority = 20)
        val high = entry("边角", "魔法", priority = 90)
        val mid = entry("标准", "魔法", priority = 50)
        val sorted = WorldInfoMatcher.matched(listOf(high, low, mid), listOf("魔法"))
        assertEquals(listOf(20, 50, 90), sorted.map { it.priority })
    }

    @Test
    fun `budget 按 token 预算截断`() {
        // 每条 100 token，预算 250 → 只能装下 2 条
        val entries = (1..5).map { idx -> entry("条目$idx", "魔法", content = "字".repeat(100)) }
        val injected = WorldInfoMatcher.budget(entries, maxToken = 250)
        assertEquals(2, injected.size)
    }

    @Test
    fun `budget 条数上限封顶`() {
        val entries = (1..10).map { idx -> entry("条目$idx", "魔法", content = "短内容") }
        assertEquals(WorldInfoMatcher.DEFAULT_MAX_ENTRIES, WorldInfoMatcher.budget(entries).size)
    }

    @Test
    fun `单条超预算则顺序截断不再放行后续`() {
        val big = entry("大", "魔法", content = "字".repeat(3000)) // 单条超默认预算
        val small = entry("小", "魔法", content = "短")
        val injected = WorldInfoMatcher.budget(listOf(big, small), maxToken = 2000)
        // T12.x P1 修复（优先级倒置）：顺序截断——大条超限即停（break），
        // 不再「跳过超限条、放行后续低优先级条」（旧 filter 语义会让 low-priority 小条插队）
        assertEquals(emptyList<String>(), injected.map { it.content })
    }

    @Test
    fun `empty 无匹配时片段为空`() {
        assertEquals("", WorldInfoMatcher.buildFragment(emptyList()))
    }

    @Test
    fun `片段包含标题头与条目正文 不含条目名`() {
        val e = entry("魔法", "魔法")
        val frag = WorldInfoMatcher.buildFragment(listOf(e))
        assertTrue(frag.contains("世界书 · 背景设定"))
        assertTrue(frag.contains("魔法内容"))       // 注入的是 content
        assertFalse(frag.contains("【魔法】"))       // title 不入文
    }
}