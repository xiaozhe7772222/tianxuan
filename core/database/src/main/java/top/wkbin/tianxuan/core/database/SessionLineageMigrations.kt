package top.wkbin.tianxuan.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 会话谱系追踪（56 → 57）：对齐 DSH SessionHeader 的 lineage 字段。
 *
 * 新增列：
 * - parentSessionId  — 分叉来源会话 id（顶级会话为 NULL）
 * - isSeeded         — 是否包含分叉继承的事件前缀
 * - origin           — 会话起源分类（'subagent' 标记子代理会话）
 * - delegationDepth  — 委派深度（顶层 null，子代理为父深度+1）
 */
val MIGRATION_56_57 = object : Migration(56, 57) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // DEFAULT NULL 必须显式写出：Room 校验默认值时按 PRAGMA 返回的 DDL 字面量逐字比较。
        // 实体用 @ColumnInfo(defaultValue = "NULL") 生成 `DEFAULT NULL`，若 ADD COLUMN 省略它，
        // PRAGMA 返回无默认（Room 记为 "undefined"），与期望 "NULL" 不符 → 校验失败 →
        // 触发破坏性回退（丢数据）。三列均为可空列，语义上 DEFAULT NULL 也完全等价。
        db.execSQL("ALTER TABLE `harness_sessions` ADD COLUMN `parentSessionId` TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE `harness_sessions` ADD COLUMN `isSeeded` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `harness_sessions` ADD COLUMN `origin` TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE `harness_sessions` ADD COLUMN `delegationDepth` INTEGER DEFAULT NULL")
    }
}
