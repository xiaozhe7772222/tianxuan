package top.wkbin.tianxuan.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** RAG 知识库迁移 53→54：文档表 + 分块表（从 DatabaseMigrations.kt 抽出，控制单文件行数）。 */
val MIGRATION_53_54 = object : Migration(53, 54) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `kb_documents` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `source` TEXT NOT NULL DEFAULT 'text',
                `contentHash` TEXT NOT NULL DEFAULT '',
                `status` TEXT NOT NULL DEFAULT 'pending',
                `chunkCount` INTEGER NOT NULL DEFAULT 0,
                `errorMessage` TEXT,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `kb_chunks` (
                `id` TEXT NOT NULL,
                `docId` TEXT NOT NULL,
                `chunkIndex` INTEGER NOT NULL,
                `text` TEXT NOT NULL,
                `embedding` BLOB,
                `tokenCount` INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`docId`) REFERENCES `kb_documents`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_kb_chunks_docId` ON `kb_chunks` (`docId`)")
    }
}
