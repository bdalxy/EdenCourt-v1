package com.bdalxy.edencourt.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bdalxy.edencourt.data.local.entity.ConversationTraits
import kotlinx.coroutines.flow.Flow

/**
 * 会话角色「独有人格」表数据访问（T13.1 角色差异化）。
 * 每会话一条：建会话时生成一次后固定；印象摘要可被 LLM 覆盖生成或用户手动编辑（T13.4/T13.7）。
 */
@Dao
interface ConversationTraitsDao {

    /** 写入/覆盖某会话的人格记录（conversationId 唯一，幂等） */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(traits: ConversationTraits): Long

    /** 取某会话的人格记录（无则 null，如默认助手会话不生成） */
    @Query("SELECT * FROM conversation_traits WHERE conversationId = :conversationId LIMIT 1")
    suspend fun getByConversation(conversationId: Long): ConversationTraits?

    /** 观察某会话的人格记录（聊天页/会话列表实时刷新，T13.6 可视化） */
    @Query("SELECT * FROM conversation_traits WHERE conversationId = :conversationId LIMIT 1")
    fun observeByConversation(conversationId: Long): Flow<ConversationTraits?>

    /** 观察全部人格记录（会话列表一次性取所有会话的标签行，T13.6） */
    @Query("SELECT * FROM conversation_traits")
    fun observeAll(): Flow<List<ConversationTraits>>

    /** 更新印象摘要 + 标签 + 来源（LLM 生成 / 用户手动编辑，T13.4；用户改后 generatedBy='user' 不被覆盖） */
    @Query(
        "UPDATE conversation_traits SET identityBlurb = :blurb, identityTags = :tags, " +
            "generatedBy = :generatedBy WHERE conversationId = :conversationId",
    )
    suspend fun updateBlurbAndTags(conversationId: Long, blurb: String, tags: String, generatedBy: String)
}
