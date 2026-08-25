package com.bdalxy.edencourt.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.bdalxy.edencourt.data.local.dao.CharacterCardDao
import com.bdalxy.edencourt.data.local.dao.ConversationDao
import com.bdalxy.edencourt.data.local.dao.MemoryDao
import com.bdalxy.edencourt.data.local.dao.MessageDao
import com.bdalxy.edencourt.data.local.dao.RelationshipDao
import com.bdalxy.edencourt.data.local.dao.InteractionDao
import com.bdalxy.edencourt.data.local.dao.UserIdentityDao
import com.bdalxy.edencourt.data.local.entity.CharacterCardEntity
import com.bdalxy.edencourt.data.local.entity.Conversation
import com.bdalxy.edencourt.data.local.entity.ConversationRelationship
import com.bdalxy.edencourt.data.local.entity.Interaction
import com.bdalxy.edencourt.data.local.entity.InteractionParticipant
import com.bdalxy.edencourt.data.local.entity.InteractionTurn
import com.bdalxy.edencourt.data.local.entity.MemoryItem
import com.bdalxy.edencourt.data.local.entity.Message
import com.bdalxy.edencourt.data.local.entity.UserIdentity

/**
 * 本地数据库单例（企划书 3.3.2 / T2.4）。
 * - 挂载：Conversation、Message、CharacterCardEntity、MemoryItem 四个实体与对应 DAO
 * - version=15：v5→v6 character_cards 加 avatar；v6→v7 memory_items 加 characterCardId；v7→v8 character_cards/conversations 加 remark（T6.14）；v8→v9 memory_items 加 visibility（共享/私有，T7.1）；v9→v10 character_cards/conversations 加 code（会话唯一身份，T8.0）；v10→v11 memory_items 加 isTopSecret（绝密兜底，T8.2）；v11→v12 conversations 加 interactionEnabled（互动资格开关，T8.7）；v12→v13 新增 conv_relationship 关系表（互动关系底座，T8.8）；v13→v14 新增 interaction/interaction_participant/interaction_turn 三层互动表（T8.10）；v14→v15 新增 user_identity 用户自设身份表（T8.13）
 * - schema 已导出到 app/schemas（企划书 4.1.5）
 */
@Database(
    entities = [Conversation::class, Message::class, CharacterCardEntity::class, MemoryItem::class, ConversationRelationship::class, Interaction::class, InteractionParticipant::class, InteractionTurn::class, UserIdentity::class],
    version = 15,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    /** 会话表数据访问 */
    abstract fun conversationDao(): ConversationDao

    /** 消息表数据访问 */
    abstract fun messageDao(): MessageDao

    /** 角色卡表数据访问 */
    abstract fun characterCardDao(): CharacterCardDao

    /** 互动表数据访问（v1.2b 互动系统，T8.10） */
    abstract fun interactionDao(): InteractionDao

    /** 记忆条目表数据访问 */
    abstract fun memoryDao(): MemoryDao

    /** 会话间关系表数据访问（v1.2b 互动系统，T8.8） */
    abstract fun relationshipDao(): RelationshipDao

    /** 用户自设身份表数据访问（v1.2b 互动系统，T8.13） */
    abstract fun userIdentityDao(): UserIdentityDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /** v8→v9：memory_items 表新增 visibility 列，默认 'SHARED'（记忆可见性底座，T7.1）。区分度低不建索引 */
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE memory_items ADD COLUMN visibility TEXT NOT NULL DEFAULT 'SHARED'")
            }
        }

        /** v9→v10：character_cards / conversations 表各加 code 列（随机短码，会话唯一身份底座，T8.0）。既有记录补发唯一短码（hex(randomblob) 12 位，前缀区分表） */
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE character_cards ADD COLUMN code TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE conversations ADD COLUMN code TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE character_cards SET code = 'C' || hex(randomblob(6)) WHERE code = ''")
                db.execSQL("UPDATE conversations SET code = 'V' || hex(randomblob(6)) WHERE code = ''")
            }
        }

        /** v10→v11：memory_items 表加 isTopSecret 列（绝密兜底，T8.2），默认 0（非绝密） */
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE memory_items ADD COLUMN isTopSecret INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v11→v12：conversations 表加 interactionEnabled 列（角色间互动资格开关，T8.7），默认 0=不参与互动 */
        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE conversations ADD COLUMN interactionEnabled INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v12→v13：新增 conv_relationship 表（会话间关系，互动系统 T8.8）。唯一索引杜绝重复关系对；字段对齐实体 */
        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `conv_relationship` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `convAId` INTEGER NOT NULL,
                        `convBId` INTEGER NOT NULL,
                        `relationStatus` TEXT NOT NULL,
                        `affinityScore` INTEGER NOT NULL,
                        `interactionCount` INTEGER NOT NULL,
                        `lastInteractionAt` INTEGER,
                        `noteSummary` TEXT
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_conv_relationship_convAId_convBId` ON `conv_relationship` (`convAId`, `convBId`)")
            }
        }

        /** v13→v14：新增 interaction 三表（互动系统 T8.10）。interaction 主表 + 参与者表 + 轮次表；索引对齐实体 */
        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `interaction` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `initiatorConvId` INTEGER NOT NULL,
                        `targetConvId` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        `topicSeed` TEXT NOT NULL,
                        `startedAt` INTEGER,
                        `endedAt` INTEGER,
                        `turnCount` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_interaction_status_createdAt` ON `interaction` (`status`, `createdAt`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `interaction_participant` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `interactionId` INTEGER NOT NULL,
                        `convId` INTEGER NOT NULL,
                        `role` TEXT NOT NULL,
                        `summary` TEXT,
                        `readAt` INTEGER,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_interaction_participant_interactionId` ON `interaction_participant` (`interactionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_interaction_participant_convId_readAt_createdAt` ON `interaction_participant` (`convId`, `readAt`, `createdAt`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `interaction_turn` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `interactionId` INTEGER NOT NULL,
                        `turnIndex` INTEGER NOT NULL,
                        `speakerConvId` INTEGER NOT NULL,
                        `content` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_interaction_turn_interactionId_turnIndex` ON `interaction_turn` (`interactionId`, `turnIndex`)")
            }
        }

        /** v14→v15：新增 user_identity 表（用户自设角色卡身份，唯一一条 id=1，T8.13）。字段对齐实体 */
        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `user_identity` (
                        `id` INTEGER PRIMARY KEY NOT NULL,
                        `name` TEXT NOT NULL,
                        `calling` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `personality` TEXT NOT NULL,
                        `background` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

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

        /** v3→v4：新增 memory_items 表（记忆系统，T5.1） */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `memory_items` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `type` TEXT NOT NULL,
                        `content` TEXT NOT NULL,
                        `keywords` TEXT NOT NULL,
                        `confidence` REAL NOT NULL,
                        `sourceConversationId` INTEGER,
                        `sourceMessageId` INTEGER,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `isDeleted` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_memory_items_type` ON `memory_items` (`type`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_memory_items_keywords` ON `memory_items` (`keywords`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_memory_items_isDeleted` ON `memory_items` (`isDeleted`)")
            }
        }

        /** v4→v5：messages 表新增 type 列，默认 TEXT（T5.7 富类型协议预留） */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN type TEXT NOT NULL DEFAULT 'TEXT'")
            }
        }

        /** v5→v6：character_cards 表新增 avatar 列，可空（自定义头像，T6.2B） */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE character_cards ADD COLUMN avatar TEXT")
            }
        }

        /** v6→v7：memory_items 表新增 characterCardId 列及其索引（记忆按角色卡隔离，T6.13） */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE memory_items ADD COLUMN characterCardId INTEGER")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_memory_items_characterCardId` ON `memory_items` (`characterCardId`)")
            }
        }

        /** v7→v8：character_cards / conversations 表各加 remark 列，默认空串（备注别名，T6.14） */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE character_cards ADD COLUMN remark TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE conversations ADD COLUMN remark TEXT NOT NULL DEFAULT ''")
            }
        }

        /** 获取单例（双重检查锁，避免并发重复建库） */
        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "edencourt.db",
                ).addMigrations(
                    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15,
                ).build().also { INSTANCE = it }
            }
    }
}
