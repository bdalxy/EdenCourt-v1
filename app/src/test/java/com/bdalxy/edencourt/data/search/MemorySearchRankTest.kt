package com.bdalxy.edencourt.data.search

import com.bdalxy.edencourt.data.local.dao.MemoryDao
import com.bdalxy.edencourt.data.local.entity.MemoryImportance
import com.bdalxy.edencourt.data.local.entity.MemoryItem
import com.bdalxy.edencourt.data.local.entity.MemoryType
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 记忆注入排序测试（方案 G-③ 回归集 Phase1）。
 * 保护 T9.3 rankByRelevance 排序 + 方案 F 记忆主动引用相关性加权：
 * - 无 query：置顶 > 重要 > 更新时间新 的基准排序（旧行为完全兼容）
 * - 有 query：与当前话题相关的记忆优先进入 top-K 注入名额
 */
class MemorySearchRankTest {

    private val service = MemorySearchService(memoryDao = mockk(), searchDao = mockk())

    /** 构造一条测试记忆 */
    private fun mem(
        id: Long,
        content: String,
        keywords: String = "",
        importance: MemoryImportance = MemoryImportance.NORMAL,
        isPinned: Boolean = false,
        updatedAt: Long = id, // 默认按 id 递增使更新时间唯一，便于断言
        type: MemoryType = MemoryType.OTHER, // A4：可通过 type 指定情感记忆等类型
    ) = MemoryItem(
        id = id,
        content = content,
        keywords = keywords,
        type = type,
        importance = importance,
        isPinned = isPinned,
        updatedAt = updatedAt,
    )

    // ---- 方案 F：query 相关性加权（核心新增逻辑）----

    @Test
    fun `相关性加权_命中奶茶关键词的记忆排最前`() {
        val items = listOf(
            mem(1, "小夏觉得图书馆有种安静的温柔", "图书馆,小夏"),
            mem(2, "小夏觉得手里的奶茶因为羽的话变得更甜了", "奶茶,羽"),
            mem(3, "小夏刚买了杯热奶茶", "热奶茶,小夏"),
            mem(4, "小夏的名字是小夏", "小夏,名字"),
        )
        val ranked = service.rankByRelevance(items, topK = 20, query = "你还记得昨天说的奶茶吗")
        // 「奶茶」关键词的记忆(2)应排最前（命中奶茶）
        assertEquals(2L, ranked[0].id)
        // 「热奶茶」为连体三字，query 是「奶茶吗」不带「热」字，按保守语义不命中 → 落回基准组（与其余 score=0 的记忆按更新序）
        val tail = ranked.drop(1).map { it.id }
        assertEquals(4L, tail[0]) // updatedAt 最大
        assertEquals(3L, tail[1])
        assertEquals(1L, tail[2])
    }

    @Test
    fun `相关性加权_图书馆话题优先_奶茶靠后`() {
        val items = listOf(
            mem(1, "小夏觉得图书馆有种安静的温柔", "图书馆,小夏"),
            mem(2, "小夏觉得手里的奶茶因为羽的话变得更甜了", "奶茶,羽"),
        )
        val ranked = service.rankByRelevance(items, topK = 20, query = "我们那天在图书馆聊了什么")
        assertEquals(1L, ranked[0].id)
        assertEquals(2L, ranked[1].id)
    }

    @Test
    fun `相关性加权_无相关命中时_基准排序`() {
        val items = listOf(
            mem(1, "光羽喜欢青蓝釉", "光羽,青蓝釉"),
            mem(2, "学姐喜欢喝美式", "学姐,美式"),
        )
        val ranked = service.rankByRelevance(items, topK = 20, query = "打车去火车站")
        // 两条都无命中 → 退回基准排序（按更新时间）
        assertEquals(listOf(2L, 1L), ranked.map { it.id })
    }

    @Test
    fun `query为空_完全兼容旧基准排序`() {
        // 关键回归：query 为 null / 空白时，行为必须与旧版一致（只按 置顶>重要>更新时间）
        val items = listOf(
            mem(1, "旧记忆A", "a", importance = MemoryImportance.NORMAL, isPinned = false, updatedAt = 100),
            mem(2, "旧记忆B", "b", importance = MemoryImportance.IMPORTANT, updatedAt = 300),
            mem(3, "旧记忆C", "c", isPinned = true, updatedAt = 200),
        )
        // 置顶(3) > 重要(2) > 普通(1)
        assertEquals(listOf(3L, 2L, 1L), service.rankByRelevance(items, topK = 20, query = null).map { it.id })
        assertEquals(listOf(3L, 2L, 1L), service.rankByRelevance(items, topK = 20, query = "   ").map { it.id })
    }

    // ---- 基准排序（T9.3 置顶 > 重要 > 更新时间新）----

    @Test
    fun `基准排序_重要优先于普通`() {
        val items = listOf(
            mem(1, "普通", "x", importance = MemoryImportance.NORMAL),
            mem(2, "重要", "y", importance = MemoryImportance.IMPORTANT),
        )
        assertEquals(2L, service.rankByRelevance(items, 20)[0].id)
    }

    @Test
    fun `基准排序_置顶优先于重要`() {
        val items = listOf(
            mem(1, "重要不置顶", "x", importance = MemoryImportance.IMPORTANT),
            mem(2, "置顶普通", "y", isPinned = true),
        )
        assertEquals(2L, service.rankByRelevance(items, 20)[0].id)
    }

    @Test
    fun `topK截断生效`() {
        val items = listOf(mem(1, "a"), mem(2, "b"), mem(3, "c"))
        assertEquals(2, service.rankByRelevance(items, topK = 2).size)
    }

    // ---- A4 情感记忆优先（EMOTION 类型提权）----

    @Test
    fun `情感记忆_无query时优先于置顶普通`() {
        // 情感记忆整体应先于(即便被置顶的)普通记忆
        val items = listOf(
            mem(1, "小夏记着羽爱喝奶茶", isPinned = true),
            mem(2, "小夏其实很在意羽", type = MemoryType.EMOTION),
        )
        assertEquals(2L, service.rankByRelevance(items, 20, query = null)[0].id)
    }

    @Test
    fun `情感记忆_优先于重要普通记忆`() {
        val items = listOf(
            mem(1, "羽的职业是设计师", importance = MemoryImportance.IMPORTANT),
            mem(2, "小夏对羽有好感，说话会不自觉变软", type = MemoryType.EMOTION),
        )
        assertEquals(2L, service.rankByRelevance(items, 20, query = null)[0].id)
    }

    @Test
    fun `情感记忆_仍让位于当前话题相关性`() {
        // 聊天正聊奶茶时，相关记忆优先；无关的情感记忆排在相关之后、普通之前
        val items = listOf(
            mem(1, "小夏其实很在意羽", type = MemoryType.EMOTION),
            mem(2, "小夏前天买了杯奶茶", "奶茶,小夏"),
        )
        val ranked = service.rankByRelevance(items, 20, query = "还记得那次买奶茶吗")
        assertEquals(2L, ranked[0].id) // 相关(奶茶)最前
        assertEquals(1L, ranked[1].id) // 情感记忆紧随
    }

    // ---- 方案 F v2：触发增强（findProactiveReference 主动引用时机）----

    @Test
    fun `触发增强_命中关键词返回该记忆`() {
        val items = listOf(
            mem(1, "小夏喜欢喝热奶茶", "热奶茶,小夏"),
            mem(2, "小夏在图书馆看书", "图书馆,小夏"),
        )
        val hit = service.findProactiveReference(items, query = "你还记得我喜欢喝热奶茶吗")
        assertEquals(1L, hit?.id)
    }

    @Test
    fun `触发增强_多条命中取相关性最高且最近更新`() {
        val items = listOf(
            mem(1, "小夏买了奶茶", "奶茶,小夏", updatedAt = 100),
            mem(2, "小夏在图书馆", "图书馆,小夏", updatedAt = 200),
            mem(3, "小夏又买了杯奶茶", "奶茶,小夏,买", updatedAt = 300),
        )
        // query「奶茶好喝吗」：记忆1/3 均命中「奶茶」且得分相同 → 取最近更新(3)
        val hit = service.findProactiveReference(items, query = "奶茶好喝吗")
        assertEquals(3L, hit?.id)
    }

    @Test
    fun `触发增强_query为空或空白_不触发返回null`() {
        val items = listOf(mem(1, "小夏喜欢喝热奶茶", "热奶茶,小夏"))
        assertNull(service.findProactiveReference(items, null))
        assertNull(service.findProactiveReference(items, "   "))
    }

    @Test
    fun `触发增强_无相关命中返回null`() {
        val items = listOf(mem(1, "小夏喜欢喝热奶茶", "热奶茶,小夏"))
        assertNull(service.findProactiveReference(items, "打车去火车站"))
    }
}