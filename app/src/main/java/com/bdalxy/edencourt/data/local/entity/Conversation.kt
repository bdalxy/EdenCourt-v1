package com.bdalxy.edencourt.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 会话表（企划书 4.1.1 / 4.2.1）。
 * characterCardId 为空表示默认助手；lastMessageTime 为空表示尚无消息。
 * code 建会话随机短码（v1.2 会话唯一身份组成部分，T8.0）。
 */
@Entity(
    tableName = "conversations",
    indices = [Index(value = ["updatedAt"], orders = [Index.Order.DESC])],
)
data class Conversation(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val title: String = "",
    /** 会话备注别名（T6.14 用户自定义显示名；空=用 title，类微信备注） */
    val remark: String = "",
    val characterCardId: Long? = null,
    /** 建会话随机短码（v1.2 会话唯一身份，T8.0） */
    val code: String = newShortCode(),
    /** 是否开放参与角色间互动（v1.2b 互动资格开关，T8.7；新建默认 false=不参与互动） */
    val interactionEnabled: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false,
    val lastMessagePreview: String = "",
    val lastMessageTime: Long? = null,
)
