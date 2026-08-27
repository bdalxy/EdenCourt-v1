package com.bdalxy.edencourt.data.setting

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

/** 顶层 DataStore 委托：进程内单例，必须在文件顶层声明 */
private val Context.replyCursorDataStore by preferencesDataStore(name = "reply_cursor")

/**
 * 回复泵已处理游标持久化（T12.4 修复 B1）。
 * 背景：回复泵的 lastProcessedUserMessageId 原是内存态，进程被杀重建后变成 null，
 *     泵会把历史用户消息全部「当新来信」重复洗澡一次（重复回应）。
 * 方案：按会话把游标写入 DataStore，进程重启后泵首轮从持久化恢复，杜绝重复回应。
 * 键名按会话隔离（cursor_{conversationId}），互不干扰。
 */
class ReplyCursorStore(private val context: Context) {

    /** DataStore 键名：按会话生成独立的 long 型游标键 */
    private object Keys {
        fun forConversation(conversationId: Long) = longPreferencesKey("cursor_$conversationId")
    }

    /** 读取某会话的已处理游标（无记录返回 null=从未保存过） */
    suspend fun get(conversationId: Long): Long? =
        context.replyCursorDataStore.data.first()[Keys.forConversation(conversationId)]

    /** 保存某会话的已处理游标（泵每完成一批后调用，进程重启可恢复） */
    suspend fun save(conversationId: Long, lastProcessedUserMessageId: Long) {
        context.replyCursorDataStore.edit { prefs ->
            prefs[Keys.forConversation(conversationId)] = lastProcessedUserMessageId
        }
    }
}