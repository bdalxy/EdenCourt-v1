package com.bdalxy.edencourt.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 会话角色当前情绪（v1.5-A2 实时情绪系统）。
 * 每会话唯一一条：记录「此刻心情」的类别 + 强度 + 来源（自主/交互）+ 有效期。
 * 交互情绪（由用户言行触发）带 expiresAt，到期后回落自主基线；自主情绪不落库、由作息派生。
 */
@Entity(
    tableName = "conversation_mood",
    indices = [Index(value = ["conversationId"], unique = true)],
)
data class ConversationMood(
    @PrimaryKey
    val conversationId: Long,
    /** 当前情绪类别（MoodTag.name 的字面量，避免 Room schema 记录跨包常量引用而 MissingType） */
    val moodTag: String = "PEACEFUL",
    /** 情绪强度 0~100 */
    val intensity: Int = 50,
    /** 来源：REACT=交互触发（用户言行），AUTO=自主（留用，暂由作息派生不落库）。字面量同 moodTag 理由 */
    val source: String = "REACT",
    /** 最近一次写情绪时间 */
    val updatedAt: Long = System.currentTimeMillis(),
    /** 交互情绪的到期时间（毫秒），到期回落自主基线；自主情绪恒 null */
    val expiresAt: Long? = null,
)

/** 情绪来源类型 */
enum class MoodSource { REACT, AUTO }