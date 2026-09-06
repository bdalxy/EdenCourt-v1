package com.bdalxy.edencourt.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 角色间「共同经历事件」表 relationship_event（T11.13 关系事件叙事）。
 * 每次互动成功后沉淀一条事件（场景/主题/氛围/要点），作为「关系故事」区的里程碑叙事素材，
 * 并供 Prompt 注入让对话有「我们经历过」的连续性；不替代好感度数值体系（叙事层叠加）。
 * 约束：与 conv_relationship 一致，存储时统一 convAId < convBId（小 id 恒为 A）。
 */
@Entity(
    tableName = "relationship_event",
    indices = [Index(value = ["convAId", "convBId", "happenedAt"])],
)
data class RelationshipEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    /** 会话 A（小 id）：INTERACTION=小 id 会话；用户共同观看事件=本角色会话 */
    val convAId: Long,
    /**
     * 会话 B（大 id）。INTERACTION=大 id 会话（且 convAId < convBId 规范化）；
     * 用户共同观看（WATCH）=null（无第二会话，非哨兵值，T12.10 由可空列明确表达）。
     */
    val convBId: Long?,
    /** 场景（T11.13）：这场互动发生在什么情境下——事件驱动时记「因为「{因由}」」，否则「日常」 */
    val scene: String = "",
    /** 主题（本次互动话题锚点，即开场者抛出的第一句话） */
    val topic: String = "",
    /** 氛围（互动开始时的好感度档位中文：陌生/相识/友好/亲密，叙事里程碑定调） */
    val mood: String = "",
    /** 要点（双方视角摘要合并，叙事素材） */
    val points: String = "",
    /** 互动来源（InteractionSource.name：MANUAL=手动 / AUTO=自动驱动） */
    val source: String = InteractionSource.MANUAL.name,
    /** 事件类型（T11.16）：INTERACTION=角色间互动共同经历 / WATCH=用户共同观看（你主动分享内容后一起看） */
    @ColumnInfo(defaultValue = "'INTERACTION'")
    val type: String = TYPE_INTERACTION,
    /** 发生时间 */
    val happenedAt: Long = System.currentTimeMillis(),
) {
    companion object {
        /** 角色间互动共同经历（默认，T11.13） */
        const val TYPE_INTERACTION = "INTERACTION"
        /** 用户共同观看：你主动分享内容（粘贴/截图说明/「我在看XX」）→ 角色陪看，T11.16 */
        const val TYPE_WATCH = "WATCH"
    }
}
