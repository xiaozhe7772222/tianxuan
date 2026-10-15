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
        db.execSQL("ALTER TABLE `harness_sessions` ADD COLUMN `parentSessionId` TEXT")
        db.execSQL("ALTER TABLE `harness_sessions` ADD COLUMN `isSeeded` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `harness_sessions` ADD COLUMN `origin` TEXT")
        db.execSQL("ALTER TABLE `harness_sessions` ADD COLUMN `delegationDepth` INTEGER")
    }
}
