package com.bdalxy.edencourt.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bdalxy.edencourt.data.local.entity.ConversationMood

/**
 * 会话情绪表数据访问（v1.5-A2 实时情绪：轻量读写，会话唯一一条）。
 */
@Dao
interface MoodDao {

    /** 取某会话当前情绪（无则 null = 未触发过交互情绪，回落自主基线） */
    @Query("SELECT * FROM conversation_mood WHERE conversationId = :conversationId")
    suspend fun get(conversationId: Long): ConversationMood?

    /** 写入/覆盖某会话情绪（同会话唯一，冲突替换） */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(mood: ConversationMood)
}