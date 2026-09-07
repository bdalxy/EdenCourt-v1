package com.bdalxy.edencourt.data.local.entity

import androidx.room.ColumnInfo
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
 * 消息类型（T5.7 富类型协议预留，为远期 AI 设备控制铺路）。
 * - TEXT：普通文本对话（默认，现有消息全兼容）
 * - ACTION：AI 发起/请求的设备操作（打开App/点按钮/查信息，远期扩展）
 * - RESULT：设备操作执行结果反馈（成功/失败/数据，远期扩展）
 * 当前阶段仅 TEXT 正常渲染，ACTION/RESULT 预留渲染骨架。
 */
enum class MessageType {
    TEXT, ACTION, RESULT,
    /** 互动摘要（v1.2b 角色间互动，T8.10）：插入双方会话的一条"今天有人来找我/我找了谁"摘要消息 */
    INTERACTION_DIGEST,
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
    /** 消息类型（T5.7：TEXT/ACTION/RESULT，默认 TEXT 兼容存量） */
    @ColumnInfo(defaultValue = "'TEXT'")
    val type: MessageType = MessageType.TEXT,
    /** 消息正文 */
    val content: String = "",
    /**
     * 思考过程（推理模型 reasoning_content；T3.4 修正，v2 新增列）。
     * 仅 AI 消息使用，普通模型/无思考时为空串；UI 层默认折叠展示。
     * 注意：不设 @ColumnInfo(defaultValue)——历史迁移 v1→v2 该列无 DEFAULT 约束，实体须与真实库一致，否则 schema 校验失败崩溃（2026-09-07 修复）。
     */
    val reasoning: String = "",
    /** 创建时间（毫秒时间戳） */
    val createdAt: Long = System.currentTimeMillis(),
    /** 发送状态（默认 done，发送时由调用方显式设置） */
    val status: MessageStatus = MessageStatus.DONE,
    /** 消耗 token 数（可选，默认 0） */
    val tokenCount: Int = 0,
    /** 使用的模型名（如 deepseek-chat / local-1.5b） */
    val modelUsed: String = "",
    /** 发送者归属会话 id（v2.0 A6-1：群聊中标识消息是哪个参与者角色发的；null=用户「我」发的，非 null=对应参与者 1v1 会话） */
    val senderConversationId: Long? = null,
)
