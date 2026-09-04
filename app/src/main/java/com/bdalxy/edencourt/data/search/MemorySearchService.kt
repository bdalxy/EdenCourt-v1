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

        /**
         * 判断查询词是否「可被 FTS unicode61 分词」：含 ASCII 字母或数字（英文/数字/标签词）。
         * 中文子串 FTS 几乎无法命中、且拖慢，故纯中文查询不走 FTS（仍 LIKE 兜底）。
         */
        private fun isFtsQuery(query: String): Boolean =
            query.any { it.code in 32..126 && it.isLetterOrDigit() }
    }

    /**
     * 管理页分页检索统一入口（T9.3 / T12.7 接线）。
     * 中文子串走 LIKE（先按角色/分类缩小候选）；可分词词（英文/数字/标签）在 LIKE 之外并入 FTS 精确命中并置前。
     * 融合规则：先跑 LIKE 分页得本页结果，再把「本页里确被 FTS 精确命中的记忆」排到最前，
     *          其余按原序补足（不改列表长度、不动 offset/count，翻页语义稳定；未命中 FTS 的 LIKE 兜底仍显示）。
     */
    suspend fun searchPaged(
        query: String,
        cardId: Long?,
        manualOnly: Boolean = false,
        mapType: MemoryType? = null,
        page: Int = 0,
        pageSize: Int = 20,
    ): List<MemoryItem> {
        val like = memoryDao.searchPaged(query, cardId, manualOnly, mapType, pageSize, page * pageSize)
        // 纯中文/空查询：LIKE 已可靠，无需 FTS 增强
        if (query.isBlank() || !isFtsQuery(query) || like.isEmpty()) return like
        // 可分词词：取本页内部被 FTS 精确命中的记忆，提至最前（不引入新的跨页去重）
        val ftsPin = runCatching { searchFts(query.trim(), topK = pageSize * 2) }.getOrElse { emptyList() }
            .filter { hit -> like.any { it.id == hit.id } }
        if (ftsPin.isEmpty()) return like
        val pinnedIds = ftsPin.map { it.id }.toSet()
        return ftsPin + like.filter { it.id !in pinnedIds }
    }

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
     * 转义说明（T12.7）：查询词整体以双引号包裹成 **短语**（`content:"..."`），短语内
     *   `*` `(` `)` `-` `OR` `AND` 等 FTS 语法字符均按字面文本参与匹配、不做操作符解析；
     *   唯一需翻倍转义的是引号自身（见 escaped），避免提前闭合短语引发语法错误。
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
     * @param query 当前对话相关性查询词（方案 F 记忆主动引用增强）。非空时在基准排序前，
     *              先按「与 query 的关键词重叠度」把相关记忆顶到前面，让「记得你说过的 X」更可靠。
     *              预期：同一角色名下，与当前话题相关的记忆优先于「最近才更新但无关」的记忆。
     */
    fun rankByRelevance(items: List<MemoryItem>, topK: Int = DEFAULT_TOP_K, query: String? = null): List<MemoryItem> {
        val q = query?.trim().orEmpty()

        val ranked = items.sortedWith(
            compareByDescending<MemoryItem> { relevanceScore(it, q) }
                // A4 情感记忆优先：与当前话题相关之上、置顶之下，插入「情感」档位——
                // 把角色对用户的情感态度(EMOTION)顶到前面，让「知道你在像我」等感受优先注入口吻
                .thenByDescending { it.type == MemoryType.EMOTION }
                .thenByDescending { it.isPinned }
                .thenByDescending { it.importance == MemoryImportance.IMPORTANT }
                .thenByDescending { it.updatedAt },
        )
        return ranked.take(topK)
    }

    /**
     * 方案 F v2「触发增强」：从候选记忆里挑一条「适合主动提起」的记忆。
     * 触发条件 = 当前用户消息 query 与某条记忆主题相关（该记忆关键词命中 ≥1）。
     * 返回命中里相关性得分最高的一条（并列取最近更新）；query 为空 / 无命中返回 null（不触发主动引用）。
     * 上层拿到非 null 后，在记忆注入片段里追加「你可以主动提起」引导，让角色自然带出记忆而非被动当背景。
     * @return 命中记忆；无可主动提起的记忆返回 null
     */
    fun findProactiveReference(items: List<MemoryItem>, query: String?): MemoryItem? {
        val q = query?.trim().orEmpty()
        if (q.isEmpty()) return null
        return items
            .filter { relevanceScore(it, q) >= 1 }
            .maxWithOrNull(
                compareBy<MemoryItem> { relevanceScore(it, q) }
                    .thenBy { it.updatedAt },
            )
    }

    /**
     * 方案 F v1：计算某条记忆与当前对话 query 的「相关性得分」（0~2）。
     * 判定方向反着来：取记忆自身的关键词（AI 提取时落库的核心词，如「热奶茶/小夏/买」），
     * 看其中有多少个「较长词（≥2 字）」出现在当前用户消息整段里。
     * - 这样只命中有意义的主题词，避免「把 query 切成 8 字长句」导致中文几乎永不命中；
     * - query 出现该记忆的关键词越多，得分越高（封顶 2）。
     */
    private fun relevanceScore(item: MemoryItem, query: String): Int {
        if (query.isEmpty()) return 0
        val keywords = (item.keywords ?: "").split(",", "，").map { it.trim() }
            .filter { it.length >= 2 } // 只取 ≥2 字的关键词（过滤掉单字/无意义公共字）
        if (keywords.isEmpty()) return 0
        val hits = keywords.count { kw -> query.contains(kw) }
        // 命中 1 个主题词 +1，≥2 个 +2（封顶）
        return when {
            hits >= 2 -> 2
            hits == 1 -> 1
            else -> 0
        }
    }
}