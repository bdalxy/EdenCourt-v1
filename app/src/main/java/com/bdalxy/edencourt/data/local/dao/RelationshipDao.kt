package com.bdalxy.edencourt.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.bdalxy.edencourt.data.local.entity.ConversationRelationship
import com.bdalxy.edencourt.data.local.entity.RelationshipEvent
import kotlinx.coroutines.flow.Flow

/**
 * 会话间关系表数据访问（v1.2b 互动系统，T8.8）。
 * 采用规范化存储：convAId < convBId；(convAId, convBId) 唯一，upsert 幂等不会产生重复关系对。
 */
@Dao
interface RelationshipDao {

    /** 新增或覆盖一条关系（同对会话幂等）。
     * 注意：关系对约定 convAId < convBId（小 id 恒 A），规范化由 RelationshipRepository 保证，
     *      业务层不直接调用本裸 upsert（T12.10）。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(relationship: ConversationRelationship): Long

    /** 更新一条关系 */
    @Update
    suspend fun update(relationship: ConversationRelationship)

    /** 取两个会话之间的关系（不论 A/B 顺序） */
    @Query(
        "SELECT * FROM conv_relationship " +
            "WHERE (convAId = :a AND convBId = :b) OR (convAId = :b AND convBId = :a) LIMIT 1",
    )
    suspend fun getBetween(a: Long, b: Long): ConversationRelationship?

    /** 取某个会话参与的全部关系（供互动中心/A 的角色显示） */
    @Query(
        "SELECT * FROM conv_relationship WHERE convAId = :id OR convBId = :id " +
            "ORDER BY lastInteractionAt DESC",
    )
    suspend fun forConversation(id: Long): List<ConversationRelationship>

    /** 观察某个会话参与的全部关系（互动中心实时刷新） */
    @Query(
        "SELECT * FROM conv_relationship WHERE convAId = :id OR convBId = :id " +
            "ORDER BY lastInteractionAt DESC",
    )
    fun observeForConversation(id: Long): kotlinx.coroutines.flow.Flow<List<ConversationRelationship>>

    /** 观察全部关系（互动中心「关系一览」好感度/档位/次数，T11.6） */
    @Query(
        "SELECT * FROM conv_relationship ORDER BY lastInteractionAt DESC",
    )
    fun observeAll(): kotlinx.coroutines.flow.Flow<List<ConversationRelationship>>

    /** 互动成功后更新计数、关系标签（按好感度分档）、好感度数值与最近互动时间 */
    @Query(
        "UPDATE conv_relationship SET interactionCount = interactionCount + 1, " +
            "relationStatus = :status, affinityScore = :affinity, lastInteractionAt = :now WHERE id = :id",
    )
    suspend fun touchInteraction(id: Long, now: Long, status: String, affinity: Int)

    // ===== 关系事件（T11.13 关系事件叙事：互动沉淀「共同经历」，供关系故事区与 Prompt 注入）=====

    /** 新增一条「共同经历事件」（每次互动成功后沉淀一次） */
    @Insert
    suspend fun insertEvent(event: RelationshipEvent): Long

    /** 观察某对会话的共同经历事件（倒序，互动中心「关系故事」时间线） */
    @Query(
        "SELECT * FROM relationship_event " +
            "WHERE (convAId = :a AND convBId = :b) OR (convAId = :b AND convBId = :a) " +
            "ORDER BY happenedAt DESC",
    )
    fun observeEventsBetween(a: Long, b: Long): Flow<List<RelationshipEvent>>

    /** 观察全部共同经历事件（倒序，互动中心「关系故事」总览） */
    @Query(
        "SELECT * FROM relationship_event ORDER BY happenedAt DESC",
    )
    fun observeAllEvents(): Flow<List<RelationshipEvent>>

    /** 取某个会话最近参与的共同经历事件（倒序，供聊天页 Prompt 注入「我们经历过」的连续性） */
    @Query(
        "SELECT * FROM relationship_event WHERE convAId = :id OR convBId = :id " +
            "ORDER BY happenedAt DESC LIMIT :limit",
    )
    suspend fun recentEventsForConversation(id: Long, limit: Int): List<RelationshipEvent>
}