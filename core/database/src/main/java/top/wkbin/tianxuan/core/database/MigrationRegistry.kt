package top.wkbin.tianxuan.core.database

import androidx.room.migration.Migration

/**
 * 全部迁移的注册表。
 *
 * 存在的理由：过去 AppModule 里是一条手写的 `addMigrations(...)` 长参数列表，
 * 没人能从里面看出链是否连续。实际就断过两次（29→30、32→33 缺失），
 * 后果是所有停在这两版的用户一升级就崩——Room找不到路径就直接抛异常，
 * 而数据库构建处又没有 destructive 兜底，崩在启动流程里。
 *
 * 这里把「链必须连续」变成可执行的断言：[assertMigrationChainIsContinuous]
 * 校验相邻迁移首尾相接、且能一路走到 [AppDatabase] 的当前版本。
 * 以后再有人在中间插版本而忘了写迁移，单测立刻红。
 *
 * 顺序即升级顺序，无需按版本号排序——断言会校验这一点。
 */
val ALL_MIGRATIONS: List<Migration> = listOf(
    MIGRATION_27_28,
    MIGRATION_28_29,
    MIGRATION_29_30,
    MIGRATION_30_31,
    MIGRATION_31_32,
    MIGRATION_32_33,
    MIGRATION_33_34,
    MIGRATION_34_35,
    MIGRATION_35_36,
    MIGRATION_36_37,
    MIGRATION_37_38,
    MIGRATION_38_39,
    MIGRATION_39_40,
    MIGRATION_40_41,
    MIGRATION_41_42,
    MIGRATION_42_43,
    MIGRATION_43_44,
    MIGRATION_44_45,
    MIGRATION_45_46,
    MIGRATION_46_47,
    MIGRATION_47_48,
    MIGRATION_48_49,
    MIGRATION_49_50,
    MIGRATION_50_51,
    MIGRATION_51_52,
    MIGRATION_52_53,
    MIGRATION_53_54,
)

/**
 * 迁移链的起点。低于此版本的库无法由本迁移链升上来。
 *
 * 之所以是 27 而不是 1：schema 只导出了 16 及以后（见 core/database/schemas），
 * 16~26 的迁移从未存在过。若线上真有停在 16 及更早的库，那条链早就断了，
 * 不该由这里假装能接上——那属于要显式处理的降级场景，不是本注册表的责任。
 */
const val MIGRATION_CHAIN_START_VERSION: Int = 27

/**
 * 校验迁移链连续，且终点等于 [AppDatabase] 的当前版本。
 *
 * 三件事，缺一不可：
 * 1. 首条迁移的起点 == [MIGRATION_CHAIN_START_VERSION]
 * 2. 每条的 startVersion 正好等于前一条的 endVersion（既不重叠也不留空档）
 * 3. 末条的 endVersion == 当前 schema 版本
 *
 * @throws IllegalStateException 断链时抛出，消息里直接指出缺口在哪两个版本之间
 */
fun assertMigrationChainIsContinuous(currentSchemaVersion: Int) {
    assertMigrationChainIsContinuous(ALL_MIGRATIONS, currentSchemaVersion)
}

/**
 * 校验给定迁移链的连续性。
 *
 * 抽成带链参数的重载，是为了让测试能注入一条**故意挖了缺口的链**，
 * 验证断言真的会失败。若测试自己抄一份校验逻辑，两份实现一旦不同步，
 * 守卫测试就变成在验证一份没人运行的复制品——恒真、且毫无意义。
 *
 * @param migrations 待校验的迁移链，通常是 [ALL_MIGRATIONS]；仅测试注入缺陷链时不同
 * @throws IllegalStateException 断链时抛出，消息里直接指出缺口在哪两个版本之间
 */
fun assertMigrationChainIsContinuous(
    migrations: List<Migration>,
    currentSchemaVersion: Int,
) {
    require(migrations.isNotEmpty()) { "迁移链为空：数据库版本 $currentSchemaVersion 无任何迁移可走" }

    val first = migrations.first()
    check(first.startVersion == MIGRATION_CHAIN_START_VERSION) {
        "迁移链起点是 ${first.startVersion}，与声明的 $MIGRATION_CHAIN_START_VERSION 不符"
    }

    migrations.zipWithNext { prev, next ->
        check(next.startVersion == prev.endVersion) {
            "迁移链断裂：${describe(prev)} 之后应当是 $MIGRATION_CHAIN_START_VERSION..${next.endVersion} 的迁移，" +
                "实际下一条是 ${describe(next)}。补上 ${prev.endVersion}→${next.startVersion} 的迁移，" +
                "或把它加进 ALL_MIGRATIONS"
        }
    }

    val last = migrations.last()
    check(last.endVersion == currentSchemaVersion) {
        "迁移链终点是 ${last.endVersion}，但 AppDatabase 当前版本是 $currentSchemaVersion。" +
            "数据库已升到 $currentSchemaVersion 却没有对应迁移，升级必然失败"
    }
}

private fun describe(m: Migration): String = "${m.startVersion}→${m.endVersion}"