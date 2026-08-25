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
 * 会话间关系表 conv_relationship（v1.2b 互动系统，T8.8；好感度数值体系 T11.6）。
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
    /** 关系标签（RelationStatus.name），默认陌生；T11.6 起由好感度分档推导，RIFT 为人工负向态 */
    val relationStatus: String = RelationStatus.STRANGER.name,
    /** 好感度数值（T11.6 生效：互动驱动增减，分档决定关系标签/氛围/对话内容） */
    val affinityScore: Int = 0,
    /** 已互动次数 */
    val interactionCount: Int = 0,
    /** 最近一次互动时间（默认空=从未互动） */
    val lastInteractionAt: Long? = null,
    /** 一句话关系总结（可后补） */
    val noteSummary: String? = null,
) {
    companion object {
        /** 好感度分档阈值（T11.6）：低于则降一档，分档驱动互动氛围与对话内容 */
        const val AFFINITY_ACQUAINTANCE = 20
        const val AFFINITY_FRIENDLY = 40
        const val AFFINITY_CLOSE = 70

        /** 单场互动好感度加成（T11.6）：破冰基础 10 + 每回合 2（封顶 5 回合），聊得越投入加越多 */
        fun affinityGain(turnCount: Int): Int = 10 + 2 * turnCount.coerceIn(0, 5)

        /** 好感度 → 关系标签（T11.6 取代按次数推导；RIFT 属人工负向态，不由数值产生） */
        fun relationStatusForAffinity(score: Int): String = when {
            score < AFFINITY_ACQUAINTANCE -> RelationStatus.STRANGER.name
            score < AFFINITY_FRIENDLY -> RelationStatus.ACQUAINTANCE.name
            score < AFFINITY_CLOSE -> RelationStatus.FRIENDLY.name
            else -> RelationStatus.CLOSE.name
        }

        /** 好感度 → 中文档位名（互动中心展示用，T11.6） */
        fun affinityTierLabel(score: Int): String = when (relationStatusForAffinity(score)) {
            RelationStatus.STRANGER.name -> "陌生"
            RelationStatus.ACQUAINTANCE.name -> "相识"
            RelationStatus.FRIENDLY.name -> "友好"
            RelationStatus.CLOSE.name -> "亲密"
            RelationStatus.RIFT.name -> "疏远"
            else -> "未知"
        }
    }
}