package top.wkbin.tianxuan.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SubagentTaskDao {
    @Query("SELECT * FROM subagent_tasks ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<SubagentTaskEntity>>

    @Query("SELECT * FROM subagent_tasks WHERE sessionId = :sessionId ORDER BY createdAt DESC")
    fun observeBySession(sessionId: String): Flow<List<SubagentTaskEntity>>

    @Query("SELECT * FROM subagent_tasks WHERE status = 'running'")
    suspend fun listRunning(): List<SubagentTaskEntity>

    @Query("SELECT * FROM subagent_tasks WHERE sessionId = :sessionId AND status = 'running'")
    suspend fun listRunningBySession(sessionId: String): List<SubagentTaskEntity>

    @Query("SELECT * FROM subagent_tasks WHERE id = :taskId")
    suspend fun findById(taskId: String): SubagentTaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: SubagentTaskEntity)

    @Query("UPDATE subagent_tasks SET status = :status, currentStep = :currentStep, errorMessage = :errorMessage, updatedAt = :updatedAt WHERE id = :taskId")
    suspend fun updateStatus(taskId: String, status: String, currentStep: Int, errorMessage: String?, updatedAt: Long)

    @Query("DELETE FROM subagent_tasks WHERE id = :taskId")
    suspend fun delete(taskId: String)

    @Query("DELETE FROM subagent_tasks WHERE sessionId = :sessionId")
    suspend fun deleteBySession(sessionId: String)

    /** 冷启动时清理：所有 running 状态的任务标记为 failed（进程被杀后无法继续） */
    @Query("UPDATE subagent_tasks SET status = 'failed', errorMessage = '进程被中断（应用重启），子代理任务无法继续', updatedAt = :now WHERE status = 'running'")
    suspend fun failInterruptedTasks(now: Long)
}
