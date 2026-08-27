package com.bdalxy.edencourt.data.repository

import com.bdalxy.edencourt.data.local.dao.RelationshipDao
import com.bdalxy.edencourt.data.local.entity.ConversationRelationship
import com.bdalxy.edencourt.data.local.entity.RelationshipEvent
import kotlinx.coroutines.flow.Flow

/**
 * 会话间关系仓储（T12.10）。
 * 承载「关系对规范化」约定（小 id 恒为 A，convAId < convBId）：关系对写入统一走本仓储，
 * 由 `createNormalized` 内部排定 min/max，调用方无需自记；不再让业务层直接调 DAO 裸 `upsert`，
 * 杜绝重复/错序关系对。关系事件（T11.13/11.16）也经此统一读写。
 */
class RelationshipRepository(
    private val relationshipDao: RelationshipDao,
) {

    // ===== 关系对（conv_relationship，约定：convAId < convBId）=====

    /** 取两会话之间的关系（不论 A/B 顺序） */
    suspend fun getBetween(aId: Long, bId: Long): ConversationRelationship? =
        relationshipDao.getBetween(aId, bId)

    /**
     * 规范化新建一条关系（内部排定小 id 为 A），仅当该对尚不存在时由调用方调用；
     * 返回生成的行 id。T12.10：调用方无需自记 min/max。
     */
    suspend fun createNormalized(
        aId: Long,
        bId: Long,
        relationStatus: String = ConversationRelationship.relationStatusForAffinity(0),
        affinityScore: Int = 0,
        now: Long,
    ): Long {
        val a = minOf(aId, bId)
        val b = maxOf(aId, bId)
        return relationshipDao.upsert(
            ConversationRelationship(
                convAId = a,
                convBId = b,
                relationStatus = relationStatus,
                affinityScore = affinityScore,
                interactionCount = 1,
                lastInteractionAt = now,
            ),
        )
    }

    /** 互动成功后：累计好感度、次数+1、按新好感度递进关系标签并刷新最近互动时间 */
    suspend fun touchInteraction(id: Long, now: Long, status: String, affinity: Int) =
        relationshipDao.touchInteraction(id, now, status, affinity)

    // ===== 关系事件（relationship_event，T11.13 共同经历 / T11.16 共同观看）=====

    /**
     * 新增一条共同经历事件（每次互动/共同观看后沉淀一次）。
     * T12.10：INTERACTION 事件在此规范化（小 id 恒 A，调用方无需自记 min/max）；
     * 用户共同观看（WATCH）事件 convBId=null，保持 convAId=本角色会话，不做排序。
     */
    suspend fun insertEvent(event: RelationshipEvent): Long {
        val b = event.convBId
        // 仅角色间互动事件需要按「小 id 恒 A」规范化；convBId=null（用户侧）不动
        val normalized = if (b != null && event.convAId > b) {
            event.copy(convAId = b, convBId = event.convAId)
        } else {
            event
        }
        return relationshipDao.insertEvent(normalized)
    }

    /** 观察某对会话的共同经历事件（倒序，互动中心「关系故事」时间线） */
    fun observeEventsBetween(a: Long, b: Long): Flow<List<RelationshipEvent>> =
        relationshipDao.observeEventsBetween(a, b)

    /** 观察全部共同经历事件（倒序，互动中心「关系故事」总览） */
    fun observeAllEvents(): Flow<List<RelationshipEvent>> = relationshipDao.observeAllEvents()

    /** 取某会话最近参与的共同经历事件（倒序，供聊天页 Prompt 注入「我们经历过」的连续性） */
    suspend fun recentEventsForConversation(id: Long, limit: Int): List<RelationshipEvent> =
        relationshipDao.recentEventsForConversation(id, limit)
}