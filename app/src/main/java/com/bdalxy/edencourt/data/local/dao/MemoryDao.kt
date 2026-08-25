package com.bdalxy.edencourt.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.bdalxy.edencourt.data.local.entity.MemoryItem
import com.bdalxy.edencourt.data.local.entity.MemoryType
import kotlinx.coroutines.flow.Flow

/**
 * 记忆条目表数据访问（企划书 4.1.4 / T5.1）。
 * - 检索：LIKE 模糊匹配 keywords / content，供 T5.4 生成前检索注入
 * - 软删除：isDeleted=1 标记，不物理删除（可回溯、可恢复）
 * - Flow 查询为响应式查询：表变化自动重查重发
 */
@Dao
interface MemoryDao {

    /** 新增记忆条目，返回生成的 id */
    @Insert
    suspend fun insert(item: MemoryItem): Long

    /** 批量新增（T11.4 记忆导入：一次事务插入多条，返回各条生成的 id） */
    @Insert
    suspend fun insertAll(items: List<MemoryItem>): List<Long>

    /** 更新记忆条目（编辑内容/置信度/软删除标记时使用） */
    @Update
    suspend fun update(item: MemoryItem)

    /** 观察未删除的全部记忆：最新在前（记忆管理页 T5.5） */
    @Query("SELECT * FROM memory_items WHERE isDeleted = 0 ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<MemoryItem>>

    /** 观察未删除记忆（仅未删除，管理页展示用） */
    @Query("SELECT * FROM memory_items WHERE isDeleted = 0 ORDER BY createdAt DESC")
    suspend fun getAllActive(): List<MemoryItem>

    /** 观察未删除记忆按更新时间降序（T10.7 时间轴：展示记忆「演化」，最近更新的在前） */
    @Query("SELECT * FROM memory_items WHERE isDeleted = 0 ORDER BY updatedAt DESC")
    fun observeAllByUpdatedDesc(): Flow<List<MemoryItem>>

    /** 按 id 取单条记忆（管理页编辑/删除定位用） */
    @Query("SELECT * FROM memory_items WHERE id = :id")
    suspend fun getById(id: Long): MemoryItem?

    /**
     * 关键词检索未删除记忆（T5.4 生成前注入）。
     * LIKE %keyword% 匹配 keywords 或 content 字段，按置信度降序、最新优先。
     */
    @Query(
        """
        SELECT * FROM memory_items
        WHERE isDeleted = 0
          AND (keywords LIKE '%' || :keyword || '%' OR content LIKE '%' || :keyword || '%')
        ORDER BY confidence DESC, createdAt DESC
        """,
    )
    suspend fun searchByKeyword(keyword: String): List<MemoryItem>

    /** 软删除：将 isDeleted 置为 1（管理页删除，二次确认后调用） */
    @Query("UPDATE memory_items SET isDeleted = 1, updatedAt = :now WHERE id = :id")
    suspend fun softDeleteById(id: Long, now: Long = System.currentTimeMillis())

    /** 批量软删除（T11.3 批量管理）：id 集合一次性置 isDeleted=1 */
    @Query("UPDATE memory_items SET isDeleted = 1, updatedAt = :now WHERE id IN (:ids)")
    suspend fun softDeleteByIds(ids: List<Long>, now: Long = System.currentTimeMillis())

    /** 批量设重要/取消重要（T11.3 批量管理）：importance 传枚举名（NORMAL/IMPORTANT） */
    @Query("UPDATE memory_items SET importance = :importance, updatedAt = :now WHERE id IN (:ids)")
    suspend fun updateImportanceByIds(
        ids: List<Long>,
        importance: String,
        now: Long = System.currentTimeMillis(),
    )

    /** 批量置顶/取消置顶（T11.3 批量管理） */
    @Query("UPDATE memory_items SET isPinned = :pinned, updatedAt = :now WHERE id IN (:ids)")
    suspend fun updatePinnedByIds(
        ids: List<Long>,
        pinned: Boolean,
        now: Long = System.currentTimeMillis(),
    )

    /**
     * 按归属角色卡取未删除记忆（T6.13 按角色卡隔离注入）。
     * 只返回该角色卡名下的记忆；旧数据 characterCardId=null 一并排除（无归属，不再注入）。
     */
    @Query(
        """
        SELECT * FROM memory_items
        WHERE isDeleted = 0 AND characterCardId = :cardId
        ORDER BY confidence DESC, createdAt DESC
        LIMIT :limit
        """,
    )
    suspend fun getActiveByCard(cardId: Long, limit: Int): List<MemoryItem>

    /**
     * 取某角色卡的「共享」记忆（v1.1 会话级私有定稿，T7.4/7.5）。
     * 共享记忆对所有「用该卡创建的会话」可见；私密的不在此列（从会话内单独取）。
     */
    @Query(
        """
        SELECT * FROM memory_items
        WHERE isDeleted = 0 AND characterCardId = :cardId AND visibility = 'SHARED'
        ORDER BY confidence DESC, createdAt DESC
        LIMIT :limit
        """,
    )
    suspend fun getSharedByCard(cardId: Long, limit: Int): List<MemoryItem>

    /**
     * 取某会话的「私有」记忆（v1.1 会话级私有定稿，T7.4/7.5）。
     * 私有记忆仅来源会话可见（sourceConversationId = 当前会话），跨会话完全隔离。
     */
    @Query(
        """
        SELECT * FROM memory_items
        WHERE isDeleted = 0 AND sourceConversationId = :conversationId AND visibility = 'PRIVATE'
        ORDER BY createdAt DESC
        LIMIT :limit
        """,
    )
    suspend fun getPrivateByConversation(conversationId: Long, limit: Int): List<MemoryItem>

    /**
     * 按关键词检索「来源会话」候选（v1.2 跨会话打听目标定位，T8.1）。
     * 命中某条记忆则其来源会话（sourceConversationId）"可能知道"该话题，可作为打听目标候选 B。
     * 只取 sourceConversationId 非空且未删除的记忆，按会话去重。
     */
    @Query(
        """
        SELECT DISTINCT m.sourceConversationId
        FROM memory_items m
        WHERE m.isDeleted = 0 AND m.sourceConversationId IS NOT NULL
          AND (m.keywords LIKE '%' || :keyword || '%' OR m.content LIKE '%' || :keyword || '%')
        """,
    )
    suspend fun findSourceConversationIdsByKeyword(keyword: String): List<Long>

    /** 记忆数量（统计用 / 空状态判断） */
    @Query("SELECT COUNT(*) FROM memory_items WHERE isDeleted = 0")
    suspend fun countActive(): Int

    /**
     * 轻量「表变化信号」（T9.5）：观察未删除记忆条数。
     * 任何行增删（count 变化）都会重发新计数，管理页用它触发分页重查——避免 observeAll 全量加载行打击分页。
     * 仅「编辑不改条数」不触发；当前管理页缺该场景（仅删除/添加，均改变条数）。
     */
    @Query("SELECT COUNT(*) FROM memory_items WHERE isDeleted = 0")
    fun observeActiveCount(): Flow<Int>

    /** 观察置顶记忆数（T10.6 概览统计：随表增删改/置顶切换自动刷新） */
    @Query("SELECT COUNT(*) FROM memory_items WHERE isDeleted = 0 AND isPinned = 1")
    fun observePinnedCount(): Flow<Int>

    /** 观察「重要」记忆数（T10.6 概览统计） */
    @Query("SELECT COUNT(*) FROM memory_items WHERE isDeleted = 0 AND importance = 'IMPORTANT'")
    fun observeImportantCount(): Flow<Int>

    /** 观察私密记忆数（T10.6 概览统计） */
    @Query("SELECT COUNT(*) FROM memory_items WHERE isDeleted = 0 AND visibility = 'PRIVATE'")
    fun observePrivateCount(): Flow<Int>

    /**
     * 管理页综合分页检索（阶段一统一检索入口，T9.3/T9.5）。
     * 按「搜索词 + 角色归属 + 分类」组合过滤；置顶/重要优先，最新在前；LIMIT 翻页加载，避免全量进内存。
     * @param query 搜索词（空串=不过滤）
     * @param cardId 角色卡 id（null=全部；配合 manualOnly）
     * @param manualOnly true=只看手动/未归属（characterCardId IS NULL）
     * @param type 分类（null=全部）
     * @param limit 每页条数
     * @param offset 偏移
     */
    @Query(
        """
        SELECT * FROM memory_items
        WHERE isDeleted = 0
          AND CASE
            WHEN :manualOnly = 1 THEN characterCardId IS NULL
            WHEN :cardId IS NULL THEN 1
            ELSE characterCardId = :cardId
          END
          AND (:type IS NULL OR type = :type)
          AND (:query = '' OR content LIKE '%' || :query || '%' OR keywords LIKE '%' || :query || '%')
        ORDER BY isPinned DESC,
                 CASE WHEN importance = 'IMPORTANT' THEN 1 ELSE 0 END DESC,
                 updatedAt DESC
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun searchPaged(
        query: String,
        cardId: Long?,
        manualOnly: Boolean,
        type: MemoryType?,
        limit: Int,
        offset: Int,
    ): List<MemoryItem>

    /** 管理页筛选计数（T9.3：分类 Tab 数量 / 分页总数），过滤条件与 searchPaged 一致 */
    @Query(
        """
        SELECT COUNT(*) FROM memory_items
        WHERE isDeleted = 0
          AND CASE
            WHEN :manualOnly = 1 THEN characterCardId IS NULL
            WHEN :cardId IS NULL THEN 1
            ELSE characterCardId = :cardId
          END
          AND (:type IS NULL OR type = :type)
          AND (:query = '' OR content LIKE '%' || :query || '%' OR keywords LIKE '%' || :query || '%')
        """,
    )
    suspend fun countFiltered(
        query: String,
        cardId: Long?,
        manualOnly: Boolean,
        type: MemoryType?,
    ): Int
}
