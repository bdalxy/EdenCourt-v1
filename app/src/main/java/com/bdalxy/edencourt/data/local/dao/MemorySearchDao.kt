package com.bdalxy.edencourt.data.local.dao

import androidx.room.Dao
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.bdalxy.edencourt.data.local.entity.MemoryItem

/**
 * FTS4 全文检索数据访问（记忆管理页搜索 + 注入检索底座，T9.2）。
 * - 对应的虚拟表 `memory_items_fts` 由 MIGRATION_16_17 手工创建，并用触发器与 memory_items 保持同步：
 *   新增/编辑/软删后索引即时一致，杜绝「删改后搜索结果残留旧数据」。
 * - 采用 @RawQuery 动态执行 SQL：FTS 虚拟表不属于 Room 实体，Room 编译期无法校验其结构，
 *   故不通过静态 @Query 引用，改由调用方构造完整 SQL（JOIN memory_items 过滤 isDeleted=0）。
 * - @RawQuery 对 `SELECT m.*` 做「宽容列映射」回到 MemoryItem（列名与实体字段一致）。
 */
@Dao
interface MemorySearchDao {

    /**
     * 动态执行任意 SQL 并返回实体列表（Room 不做表结构静态校验，运行时按列名宽容映射）。
     * 供 FTS4 MATCH 检索使用：SQL 由调用方构造（如 `SELECT m.* FROM memory_items m JOIN memory_items_fts ... WHERE memory_items_fts MATCH 'content:"词"'`）。
     */
    @RawQuery
    @Suppress("DEPRECATION")
    suspend fun searchFtsRaw(query: SupportSQLiteQuery): List<MemoryItem>
}