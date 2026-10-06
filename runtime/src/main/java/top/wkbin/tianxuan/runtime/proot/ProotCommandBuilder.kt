package top.wkbin.tianxuan.runtime.proot

import top.wkbin.tianxuan.core.common.logging.AppLogger
import top.wkbin.tianxuan.core.model.StorageMountBinding
import top.wkbin.tianxuan.runtime.EnvironmentResolver
import top.wkbin.tianxuan.runtime.shell.ShellCommand
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class ProotCommandBuilder private constructor(
    private val environmentResolver: EnvironmentResolver,
    private val logWarning: (String) -> Unit,
) {

    constructor(
        environmentResolver: EnvironmentResolver,
        logger: AppLogger,
    ) : this(environmentResolver, logger::w)

    /** JVM tests do not need Android logging while validating pure argument construction. */
    internal constructor(environmentResolver: EnvironmentResolver) : this(environmentResolver, {})

    fun build(
        prootBinary: File,
        rootfsDir: File,
        workspaceDir: File,
        homeDir: File = File(rootfsDir.parentFile, "home"),
        optDir: File = File(rootfsDir.parentFile, "opt/tianxuan"),
        tmpDir: File = File(rootfsDir.parentFile, "tmp"),
        attachmentsDir: File = File(rootfsDir.parentFile, "attachments"),
        command: ShellCommand,
        mounts: List<StorageMountBinding> = emptyList(),
        emulatorBinary: File? = null,
    ): List<String> = buildList {
        add(prootBinary.absolutePath)
        addEmulator(emulatorBinary)
        add("--kill-on-exit")
        add("--link2symlink")
        add("-L")
        add("--sysvipc")
        add("--kernel-release=$GUEST_KERNEL_RELEASE")
        add("--change-id=0:0")
        add("-r")
        add(rootfsDir.absolutePath)
        addLink2SymlinkBackingStoreBinding(rootfsDir)
        add("-b")
        add("/dev")
        add("-b")
        add("/proc")
        add("-b")
        add("/sys")
        add("-b")
        add("${tmpDir.absolutePath}:/tmp")
        add("-b")
        add("${workspaceDir.absolutePath}:/workspace")
        add("-b")
        add("${homeDir.absolutePath}:/root")
        add("-b")
        add("${optDir.absolutePath}:/opt/tianxuan")
        attachmentsDir.mkdirs()
        add("-b")
        add("${attachmentsDir.absolutePath}:/attachments")
        addHostSystemBindings()
        addStorageMountBindings(mounts)
        add("-w")
        add(command.workingDirectory)
        add(GUEST_SHELL)
        add("-lc")
        val resolvedCommand = shellCommand(
            commandLine = command.commandLine,
            environment = environmentResolver.merge(provider = command.environment),
        )
        add(if (command.forcePty) wrapInPty(resolvedCommand) else resolvedCommand)
    }

    fun buildInteractive(
        prootBinary: File,
        rootfsDir: File,
        workspaceDir: File,
        homeDir: File = File(rootfsDir.parentFile, "home"),
        optDir: File = File(rootfsDir.parentFile, "opt/tianxuan"),
        tmpDir: File = File(rootfsDir.parentFile, "tmp"),
        attachmentsDir: File = File(rootfsDir.parentFile, "attachments"),
        config: top.wkbin.tianxuan.runtime.shell.SessionConfig,
        ptyMarker: String? = null,
        nativePty: Boolean = false,
        mounts: List<StorageMountBinding> = emptyList(),
        emulatorBinary: File? = null,
    ): List<String> = buildList {
        val columns = config.columns.coerceIn(20, 400)
        val rows = config.rows.coerceIn(5, 200)
        add(prootBinary.absolutePath)
        addEmulator(emulatorBinary)
        add("--kill-on-exit")
        add("--link2symlink")
        add("-L")
        add("--sysvipc")
        add("--kernel-release=$GUEST_KERNEL_RELEASE")
        add("--change-id=0:0")
        add("-r")
        add(rootfsDir.absolutePath)
        addLink2SymlinkBackingStoreBinding(rootfsDir)
        add("-b")
        add("/dev")
        add("-b")
        add("/proc")
        add("-b")
        add("/sys")
        add("-b")
        add("${tmpDir.absolutePath}:/tmp")
        add("-b")
        add("${workspaceDir.absolutePath}:/workspace")
        add("-b")
        add("${homeDir.absolutePath}:/root")
        add("-b")
        add("${optDir.absolutePath}:/opt/tianxuan")
        attachmentsDir.mkdirs()
        add("-b")
        add("${attachmentsDir.absolutePath}:/attachments")
        addHostSystemBindings()
        addStorageMountBindings(mounts)
        add("-w")
        add(config.workingDirectory)
        add(GUEST_SHELL)
        add("-lc")
        val environment = environmentResolver.merge(
            provider = config.environment,
            interactive = true,
        )
        val bannerPrelude = if (config.showBanner) "cat $GUEST_MOTD 2>/dev/null; " else ""
        if (nativePty) {
            // 真 PTY：命令串直接交给 bash 解析。commandLine 已由调用方完成 shell 引用
            // （如 MCP STDIO 的单引号包裹），此处严禁再做引号替换——单层 bash 下
            // '\'' 类转义会把命令名变成带引号的字面量（exec: "'python3'": not found）。
            add(shellCommand(commandLine = bannerPrelude + config.commandLine, environment = environment))
        } else {
            val marker = ptyMarker?.also {
                require(PTY_MARKER.matches(it)) { "PTY marker path is invalid" }
            }
            val markerPrelude = marker?.let { "tty > $it; " }.orEmpty()
            // fallback：整条命令会被塞进 script -qfec '...' 的单引号里，再由 script 内层
            // shell 解析，需要 '\'' 双层转义。
            val scriptEmbed = config.commandLine.replace("'", "'\\''")
            add(
                shellCommand(
                    commandLine =
                        bannerPrelude +
                            "if command -v script >/dev/null 2>&1; then " +
                            "exec script -qfec '$markerPrelude stty cols $columns rows $rows; " +
                            "$scriptEmbed' /dev/null; " +
                            "else ${config.commandLine}; fi",
                    environment = environment,
                ),
            )
        }
    }

    /**
     * Wrap a long-running build command in a Debian `script` PTY. Java/Gradle fully buffer
     * stdout when it is a pipe (non-TTY), so the app stops receiving logs mid-build.
     * A real TTY makes the child line-buffer and flush, streaming progress to the UI.
     */
    private fun wrapInPty(commandLine: String): String =
        "if command -v script >/dev/null 2>&1; then " +
            "exec script -qfec " + shellQuote(commandLine) + " /dev/null; " +
            "else $commandLine; fi"

    private fun shellCommand(
        commandLine: String,
        environment: Map<String, String>,
    ): String {
        val exports = environment.entries.joinToString("; ") { (key, value) ->
            require(ENVIRONMENT_KEY.matches(key)) { "Invalid environment variable name: $key" }
            "export $key=${shellQuote(value)}"
        }
        return if (exports.isBlank()) commandLine else "$exports; $commandLine"
    }

    private fun shellQuote(value: String): String =
        "'${value.replace("'", "'\\\''")}'"

    /** Add PRoot QEMU user-mode emulation for a dedicated x86_64 guest only. */
    private fun MutableList<String>.addEmulator(emulatorBinary: File?) {
        if (emulatorBinary == null) return
        require(emulatorBinary.isFile && emulatorBinary.canExecute()) {
            "QEMU emulator 不可执行：${emulatorBinary.absolutePath}"
        }
        add("-q")
        add(emulatorBinary.absolutePath)
    }

    /**
     * The Termux PRoot build stores emulated hard-link payloads under the host-side
     * [RuntimePathManager] `PROOT_L2S_DIR`. Link proxies contain that absolute host
     * path. Expose the same path inside the guest so dpkg can lchown/lstat a newly
     * unpacked hard link instead of following a dangling proxy and reporting ENOENT.
     */
    private fun MutableList<String>.addLink2SymlinkBackingStoreBinding(rootfsDir: File) {
        val backingStore = File(rootfsDir, LINK2SYMLINK_DIRECTORY).absolutePath
        add("-b")
        add("$backingStore:$backingStore")
    }

    /** Android host paths used by the PRoot tracer and Android linker. */
    private fun MutableList<String>.addHostSystemBindings() {
        val skipped = mutableListOf<String>()
        ProotMountLayout.hostSystemPaths.forEach { path ->
            val hostPath = File(path)
            if (hostPath.exists() && hostPath.canRead()) {
                add("-b")
                add(path)
            } else {
                skipped.add(path)
            }
        }
        // 缺失绑定会导致沙箱内 Android 二进制无法执行；同一进程只告警一次，避免刷屏。
        if (skipped.isNotEmpty() && hostBindingsWarningLogged.compareAndSet(false, true)) {
            logWarning("HostSystemBindings: ${skipped.size} path(s) skipped (not exist/unreadable): $skipped")
        }
    }

    /** 宿主外部存储映射绑定 (如 /storage/emulated/0/Download -> /sdcard/Download) */
    private fun MutableList<String>.addStorageMountBindings(
        mounts: List<StorageMountBinding>,
    ) {
        if (mounts.isNotEmpty()) {
            mounts.filter { it.enabled }.forEach { binding ->
                val argument = validateStorageMount(binding) ?: return@forEach
                add("-b")
                add(argument)
            }
        }
    }

    private fun validateStorageMount(
        binding: StorageMountBinding,
    ): String? {
        // 规则统一收敛在 StorageMountBinding.validationError，与运行时过滤、设置页校验共用。
        // 此处 require 是防挂载逃逸的最后安全防线：即使 DB 被篡改也不允许越界绑定进入 PRoot。
        val error = StorageMountBinding.validationError(binding)
        require(error == null) { error ?: "存储挂载绑定不合法" }

        val guest = checkNotNull(StorageMountBinding.normalizeGuestPath(binding.guestPath))
        val host = File(binding.hostPath).canonicalFile
        if (!host.isDirectory || !host.canRead()) {
            logWarning("宿主挂载目录不可访问（不存在/非目录/不可读），已跳过绑定：${binding.hostPath}")
            return null
        }
        return "${host.absolutePath}:$guest"
    }

    private companion object {
        const val GUEST_SHELL = "/bin/sh"
        const val GUEST_KERNEL_RELEASE = "6.17.0-TianXuan"
        const val GUEST_MOTD = "/opt/tianxuan/motd"
        const val LINK2SYMLINK_DIRECTORY = ".l2s"
        val PTY_MARKER = Regex("/opt/tianxuan/\\.pty-[A-Za-z0-9-]{8,64}")
        val ENVIRONMENT_KEY = Regex("[A-Za-z_][A-Za-z0-9_]*")
        private val hostBindingsWarningLogged = AtomicBoolean(false)
    }
}
