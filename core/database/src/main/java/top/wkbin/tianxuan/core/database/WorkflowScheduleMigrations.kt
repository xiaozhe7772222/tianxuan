package top.wkbin.tianxuan.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Cron 日历调度（55 → 56）：WEEKLY + CRON 重复类型的新字段。 */
val MIGRATION_55_56 = object : Migration(55, 56) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `workflow_schedules` ADD COLUMN `weekDays` TEXT")
        db.execSQL("ALTER TABLE `workflow_schedules` ADD COLUMN `cronExpression` TEXT")
    }
}
