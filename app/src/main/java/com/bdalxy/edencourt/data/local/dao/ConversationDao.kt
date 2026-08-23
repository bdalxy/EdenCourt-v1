package com.bdalxy.edencourt.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.bdalxy.edencourt.data.local.entity.Conversation
import kotlinx.coroutines.flow.Flow

/**
 * 会话表数据访问（企划书 4.1 / T2.3）。
 * 说明：返回 Flow 的查询是 Room 响应式查询——首次订阅查询一次，
 * 之后凡是导致 conversations 表变化的写入（insert/update/delete）都会触发自动重查并重发。
 */
@Dao
interface ConversationDao {

    /** 新增会话，返回生成的 id */
    @Insert
    suspend fun insert(conversation: Conversation): Long

    /** 更新会话（会触发 observeAll 等 Flow 重新发射） */
    @Update
    suspend fun update(conversation: Conversation)

    /** 删除会话（messages 由外键 ON DELETE CASCADE 级联删除） */
    @Delete
    suspend fun delete(conversation: Conversation)

    /** 观察全部会话，按更新时间降序（会话列表数据源） */
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<Conversation>>

    /** 观察单个会话（聊天页标题等实时跟随） */
    @Query("SELECT * FROM conversations WHERE id = :id")
    fun observeById(id: Long): Flow<Conversation?>

    /** 按 id 取单个会话（一次性查询） */
    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getById(id: Long): Conversation?
}
