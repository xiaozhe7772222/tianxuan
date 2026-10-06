package com.ai.assistance.showerclient

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Helper to manage the lifecycle of the Shower server (shower-server.jar) on the device.
 *
 * This implementation is host-agnostic: it relies only on [ShellRunner] and Binder
 * registration via [ShowerBinderRegistry]. The host app is responsible for:
 * - Providing a [ShellRunner] via [ShowerEnvironment.shellRunner]
 * - Packaging `shower-server.jar` into its assets
 */
object ShowerServerManager {

    private const val TAG = "ShowerServerManager"
    private const val ASSET_JAR_NAME = "shower-server.jar"
    private const val LOCAL_JAR_NAME = "shower-server.jar"

    @Volatile
    var additionalTargetPackages: Set<String> = emptySet()

    /**
     * Ensure the Shower server is started in the background.
     * Returns true if the start command was issued successfully and a Binder
     * was received within the timeout window.
     */
    suspend fun ensureServerStarted(context: Context): Boolean {
        // 0) If we already have an alive Binder from the handoff broadcast, just reuse it.
        if (ShowerBinderRegistry.hasAliveService()) {
            ShowerLog.d(TAG, "Shower Binder already cached and alive, skipping start")
            return true
        }

        val runner = ShowerEnvironment.shellRunner
        if (runner == null) {
            ShowerLog.e(TAG, "No ShellRunner configured in ShowerEnvironment; cannot start server")
            return false
        }

        val appContext = context.applicationContext
        val jarFile = try {
            copyJarToExternalDir(appContext)
        } catch (e: Exception) {
            ShowerLog.e(TAG, "Failed to copy shower-server.jar from assets", e)
            return false
        }

        // 1) Kill existing server (ignore errors about missing process).
        val killCmd = "pkill -f com.ai.assistance.shower.Main || true"
        ShowerLog.d(TAG, "Stopping existing Shower server (if any) with command: $killCmd")
        runner.run(killCmd, ShellIdentity.DEFAULT)

        // 2) 选一个「存在且可写」的工作目录。
        //    /data/local/tmp 在部分设备上缺失（见 ShowerEnvironment.workDirCandidates 注释），
        //    旧实现硬编码单目录，缺失时 cp 必败、虚拟屏完全不可用。
        val workDir = resolveWorkDir(runner)
        if (workDir == null) {
            ShowerLog.e(
                TAG,
                "无可用工作目录（已尝试 ${ShowerEnvironment.workDirCandidates}）：检查 Shizuku/Root 授权与 SELinux 限制"
            )
            return false
        }

        // 3) Remove any stale jar and log in the resolved work dir.
        val remoteJarPath = "$workDir/$LOCAL_JAR_NAME"
        val remoteLogPath = "$workDir/shower.log"
        val cleanupCmd = "rm -f $remoteJarPath $remoteLogPath || true"
        ShowerLog.d(TAG, "Cleaning up previous Shower jar and log with command: $cleanupCmd")
        val cleanupResult = runner.run(cleanupCmd, ShellIdentity.DEFAULT)
        if (!cleanupResult.success) {
            ShowerLog.w(
                TAG,
                "Cleanup of Shower jar/log may have failed (exitCode=${cleanupResult.exitCode}). stdout='${cleanupResult.stdout}', stderr='${cleanupResult.stderr}'"
            )
        }

        // 4) Copy the jar into the work dir using shell identity, so that the resulting
        //    file is owned by the shell user.
        val copyCmd = "cp ${jarFile.absolutePath} $remoteJarPath"
        ShowerLog.d(TAG, "Copying Shower jar with shell identity using command: $copyCmd")
        val copyResult = runner.run(copyCmd, ShellIdentity.SHELL)
        if (!copyResult.success) {
            ShowerLog.e(
                TAG,
                "Failed to copy Shower jar to $remoteJarPath (exitCode=${copyResult.exitCode}). stdout='${copyResult.stdout}', stderr='${copyResult.stderr}'"
            )
            return false
        }

        // 5) Start app_process with CLASSPATH pointing to the copied jar, in background.
        val targetPackagesArg = appContext.packageName
        val startCmd = "CLASSPATH=$remoteJarPath app_process / com.ai.assistance.shower.Main $targetPackagesArg &"
        ShowerLog.d(TAG, "Starting Shower server with command: $startCmd")
        val startResult = runner.run(startCmd, ShellIdentity.SHELL)
        if (!startResult.success) {
            ShowerLog.e(
                TAG,
                "Failed to start Shower server (exitCode=${startResult.exitCode}). stdout='${startResult.stdout}', stderr='${startResult.stderr}'"
            )
            return false
        }

        // 6) Poll for up to 10 seconds for the Binder handoff broadcast to be received and cached.
        for (attempt in 0 until 50) { // 50 * 200ms = 10s
            delay(200)
            if (ShowerBinderRegistry.hasAliveService()) {
                ShowerLog.d(
                    TAG,
                    "Shower Binder cached and alive after ~${(attempt + 1) * 200}ms"
                )
                return true
            }
        }

        ShowerLog.e(TAG, "Shower Binder was not received within the expected time")
        return false
    }

    /**
     * Stop the Shower server process if running.
     */
    suspend fun stopServer(): Boolean {
        val runner = ShowerEnvironment.shellRunner
        if (runner == null) {
            ShowerLog.e(TAG, "No ShellRunner configured in ShowerEnvironment; cannot stop server")
            return false
        }
        val cmd = "pkill -f com.ai.assistance.shower.Main || true"
        val result = runner.run(cmd, ShellIdentity.DEFAULT)
        if (!result.success) {
            ShowerLog.e(TAG, "Failed to stop Shower server: ${result.stderr}")
        }
        return result.success
    }

    /**
     * 按顺序探测第一个「存在且可写」的工作目录；均不可用时返回 null。
     *
     * 探测在 **shell 身份下**真实试写一个文件（而非只看 `ls`/`test -d`），
     * 因为目录存在不代表可写（SELinux 与 UNIX 权限都可能拦）。
     */
    private suspend fun resolveWorkDir(runner: ShellRunner): String? {
        for (dir in ShowerEnvironment.workDirCandidates) {
            // 先尝试确保目录存在（已是 shell 拥有的目录时 mkdir -p 为幂等空操作）。
            val probeFile = "$dir/.shower_probe"
            val cmd = "mkdir -p $dir 2>/dev/null; " +
                "if touch $probeFile 2>/dev/null; then rm -f $probeFile; echo $SHOWER_WORKDIR_PROBE_MARK; fi"
            val result = runner.run(cmd, ShellIdentity.SHELL)
            if (result.success && result.stdout.contains(SHOWER_WORKDIR_PROBE_MARK)) {
                ShowerLog.d(TAG, "resolveWorkDir: 选用 $dir")
                return dir
            }
            ShowerLog.w(
                TAG,
                "resolveWorkDir: $dir 不可写（exit=${result.exitCode}），尝试下一个候选"
            )
        }
        return null
    }

    /**
     * Copy shower-server.jar from assets to an external directory.
     * Host apps can override this behaviour by providing a different wrapper
     * around [ShellRunner] if needed.
     */
    private suspend fun copyJarToExternalDir(context: Context): File = withContext(Dispatchers.IO) {
        // TODO: 后续对接 RuntimePathManager 统一管理外部目录，避免硬编码。
        val baseDir = File("/sdcard/Download/TianXuan")
        // mkdirs() 返回 false 可能只是“已存在”，故以 exists() 为准；两者均不满足时
        // 提前给出可定位的原因，而不是让后续 FileOutputStream 报一个抽象错误。
        if (!baseDir.exists() && !baseDir.mkdirs() && !baseDir.exists()) {
            throw java.io.IOException("无法创建过渡目录：${baseDir.absolutePath}")
        }
        val outFile = File(baseDir, LOCAL_JAR_NAME)
        context.assets.open(ASSET_JAR_NAME).use { input ->
            FileOutputStream(outFile).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                }
                output.flush()
            }
        }
        ShowerLog.d(TAG, "Copied $ASSET_JAR_NAME to ${outFile.absolutePath}")
        outFile
    }
}
