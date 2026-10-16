package top.wkbin.tianxuan.runtime.shell

import top.wkbin.tianxuan.core.model.StorageMountBinding
import top.wkbin.tianxuan.runtime.RuntimePathManager
import top.wkbin.tianxuan.runtime.proot.ProotCommandBuilder
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class ProcessType {
    TERMINAL,
    SERVICE,
    COMMAND,
    UNKNOWN,
}

data class ManagedProcess(
    val id: String,
    val startedAt: Long,
    val session: LinuxSession,
    val toolId: String? = null,
    val pid: Long? = session.pid,
    val type: ProcessType = ProcessType.UNKNOWN,
)

interface ProcessRegistry {
    suspend fun start(
        id: String,
        command: ShellCommand,
        toolId: String? = null,
        type: ProcessType = ProcessType.SERVICE,
        distroId: String? = null,
        mounts: List<StorageMountBinding> = emptyList(),
    ): ManagedProcess
    suspend fun stop(id: String): Boolean
    suspend fun stopAll()
    suspend fun cleanupDeadProcesses(): Int
    fun list(): List<ManagedProcess>
    fun observeLogs(idOrToolId: String): Flow<List<String>>
    fun getLogs(idOrToolId: String): List<String>
    fun clearLogs(idOrToolId: String)
}

class ProcessRegistryImpl(
    private val pathManager: RuntimePathManager,
    private val prootCommandBuilder: ProotCommandBuilder,
) : ProcessRegistry {
    private val mutex = Mutex()

    /**
     * 保护 [processes] 的 Java 监视器。
     *
     * 协程 [mutex] 只能串行化 suspend 变更路径，而 [list] 是非 suspend 的，无法获取
     * [mutex]。若 `list()` 无锁遍历这个普通 `LinkedHashMap`，与并发变更
     * （start/stop/stopAll/cleanupDeadProcesses）交错就会抛
     * `ConcurrentModificationException`。因此所有对 [processes] 的读写统一在
     * [processesLock] 下进行。
     */
    private val processesLock = Any()
    private val processes = LinkedHashMap<String, ManagedProcess>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val logsMap = ConcurrentHashMap<String, MutableStateFlow<List<String>>>()

    private fun getOrCreateLogFlow(key: String): MutableStateFlow<List<String>> =
        logsMap.computeIfAbsent(key) { MutableStateFlow(emptyList()) }

    private fun appendLog(key: String, line: String) {
        val flow = getOrCreateLogFlow(key)
        val current = flow.value
        val updated = if (current.size >= 500) {
            current.drop(current.size - 499) + line
        } else {
            current + line
        }
        flow.value = updated
    }

    override suspend fun start(
        id: String,
        command: ShellCommand,
        toolId: String?,
        type: ProcessType,
        distroId: String?,
        mounts: List<StorageMountBinding>,
    ): ManagedProcess = mutex.withLock {
        val previous = synchronized(processesLock) { processes.remove(id) }
        previous?.session?.close()
        val safeDistro = distroId?.lowercase()?.trim()?.takeIf { it.isNotBlank() }
            ?: pathManager.listInstalledDistroIds().firstOrNull() ?: "ubuntu"
        val session = ProcessLinuxSession(
            prootCommandBuilder.build(
                prootBinary = pathManager.activeProotFile(),
                rootfsDir = pathManager.rootfsDir(safeDistro),
                workspaceDir = pathManager.workspaceDir,
                homeDir = pathManager.homeDir(safeDistro),
                optDir = pathManager.tianxuanRootDir(safeDistro),
                tmpDir = pathManager.tmpDir,
                attachmentsDir = pathManager.attachmentsDir,
                command = command,
                mounts = mounts,
            ),
            hostEnvironment = pathManager.hostProcessEnvironment(safeDistro),
        )

        val logKey = toolId ?: id
        appendLog(logKey, "[TianXuan] 正在启动服务进程...")
        if (toolId != null && toolId != id) {
            appendLog(id, "[TianXuan] 正在启动服务进程...")
        }

        scope.launch {
            try {
                session.output.collect { terminalOutput ->
                    terminalOutput.text.lineSequence()
                        .filter { it.isNotBlank() }
                        .forEach { line ->
                            appendLog(logKey, line)
                            if (toolId != null && toolId != id) {
                                appendLog(id, line)
                            }
                        }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                // 日志收集异常（如 fd 关闭）不应中断会话；停止通知由 finally 追加。
            } finally {
                val exitNotice = "[TianXuan] 服务进程已停止"
                appendLog(logKey, exitNotice)
                if (toolId != null && toolId != id) {
                    appendLog(id, exitNotice)
                }
            }
        }

        ManagedProcess(
            id = id,
            startedAt = System.currentTimeMillis(),
            session = session,
            toolId = toolId,
            pid = session.pid,
            type = type,
        ).also { synchronized(processesLock) { processes[id] = it } }
    }

    override suspend fun stop(id: String): Boolean = mutex.withLock {
        val process = synchronized(processesLock) { processes.remove(id) } ?: return@withLock false
        process.session.close()
        true
    }

    override suspend fun stopAll() = mutex.withLock {
        // 先在锁内取出快照并清空表，再在锁外逐个 close（close 是 suspend，不能持监视器调用）。
        val snapshot = synchronized(processesLock) {
            processes.values.toList().also { processes.clear() }
        }
        snapshot.forEach { it.session.close() }
    }

    override suspend fun cleanupDeadProcesses(): Int = mutex.withLock {
        val dead = synchronized(processesLock) {
            val stale = processes.values.filter { !it.session.isAlive }
            stale.forEach { processes.remove(it.id) }
            stale
        }
        dead.size
    }

    override fun list(): List<ManagedProcess> = synchronized(processesLock) { processes.values.toList() }

    override fun observeLogs(idOrToolId: String): Flow<List<String>> =
        getOrCreateLogFlow(idOrToolId).asStateFlow()

    override fun getLogs(idOrToolId: String): List<String> =
        getOrCreateLogFlow(idOrToolId).value

    override fun clearLogs(idOrToolId: String) {
        getOrCreateLogFlow(idOrToolId).value = emptyList()
    }
}
