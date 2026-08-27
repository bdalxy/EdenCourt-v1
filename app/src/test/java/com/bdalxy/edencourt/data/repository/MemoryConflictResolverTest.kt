package com.bdalxy.edencourt.data.repository

import com.bdalxy.edencourt.data.local.entity.MemoryItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * MemoryConflictResolver 冲突判定三态单测（T12.5）。
 * 固化 T11.8b 事故回归：宽泛关键词不得覆盖无关旧记忆。判定采用「宁可新增、不可误覆盖」。
 */
class MemoryConflictResolverTest {

    /** 便捷构造：只需 content，可选 keywords/isDeleted，其余走默认 */
    private fun mem(
        content: String,
        keywords: String = "",
        isDeleted: Boolean = false,
        id: Long = 0L,
    ) = MemoryItem(
        id = id,
        content = content,
        keywords = keywords,
        isDeleted = isDeleted,
    )

    // ---------- 证据1：内容重叠（同事实合并） ----------

    @Test
    fun `证据1_内容互为包含且较长一方达标_判定同事实_返回旧记忆`() {
        val old = mem(content = "用户喜欢喝奶茶", id = 1L)
        // 新内容更短但被完整包含，且较长方(旧)长度>=5 → 判为同一事实
        val hit = MemoryConflictResolver.resolveConflict(listOf(old), content = "喝奶茶", keywords = "奶茶,喜欢")
        assertEquals(1L, hit?.id)
    }

    @Test
    fun `证据1_两内容都过短_即使重叠也不判同事实`() {
        // 双方均<5字，长度条件不满足 → 不合并（避免把短词黏进长记忆造成语义污染）
        val old = mem(content = "奶茶", id = 1L)
        val hit = MemoryConflictResolver.resolveConflict(listOf(old), content = "喜欢奶茶", keywords = "奶茶")
        assertNull(hit)
    }

    @Test
    fun `证据1_完全无关内容_不判同事实_应新增`() {
        val old = mem(content = "用户在图书馆安静地看书", id = 1L)
        val hit = MemoryConflictResolver.resolveConflict(listOf(old), content = "小夏觉得图书馆有种温柔的安静", keywords = "图书馆")
        // 仅1个宽泛关键词、且两内容不互为包含 → 不覆盖（T11.8b 事故修复核心回归）
        assertNull(hit)
    }

    // ---------- 证据2：多关键词命中同一条（可信才合并） ----------

    @Test
    fun `证据2_两个关键词同时命中同一条_判定同事实`() {
        val old = mem(content = "用户喜欢逛夜市吃烧烤", keywords = "夜市,烧烤", id = 2L)
        val hit = MemoryConflictResolver.resolveConflict(listOf(old), content = "最近常和同事去夜市撸串", keywords = "夜市,烧烤")
        assertEquals(2L, hit?.id)
    }

    @Test
    fun `证据2_关键词命中分散在不同记忆_不判同事实`() {
        // 「夜市」「喝酒」分别命中两条不同记忆，但没有一条同时命中两个 → 各自保留，不动
        val pool = listOf(
            mem(content = "用户喜欢逛夜市", keywords = "夜市", id = 1L),
            mem(content = "用户偶尔喝酒", keywords = "喝酒", id = 2L),
        )
        val hit = MemoryConflictResolver.resolveConflict(pool, content = "用户最近常加班到深夜去逛夜市", keywords = "夜市,喝酒")
        assertNull(hit)
    }

    @Test
    fun `证据2_仅单关键词命中_不判同事实`() {
        // 仅1个关键词命中但另一关键词缺，不足2证据 → 新增（宽泛词不覆盖）
        val old = mem(content = "小夏觉得图书馆有种温柔的安静", keywords = "图书馆", id = 1L)
        val hit = MemoryConflictResolver.resolveConflict(listOf(old), content = "用户周末爱泡图书馆", keywords = "图书馆,阅读")
        assertNull(hit)
    }

    // ---------- 边界 ----------

    @Test
    fun `空内容或仅空白_不判同事实`() {
        val old = mem(content = "用户喜欢喝奶茶", id = 1L)
        assertNull(MemoryConflictResolver.resolveConflict(listOf(old), content = "   ", keywords = "奶茶"))
        assertNull(MemoryConflictResolver.resolveConflict(listOf(old), content = "", keywords = "奶茶"))
    }

    @Test
    fun `已删除记忆不参与判定`() {
        val deleted = mem(content = "用户喜欢喝奶茶", isDeleted = true, id = 1L)
        assertNull(MemoryConflictResolver.resolveConflict(listOf(deleted), content = "喝奶茶", keywords = "奶茶,喜欢"))
    }

    @Test
    fun `证据1优先级高于证据2_同一候选池`() {
        // 池里同时存在「内容与旧记忆完全一致(证据1)」多条；即便另一条关键词命中更多，也应优先内容重叠那条
        val pool = listOf(
            mem(content = "用户每天早晨喝牛奶", keywords = "牛奶喝", id = 1L),
            mem(content = "用户偶尔喝果汁", keywords = "牛奶,果汁", id = 2L),
        )
        val hit = MemoryConflictResolver.resolveConflict(pool, content = "用户每天早晨喝牛奶", keywords = "牛奶,果汁")
        // content 与 old1 完全一致 → 证据1命中 id1；证据1 优先于仅关键词命中
        assertEquals(1L, hit?.id)
    }

    @Test
    fun `空池_不判同事实_返回null`() {
        assertNull(MemoryConflictResolver.resolveConflict(emptyList(), content = "用户喜欢喝奶茶", keywords = "奶茶,喜欢"))
    }
}