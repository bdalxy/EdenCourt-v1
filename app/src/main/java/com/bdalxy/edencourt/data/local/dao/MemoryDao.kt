package com.bdalxy.edencourt.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.bdalxy.edencourt.data.local.entity.MemoryItem
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

    /** 更新记忆条目（编辑内容/置信度/软删除标记时使用） */
    @Update
    suspend fun update(item: MemoryItem)

    /** 观察未删除的全部记忆：最新在前（记忆管理页 T5.5） */
    @Query("SELECT * FROM memory_items WHERE isDeleted = 0 ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<MemoryItem>>

    /** 观察未删除记忆（仅未删除，管理页展示用） */
    @Query("SELECT * FROM memory_items WHERE isDeleted = 0 ORDER BY createdAt DESC")
    suspend fun getAllActive(): List<MemoryItem>

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
}
