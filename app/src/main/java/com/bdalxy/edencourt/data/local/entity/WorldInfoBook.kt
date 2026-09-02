package com.bdalxy.edencourt.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 世界书（Lorebook，v1.5-A3）。
 * 独立实体：一本世界书含多条条目，可被多个会话各自挂载（第一版一个会话挂 0 或 1 本）。
 * 不直接注入；实际注入的是其下命中的条目（world_info_entry）。
 */
@Entity(tableName = "world_info_book")
data class WorldInfoBook(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    /** 世界书名称 */
    val name: String,
    /** 简介（不注入，仅管理页展示） */
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)