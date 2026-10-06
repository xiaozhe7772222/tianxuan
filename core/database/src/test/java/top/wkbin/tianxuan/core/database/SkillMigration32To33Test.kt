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
 * MIGRATION_32_33：为agent_skills 追加 resourcePath。
 *
 * 历史上缺失的又一条链路。重点验证 resourcePath 对存量技能必须是 NULL
 * 而不是空串——空串会被上层当成根目录去 stat，NULL 才是「无资源目录」。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SkillMigration32To33Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    private val dbPath: String =
        InstrumentationRegistry.getInstrumentation().targetContext.getDatabasePath(TEST_DB).absolutePath

    @Test
    fun `existing skills get a null resource path`() {
        helper.createDatabase(dbPath, 32).use { db ->
            db.execSQL(
                """INSERT INTO agent_skills (id, name, description, systemPrompt, triggerCommand, iconName,
                   isEnabled, isBuiltin, isImmutable, category)
                   VALUES ('sk1', '代码审查', '审查代码', '你是审查者', '/review', 'Code', 1, 0, 0, 'dev')""",
            )
        }

        helper.runMigrationsAndValidate(dbPath, 33, true, MIGRATION_32_33).use { db ->
            db.query("SELECT resourcePath FROM agent_skills WHERE id = 'sk1'").use { c ->
                assertTrue(c.moveToFirst())
                assertNull("存量技能的 resourcePath 应为 NULL 而非空串", c.getString(0))
            }
        }
    }

    @Test
    fun `resource path is writable and readable after migration`() {
        helper.createDatabase(dbPath, 32).use { db ->
            db.execSQL(
                """INSERT INTO agent_skills (id, name, description, systemPrompt, triggerCommand, iconName,
                   isEnabled, isBuiltin, isImmutable, category)
                   VALUES ('sk1', '文档', 'd', 'p', NULL, 'File', 1, 0, 0, 'doc')""",
            )
        }

        helper.runMigrationsAndValidate(dbPath, 33, true, MIGRATION_32_33).use { db ->
            db.execSQL("UPDATE agent_skills SET resourcePath = '/skills/doc' WHERE id = 'sk1'")
            db.query("SELECT resourcePath FROM agent_skills WHERE id = 'sk1'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("/skills/doc", c.getString(0))
            }
            // 可空性未被破坏：另一条技能仍可留空
            db.execSQL(
                """INSERT INTO agent_skills (id, name, description, systemPrompt, triggerCommand, iconName,
                   isEnabled, isBuiltin, isImmutable, category, resourcePath)
                   VALUES ('sk2', '无资源', 'd', 'p', NULL, 'File', 1, 0, 0, 'doc', NULL)""",
            )
        }
    }

    @Test
    fun `pre-existing columns are untouched`() {
        helper.createDatabase(dbPath, 32).use { db ->
            db.execSQL(
                """INSERT INTO agent_skills (id, name, description, systemPrompt, triggerCommand, iconName,
                   isEnabled, isBuiltin, isImmutable, category)
                   VALUES ('sk1', '原名', '描述', '提示', '/cmd', 'Star', 1, 1, 1, 'builtin')""",
            )
        }

        helper.runMigrationsAndValidate(dbPath, 33, true, MIGRATION_32_33).use { db ->
            db.query(
                "SELECT name, description, systemPrompt, triggerCommand, iconName, isEnabled, isBuiltin, isImmutable, category FROM agent_skills WHERE id='sk1'",
            ).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("原名", c.getString(0))
                assertEquals("描述", c.getString(1))
                assertEquals("提示", c.getString(2))
                assertEquals("/cmd", c.getString(3))
                assertEquals("Star", c.getString(4))
                assertEquals(1, c.getInt(5))
                assertEquals(1, c.getInt(6))
                assertEquals(1, c.getInt(7))
                assertEquals("builtin", c.getString(8))
            }
        }
    }

    private companion object {
        const val TEST_DB = "migration-32-33-test.db"
    }
}