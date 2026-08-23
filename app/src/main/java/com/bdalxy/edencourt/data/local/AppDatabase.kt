package com.bdalxy.edencourt.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.bdalxy.edencourt.data.local.dao.CharacterCardDao
import com.bdalxy.edencourt.data.local.dao.ConversationDao
import com.bdalxy.edencourt.data.local.dao.MessageDao
import com.bdalxy.edencourt.data.local.entity.CharacterCardEntity
import com.bdalxy.edencourt.data.local.entity.Conversation
import com.bdalxy.edencourt.data.local.entity.Message

/**
 * 本地数据库单例（企划书 3.3.2 / T2.4）。
 * - 挂载：Conversation、Message、CharacterCardEntity 三个实体与对应 DAO
 * - version=3：v2→v3 新增 character_cards 表（角色卡系统，T4.2）
 * - schema 已导出到 app/schemas（企划书 4.1.5）
 */
@Database(
    entities = [Conversation::class, Message::class, CharacterCardEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    /** 会话表数据访问 */
    abstract fun conversationDao(): ConversationDao

    /** 消息表数据访问 */
    abstract fun messageDao(): MessageDao

    /** 角色卡表数据访问 */
    abstract fun characterCardDao(): CharacterCardDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /** v1→v2：messages 表新增 reasoning 列，默认空串（兼容旧数据） */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN reasoning TEXT NOT NULL DEFAULT ''")
            }
        }

        /** v2→v3：新增 character_cards 表（角色卡系统，T4.2） */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `character_cards` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `jsonContent` TEXT NOT NULL,
                        `isBuiltIn` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_character_cards_name` ON `character_cards` (`name`)")
            }
        }

        /** 获取单例（双重检查锁，避免并发重复建库） */
        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "edencourt.db",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { INSTANCE = it }
            }
    }
}
