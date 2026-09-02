package com.bdalxy.edencourt.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 世界书条目（Lorebook entry，v1.5-A3）。
 * 「关键词 → 内容」动态词典：聊天中命中 keys 才注入 content；未命中不注入。
 * 字段取 SillyTavern / DeepLore / Tavo 公约：keys 主关键词 + secondaryKeys 副关键词收窄 +
 * priority 优先级（越小越先）+ constant 恒定注入 + enabled 开关。
 */
@Entity(
    tableName = "world_info_entry",
    indices = [Index(value = ["bookId"])],
)
data class WorldInfoEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    /** 所属世界书 id */
    val bookId: Long,
    /** 条目名（仅管理导航，不注入） */
    val title: String = "",
    /** 主关键词，顿号/逗号隔开（命中任一即触发） */
    val keys: String = "",
    /** 副关键词，顿号/逗号隔开（可空，配合 logic 收窄） */
    val secondaryKeys: String = "",
    /** 副关键词逻辑：AND_ANY=主+任一副 / AND_ALL=主+全副（默认 AND_ANY，见 WorldInfoBook.LOGIC_*。默认用字面量，避免 Room schema 记录跨类常量引用） */
    val logic: String = "AND_ANY",
    /** 注入内容（自包含，不含关键词/标题） */
    val content: String = "",
    /** 优先级：越小越先注入（10~20 核心 / 50 标准 / 80~100 边角） */
    val priority: Int = 50,
    /** 恒定注入：无条件每次注入（核心世界观规则） */
    val constant: Boolean = false,
    /** 开关 */
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
)