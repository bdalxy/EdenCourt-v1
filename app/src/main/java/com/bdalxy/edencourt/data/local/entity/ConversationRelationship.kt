package com.bdalxy.edencourt.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 角色间互动关系标签（v1.2b 互动系统，T8.8）。
 * 用关系标签而非 0-100 数值表达"熟不熟"，避免数值体系返工；affinityScore 预留但不参与核心逻辑。
 * 等级递进：陌生 < 相识 < 友好 < 亲密；RIFT（疏远）为关系分歧的负向态。
 */
enum class RelationStatus { STRANGER, ACQUAINTANCE, FRIENDLY, CLOSE, RIFT }

/**
 * 会话间关系表 conv_relationship（v1.2b 互动系统，T8.8）。
 * 表达两个会话个体之间的关系（标签 + 好感度 + 互动次数 + 最近互动时间），供互动对象选择排权使用。
 * 约束：存储时统一 convAId < convBId（小 id 恒为 A），配合 (convAId, convBId) 唯一索引杜绝重复关系对。
 */
@Entity(
    tableName = "conv_relationship",
    indices = [Index(value = ["convAId", "convBId"], unique = true)],
)
data class ConversationRelationship(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    /** 会话 A（小 id） */
    val convAId: Long,
    /** 会话 B（大 id） */
    val convBId: Long,
    /** 关系标签（RelationStatus.name），默认陌生 */
    val relationStatus: String = RelationStatus.STRANGER.name,
    /** 好感度数值（预留，暂不参与核心逻辑，默认 0） */
    val affinityScore: Int = 0,
    /** 已互动次数 */
    val interactionCount: Int = 0,
    /** 最近一次互动时间（默认空=从未互动） */
    val lastInteractionAt: Long? = null,
    /** 一句话关系总结（可后补） */
    val noteSummary: String? = null,
)