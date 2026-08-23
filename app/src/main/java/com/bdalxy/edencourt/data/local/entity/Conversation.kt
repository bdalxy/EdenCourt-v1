package com.bdalxy.edencourt.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 会话表（企划书 4.1.1 / 4.2.1）。
 * characterCardId 为空表示默认助手；lastMessageTime 为空表示尚无消息。
 */
@Entity(
    tableName = "conversations",
    indices = [Index(value = ["updatedAt"], orders = [Index.Order.DESC])],
)
data class Conversation(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val title: String = "",
    val characterCardId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false,
    val lastMessagePreview: String = "",
    val lastMessageTime: Long? = null,
)
