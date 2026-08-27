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
import com.bdalxy.edencourt.data.local.dao.MemorySearchDao
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
import com.bdalxy.edencourt.data.local.entity.RelationshipEvent
import com.bdalxy.edencourt.data.local.entity.UserIdentity

/**
 * 本地数据库单例（企划书 3.3.2 / T2.4）。
 * - 挂载：Conversation、Message、CharacterCardEntity、MemoryItem 四个实体与对应 DAO
 * - version=20：v5→v6 character_cards 加 avatar；v6→v7 memory_items 加 characterCardId；v7→v8 character_cards/conversations 加 remark（T6.14）；v8→v9 memory_items 加 visibility（共享/私有，T7.1）；v9→v10 character_cards/conversations 加 code（会话唯一身份，T8.0）；v10→v11 memory_items 加 isTopSecret（绝密兜底，T8.2）；v11→v12 conversations 加 interactionEnabled（互动资格开关，T8.7）；v12→v13 新增 conv_relationship 关系表（互动关系底座，T8.8）；v13→v14 新增 interaction/interaction_participant/interaction_turn 三层互动表（T8.10）；v14→v15 新增 user_identity 用户自设身份表（T8.13）；v15→v16 memory_items 补多类型管理字段；v16→v17 新增 FTS4 记忆检索虚拟表；v17→v18 interaction 加 source 列；v18→v19 character_cards 加自定义作息 6 列；v19→v20 conversations 加会话作息 3 列（lifeEnabled+起床/入睡，T11.10 深化：每个会话角色单独调整）；v20→v21 interaction 加 motive 列（T11.12 事件驱动）；v21→v22 新增 relationship_event 关系事件表（T11.13 关系事件叙事）
 * - schema 已导出到 app/schemas（企划书 4.1.5）
 */
@Database(
    entities = [Conversation::class, Message::class, CharacterCardEntity::class, MemoryItem::class, ConversationRelationship::class, RelationshipEvent::class, Interaction::class, InteractionParticipant::class, InteractionTurn::class, UserIdentity::class],
    version = 24,
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

    /** 记忆检索（FTS4 虚拟表）数据访问（T9.2） */
    abstract fun memorySearchDao(): MemorySearchDao

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

        /** v15→v16：memory_items 表一次性补齐阶段一多类型管理字段（T9.1），一次到位避免二次迁移。
         * 新增列：timeScope(生命周期)/expiresAt(到期)/isPinned(置顶)/importance(重要)/locked(锁定)/sourceType(来源)。
         * 默认值对齐实体 @ColumnInfo(defaultValue)，Room 校验默认值需一致；并建 type+timeScope+characterCardId+visibility 组合索引。 */
        private val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE memory_items ADD COLUMN timeScope TEXT NOT NULL DEFAULT 'LONG_TERM'")
                db.execSQL("ALTER TABLE memory_items ADD COLUMN expiresAt INTEGER")
                db.execSQL("ALTER TABLE memory_items ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE memory_items ADD COLUMN importance TEXT NOT NULL DEFAULT 'NORMAL'")
                db.execSQL("ALTER TABLE memory_items ADD COLUMN locked INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE memory_items ADD COLUMN sourceType TEXT NOT NULL DEFAULT 'CHAT'")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_memory_type_scope` ON `memory_items` (`type`, `timeScope`, `characterCardId`, `visibility`)")
            }
        }

        /** v16→v17：新增 FTS4 虚拟表 memory_items_fts（全文检索底座，T9.2）。
         * 虚拟表不在 main 实体里声明（避免 Room 校验 FTS schema），用触发器与 memory_items 手动同步：
         *   - AFTER INSERT：新增记忆 → 建索引行
         *   - AFTER DELETE：物理删除 → 删索引行
         *   - AFTER UPDATE OF content,keywords：编辑关键词 → 更新索引行
         * 软删除为 UPDATE isDeleted=1（非 DELETE），索引行保留，检索时以 m.isDeleted=0 过滤，正确隐藏已删记录。
         * tokenize=unicode61：Android/SQLite 稳定内置；中文子串命中由 MemorySearchService 兜底 LIKE（先按角色/分类缩小候选）。 */
        private val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE VIRTUAL TABLE IF NOT EXISTS `memory_items_fts` USING fts4(
                        `content` TEXT,
                        `keywords` TEXT,
                        tokenize=unicode61
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS memory_items_ai AFTER INSERT ON memory_items BEGIN
                        INSERT INTO memory_items_fts(rowid, content, keywords) VALUES (new.id, new.content, new.keywords);
                    END
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS memory_items_ad AFTER DELETE ON memory_items BEGIN
                        DELETE FROM memory_items_fts WHERE rowid = old.id;
                    END
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS memory_items_au AFTER UPDATE OF content, keywords ON memory_items BEGIN
                        UPDATE memory_items_fts SET content = new.content, keywords = new.keywords WHERE rowid = new.id;
                    END
                    """.trimIndent(),
                )
            }
        }

        /** v17→v18：interaction 表加 source 列（互动来源：MANUAL=手动 / AUTO=自动驱动，T11.7），默认 'MANUAL' 兼容存量数据 */
        private val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE interaction ADD COLUMN source TEXT NOT NULL DEFAULT 'MANUAL'")
            }
        }

        /** v18→v19：character_cards 表加自定义作息 6 列（T11.10 深化：角色可基于基础作息单独调节）。全为 NULL=跟随默认，故均不加默认值 */
        private val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE character_cards ADD COLUMN scheduleWakeUpMinute INTEGER")
                db.execSQL("ALTER TABLE character_cards ADD COLUMN scheduleSleepMinute INTEGER")
                db.execSQL("ALTER TABLE character_cards ADD COLUMN scheduleWorkStartMinute INTEGER")
                db.execSQL("ALTER TABLE character_cards ADD COLUMN scheduleWorkEndMinute INTEGER")
                db.execSQL("ALTER TABLE character_cards ADD COLUMN scheduleBreakStartMinute INTEGER")
                db.execSQL("ALTER TABLE character_cards ADD COLUMN scheduleBreakEndMinute INTEGER")
            }
        }

        /** v19→v20：conversations 表加会话作息 3 列（T11.10 深化：每个会话角色单独调整）。lifeEnabled 默认 0（关），作息两列可空（NULL=跟随角色卡/默认） */
        private val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE conversations ADD COLUMN lifeEnabled INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE conversations ADD COLUMN scheduleWakeUpMinute INTEGER")
                db.execSQL("ALTER TABLE conversations ADD COLUMN scheduleSleepMinute INTEGER")
            }
        }

        /** v20→v21：interaction 表加 motive 列（T11.12 事件驱动：本次互动的「因由」，可空） */
        private val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE interaction ADD COLUMN motive TEXT")
            }
        }

        /** v21→v22：新增 relationship_event 表（T11.13 关系事件叙事：互动沉淀「共同经历」，供关系故事区与 Prompt 注入） */
        private val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `relationship_event` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`convAId` INTEGER NOT NULL, " +
                        "`convBId` INTEGER NOT NULL, " +
                        "`scene` TEXT NOT NULL, " +
                        "`topic` TEXT NOT NULL, " +
                        "`mood` TEXT NOT NULL, " +
                        "`points` TEXT NOT NULL, " +
                        "`source` TEXT NOT NULL, " +
                        "`happenedAt` INTEGER NOT NULL" +
                        ")",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_relationship_event_convAId_convBId_happenedAt` " +
                        "ON `relationship_event` (`convAId`, `convBId`, `happenedAt`)",
                )
            }
        }

        /** v22→v23：relationship_event 表加 type 列（T11.16 共同内容会话：WATCH=用户共同观看，默认 INTERACTION 兼容存量） */
        private val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE relationship_event ADD COLUMN type TEXT NOT NULL DEFAULT 'INTERACTION'")
            }
        }

        /** v23→v24：relationship_event 的 convBId 改可空列（T12.10，陪看事件取消哨兵 0）。
         *  此前陪看事件以 convBId=0 表示「用户共同观看」；现改为可空列明确表达：
         *  convBId=NULL = 用户共同观看（无第二会话），INTERACTION 事件不受影响（仍 convAId < convBId 规范化）。
         *  SQLite 不能直接改列可空性，故整表重建，并将存量 convBId=0 的行迁移为 NULL。
         */
        private val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `relationship_event_new` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`convAId` INTEGER NOT NULL, " +
                        "`convBId` INTEGER, " +
                        "`scene` TEXT NOT NULL, " +
                        "`topic` TEXT NOT NULL, " +
                        "`mood` TEXT NOT NULL, " +
                        "`points` TEXT NOT NULL, " +
                        "`source` TEXT NOT NULL, " +
                        "`type` TEXT NOT NULL DEFAULT 'INTERACTION', " +
                        "`happenedAt` INTEGER NOT NULL" +
                        ")",
                )
                db.execSQL(
                    "INSERT INTO relationship_event_new (id, convAId, convBId, scene, topic, mood, points, source, type, happenedAt) " +
                        "SELECT id, convAId, CASE WHEN convBId = 0 THEN NULL ELSE convBId END, " +
                        "scene, topic, mood, points, source, type, happenedAt FROM relationship_event",
                )
                db.execSQL("DROP TABLE relationship_event")
                db.execSQL("ALTER TABLE relationship_event_new RENAME TO relationship_event")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_relationship_event_convAId_convBId_happenedAt` " +
                        "ON `relationship_event` (`convAId`, `convBId`, `happenedAt`)",
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

        /** 全部迁移（按版本升序：运行时挂载与 androidTest 迁移测试共用；每次新增版本需在此追加并同步顶部注释） */
        val ALL_MIGRATIONS: Array<Migration> = arrayOf(
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
            MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13,
            MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19,
            MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23, MIGRATION_23_24,
        )

        /** 获取单例（双重检查锁，避免并发重复建库） */
        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "edencourt.db",
                ).addMigrations(*ALL_MIGRATIONS).build().also { INSTANCE = it }
            }
    }
}
