package com.bdalxy.edencourt.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.bdalxy.edencourt.data.local.entity.Message
import com.bdalxy.edencourt.data.local.entity.Role
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

    /** 批量新增（性能种子工具用：千条历史一次性入库，优于逐条 insert 被 Room 逐次级联重查） */
    @Insert
    suspend fun insertAll(messages: List<Message>)

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

    /** 按会话一次性取全部消息（按时间正序，供确需全量历史的场景用，T8.2） */
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC, id ASC")
    suspend fun getByConversation(conversationId: Long): List<Message>

    /**
     * 定向取某会话「最近 limit 条」消息（时间正序，LIMIT 在数据库层完成，不拉全文）。
     * T12.6：供跨会话暗访/每日打听等只需最近一段上下文的旁路场景用，
     *     避免此前「getByConversation 拉全量再 takeLast」的长会话浪费。
     * 实现：先按时间倒序取末尾 limit 条，再整体倒序回正序，保证返回仍是「旧→新」。
     */
    @Query(
        """
        SELECT * FROM (
            SELECT * FROM messages
            WHERE conversationId = :conversationId
            ORDER BY createdAt DESC, id DESC
            LIMIT :limit
        ) ORDER BY createdAt ASC, id ASC
        """
    )
    suspend fun getRecentByConversation(conversationId: Long, limit: Int): List<Message>

    /**
     * 观察分页（T12.6 基础设施，供确需按页订阅的场景）。
     * 按时间正序取第 offset 起的 limit 条；翻页以 offset 递增。
     * 说明：Room 表级校验会使本 Flow 在写好该会话时整体重发一次，
     *      offset 语义在有增删的消息表中可能跳位，UI 持续滚动场景通常改用
     *      observeByConversation + UI 侧窗口（G-① displayMessages）更稳。
     */
    @Query(
        """
        SELECT * FROM messages
        WHERE conversationId = :conversationId
        ORDER BY createdAt ASC, id ASC
        LIMIT :limit OFFSET :offset
        """
    )
    fun observePaged(conversationId: Long, limit: Int, offset: Int): Flow<List<Message>>

    /**
     * 统计某会话自给定时间戳之后（不含等于）指定角色的消息条数。
     * R3 方向判断触发条件「满 5 条用户消息」用：以 candidateLastSeen 为界统计新增用户消息数。
     */
    @Query("SELECT COUNT(*) FROM messages WHERE conversationId = :conversationId AND role = :role AND createdAt > :since")
    suspend fun countMessagesSince(conversationId: Long, role: Role, since: Long): Int
}
