package top.wkbin.tianxuan.core.tools

import top.wkbin.tianxuan.core.database.InstallTaskDao
import top.wkbin.tianxuan.core.database.InstallTaskEntity

/** Persistence boundary for durable install transactions. */
class InstallTaskRepository(
    private val dao: InstallTaskDao,
) {
    suspend fun upsert(task: InstallTaskEntity) = dao.upsert(task)
    suspend fun findByTool(distroId: String, toolId: String): InstallTaskEntity? = dao.findByTool(distroId, toolId)
    suspend fun listByState(state: String): List<InstallTaskEntity> = dao.listByState(state)
    suspend fun deleteByDistro(distroId: String) = dao.deleteByDistro(distroId)
}

