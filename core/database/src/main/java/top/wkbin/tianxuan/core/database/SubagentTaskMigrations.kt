package top.wkbin.tianxuan.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** 子代理任务持久化（54 → 55）：进程被杀后可从 Room 识别并恢复未完成任务。 */
val MIGRATION_54_55 = object : Migration(54, 55) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `subagent_tasks` (
                `id` TEXT NOT NULL,
                `sessionId` TEXT NOT NULL,
                `laneName` TEXT NOT NULL,
                `taskName` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `description` TEXT NOT NULL DEFAULT '',
                `profileId` TEXT NOT NULL DEFAULT '',
                `modelId` TEXT,
                `modelVariant` TEXT,
                `currentStep` INTEGER NOT NULL DEFAULT 0,
                `errorMessage` TEXT,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_subagent_tasks_sessionId` ON `subagent_tasks` (`sessionId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_subagent_tasks_status` ON `subagent_tasks` (`status`)")
    }
}