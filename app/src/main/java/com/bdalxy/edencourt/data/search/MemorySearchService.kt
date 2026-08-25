package com.bdalxy.edencourt.data.search

import android.util.Log
import androidx.sqlite.db.SimpleSQLiteQuery
import com.bdalxy.edencourt.data.local.dao.MemoryDao
import com.bdalxy.edencourt.data.local.dao.MemorySearchDao
import com.bdalxy.edencourt.data.local.entity.MemoryImportance
import com.bdalxy.edencourt.data.local.entity.MemoryItem
import com.bdalxy.edencourt.data.local.entity.MemoryType

/**
 * 统一记忆检索服务（T9.3）。
 * 同时覆盖两类场景：
 *  1. 记忆管理页搜索（即输即查）——searchPaged / countFiltered
 *  2. 生成上下文注入——rankByRelevance 提供「置顶/重要优先、按更新时间」的 top-K 排序
 * 检索策略：
 *  - 中文子串 / 两字短词：FTS unicode61 常无法命中，退回 LIKE 子串检索（由 MemoryDao.searchPaged 承担，先按角色/分类缩小候选）。
 *  - 可分词词（英文/数字/标签）：用 FTS4 MEMORY 精确命中（searchFts；JOIN 主表已过滤 isDeleted=0）。
 * 两者共用本服务入口，避免「管理页搜索」与「注入检索」各自为战。
 */
class MemorySearchService(
    private val memoryDao: MemoryDao,
    private val searchDao: MemorySearchDao,
) {

    companion object {
        /** 注入 top-K 上限（防止 prompt 过长；与管理页分页无关） */
        const val DEFAULT_TOP_K = 20
        private const val TAG = "MemorySearch"
    }

    /**
     * 管理页分页检索统一入口（T9.3）。
     * 中文子串走 LIKE（先按角色/分类缩小候选）；英文等走 FTS 精确匹配由 searchFts 承担。
     */
    suspend fun searchPaged(
        query: String,
        cardId: Long?,
        manualOnly: Boolean = false,
        mapType: MemoryType? = null,
        page: Int = 0,
        pageSize: Int = 20,
    ): List<MemoryItem> = memoryDao.searchPaged(query, cardId, manualOnly, mapType, pageSize, page * pageSize)

    /** 管理页筛选计数（分类 Tab 数量 / 分页总数），过滤条件与 searchPaged 一致 */
    suspend fun countFiltered(
        query: String,
        cardId: Long?,
        manualOnly: Boolean = false,
        mapType: MemoryType? = null,
    ): Int = memoryDao.countFiltered(query, cardId, manualOnly, mapType)

    /**
     * FTS4 精确命中检索（英文/数字/标签等 Unicode61 可分词的词）。
     * 通过 @RawQuery 动态执行（FTS 虚拟表非 Room 实体），对查询词做安全转义后用 `content:"词"` 匹配；
     * 结果 JOIN 主表过滤 isDeleted=0，并按「置顶/重要/更新时间」排序截断 top-K。
     * 失败静默返回空（FTS 为增强检索，不影响主链路）。
     */
    suspend fun searchFts(keyword: String, topK: Int = DEFAULT_TOP_K): List<MemoryItem> {
        if (keyword.isBlank()) return emptyList()
        // 转义：FTS 查询串中双引号需翻倍转义，避免语法错误
        val escaped = keyword.trim().replace("\"", "\"\"")
        val sql = buildString {
            append("SELECT m.* FROM memory_items m ")
            append("JOIN memory_items_fts f ON m.id = f.rowid ")
            append("WHERE m.isDeleted = 0 AND memory_items_fts MATCH 'content:\"$escaped\"' ")
            append("ORDER BY m.isPinned DESC, ")
            append("CASE WHEN m.importance = 'IMPORTANT' THEN 1 ELSE 0 END DESC, m.updatedAt DESC ")
            append("LIMIT $topK")
        }
        return try {
            searchDao.searchFtsRaw(SimpleSQLiteQuery(sql))
        } catch (e: Exception) {
            Log.w(TAG, "FTS 检索失败，退回空结果：${e.javaClass.simpleName}: ${e.message}")
            emptyList()
        }
    }

    /**
     * 注入检索排序（top-K，T9.3）。
     * 把候选记忆按「置顶 > 重要 > 更新时间新」排序并截断——保证注入进 prompt 的是当前最该被记得的。
     * @param items 候选记忆（如某角色的共享+私有记忆合并）
     * @param topK 想注入的最多条数
     */
    fun rankByRelevance(items: List<MemoryItem>, topK: Int = DEFAULT_TOP_K): List<MemoryItem> =
        items.sortedWith(
            compareByDescending<MemoryItem> { it.isPinned }
                .thenByDescending { it.importance == MemoryImportance.IMPORTANT }
                .thenByDescending { it.updatedAt },
        ).take(topK)
}