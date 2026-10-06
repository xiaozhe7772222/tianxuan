package top.wkbin.tianxuan.runtime

import top.wkbin.tianxuan.core.common.result.AppResult
import top.wkbin.tianxuan.core.model.InstalledDistro
import top.wkbin.tianxuan.core.model.RuntimeState
import top.wkbin.tianxuan.runtime.shell.CommandResult
import top.wkbin.tianxuan.runtime.shell.InteractiveLaunchSpec
import top.wkbin.tianxuan.runtime.shell.LinuxSession
import top.wkbin.tianxuan.runtime.shell.ManagedProcess
import top.wkbin.tianxuan.runtime.shell.ProcessType
import top.wkbin.tianxuan.runtime.shell.SessionConfig
import top.wkbin.tianxuan.runtime.shell.ShellCommand
import java.io.File
import kotlinx.coroutines.flow.StateFlow

interface LinuxRuntime {
    val state: StateFlow<RuntimeState>
    val activeDistroId: StateFlow<String>
    val installedDistros: StateFlow<List<InstalledDistro>>

    suspend fun initialize(request: RuntimeInstallRequest = RuntimeInstallRequest("ubuntu")): AppResult<Unit>
    suspend fun restoreInstalledState(): Boolean
    suspend fun updateRootfs(distroId: String? = null): AppResult<Unit>
    suspend fun checkRootfsUpdate(distroId: String? = null): AppResult<RootfsUpdateInfo>
    suspend fun healthCheck(distroId: String? = null): RuntimeHealth

    suspend fun switchActiveDistro(distroId: String): AppResult<Unit>
    suspend fun installDistro(request: RuntimeInstallRequest, onProgress: suspend (DownloadProgress) -> Unit = {}): AppResult<Unit>
    suspend fun importDistro(request: RuntimeInstallRequest, archive: File): AppResult<Unit> =
        AppResult.Failure(top.wkbin.tianxuan.core.common.result.AppError(
            top.wkbin.tianxuan.core.common.result.ErrorCode.INSTALLATION_FAILED,
            "当前运行时不支持导入 RootFS",
        ))
    suspend fun uninstallDistro(distroId: String): AppResult<Unit>
    /** Remove the active distro and its installed tools while preserving /workspace. */
    suspend fun resetSandbox(distroId: String? = null): AppResult<Unit>
    fun refreshInstalledDistros()

    suspend fun execute(command: ShellCommand, distroId: String? = null): CommandResult
    suspend fun startSession(config: SessionConfig = SessionConfig(), distroId: String? = null): LinuxSession

    /**
     * Build host argv/env for an interactive console owned by Termux TerminalSession JNI,
     * without opening our NativePty. Used by the matrix terminal UI.
     */
    suspend fun buildInteractiveLaunch(
        config: SessionConfig = SessionConfig(),
        distroId: String? = null,
    ): InteractiveLaunchSpec = error("当前运行时不支持交互式终端启动规格")

    suspend fun startBackground(
        id: String,
        command: ShellCommand,
        toolId: String? = null,
        type: ProcessType = ProcessType.SERVICE,
        distroId: String? = null,
    ): ManagedProcess

    suspend fun stopBackground(id: String): Boolean
    fun listBackground(): List<ManagedProcess>
    suspend fun cleanupDeadBackground(): Int
    fun observeBackgroundLogs(idOrToolId: String): kotlinx.coroutines.flow.Flow<List<String>> = kotlinx.coroutines.flow.emptyFlow()
    fun getBackgroundLogs(idOrToolId: String): List<String> = emptyList()
    fun clearBackgroundLogs(idOrToolId: String) = Unit

    suspend fun shutdown()
    /** Execute file cleanup only while runtime launches and installation are excluded. */
    suspend fun withStorageCleanup(block: suspend () -> Unit) {
        error("当前运行时不支持安全存储清理")
    }
    fun rootfsPath(distroId: String? = null): File
    fun rootfsVersion(distroId: String? = null): String? = null
    fun workspacePath(): File
}
