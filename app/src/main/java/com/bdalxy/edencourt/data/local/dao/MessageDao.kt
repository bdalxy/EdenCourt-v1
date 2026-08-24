package com.bdalxy.edencourt.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.bdalxy.edencourt.data.local.entity.Message
import kotlinx.coroutines.flow.Flow

/**
 * 消息表数据访问（企划书 4.1.2 / T2.3）。
 * 响应式说明：observeByConversation 返回 Flow，聊天页订阅后，
 * 只要该会话相关消息发生 insert/update/delete 就会自动重查并重发新列表，无需手动刷新。
 */
@Dao
interface MessageDao {

    /** 新增消息，返回生成的 id */
    @Insert
    suspend fun insert(message: Message): Long

    /** 更新消息（流式生成中逐段更新 AI 消息、失败置 FAILED 等场景使用） */
    @Update
    suspend fun update(message: Message)

    /** 删除单条消息 */
    @Delete
    suspend fun delete(message: Message)

    /**
     * 观察某会话的全部消息，按创建时间正序（聊天页消息流数据源）。
     * 同毫秒插入的消息（如打断瞬间用户消息与 AI 占位消息）以 id 作次级排序，保证交错稳定（T5.10）。
     */
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC, id ASC")
    fun observeByConversation(conversationId: Long): Flow<List<Message>>

    /** 按 id 取单条消息（一次性查询） */
    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getById(id: Long): Message?

    /** 按会话一次性取全部消息（按时间正序，供跨会话旁路暗访构建目标上下文用，T8.2） */
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC, id ASC")
    suspend fun getByConversation(conversationId: Long): List<Message>
}
