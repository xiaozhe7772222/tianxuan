package top.wkbin.tianxuan.core.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Index
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import top.wkbin.tianxuan.core.model.ApprovalMode
import top.wkbin.tianxuan.core.model.RunMode

/** Harness 会话：一条会话聚合一批消息，并记录使用的模型。 */
@Entity(tableName = "harness_sessions", indices = [Index(value = ["updatedAt"])])
data class HarnessSessionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    /** Bound model profile id. Null keeps legacy fallback to the global default profile. */
    val modelId: String?,
    /** Concrete provider model id selected inside [modelId]'s profile. */
    val modelVariant: String? = null,
    val workspace: String = "",
    /** Explicit type selected for empty/imported workspaces; blank means auto-detect. */
    val projectType: String = "",
    /** Tool approval authority for this session; new sessions inherit the global default. */
    val approvalMode: String = ApprovalMode.ASSISTED.id,
    /** 运行意图（BUILD/PLAN），与 [approvalMode] 正交；新会话继承全局默认。 */
    @ColumnInfo(defaultValue = "build") val runMode: String = RunMode.BUILD.id,
    /**
     * 会话谱系（对齐 DSH SessionHeader.lineage）：本会话从哪个会话分叉而来。
     * 分叉（fork）时记录父会话 id；顶级会话为 null。
     */
    @ColumnInfo(defaultValue = "NULL") val parentSessionId: String? = null,
    /**
     * 是否包含分叉继承的事件前缀（fork-inherited event prefix）。
     * 由分叉构造写 true；普通新会话为 false。
     */
    @ColumnInfo(defaultValue = "0") val isSeeded: Boolean = false,
    /**
     * 会话起源分类：'subagent' 标记由子智能体会话创建；null 为顶层会话。
     * 这是呈现元数据，不证明子会话可续跑（对齐 DSH origin?: 'subagent'）。
     */
    @ColumnInfo(defaultValue = "NULL") val origin: String? = null,
    /**
     * 委派深度：顶层会话为 null（0），子代理子会话为父深度 + 1。
     * 持久化保证递归预算跨重启存活（对齐 DSH delegationDepth）。
     */
    @ColumnInfo(defaultValue = "NULL") val delegationDepth: Int? = null,
)

@Dao
interface HarnessSessionDao {
    @Query("SELECT * FROM harness_sessions ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<HarnessSessionEntity>>

    @Query("SELECT * FROM harness_sessions WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): HarnessSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: HarnessSessionEntity)

    @Query("UPDATE harness_sessions SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun touch(id: String, updatedAt: Long)

    @Query("UPDATE harness_sessions SET title = :title, updatedAt = :updatedAt WHERE id = :id")
    suspend fun rename(id: String, title: String, updatedAt: Long)

    @Query("UPDATE harness_sessions SET approvalMode = :approvalMode, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setApprovalMode(id: String, approvalMode: String, updatedAt: Long)

    @Query("UPDATE harness_sessions SET approvalMode = :approvalMode, updatedAt = :updatedAt")
    suspend fun setApprovalModeForAll(approvalMode: String, updatedAt: Long)

    @Query("UPDATE harness_sessions SET runMode = :runMode, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setRunMode(id: String, runMode: String, updatedAt: Long)

    @Query("UPDATE harness_sessions SET modelId = :modelId, modelVariant = :modelVariant, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setModelSelection(id: String, modelId: String?, modelVariant: String?, updatedAt: Long)

    @Query("DELETE FROM harness_sessions WHERE id = :id")
    suspend fun deleteSession(id: String)

    @Query("SELECT COUNT(*) FROM harness_sessions WHERE (:start IS NULL OR createdAt >= :start) AND (:end IS NULL OR createdAt < :end)")
    suspend fun countInRange(start: Long?, end: Long?): Int

    @Query("SELECT * FROM harness_sessions")
    suspend fun listAll(): List<HarnessSessionEntity>

    /** 查询某会话的谱系信息（分叉来源、是否为子代理等）。 */
    @Query("SELECT parentSessionId, isSeeded, origin, delegationDepth FROM harness_sessions WHERE id = :id LIMIT 1")
    suspend fun findLineage(id: String): SessionLineageTuple?

    /** 查询某会话的所有子会话（分叉/子代理后代）。 */
    @Query("SELECT * FROM harness_sessions WHERE parentSessionId = :parentId ORDER BY createdAt ASC")
    suspend fun findChildren(parentId: String): List<HarnessSessionEntity>

    /** 更新会话谱系（分叉/子代理创建时调用）。 */
    @Query("UPDATE harness_sessions SET parentSessionId = :parentSessionId, isSeeded = :isSeeded, origin = :origin, delegationDepth = :delegationDepth WHERE id = :id")
    suspend fun setLineage(id: String, parentSessionId: String?, isSeeded: Boolean, origin: String?, delegationDepth: Int?)

    /** 谱系查询结果的 Room 投影。 */
    data class SessionLineageTuple(
        val parentSessionId: String?,
        val isSeeded: Boolean,
        val origin: String?,
        val delegationDepth: Int?,
    )
}
