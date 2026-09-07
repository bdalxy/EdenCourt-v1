package com.bdalxy.edencourt.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bdalxy.edencourt.data.local.AppDatabase.Companion.ALL_MIGRATIONS
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Room 数据库迁移升级回归测试（方案 G-③ Phase2）。
 * 作用：数据库已 20+ 次升级（v1→v2→...→v25），迁移是「最易丢数据/最隐蔽」的回归点。
 *   本测试保证：从较旧版本（带真实结构+旧数据）一路执行到最新版本的所有迁移后，旧数据不丢失、
 *   目标版本新增/调整的列字段可正常读取（含迁移里的默认值/数据回填）。
 *
 *   实现方式说明：不使用 helper.runMigrationsAndValidate 做 schema 校验，因为——
 *   ① FTS 虚拟表 memory_items_fts 是迁移里原生 SQL 自建的、没有声明的 Room 实体，不在导出 schema 里，
 *      runMigrationsAndValidate 会把「迁移后多出的它」判为 "Unexpected table" 误报失败；
 *   ② 也不用「真实 Room builder 再打开」校验收，因为 MigrationTestHelper 建库时不写 room_master_table，
 *      真实 Room 会因身份哈希不一致触发 mpass，破坏蒙入数据。
 *   故采用「把 ALL_MIGRATIONS 当纯函数链式执行」：与生产迁移逻辑完全同源同序，验证迁移能跑通、
 *   数据在穿越全部版本后保持，且最新结构字段可查（schema 完整有效性由真机长期运行兜底）。
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    /**
     * 用例1：从 v12（核心业务表 + conv_relationship 初具形态）一路迁移到 v25。
     * 迁移前埋入一条记忆 + 一条会话，验证穿越全部版本后数据完整保留，且最新新增表可查。
     */
    @Test
    fun migrateFrom_v12_to_v25_keepsData() {
        helper.createDatabase(TEST_DB, 12).use { db ->
            // 埋入 v12 形态的旧数据
            db.execSQL(
                "INSERT INTO memory_items (type, content, keywords, confidence, createdAt, updatedAt, " +
                    "isDeleted, characterCardId, visibility, isTopSecret) " +
                    "VALUES ('PREFERENCE', '她喜欢喝热奶茶', '奶茶,热', 0.95, 1000, 2000, 0, 1, 'SHARED', 0)",
            )
            db.execSQL(
                "INSERT INTO conversations (title, remark, code, interactionEnabled, createdAt, updatedAt, " +
                    "isPinned, lastMessagePreview, lastMessageTime) " +
                    "VALUES ('测试会话', '备注名', 'V-test01', 0, 1000, 2000, 0, '你好', 2000)",
            )

            // 与生产同源同序地跑完全部版本迁移（12→25）
            migrateChain(db, from = 12, to = 25)

            // 迁移后数据仍在（穿越 13 次迁移未丢）
            val count = queryInt(db, "SELECT COUNT(*) FROM memory_items WHERE content = '她喜欢喝热奶茶'")
            check(count == 1) { "迁移后记忆数据丢失: count=$count" }
            val convCount = queryInt(db, "SELECT COUNT(*) FROM conversations WHERE code = 'V-test01'")
            check(convCount == 1) { "迁移后会话数据丢失: convCount=$convCount" }
            // 最新版本新增的 conversation_traits 表已建好（可查询）
            val tableCount = queryInt(db, "SELECT COUNT(*) FROM conversation_traits")
            check(tableCount == 0) { "conversation_traits 表不可用: tableCount=$tableCount" }
        }
    }

    /**
     * 用例2：迁移 v22→v24（T11.16 加 type 列 + T12.10 convBId 改可空）。
     * 验证：type 列存在且存量数据被填默认值 'INTERACTION'；convBId=0 的 WATCH 哨兵行迁移为 NULL。
     */
    @Test
    fun migrateFrom_v22_to_v24_addsTypeAndNormalizesWatchSentinel() {
        helper.createDatabase(TEST_DB2, 22).use { db ->
            db.execSQL(
                "INSERT INTO relationship_event (convAId, convBId, scene, topic, mood, points, source, happenedAt) " +
                    "VALUES (1, 2, '午夜', '星座话题', '轻松', '聊了很多', 'AUTO', 3000)",
            )
            // 埋一条旧 WATCH 哨兵事件（convBId=0 表示用户共同观看）
            db.execSQL(
                "INSERT INTO relationship_event (convAId, convBId, scene, topic, mood, points, source, happenedAt) " +
                    "VALUES (1, 0, '你分享内容一起看', '新番', '一起看', '聊了很多', 'MANUAL', 4000)",
            )

            migrateChain(db, from = 22, to = 24)

            // 最新迁移新增的 type 列可读，且存量数据被填默认值
            val type = queryString(db, "SELECT type FROM relationship_event WHERE convAId = 1 AND convBId = 2 LIMIT 1")
            check(type == "INTERACTION") { "type 列默认值不对: $type" }
            // T12.10：convBId=0 哨兵被迁移为 NULL（用户侧明确表达）
            val sentinelIsNull = queryInt(
                db,
                "SELECT COUNT(*) FROM relationship_event WHERE convAId = 1 AND convBId IS NULL AND type = 'INTERACTION'",
            )
            check(sentinelIsNull == 1) { "WATCH 哨兵未迁移为 NULL: count=$sentinelIsNull" }
            val orphanZero = queryInt(db, "SELECT COUNT(*) FROM relationship_event WHERE convBId = 0")
            check(orphanZero == 0) { "仍存在 convBId=0 哨兵: count=$orphanZero" }
        }
    }

    /**
     * 用例3：最新迁移 v24→v25（T13.1 新增 conversation_traits 会话独有人格表）。
     * 验证：新表字段可写可读（6 维 + 反差对 + 印象摘要默认值）；conversationId 唯一索引生效（同会话二次插入被拒）。
     */
    @Test
    fun migrateFrom_v24_to_v25_addsConversationTraitsTable() {
        helper.createDatabase(TEST_DB3, 24).use { db ->
            db.execSQL(
                "INSERT INTO conversations (title, remark, code, interactionEnabled, lifeEnabled, createdAt, updatedAt, " +
                    "isPinned, lastMessagePreview, lastMessageTime) " +
                    "VALUES ('小夏会话', '', 'V-test25', 0, 0, 1000, 2000, 0, '你好', 2000)",
            )

            migrateChain(db, from = 24, to = 25)

            // 新表字段完整可写（6 维 + 反差对 + 印象摘要/标签/来源 + 时间）
            db.execSQL(
                "INSERT INTO conversation_traits (conversationId, characterCardId, sociability, initiative, " +
                    "warmth, attachment, humor, casualness, contrastSurface, contrastInner, contrastTrigger, " +
                    "identityBlurb, identityTags, generatedBy, createdAt) " +
                    "VALUES (1, 1, 55, 60, 85, 70, 40, 30, '温柔体贴的学姐', '很要强', '被误解或低估时', " +
                    "'慢热·暖心｜平时温柔体贴，但初识话不多', '[\"慢热\",\"暖心\"]', 'template', 5000)",
            )
            val count = queryInt(db, "SELECT COUNT(*) FROM conversation_traits")
            check(count == 1) { "conversation_traits 插入失败: count=$count" }
            val blurb = queryString(db, "SELECT identityBlurb FROM conversation_traits WHERE conversationId = 1 LIMIT 1")
            check(blurb == "慢热·暖心｜平时温柔体贴，但初识话不多") { "印象摘要回读不对: $blurb" }
            val warmth = queryInt(db, "SELECT warmth FROM conversation_traits WHERE conversationId = 1 LIMIT 1")
            check(warmth == 85) { "6 维数值回读不对: warmth=$warmth" }

            // conversationId 唯一索引生效：同会话二次插入应被唯一约束拒绝
            val dupRejected = runCatching {
                db.execSQL(
                    "INSERT INTO conversation_traits (conversationId, characterCardId, sociability, initiative, " +
                        "warmth, attachment, humor, casualness, contrastSurface, contrastInner, contrastTrigger, " +
                        "identityBlurb, identityTags, generatedBy, createdAt) " +
                        "VALUES (1, 1, 50, 50, 50, 50, 50, 50, '', '', '', '', '[]', 'template', 6000)",
                )
            }.isFailure
            check(dupRejected) { "conversationId 唯一索引未生效，同会话可重复插入" }
            val finalCount = queryInt(db, "SELECT COUNT(*) FROM conversation_traits WHERE conversationId = 1")
            check(finalCount == 1) { "唯一索引失效后数据被重复写入: count=$finalCount" }
        }
    }

    /**
     * 用例4：迁移 v31→v32（v2.0 关系引擎 R1，conversations 加 relationDirection 方向列）。
     * 验证：存量会话默认方向为 'NONE'，且迁移前已设方向的会话保留其值（迁移只加列不覆盖）。
     */
    @Test
    fun migrateFrom_v31_to_v32_addsRelationDirection() {
        helper.createDatabase(TEST_DB4, 31).use { db ->
            // 埋入 v31 形态的存量会话（无 relationDirection 列，顺应迁移前结构需按 v31 字段 INSERT）
            db.execSQL(
                "INSERT INTO conversations (title, remark, code, interactionEnabled, lifeEnabled, createdAt, updatedAt, " +
                    "isPinned, lastMessagePreview, lastMessageTime) " +
                    "VALUES ('存量会话', '', 'V-test32', 0, 0, 1000, 2000, 0, '你好', 2000)",
            )

            migrateChain(db, from = 31, to = 32)

            // 新列可读，存量数据默认方向 = 'NONE'（兼容旧数据）
            val direction = queryString(db, "SELECT relationDirection FROM conversations WHERE code = 'V-test32' LIMIT 1")
            check(direction == "NONE") { "relationDirection 默认值不对: $direction" }
        }
    }

    /**
     * 用例5：迁移 v32→v33（v2.0 关系引擎 R3，conversations 加候选状态 4 列）。
     * 验证：存量会话候选方向默认 'NONE'、候选次数默认 0，两个时间戳列可空且默认 NULL（迁移只加列不覆盖）。
     */
    @Test
    fun migrateFrom_v32_to_v33_addsRelationDirectionCandidates() {
        helper.createDatabase(TEST_DB5, 32).use { db ->
            // 埋入 v32 形态的存量会话（无候选状态 4 列，按 v32 已有字段 INSERT）
            db.execSQL(
                "INSERT INTO conversations (title, remark, code, interactionEnabled, lifeEnabled, createdAt, updatedAt, " +
                    "isPinned, lastMessagePreview, lastMessageTime) " +
                    "VALUES ('存量会话', '', 'V-test33', 0, 0, 1000, 2000, 0, '你好', 2000)",
            )

            migrateChain(db, from = 32, to = 33)

            // 候选方向默认 'NONE'、候选次数默认 0（兼容旧数据）
            val candDir = queryString(db, "SELECT candidateDirection FROM conversations WHERE code = 'V-test33' LIMIT 1")
            check(candDir == "NONE") { "candidateDirection 默认值不对: $candDir" }
            val candCount = queryInt(db, "SELECT candidateCount FROM conversations WHERE code = 'V-test33' LIMIT 1")
            check(candCount == 0) { "candidateCount 默认值不对: $candCount" }
            // 两个时间戳列可空、默认 NULL
            val seenNull = queryInt(db, "SELECT COUNT(*) FROM conversations WHERE code = 'V-test33' AND candidateLastSeen IS NULL")
            check(seenNull == 1) { "candidateLastSeen 应为 NULL，实测非空对象数: $seenNull" }
            val switchNull = queryInt(db, "SELECT COUNT(*) FROM conversations WHERE code = 'V-test33' AND lastDirectionSwitch IS NULL")
            check(switchNull == 1) { "lastDirectionSwitch 应为 NULL，实测非空对象数: $switchNull" }
        }
    }

    /**
     * 用例6：迁移 v33→v34（v2.0 A6-1 群聊数据层：conversations 加 isGroup、messages 加 senderConversationId、新增 conversation_participants 表）。
     * 验证：存量会话 isGroup 默认 0、存量消息 senderConversationId 默认 NULL、新表可创建并插入。
     */
    @Test
    fun migrateFrom_v33_to_v34_addsGroupChatFields() {
        helper.createDatabase(TEST_DB6, 33).use { db ->
            // 埋入 v33 形态的存量会话和消息
            db.execSQL(
                "INSERT INTO conversations (title, remark, code, interactionEnabled, lifeEnabled, createdAt, updatedAt, " +
                    "isPinned, lastMessagePreview, lastMessageTime, affinityScore, relationDirection, " +
                    "candidateDirection, candidateCount) " +
                    "VALUES ('存量会话', '', 'V-test34', 0, 0, 1000, 2000, 0, '你好', 2000, 30, 'NONE', 'NONE', 0)",
            )
            db.execSQL(
                "INSERT INTO messages (conversationId, role, content, createdAt, status) " +
                    "VALUES (1, 'USER', '测试消息', 3000, 'DONE')",
            )

            migrateChain(db, from = 33, to = 34)

            // isGroup 默认 0（存量 1v1 会话不是群聊）
            val isGroup = queryInt(db, "SELECT isGroup FROM conversations WHERE code = 'V-test34' LIMIT 1")
            check(isGroup == 0) { "isGroup 默认值不对: $isGroup" }
            // senderConversationId 默认 NULL（存量消息无发送者归属）
            val senderNull = queryInt(db, "SELECT COUNT(*) FROM messages WHERE senderConversationId IS NULL")
            check(senderNull == 1) { "存量消息 senderConversationId 应为 NULL，实测非空数: $senderNull" }
            // 新表 conversation_participants 可写入
            db.execSQL("INSERT INTO conversation_participants (conversationId, participantConversationId) VALUES (1, 1)")
            val count = queryInt(db, "SELECT COUNT(*) FROM conversation_participants")
            check(count == 1) { "conversation_participants 应可插入，实测: $count" }
        }
    }

    /**
     * 用例7：迁移 v34→v35（v2.0 审查修复：群聊参与者表索引名统一为 Room 默认名）。
     * 模拟「由 v33→v34 迁移而来」的存量库（索引为旧自定义名 index_cp_*）：
     * 验证旧索引名被清理、默认索引名建齐、数据保留、唯一约束仍生效。
     */
    @Test
    fun migrateFrom_v34_to_v35_unifiesParticipantIndexes() {
        helper.createDatabase(TEST_DB7, 34).use { db ->
            // 模拟存量 v34 库（真实来源是 MIGRATION_33_34，索引名为旧自定义短名 index_cp_*）：
            // 先删掉 schema 默认索引，重建为旧名，以贴近真实迁移库形态
            db.execSQL("DROP INDEX IF EXISTS `index_conversation_participants_conversationId`")
            db.execSQL("DROP INDEX IF EXISTS `index_conversation_participants_conversationId_participantConversationId`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cp_conversationId` ON `conversation_participants` (`conversationId`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_cp_unique` ON `conversation_participants` (`conversationId`, `participantConversationId`)")
            // 埋一条参与者数据
            db.execSQL("INSERT INTO conversation_participants (conversationId, participantConversationId) VALUES (1, 2)")

            migrateChain(db, from = 34, to = 35)

            // 旧自定义索引名已清理
            val oldIdx = queryInt(
                db,
                "SELECT COUNT(*) FROM sqlite_master WHERE type='index' " +
                    "AND name IN ('index_cp_conversationId','index_cp_unique')",
            )
            check(oldIdx == 0) { "旧索引名未清理: count=$oldIdx" }
            // 默认索引名已建齐（与实体/schema 导出一致）
            val newIdx = queryInt(
                db,
                "SELECT COUNT(*) FROM sqlite_master WHERE type='index' " +
                    "AND name IN ('index_conversation_participants_conversationId'," +
                    "'index_conversation_participants_conversationId_participantConversationId')",
            )
            check(newIdx == 2) { "默认索引名未建齐: count=$newIdx" }
            // 数据保留
            val count = queryInt(db, "SELECT COUNT(*) FROM conversation_participants")
            check(count == 1) { "参与者数据丢失: count=$count" }
            // 唯一约束仍生效：同参与者二次插入应被拒绝
            val dupRejected = runCatching {
                db.execSQL("INSERT INTO conversation_participants (conversationId, participantConversationId) VALUES (1, 2)")
            }.isFailure
            check(dupRejected) { "唯一索引未生效，同参与者可重复插入" }
        }
    }

    /**
     * 用例9：迁移 v35→v36（no-op，修复 71847ce 在同一版本号内改 defaultValue 注解导致 identity hash 漂移而崩溃）。
     * 验证：no-op 迁移能跑通、数据无损、关键列值（关系方向/浓度）在穿越迁移后原样保留。
     */
    @Test
    fun migrateFrom_v35_to_v36_noopKeepsData() {
        helper.createDatabase(TEST_DB9, 35).use { db ->
            // 埋入 v35 形态的存量会话（含 v2.0 关系引擎 + 群聊全部字段）
            db.execSQL(
                "INSERT INTO conversations (title, remark, code, interactionEnabled, lifeEnabled, createdAt, updatedAt, " +
                    "isPinned, lastMessagePreview, lastMessageTime, affinityScore, relationDirection, " +
                    "candidateDirection, candidateCount, worldBookId, graphEnabled, isGroup) " +
                    "VALUES ('存量会话', '', 'V-test36', 0, 0, 1000, 2000, 0, '你好', 2000, 30, 'FRIEND', 'AMBIGUOUS', 2, NULL, 0, 0)",
            )

            migrateChain(db, from = 35, to = 36)

            // no-op：数据无损
            val count = queryInt(db, "SELECT COUNT(*) FROM conversations WHERE code = 'V-test36'")
            check(count == 1) { "迁移后会话数据丢失: count=$count" }
            // 关键列值保留（关系方向 / 浓度未被动）
            val direction = queryString(db, "SELECT relationDirection FROM conversations WHERE code = 'V-test36' LIMIT 1")
            check(direction == "FRIEND") { "relationDirection 应保留 FRIEND，实测: $direction" }
            val affinity = queryInt(db, "SELECT affinityScore FROM conversations WHERE code = 'V-test36' LIMIT 1")
            check(affinity == 30) { "affinityScore 应保留 30，实测: $affinity" }
        }
    }

    /**
     * 用例8：迁移 v25→v31（补齐审查点名的覆盖缺口区间）。
     * 覆盖：v27→v28 新增世界书两表 world_info_book/world_info_entry + conversations 加 worldBookId（A3），
     * v30→v31 conversations 加 graphEnabled 图谱开关（B4）。
     * 验证：世界书两表可建可写、存量会话 worldBookId 默认 NULL、graphEnabled 默认 0。
     */
    @Test
    fun migrateFrom_v25_to_v31_addsWorldBookAndGraphFields() {
        helper.createDatabase(TEST_DB8, 25).use { db ->
            // 埋入 v25 形态的存量会话（无 affinityScore/worldBookId/graphEnabled 等后续列，按 v25 字段集 INSERT）
            db.execSQL(
                "INSERT INTO conversations (title, remark, code, interactionEnabled, lifeEnabled, createdAt, updatedAt, " +
                    "isPinned, lastMessagePreview, lastMessageTime) " +
                    "VALUES ('存量会话', '', 'V-test31', 0, 0, 1000, 2000, 0, '你好', 2000)",
            )

            migrateChain(db, from = 25, to = 31)

            // world_info_book 表已建且可写（v27→v28 新增）
            db.execSQL(
                "INSERT INTO world_info_book (name, description, createdAt) VALUES ('幻想大陆设定集', '测试简介', 1000)",
            )
            val bookCount = queryInt(db, "SELECT COUNT(*) FROM world_info_book")
            check(bookCount == 1) { "world_info_book 表不可用: count=$bookCount" }
            // world_info_entry 表已建且可写（v27→v28 新增，bookId 关联书）
            db.execSQL(
                "INSERT INTO world_info_entry (bookId, title, keys, content, priority, constant, enabled) " +
                    "VALUES (1, '大陆', '大陆,世界', '这里是一片广袤大陆', 10, 0, 1)",
            )
            val entryCount = queryInt(db, "SELECT COUNT(*) FROM world_info_entry")
            check(entryCount == 1) { "world_info_entry 表不可用: count=$entryCount" }
            // 存量会话 worldBookId 默认 NULL（v27→v28 加列不覆盖存量）
            val wbNull = queryInt(db, "SELECT COUNT(*) FROM conversations WHERE code = 'V-test31' AND worldBookId IS NULL")
            check(wbNull == 1) { "worldBookId 应为 NULL，实测非空数: $wbNull" }
            // graphEnabled 默认 0（v30→v31 加列，存量会话图谱未开启）
            val graph = queryInt(db, "SELECT graphEnabled FROM conversations WHERE code = 'V-test31' LIMIT 1")
            check(graph == 0) { "graphEnabled 默认值不对: $graph" }
        }
    }

    /**
     * 中途任一迁移抛异常即测试失败——从而捕获「某次迁移在真实数据形态下跑不通」的回归。
     */
    private fun migrateChain(db: SupportSQLiteDatabase, from: Int, to: Int) {
        (from + 1..to).forEach { version ->
            ALL_MIGRATIONS.first { it.endVersion == version }.migrate(db)
        }
    }

    private fun queryInt(db: SupportSQLiteDatabase, sql: String): Int =
        db.query(sql).use { c -> if (c.moveToFirst()) c.getInt(0) else -1 }

    private fun queryString(db: SupportSQLiteDatabase, sql: String): String =
        db.query(sql).use { c -> if (c.moveToFirst()) c.getString(0) else "" }

    companion object {
        private const val TEST_DB = "migration-test-v12.db"
        private const val TEST_DB2 = "migration-test-v22.db"
        private const val TEST_DB3 = "migration-test-v24.db"
        private const val TEST_DB4 = "migration-test-v31.db"
        private const val TEST_DB5 = "migration-test-v32.db"
        private const val TEST_DB6 = "migration-test-v33.db"
        private const val TEST_DB7 = "migration-test-v34.db"
        private const val TEST_DB8 = "migration-test-v25.db"
        private const val TEST_DB9 = "migration-test-v35.db"
    }
}