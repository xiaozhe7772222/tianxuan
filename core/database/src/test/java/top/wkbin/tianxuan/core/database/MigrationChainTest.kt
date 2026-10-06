package top.wkbin.tianxuan.core.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 迁移注册表的链连续性守卫。
 *
 * 这组测试针对的是「版本号升了但迁移没写」这类故障。它的特点是：代码能编译、
 * 架构检查全绿、APK 能装上，只有存量用户升级那一刻才崩在启动流程里，
 * 而且崩在现场、无法自愈。因此必须在CI 里就拦住。
 */
class MigrationChainTest {

    @Test
    fun `chain is continuous from declared start to current schema`() {
        // 不断言即通过。断链时 check 会抛出并把缺口位置写进消息。
        assertMigrationChainIsContinuous(SCHEMA_VERSION)
    }

    @Test
    fun `chain start matches declared start version`() {
        assertEquals(
            "ALL_MIGRATIONS 首条的起点必须等于 MIGRATION_CHAIN_START_VERSION，" +
                "否则声明的起点版本用户根本无从升上来",
            MIGRATION_CHAIN_START_VERSION,
            ALL_MIGRATIONS.first().startVersion,
        )
    }

    @Test
    fun `chain end reaches current schema version`() {
        assertEquals(SCHEMA_VERSION, ALL_MIGRATIONS.last().endVersion)
    }

    @Test
    fun `no duplicate or overlapping migrations`() {
        val seen = mutableSetOf<Pair<Int, Int>>()
        for (m in ALL_MIGRATIONS) {
            assertTrue("重复的迁移 ${m.startVersion}→${m.endVersion}", seen.add(m.startVersion to m.endVersion))
            assertTrue("迁移的起止版本必须递增 ${m.startVersion}→${m.endVersion}", m.startVersion < m.endVersion)
        }
    }

    @Test
    fun `the two historically broken links are now present`() {
        // 这两条正是历史上缺失、导致 29 版与 32 版用户升级即崩的缺口。
        // 显式钉住它们，避免「重构时觉得不需要」而被删掉。
        val links = ALL_MIGRATIONS.map { it.startVersion to it.endVersion }.toSet()
        assertTrue("缺少 29→30 迁移", (29 to 30) in links)
        assertTrue("缺少 32→33 迁移", (32 to 33) in links)
    }

    @Test
    fun `gap detection actually fires on an incomplete chain`() {
        // 反向验证：断言本身必须真的会失败，否则上面几条都是恒真的摆设。
        // 注入一条缺了 32→33 的链，检验守卫是否会报警。
        val broken = ALL_MIGRATIONS.filterNot { it.startVersion == 32 }
        assertTrue("测试自身前提错误：这条链本来就该缺一条", broken.size == ALL_MIGRATIONS.size - 1)

        var fired = false
        try {
            assertMigrationChainIsContinuous(broken, SCHEMA_VERSION)
        } catch (e: IllegalStateException) {
            fired = true
            assertTrue(
                "异常消息要指出缺口在哪个版本之间，实际是：${e.message}",
                e.message!!.contains("31→32") && e.message!!.contains("33→34"),
            )
        }
        assertTrue("缺一条迁移时断言却没有报错，守卫是假的", fired)
    }
}