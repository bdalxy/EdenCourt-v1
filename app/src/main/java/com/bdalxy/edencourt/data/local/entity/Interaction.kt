package com.bdalxy.edencourt.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 互动状态（v1.2b 互动系统，T8.10） */
enum class InteractionStatus {
    SCHEDULED, RUNNING, COMPLETED, SKIPPED, FAILED
}

/** 参与者角色（v1.2b 互动系统，T8.10）：发起方 / 目标方 */
enum class ParticipantRole { INITIATOR, TARGET }

/**
 * 互动主表 interaction（v1.2b 互动系统，T8.10）。
 * 记录一场「会话角色 A → B 的双向对话」的元信息：谁发起、谁目标、话题锚点、轮次与起止时间。
 */
@Entity(
    tableName = "interaction",
    indices = [Index(value = ["status", "createdAt"])],
)
data class Interaction(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    /** 发起方会话（小号 A） */
    val initiatorConvId: Long,
    /** 目标会话（B） */
    val targetConvId: Long,
    /** 互动状态（InteractionStatus.name） */
    val status: String = InteractionStatus.SCHEDULED.name,
    /** 本次互动话题锚点（A 的开场抛题，每轮携带防跑偏） */
    val topicSeed: String = "",
    /** 开始时间 */
    val startedAt: Long? = null,
    /** 结束时间 */
    val endedAt: Long? = null,
    /** 实际完成轮次（台词句数） */
    val turnCount: Int = 0,
    /** 创建时间 */
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * 互动参与者表 interaction_participant（v1.2b 互动系统，T8.10）。
 * 一场互动天然两行（A/B 各一）；未读 = readAt IS NULL；摘要按会话视角分开存（A 看到"我去找 B"、B 看到"A 来找我"）。
 */
@Entity(
    tableName = "interaction_participant",
    indices = [
        Index(value = ["interactionId"]),
        Index(value = ["convId", "readAt", "createdAt"]),
    ],
)
data class InteractionParticipant(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    /** 关联互动 */
    val interactionId: Long,
    /** 参与者会话 id */
    val convId: Long,
    /** 参与角色（ParticipantRole.name：initiator / target） */
    val role: String = ParticipantRole.INITIATOR.name,
    /** 该会话视角的一条摘要（互动中心/会话内 digest 展示） */
    val summary: String? = null,
    /** 已读时间（null=未读，供红点） */
    val readAt: Long? = null,
    /** 创建时间 */
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * 互动轮次表 interaction_turn（v1.2b 互动系统，T8.10）。
 * 逐句台词；speakerConvId 记录本轮谁说话；(interactionId, turnIndex) 唯一防重复。
 */
@Entity(
    tableName = "interaction_turn",
    indices = [Index(value = ["interactionId", "turnIndex"], unique = true)],
)
data class InteractionTurn(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    /** 关联互动 */
    val interactionId: Long,
    /** 轮次序号（0 起，按发言顺序） */
    val turnIndex: Int,
    /** 本轮说话者会话 id */
    val speakerConvId: Long,
    /** 台词内容 */
    val content: String,
    /** 时间 */
    val createdAt: Long = System.currentTimeMillis(),
)