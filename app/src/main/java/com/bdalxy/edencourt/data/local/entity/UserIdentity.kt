package com.bdalxy.edencourt.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 用户自设角色卡身份（v1.2b 互动系统，T8.13，用户新需求 2026-08-25 拍板）。
 * - **唯一绑定**：id 恒为 1，同一时间只有一条（upsert 覆盖），即"用户给自己安排的一个唯一身份"。
 * - 所有角色默认知道这个身份：角色 persona 构建时注入（统一对用户的称呼），互动 prompt 同步注入。
 * - calling=别人对用户的称呼（如「学姐」）；name=用户名字/昵称；description/personality/background=用户设定。
 */
@Entity(tableName = "user_identity")
data class UserIdentity(
    @PrimaryKey
    val id: Int = 1,
    /** 用户名字/昵称 */
    val name: String = "我",
    /** 别人对用户的称呼（如「学姐」），全角色统一 */
    val calling: String = "你",
    /** 用户设定描述（"我是谁"，一句话） */
    val description: String = "",
    /** 用户性格（可选） */
    val personality: String = "",
    /** 用户背景（可选） */
    val background: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
)
