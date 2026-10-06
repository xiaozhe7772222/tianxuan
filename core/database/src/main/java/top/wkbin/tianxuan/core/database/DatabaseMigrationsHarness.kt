package top.wkbin.tianxuan.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v29 → v30：harness 存储内核重构。
 *
 * 旧模型是「线性消息流」：harness_messages(id, sessionId, createdAt, type, payloadJson)，
 * 一个会话就是一串按时间排序的消息。新模型是「车道树」：
 * - harness_entries    会话内的不可变节点，靠 parentId 组成树
 * - harness_lanes      每个 (sessionId, laneName) 一条车道，leafId 指向当前末端
 * - harness_operations 一次操作的状态机
 * - harness_queue_items 排队中的操作
 * - harness_usage      token / 成本流水
 * - harness_lane_results 车道的终态
 *
 * 旧数据搬运策略：harness_messages 按 (sessionId, createdAt) 顺序串成一条链，
 * 逐条搬进 harness_entries，entryType 固定为 'message'，parentId 指向同会话的
 * 前一条。type 不做语义猜测——旧 type 的取值集合已随重构不可考，猜错会让
 * 上层按错误的分支渲染历史消息，比标成 message 更糟。旧消息仅作只读回放，
 * 新交互一律走车道。
 *
 * 不建 harness_lanes 行：ensureLane 是懒创建，缺车道的会话会在首次使用时自动
 * 补出主车道，提前写入反而会造出 leafId 为空的空车道。
 */
val MIGRATION_29_30 = object : Migration(29, 30) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS harness_entries (sequence INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, id TEXT NOT NULL, sessionId TEXT NOT NULL, parentId TEXT, createdAt INTEGER NOT NULL, entryType TEXT NOT NULL, customType TEXT, payloadJson TEXT NOT NULL)""")
        db.execSQL("""CREATE UNIQUE INDEX IF NOT EXISTS index_harness_entries_id ON harness_entries (id)""")
        db.execSQL("""CREATE INDEX IF NOT EXISTS index_harness_entries_sessionId_sequence ON harness_entries (sessionId, sequence)""")
        db.execSQL("""CREATE INDEX IF NOT EXISTS index_harness_entries_sessionId_parentId ON harness_entries (sessionId, parentId)""")

        // 旧消息串成链搬入新节点表。rowid 排序保证 createdAt 相同时也有确定次序，
        // 否则 parentId 会成环。必须先建表再搬，最后才敢丢旧表。
        db.execSQL(
            """
            INSERT INTO harness_entries (id, sessionId, parentId, createdAt, entryType, customType, payloadJson)
            SELECT m.id,
                   m.sessionId,
                   (SELECT p.id FROM harness_messages p
                     WHERE p.sessionId = m.sessionId
                       AND (p.createdAt < m.createdAt
                            OR (p.createdAt = m.createdAt AND p.rowid < m.rowid))
                     ORDER BY p.createdAt DESC, p.rowid DESC
                     LIMIT 1),
                   m.createdAt,
                   'message',
                   NULL,
                   m.payloadJson
            FROM harness_messages m
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE IF EXISTS harness_messages")

        db.execSQL("""CREATE TABLE IF NOT EXISTS harness_lanes (sessionId TEXT NOT NULL, name TEXT NOT NULL, leafId TEXT, currentOperationId TEXT, modelId TEXT, thinkingLevel TEXT NOT NULL, faulted INTEGER NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(sessionId, name))""")
        db.execSQL("""CREATE INDEX IF NOT EXISTS index_harness_lanes_sessionId_currentOperationId ON harness_lanes (sessionId, currentOperationId)""")

        db.execSQL("""CREATE TABLE IF NOT EXISTS harness_operations (id TEXT NOT NULL, sessionId TEXT NOT NULL, laneName TEXT NOT NULL, kind TEXT NOT NULL, status TEXT NOT NULL, phase TEXT NOT NULL, startedAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, startLeafId TEXT, stateJson TEXT NOT NULL, pendingEffectKind TEXT, pendingEffectId TEXT, replayPolicy TEXT, attempt INTEGER NOT NULL, PRIMARY KEY(id))""")
        db.execSQL("""CREATE INDEX IF NOT EXISTS index_harness_operations_sessionId_laneName ON harness_operations (sessionId, laneName)""")
        db.execSQL("""CREATE INDEX IF NOT EXISTS index_harness_operations_status_updatedAt ON harness_operations (status, updatedAt)""")

        db.execSQL("""CREATE TABLE IF NOT EXISTS harness_queue_items (id TEXT NOT NULL, sessionId TEXT NOT NULL, laneName TEXT NOT NULL, operationId TEXT, queueType TEXT NOT NULL, createdAt INTEGER NOT NULL, payloadJson TEXT NOT NULL, PRIMARY KEY(id))""")
        db.execSQL("""CREATE INDEX IF NOT EXISTS index_harness_queue_items_sessionId_laneName_queueType_createdAt ON harness_queue_items (sessionId, laneName, queueType, createdAt)""")
        db.execSQL("""CREATE INDEX IF NOT EXISTS index_harness_queue_items_operationId ON harness_queue_items (operationId)""")

        db.execSQL("""CREATE TABLE IF NOT EXISTS harness_usage (sequence INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, id TEXT NOT NULL, sessionId TEXT NOT NULL, operationId TEXT, entryId TEXT, provider TEXT, modelId TEXT, inputTokens INTEGER NOT NULL, outputTokens INTEGER NOT NULL, reasoningTokens INTEGER NOT NULL, cacheReadTokens INTEGER NOT NULL, cacheWriteTokens INTEGER NOT NULL, estimatedCostUsd REAL, adjustment INTEGER NOT NULL, detailsJson TEXT, createdAt INTEGER NOT NULL)""")
        db.execSQL("""CREATE UNIQUE INDEX IF NOT EXISTS index_harness_usage_id ON harness_usage (id)""")
        db.execSQL("""CREATE INDEX IF NOT EXISTS index_harness_usage_sessionId_sequence ON harness_usage (sessionId, sequence)""")
        db.execSQL("""CREATE INDEX IF NOT EXISTS index_harness_usage_operationId ON harness_usage (operationId)""")
        db.execSQL("""CREATE INDEX IF NOT EXISTS index_harness_usage_entryId ON harness_usage (entryId)""")

        db.execSQL("""CREATE TABLE IF NOT EXISTS harness_lane_results (sessionId TEXT NOT NULL, laneName TEXT NOT NULL, operationId TEXT NOT NULL, outcome TEXT NOT NULL, finalEntryId TEXT, detailsJson TEXT, completedAt INTEGER NOT NULL, PRIMARY KEY(sessionId, laneName))""")
    }
}

/**
 * v32 → v33：技能可挂载资源目录。
 *
 * resourcePath 可空——存量技能没有资源目录，迁移后填NULL 表示「无」而不是空串，
 * 免得上层把空串当成根目录去 stat。
 */
val MIGRATION_32_33 = object : Migration(32, 33) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE agent_skills ADD COLUMN resourcePath TEXT")
    }
}