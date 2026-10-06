package top.wkbin.tianxuan.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 端到端：从历史上最脆弱的两个版本一路升到当前版本。
 *
 * 单条迁移的测试只能证明「这一步合法」，证明不了「这条路通」。
 * 而用户实际走的正是全程——Room 在运行时是逐步应用迁移的，任何一步失败都
 * 让整个升级回滚，表现为启动崩溃。所以必须把跨断点的那几条串起来跑。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FullChainMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    private fun path(db: String): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getDatabasePath(db).absolutePath

    @Test
    fun `database at version 29 upgrades all the way to current`() {
        val dbPath = path("chain-29-test.db")
        helper.createDatabase(dbPath, 29).use { db ->
            db.execSQL(
                """INSERT INTO harness_messages (id, sessionId, createdAt, type, payloadJson)
                   VALUES ('m1', 's1', 100, 'user', '{"text":"hi"}')""",
            )
            db.execSQL(
                """INSERT INTO agent_skills (id, name, description, systemPrompt, triggerCommand, iconName,
                   isEnabled, isBuiltin, isImmutable, category)
                   VALUES ('sk1', '旧技能', 'd', 'p', NULL, 'Star', 1, 0, 0, 'dev')""",
            )
        }

        // 传整条链：Room 会从库的实际版本开始，逐个应用到 SCHEMA_VERSION
        helper.runMigrationsAndValidate(dbPath, SCHEMA_VERSION, true, *ALL_MIGRATIONS.toTypedArray()).use { db ->
            // 老数据在整条链之后依然可读，且没被后续迁移改坏
            db.query("SELECT COUNT(*) FROM harness_entries").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("旧消息应在全程升级后仍然存在", 1, c.getInt(0))
            }
            db.query("SELECT name, resourcePath FROM agent_skills WHERE id = 'sk1'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("旧技能", c.getString(0))
                assertTrue("resourcePath 应保持为 NULL", c.isNull(1))
            }
        }
    }

    @Test
    fun `database at version 32 upgrades all the way to current`() {
        val dbPath = path("chain-32-test.db")
        helper.createDatabase(dbPath, 32).use { db ->
            db.execSQL(
                """INSERT INTO agent_skills (id, name, description, systemPrompt, triggerCommand, iconName,
                   isEnabled, isBuiltin, isImmutable, category)
                   VALUES ('sk1', '旧技能', 'd', 'p', NULL, 'Star', 1, 0, 0, 'dev')""",
            )
        }

        helper.runMigrationsAndValidate(dbPath, SCHEMA_VERSION, true, *ALL_MIGRATIONS.toTypedArray()).use { db ->
            db.query("SELECT COUNT(*) FROM agent_skills WHERE id = 'sk1'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(1, c.getInt(0))
            }
        }
    }
}