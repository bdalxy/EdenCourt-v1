package com.bdalxy.edencourt.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 生成会话/角色卡的全局唯一随机短码（v1.2 会话唯一身份底座，T8.0）。
 * - 建档/建会话时生成，作为该个体"独一无二身份验证"的一部分
 * - 取 UUID 连续 12 位十六进制，碰撞概率极低
 */
internal fun newShortCode(): String =
    java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12)

/**
 * 角色卡表（企划书 4.1.3 / 4.2.3，T4.2）。
 * - jsonContent 保存角色卡完整 JSON（可回溯、可再编辑后整体回写）
 * - isBuiltIn=true 表示内置卡（不可删除，见 T4.3）
 * - name 建索引便于按名检索
 * - code 建档随机短码（v1.2 会话唯一身份组成部分，T8.0）
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
    /** 备注别名（T6.14 用户自定义显示名；空=用 name，类微信备注） */
    val remark: String = "",
    /** 简介（列表页展示用一句话描述） */
    val description: String = "",
    /** 角色卡完整 JSON（CharacterCard 序列化结果） */
    val jsonContent: String,
    /** 自定义头像本地文件路径（T6.2B；null=使用默认首字头像） */
    val avatar: String? = null,
    /** 是否内置卡（内置卡不可删除） */
    val isBuiltIn: Boolean = false,
    /** 建档随机短码（v1.2 会话唯一身份，T8.0） */
    val code: String = newShortCode(),
    /** 创建时间（毫秒时间戳） */
    val createdAt: Long = System.currentTimeMillis(),
    /** 更新时间（毫秒时间戳，编辑后刷新） */
    val updatedAt: Long = System.currentTimeMillis(),
)
