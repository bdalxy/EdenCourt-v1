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
 * 作用：数据库已 20+ 次升级（v1→v2→...→v23），迁移是「最易丢数据/最隐蔽」的回归点。
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
     * 用例1：从 v12（核心业务表 + conv_relationship 初具形态）一路迁移到 v23。
     * 迁移前埋入一条记忆 + 一条会话，验证穿越全部版本后数据完整保留。
     */
    @Test
    fun migrateFrom_v12_to_v23_keepsData() {
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

            // 与生产同源同序地跑完全部版本迁移（12→23）
            migrateChain(db, from = 12, to = 23)

            // 迁移后数据仍在（穿越 11 次迁移未丢）
            val count = queryInt(db, "SELECT COUNT(*) FROM memory_items WHERE content = '她喜欢喝热奶茶'")
            check(count == 1) { "迁移后记忆数据丢失: count=$count" }
            val convCount = queryInt(db, "SELECT COUNT(*) FROM conversations WHERE code = 'V-test01'")
            check(convCount == 1) { "迁移后会话数据丢失: convCount=$convCount" }
        }
    }

    /**
     * 用例2：最新一次迁移 v22→v23（T11.16 给 relationship_event 加 type 列 + 对存量数据填默认）。
     * 验证：type 列存在且存量数据被填默认值 'INTERACTION'。
     */
    @Test
    fun migrateFrom_v22_to_v23_addsTypeColumnWithDefault() {
        helper.createDatabase(TEST_DB2, 22).use { db ->
            db.execSQL(
                "INSERT INTO relationship_event (convAId, convBId, scene, topic, mood, points, source, happenedAt) " +
                    "VALUES (1, 2, '午夜', '星座话题', '轻松', '聊了很多', 'AUTO', 3000)",
            )

            migrateChain(db, from = 22, to = 23)

            // 最新迁移新增的 type 列可读，且存量数据被填默认值
            val type = queryString(db, "SELECT type FROM relationship_event WHERE convAId = 1 LIMIT 1")
            check(type == "INTERACTION") { "type 列默认值不对: $type" }
        }
    }

    /**
     * 把 ALL_MIGRATIONS 当作纯函数按版本升序链式执行（与生产 builder.addMigrations 用的是同一批对象）。
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
    }
}