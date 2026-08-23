package com.bdalxy.edencourt.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 角色卡表（企划书 4.1.3 / 4.2.3，T4.2）。
 * - jsonContent 保存角色卡完整 JSON（可回溯、可再编辑后整体回写）
 * - isBuiltIn=true 表示内置卡（不可删除，见 T4.3）
 * - name 建索引便于按名检索
 */
@Entity(
    tableName = "character_cards",
    indices = [Index(value = ["name"])],
)
data class CharacterCardEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    /** 角色名（内置卡索引名） */
    val name: String,
    /** 简介（列表页展示用一句话描述） */
    val description: String = "",
    /** 角色卡完整 JSON（CharacterCard 序列化结果） */
    val jsonContent: String,
    /** 自定义头像本地文件路径（T6.2B；null=使用默认首字头像） */
    val avatar: String? = null,
    /** 是否内置卡（内置卡不可删除） */
    val isBuiltIn: Boolean = false,
    /** 创建时间（毫秒时间戳） */
    val createdAt: Long = System.currentTimeMillis(),
    /** 更新时间（毫秒时间戳，编辑后刷新） */
    val updatedAt: Long = System.currentTimeMillis(),
)
