package com.bdalxy.edencourt.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.bdalxy.edencourt.data.local.dao.ConversationDao
import com.bdalxy.edencourt.data.local.dao.MessageDao
import com.bdalxy.edencourt.data.local.entity.Conversation
import com.bdalxy.edencourt.data.local.entity.Message

/**
 * 本地数据库单例（企划书 3.3.2 / T2.4）。
 * - 挂载：Conversation、Message 两个实体与对应 DAO
 * - version=1：后续实体变更需递增版本并编写 Migration（企划书 4.1.5，schema 已导出到 app/schemas）
 */
@Database(
    entities = [Conversation::class, Message::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    /** 会话表数据访问 */
    abstract fun conversationDao(): ConversationDao

    /** 消息表数据访问 */
    abstract fun messageDao(): MessageDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /** 获取单例（双重检查锁，避免并发重复建库） */
        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "edencourt.db",
                ).build().also { INSTANCE = it }
            }
    }
}
