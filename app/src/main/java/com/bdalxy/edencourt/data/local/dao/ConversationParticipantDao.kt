package com.bdalxy.edencourt.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bdalxy.edencourt.data.local.entity.ConversationParticipant
import kotlinx.coroutines.flow.Flow

/**
 * 群聊参与者关联 DAO（v2.0 A6-1）。
 */
@Dao
interface ConversationParticipantDao {

    /** 加入群聊（已存在则忽略，防重复） */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(participant: ConversationParticipant)

    /** 移除参与者 */
    @Delete
    suspend fun delete(participant: ConversationParticipant)

    /** 查询某群聊的所有参与者 */
    @Query("SELECT * FROM conversation_participants WHERE conversationId = :conversationId")
    suspend fun getByConversation(conversationId: Long): List<ConversationParticipant>

    /** 观察某群聊的参与者列表（响应式） */
    @Query("SELECT * FROM conversation_participants WHERE conversationId = :conversationId")
    fun observeByConversation(conversationId: Long): Flow<List<ConversationParticipant>>
}