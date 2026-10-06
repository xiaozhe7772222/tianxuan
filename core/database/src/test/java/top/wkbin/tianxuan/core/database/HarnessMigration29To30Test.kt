package top.wkbin.tianxuan.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * MIGRATION_29_30：harness 从线性消息流迁移到车道树。
 *
 * 这是历史上真实缺失的一条链路——29 版用户一升级就崩。此测试除校验 schema
 * 合法性外，重点验证旧消息被正确搬成一条链：
 * - 首条parentId 应为空（链头），后续依次指向前一条
 * - 同一createdAt 的消息靠 rowid 决定次序，不能成环
 * - 不跨会话串链
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HarnessMigration29To30Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        // Room 2.8 默认 JVM driver 与 Robolectric 的 databasePath 解析不一致
        // （裸名 vs 绝对路径），显式指定 framework factory 走统一的 getDatabasePath 逻辑
        FrameworkSQLiteOpenHelperFactory(),
    )

    // createDatabase 接受裸名而 runMigrationsAndValidate 内部解析为绝对路径，
    // 两边不一致会触发 "driver is configured" 守卫；统一改传绝对路径
    private val dbPath: String =
        InstrumentationRegistry.getInstrumentation().targetContext.getDatabasePath(TEST_DB).absolutePath

    @Test
    fun `migrated schema is valid and old table is gone`() {
        helper.createDatabase(dbPath, 29).close()

        helper.runMigrationsAndValidate(dbPath, 30, true, MIGRATION_29_30).use { db ->
            // 旧表必须已删除：留着既浪费空间，又会让上层误以为还能读旧模型
            db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='harness_messages'").use {
                assertTrue("harness_messages 未被删除",it.moveToFirst().not())
            }
            // 新表齐备
            for (t in NEW_TABLES) {
                db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='$t'").use {
                    assertTrue("缺少新表 $t", it.moveToFirst())
                }
            }
        }
    }

    @Test
    fun `legacy messages become a single parent chain`() {
        helper.createDatabase(dbPath, 29).use { db ->
            db.execSQL(
                """INSERT INTO harness_messages (id, sessionId, createdAt, type, payloadJson)
                   VALUES ('m1', 's1', 100, 'user', '{"text":"a"}')""",
            )
            db.execSQL(
                """INSERT INTO harness_messages (id, sessionId, createdAt, type, payloadJson)
                   VALUES ('m2', 's1', 200, 'assistant', '{"text":"b"}')""",
            )
            db.execSQL(
                """INSERT INTO harness_messages (id, sessionId, createdAt, type, payloadJson)
                   VALUES ('m3', 's1', 300, 'user', '{"text":"c"}')""",
            )
        }

        helper.runMigrationsAndValidate(dbPath, 30, true, MIGRATION_29_30).use { db ->
            db.query(
                "SELECT id, sessionId, parentId, createdAt, entryType, payloadJson FROM harness_entries ORDER BY sequence",
            ).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("m1", c.getString(0)); assertEquals("s1", c.getString(1))
                // 链头无父，否则树里会出现悬空引用
                assertNull("首条消息不该有 parentId", c.getString(2))
                assertEquals(100L, c.getLong(3))
                assertEquals("message", c.getString(4))
                assertEquals("{\"text\":\"a\"}", c.getString(5))

                assertTrue(c.moveToNext())
                assertEquals("m2", c.getString(0)); assertEquals("m1", c.getString(2))

                assertTrue(c.moveToNext())
                assertEquals("m3", c.getString(0)); assertEquals("m2", c.getString(2))

                assertTrue("消息条数不符", !c.moveToNext())
            }
        }
    }

    @Test
    fun `messages of same timestamp keep insertion order without cycle`() {
        // createdAt 全同是最容易成环的输入：若并列时比较失效，
        // m3 的父可能算成m1 之后又算成 m2 之后，链就断了或成环。
        helper.createDatabase(dbPath, 29).use { db ->
            listOf("m1", "m2", "m3", "m4").forEach { id ->
                db.execSQL(
                    """INSERT INTO harness_messages (id, sessionId, createdAt, type, payloadJson)
                       VALUES ('$id', 's1', 500, 'user', '{}')""",
                )
            }
        }

        helper.runMigrationsAndValidate(dbPath, 30, true, MIGRATION_29_30).use { db ->
            db.query("SELECT id, parentId FROM harness_entries ORDER BY sequence").use { c ->
                // id 非空，parentId 可空（链头为 NULL），故Pair 第二项要显式可空
                val chain = mutableListOf<Pair<String, String?>>()
                while (c.moveToNext()) chain.add(c.getString(0) to c.getString(1))
                assertEquals(4, chain.size)
                assertNull("并列时间戳下链头不应有父", chain[0].second)
                // 逐条验证 parent 就是前一条，且不存在自引用
                chain.forEachIndexed { i, (id, parent) ->
                    if (i == 0) return@forEachIndexed
                    assertEquals("第 $i 条的 parent 应为前一条", chain[i - 1].first, parent)
                    assertTrue("出现自引用：$id", id != parent)
                }
            }
        }
    }

    @Test
    fun `messages never link across sessions`() {
        helper.createDatabase(dbPath, 29).use { db ->
            db.execSQL(
                """INSERT INTO harness_messages (id, sessionId, createdAt, type, payloadJson)
                   VALUES ('a1', 'sa', 100, 'user', '{}')""",
            )
            db.execSQL(
                """INSERT INTO harness_messages (id, sessionId, createdAt, type, payloadJson)
                   VALUES ('b1', 'sb', 150, 'user', '{}')""",
            )
        }

        helper.runMigrationsAndValidate(dbPath, 30, true, MIGRATION_29_30).use { db ->
            db.query("SELECT id, sessionId, parentId FROM harness_entries ORDER BY sequence").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("a1", c.getString(0)); assertNull(c.getString(2))
                assertTrue(c.moveToNext())
                assertEquals("b1", c.getString(0))
                assertNull("b1 属于另一会话，不该挂在 a1 下", c.getString(2))
            }
        }
    }

    @Test
    fun `no lanes are fabricated for migrated sessions`() {
        // 车道由 ensureLane 懒创建。迁移期提前造出会得到 leafId 为空的空车道，
        // 上层据此判定「会话已初始化但无进度」，行为反而错乱。
        helper.createDatabase(dbPath, 29).use { db ->
            db.execSQL(
                """INSERT INTO harness_messages (id, sessionId, createdAt, type, payloadJson)
                   VALUES ('m1', 's1', 100, 'user', '{}')""",
            )
        }

        helper.runMigrationsAndValidate(dbPath, 30, true, MIGRATION_29_30).use { db ->
            db.query("SELECT COUNT(*) FROM harness_lanes").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(0, c.getInt(0))
            }
        }
    }

    private companion object {
        const val TEST_DB = "migration-29-30-test.db"

        val NEW_TABLES = listOf(
            "harness_entries",
            "harness_lanes",
            "harness_operations",
            "harness_queue_items",
            "harness_usage",
            "harness_lane_results",
        )
    }
}