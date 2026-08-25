package com.bdalxy.edencourt.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.bdalxy.edencourt.data.local.entity.Interaction
import com.bdalxy.edencourt.data.local.entity.InteractionParticipant
import com.bdalxy.edencourt.data.local.entity.InteractionTurn
import kotlinx.coroutines.flow.Flow

/**
 * 互动数据访问（v1.2b 互动系统，T8.10）。
 * 覆盖三表：interaction（主）/ interaction_participant（参与者）/ interaction_turn（轮次）。
 */
@Dao
interface InteractionDao {

    /** 新增互动主记录，返回 id */
    @Insert
    suspend fun insertInteraction(interaction: Interaction): Long

    /** 更新互动主记录（状态流转/轮次/起止时间） */
    @Update
    suspend fun updateInteraction(interaction: Interaction)

    /** 新增参与者 */
    @Insert
    suspend fun insertParticipant(participant: InteractionParticipant): Long

    /** 更新参与者（写摘要/已读） */
    @Update
    suspend fun updateParticipant(participant: InteractionParticipant)

    /** 取某互动的参与者（互动中心详情用） */
    @Query("SELECT * FROM interaction_participant WHERE interactionId = :interactionId ORDER BY id ASC")
    suspend fun participantsFor(interactionId: Long): List<InteractionParticipant>

    /** 取某互动中指定会话的参与者行（更新摘要/已读用） */
    @Query("SELECT * FROM interaction_participant WHERE interactionId = :interactionId AND convId = :convId LIMIT 1")
    suspend fun participantFor(interactionId: Long, convId: Long): InteractionParticipant?

    /** 观察某会话参与的互动（互动中心红点/列表实时刷新） */
    @Query("SELECT p.* FROM interaction_participant p WHERE p.convId = :convId ORDER BY p.id DESC")
    fun observeForConversation(convId: Long): Flow<List<InteractionParticipant>>

    /** 观察全部互动（倒序，互动中心列表） */
    @Query("SELECT * FROM interaction ORDER BY id DESC")
    fun observeAll(): Flow<List<Interaction>>

    /** 观察全部参与者（互动中心组装双方名用） */
    @Query("SELECT * FROM interaction_participant ORDER BY id DESC")
    fun observeAllParticipants(): Flow<List<InteractionParticipant>>

    /** 一次性取某会话参与的互动（按时间倒序） */
    @Query("SELECT p.* FROM interaction_participant p WHERE p.convId = :convId ORDER BY p.id DESC")
    suspend fun listParticipantsForConversation(convId: Long): List<InteractionParticipant>

    /** 新增台词 */
    @Insert
    suspend fun insertTurn(turn: InteractionTurn): Long

    /** 取某互动的全部台词（按轮次正序，互动中心详情） */
    @Query("SELECT * FROM interaction_turn WHERE interactionId = :interactionId ORDER BY turnIndex ASC")
    suspend fun turnsFor(interactionId: Long): List<InteractionTurn>

    /** 取某互动的台词（一次性） */
    @Query("SELECT * FROM interaction WHERE id = :id")
    suspend fun getInteraction(id: Long): Interaction?

    /** 全部互动（倒序，早期观测用） */
    @Query("SELECT * FROM interaction ORDER BY id DESC")
    suspend fun listInteractions(): List<Interaction>

    /** 标记参与者已读（红点消除） */
    @Query("UPDATE interaction_participant SET readAt = :at WHERE id = :id")
    suspend fun markParticipantRead(id: Long, at: Long)

    /** 某对会话最近一次【自动驱动】互动的创建时间（T11.7 冷却期去重：从未自动互动过则 null） */
    @Query(
        "SELECT MAX(createdAt) FROM interaction " +
            "WHERE source = 'AUTO' " +
            "AND ((initiatorConvId = :a AND targetConvId = :b) OR (initiatorConvId = :b AND targetConvId = :a))",
    )
    suspend fun lastAutoTimeBetween(a: Long, b: Long): Long?
}