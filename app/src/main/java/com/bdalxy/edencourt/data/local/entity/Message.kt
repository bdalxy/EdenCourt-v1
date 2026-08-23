package com.bdalxy.edencourt.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** 消息角色（企划书 4.1.2：user/assistant/system）。 */
enum class Role {
    USER, ASSISTANT, SYSTEM
}

/** 消息状态（企划书 4.1.2：sending/sent/failed/generating/done）。 */
enum class MessageStatus {
    SENDING, SENT, FAILED, GENERATING, DONE
}

/**
 * 消息表（企划书 4.1.2 / 4.2.2）。
 * - 外键：conversationId -> conversations.id，ON DELETE CASCADE（删会话级联删消息）
 * - 索引：conversationId + createdAt 复合，用于按会话按时间取消息
 */
@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = Conversation::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["conversationId", "createdAt"])],
)
data class Message(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    /** 所属会话 id（外键，级联删除） */
    val conversationId: Long,
    /** 消息角色（用户/助手/系统） */
    val role: Role = Role.USER,
    /** 消息正文 */
    val content: String = "",
    /** 创建时间（毫秒时间戳） */
    val createdAt: Long = System.currentTimeMillis(),
    /** 发送状态（默认 done，发送时由调用方显式设置） */
    val status: MessageStatus = MessageStatus.DONE,
    /** 消耗 token 数（可选，默认 0） */
    val tokenCount: Int = 0,
    /** 使用的模型名（如 deepseek-chat / local-1.5b） */
    val modelUsed: String = "",
)
