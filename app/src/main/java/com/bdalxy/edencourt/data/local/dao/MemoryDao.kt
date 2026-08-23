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

    /** 记忆数量（统计用 / 空状态判断） */
    @Query("SELECT COUNT(*) FROM memory_items WHERE isDeleted = 0")
    suspend fun countActive(): Int
}
