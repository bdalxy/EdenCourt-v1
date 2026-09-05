package com.bdalxy.edencourt.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 群聊参与者关联（v2.0 A6-1：会话 ↔ 多个已有会话角色）。
 * 一个群聊会话可关联多个已有 1v1 会话作为参与者；每个参与者以自己独立的亲密度/记忆/作息参与群聊。
 * conversationId 与 participantConversationId 联合唯一——同一参与者不会重复加入同一群。
 */
@Entity(
    tableName = "conversation_participants",
    foreignKeys = [
        ForeignKey(
            entity = Conversation::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Conversation::class,
            parentColumns = ["id"],
            childColumns = ["participantConversationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["conversationId"]),
        Index(value = ["conversationId", "participantConversationId"], unique = true),
    ],
)
data class ConversationParticipant(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    /** 所属群聊会话 id（外键 → conversations，级联删除） */
    val conversationId: Long,
    /** 参与者对应的 1v1 会话 id（外键 → conversations，级联删除） */
    val participantConversationId: Long,
)