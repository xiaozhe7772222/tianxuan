package top.wkbin.tianxuan.runtime

import java.io.File
import top.wkbin.tianxuan.core.common.logging.AppLogger
import top.wkbin.tianxuan.runtime.bridge.HostBridge
import top.wkbin.tianxuan.runtime.scripts.RuntimeAssetSynchronizer

/**
 * 发行版配置器：安装 / 导入 / 重置 / 更新 RootFS 后的沙箱内配置落盘。
 *
 * 覆盖 apt/pip 国内镜像（TUNA）、dpkg 免文档与 statoverride、setuid 剥离
 * （PRoot 下 setuid 不生效，保留会导致 dpkg 升级卡死）、perl 硬链接修复脚本、
 * DNS、环境变量、HostBridge 沙箱端脚本与密钥。
 *
 * 从 LinuxRuntimeImpl 拆出（原 configureRootfs 系列），逻辑逐字保留；
 * 唯一行为变化：[configureRootfs] 由同步 runBlocking 改为 suspend，
 * 不再阻塞调用线程且可被协作取消。
 */
class DistroConfigurator(
    private val pathManager: RuntimePathManager,
    private val hostBridge: HostBridge,
    private val assetSynchronizer: RuntimeAssetSynchronizer,
    private val logger: AppLogger,
) {

    /** RootFS 就位后的完整配置流水线（安装 / 导入 / 重置 / 更新共用）。 */
    suspend fun configureRootfs(distroId: String = "ubuntu") {
        val rootfs = pathManager.rootfsDir(distroId)
        val etcDir = File(rootfs, "etc")
        etcDir.mkdirs()
        File(etcDir, "tianxuan-runtime").writeText("tianxuan-runtime=0.3.0\n")
        File(rootfs, "opt/tianxuan").mkdirs()
        pathManager.ensureDistroDirectories(distroId)
        stripSetuidBits(distroId)
        configureDpkgStatoverride(distroId)
        configureDpkgNoDoc(distroId)
        configureAptSettings(distroId)
        configureChinaMirrors(distroId)
        configurePipMirror(distroId)
        installAptStripSetuidHook(distroId)
        installPerlFixScript(distroId)
        runCatching {
            assetSynchronizer.syncAssetsToDistro(distroId)
        }
    }

    /**
     * 恢复已安装环境时的幂等资产同步：APK 升级可能新增或替换内置 MCP/构建脚本，
     * 已有 rootfs 不会再走 [configureRootfs]，恢复时必须同步一次避免沙箱内文件是旧版本。
     */
    suspend fun syncAssets(distroId: String) {
        assetSynchronizer.syncAssetsToDistro(distroId)
    }

    /**
     * 在 PRoot 沙箱中排除文档/手册页/区域语言包解包：
     * 1. 降低 50%~60% 的磁盘 I/O 和解包时间，极大避免 300s 安装超时；
     * 2. 规避 man 手册硬链接（如 perlthanks.1.gz 等）在 PRoot 下的 chown 报错。
     */
    private fun configureDpkgNoDoc(distroId: String = "ubuntu") {
        val dpkgCfgDir = File(pathManager.rootfsDir(distroId), "etc/dpkg/dpkg.cfg.d")
        dpkgCfgDir.mkdirs()
        File(dpkgCfgDir, "01_tianxuan_nodoc").writeText(
            """
            # PRoot 性能与稳定性优化：跳过文档与手册文件以减少 I/O 并避免硬链接解包异常
            path-exclude /usr/share/doc/*
            path-exclude /usr/share/man/*
            path-exclude /usr/share/groff/*
            path-exclude /usr/share/info/*
            path-exclude /usr/share/locale/*
            path-exclude /usr/share/lintian/*
            path-exclude /usr/share/linda/*
            """.trimIndent() + "\n",
        )
    }

    /**
     * 配置 apt 超时重试以及非交互默认选项，避免后台安装任务被挂起。
     *
     * 额外加固（来自真机用户网络环境实测）：
     * - ForceIPv4：手机 IPv6 半残（能配 v6 路由但出不去）是 apt 卡慢/探测假死的头号元凶；
     * - Languages=en：跳过约 24MB 的 Translation 翻译文件下载，显著加速 apt update。
     */
    private fun configureAptSettings(distroId: String = "ubuntu") {
        val hookDir = File(pathManager.rootfsDir(distroId), "etc/apt/apt.conf.d")
        hookDir.mkdirs()
        File(hookDir, "99tianxuan-apt-config").writeText(
            """
            // 提高网络波动环境下的安装鲁棒性并默认使用非交互配置
            Acquire::Retries "3";
            Acquire::http::Timeout "60";
            Acquire::https::Timeout "60";
            // 手机网络 IPv6 半残时 apt 会长时间假死，强制 IPv4
            Acquire::ForceIPv4 "true";
            // 跳过 Translation 翻译文件下载（省约 24MB / 次 apt update）
            Acquire::Languages "en";
            DPkg::Options {
               "--force-confdef";
               "--force-confold";
            };
            """.trimIndent() + "\n",
        )
    }

    /**
     * 将沙箱内软件源切换到清华大学 TUNA 镜像站，加速国内 apt / pip 安装。
     * 仅对 apt 系发行版（debian / ubuntu / kali）生效；其余发行版保持官方源。
     */
    fun configureChinaMirrors(distroId: String = "ubuntu") {
        val osRelease = readOsRelease(distroId)
        if (osRelease.isEmpty()) {
            logger.i("China mirrors skipped: os-release not found for $distroId")
            return
        }
        val id = osRelease["ID"] ?: osRelease["ID_LIKE"]
        val codename = osRelease["VERSION_CODENAME"]
        val sources = when (id) {
            "debian" -> codename?.let(::tunaDebianSources)
            "ubuntu" -> codename?.let(::tunaUbuntuSources)
            "kali" -> tunaKaliSources()
            else -> null
        }
        if (sources == null) {
            logger.i("China mirrors skipped for distro id=$id codename=$codename")
            return
        }
        val sourcesListDir = File(pathManager.rootfsDir(distroId), "etc/apt/sources.list.d")
        sourcesListDir.mkdirs()
        disableStockAptSources(sourcesListDir, distroId)
        File(sourcesListDir, "tianxuan-mirrors.list").writeText(sources + "\n")
        logger.i("China mirrors applied: TUNA apt sources for $id ($codename) in $distroId")
    }

    private fun disableStockAptSources(sourcesListDir: File, distroId: String = "ubuntu") {
        File(pathManager.rootfsDir(distroId), "etc/apt/sources.list").takeIf { it.isFile }
            ?.let { stock -> renameToDisabled(stock) }
        sourcesListDir.listFiles()?.forEach { entry ->
            val name = entry.name
            if (entry.isFile && (name.endsWith(".list") || name.endsWith(".sources")) &&
                name != "tianxuan-mirrors.list"
            ) {
                renameToDisabled(entry)
            }
        }
    }

    private fun renameToDisabled(file: File) {
        val disabled = File(file.parentFile, "${file.name}.tianxuan-disabled")
        if (!file.renameTo(disabled)) {
            logger.w("Failed to disable stock apt source: ${file.path}")
        }
    }

    private fun tunaDebianSources(codename: String): String = """
        # TianXuan: 清华大学 TUNA 镜像站（由官方源自动切换）
        deb https://mirrors.tuna.tsinghua.edu.cn/debian $codename main contrib non-free non-free-firmware
        deb https://mirrors.tuna.tsinghua.edu.cn/debian $codename-updates main contrib non-free non-free-firmware
        deb https://mirrors.tuna.tsinghua.edu.cn/debian-security $codename-security main contrib non-free non-free-firmware
    """.trimIndent()

    private fun tunaUbuntuSources(codename: String): String = """
        # TianXuan: 清华大学 TUNA 镜像站（由官方源自动切换）
        deb https://mirrors.tuna.tsinghua.edu.cn/ubuntu-ports $codename main restricted universe multiverse
        deb https://mirrors.tuna.tsinghua.edu.cn/ubuntu-ports $codename-updates main restricted universe multiverse
        deb https://mirrors.tuna.tsinghua.edu.cn/ubuntu-ports $codename-security main restricted universe multiverse
        deb https://mirrors.tuna.tsinghua.edu.cn/ubuntu-ports $codename-backports main restricted universe multiverse
    """.trimIndent()

    private fun tunaKaliSources(): String = """
        # TianXuan: 清华大学 TUNA 镜像站（由官方源自动切换）
        deb https://mirrors.tuna.tsinghua.edu.cn/kali kali-rolling main contrib non-free
    """.trimIndent()

    private fun configurePipMirror(distroId: String = "ubuntu") {
        val config = File(pathManager.rootfsDir(distroId), "etc/pip.conf")
        config.parentFile?.mkdirs()
        config.writeText(
            """
            # TianXuan: 清华大学 TUNA PyPI 镜像
            [global]
            index-url = https://pypi.tuna.tsinghua.edu.cn/simple
            """.trimIndent() + "\n",
        )
    }

    private fun readOsRelease(distroId: String = "ubuntu"): Map<String, String> {
        val rootfs = pathManager.rootfsDir(distroId)
        val file = File(rootfs, "etc/os-release")
            .takeIf { it.isFile }
            ?: File(rootfs, "usr/lib/os-release").takeIf { it.isFile }
            ?: return emptyMap()
        return runCatching {
            file.readLines().mapNotNull { line ->
                val index = line.indexOf('=')
                if (index <= 0) return@mapNotNull null
                val key = line.substring(0, index).trim()
                val value = line.substring(index + 1).trim().trim('"', '\'')
                key to value
            }.toMap()
        }.onFailure { logger.w("Failed to parse os-release for $distroId", it) }
            .getOrDefault(emptyMap())
    }

    private fun installPerlFixScript(distroId: String = "ubuntu") {
        val binDir = File(pathManager.rootfsDir(distroId), "usr/local/sbin")
        binDir.mkdirs()
        val script = File(binDir, "tianxuan-fix-perl")
        script.writeText(
            """
            #!/bin/sh
            set -e
            echo "[TianXuan] Scanning for perl deb packages to patch hardlinks..."
            TMPDIR="${'$'}(mktemp -d /tmp/tianxuan-perl-fix.XXXXXX)"
            trap 'rm -rf "${'$'}TMPDIR"' EXIT INT TERM

            # 优先从 apt 缓存寻找 perl deb 包，若无则尝试下载
            PERL_DEB="${'$'}(ls -1 /var/cache/apt/archives/perl_*.deb 2>/dev/null | head -n 1 || true)"
            if [ -z "${'$'}PERL_DEB" ]; then
                echo "[TianXuan] Downloading perl package..."
                cd "${'$'}TMPDIR" && apt-get download perl || true
                PERL_DEB="${'$'}(ls -1 "${'$'}TMPDIR"/perl_*.deb 2>/dev/null | head -n 1 || true)"
            fi

            if [ -z "${'$'}PERL_DEB" ] || [ ! -f "${'$'}PERL_DEB" ]; then
                echo "[TianXuan] Perl deb package not found, attempting apt --fix-broken install..."
                apt-get --fix-broken install -y || true
                exit 0
            fi

            echo "[TianXuan] Patching ${'$'}PERL_DEB..."
            WORKDIR="${'$'}TMPDIR/repack"
            mkdir -p "${'$'}WORKDIR/DEBIAN"
            dpkg-deb -e "${'$'}PERL_DEB" "${'$'}WORKDIR/DEBIAN"
            dpkg-deb --fsys-tarfile "${'$'}PERL_DEB" | tar -C "${'$'}WORKDIR" -xf -

            # 将 usr/bin/perlthanks 等硬链接转换为符号链接
            if [ -f "${'$'}WORKDIR/usr/bin/perlthanks" ]; then
                rm -f "${'$'}WORKDIR/usr/bin/perlthanks"
                ln -s perlbug "${'$'}WORKDIR/usr/bin/perlthanks"
                echo "[TianXuan] Converted /usr/bin/perlthanks to symlink"
            fi

            dpkg-deb -b "${'$'}WORKDIR" "${'$'}TMPDIR/perl-patched.deb"
            dpkg -i --force-overwrite "${'$'}TMPDIR/perl-patched.deb"
            echo "[TianXuan] Perl patch installed successfully."
            """.trimIndent() + "\n",
        )
        runCatching {
            android.system.Os.chmod(script.absolutePath, 0x1ED) // 0755
        }
    }

    private fun stripSetuidBits(distroId: String = "ubuntu") {
        var stripped = 0
        var failed = 0
        pathManager.rootfsDir(distroId).walkTopDown()
            .forEach { file ->
                if (file.name.endsWith(".dpkg-tmp")) {
                    file.delete()
                } else if (file.isFile) {
                    runCatching {
                        val mode = android.system.Os.lstat(file.absolutePath).st_mode
                        if (mode and 0xC00 != 0) {
                            android.system.Os.chmod(file.absolutePath, mode and 0x3FF)
                            stripped++
                        }
                    }.onFailure { failed++ }
                }
            }
        logger.i("stripSetuidBits ($distroId): cleared $stripped setuid/setgid bits, $failed failures")
    }

    private fun configureDpkgStatoverride(distroId: String = "ubuntu") {
        val dpkgDir = File(pathManager.rootfsDir(distroId), "var/lib/dpkg")
        dpkgDir.mkdirs()
        val statoverrideFile = File(dpkgDir, "statoverride")
        val overrides = listOf(
            "/usr/bin/su",
            "/usr/bin/mount",
            "/usr/bin/umount",
            "/usr/bin/newgrp",
            "/usr/bin/gpasswd",
            "/usr/bin/passwd",
            "/usr/bin/chfn",
            "/usr/bin/chsh",
            "/usr/bin/expiry",
        )
        val existingLines = if (statoverrideFile.exists()) {
            statoverrideFile.readLines().map { it.trim() }.filter { it.isNotEmpty() }.toMutableSet()
        } else {
            mutableSetOf()
        }
        overrides.forEach { path ->
            val entry = "root root 0755 $path"
            val pathSuffix = " $path"
            if (existingLines.none { it.endsWith(pathSuffix) }) {
                existingLines.add(entry)
            }
        }
        statoverrideFile.writeText(existingLines.sorted().joinToString("\n", postfix = "\n"))
    }

    private fun installAptStripSetuidHook(distroId: String = "ubuntu") {
        val hookDir = File(pathManager.rootfsDir(distroId), "etc/apt/apt.conf.d")
        hookDir.mkdirs()
        val findCommand = "find /usr /bin /sbin -xdev -type f -perm /6000 -exec chmod ug-s {} +"
        val cleanupTmp = "rm -f /bin/*.dpkg-tmp /usr/bin/*.dpkg-tmp /usr/sbin/*.dpkg-tmp /sbin/*.dpkg-tmp 2>/dev/null || true"
        File(hookDir, "99tianxuan-strip-setuid").writeText(
            "// PRoot 沙箱：setuid 不生效，保留会导致 dpkg 升级卡死（见 tianxuan-runtime 文档）。\n" +
                "DPkg::Pre-Invoke { \"$cleanupTmp\"; };\n" +
                "DPkg::Post-Invoke { \"$findCommand\"; };\n",
        )
    }

    fun configureDns(distroId: String = "ubuntu") {
        val etcDir = File(pathManager.rootfsDir(distroId), "etc")
        etcDir.mkdirs()
        val resolvConf = File(etcDir, "resolv.conf")
        // Ubuntu/Debian OCI 镜像里 /etc/resolv.conf 通常是指向 /run/systemd/resolve/... 的符号链接，
        // PRoot 沙箱无 systemd 运行导致链接悬空，直接写入会抛 FileNotFoundException: ENOENT，
        // 必须先移除该链接（普通文件/悬空链接两种情况都要处理）再写入。
        try {
            val resolvPath = resolvConf.toPath()
            if (java.nio.file.Files.isSymbolicLink(resolvPath) || resolvConf.exists()) {
                resolvConf.delete()
            }
        } catch (_: Exception) {
            // 删除失败不阻塞后续写入尝试（writeText 会给出最终错误）
        }
        resolvConf.writeText("nameserver 1.1.1.1\nnameserver 8.8.8.8\n")
    }

    fun configureEnvironment(distroId: String = "ubuntu") {
        val profileDir = File(pathManager.rootfsDir(distroId), "etc/profile.d")
        profileDir.mkdirs()
        File(profileDir, "tianxuan-env.sh").writeText(
            """
            export LANG=C.UTF-8
            export PATH=/root/.local/bin:/opt/tianxuan/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
            export HOME=/root
            # HostBridge — 沙箱通过 localhost HTTP 桥接触发宿主侧操作
            export TIANXUAN_BRIDGE_URL="http://127.0.0.1:7980"
            export TIANXUAN_BRIDGE_PORT=7980
            # Android 二进制参考路径（用 tianxuan-android-exec 包装器执行）
            export ANDROID_BIN_PATH="/system/bin:/system/xbin"
            export ANDROID_LIB_PATH="/system/lib64:/system/lib:/vendor/lib64:/vendor/lib"
            """.trimIndent() + "\n",
        )
        val home = pathManager.homeDir(distroId)
        home.mkdirs()
        File(home, ".gitconfig").writeText(
            """
            [safe]
            	directory = *
            [http]
            	version = HTTP/1.1
            """.trimIndent() + "\n",
        )
        // 安装 HostBridge 沙箱脚本与密钥
        installHostBridgeScripts(distroId)
    }

    /**
     * 安装宿主桥接沙箱端脚本与 API 密钥。
     *
     * 写入三个文件到 /opt/tianxuan/（bind-mounted 到沙箱）：
     * - bin/tianxuan-host        — 桥接 CLI（install-apk / shell / health）
     * - bin/tianxuan-android-exec — Android 二进制执行包装器（设置正确的 linker 环境）
     * - .bridge-key           — API 认证密钥
     */
    private fun installHostBridgeScripts(distroId: String) {
        val binDir = pathManager.tianxuanBinDir(distroId)
        binDir.mkdirs()
        val rootDir = pathManager.tianxuanRootDir(distroId)

        // tianxuan-host — 宿主桥接 CLI
        val hostScript = File(binDir, "tianxuan-host")
        hostScript.writeText(TIANXUAN_HOST_SCRIPT)
        runCatching { android.system.Os.chmod(hostScript.absolutePath, 0x1ED) } // 0755

        // tianxuan-android-exec — Android 二进制执行包装器
        val androidExecScript = File(binDir, "tianxuan-android-exec")
        androidExecScript.writeText(TIANXUAN_ANDROID_EXEC_SCRIPT)
        runCatching { android.system.Os.chmod(androidExecScript.absolutePath, 0x1ED) }

        // API 密钥 — 每次配置时刷新（确保 app 重启后密钥同步）
        File(rootDir, ".bridge-key").writeText(hostBridge.bridgeKey)

        logger.i("HostBridge scripts installed for distro: $distroId")
    }

    companion object {
        val TIANXUAN_HOST_SCRIPT = listOf(
            "#!/bin/sh",
            "# TianXuan Host Bridge CLI",
            "# Usage: tianxuan-host install-apk <path> | tianxuan-host shell <cmd> | tianxuan-host health",
            "",
            "BRIDGE_URL=\"http://127.0.0.1:7980\"",
            "KEY_FILE=\"/opt/tianxuan/.bridge-key\"",
            "",
            "if [ -f \"\${KEY_FILE}\" ]; then",
            "  BRIDGE_KEY=\$(cat \"\${KEY_FILE}\" 2>/dev/null | tr -d '[:space:]')",
            "else",
            "  BRIDGE_KEY=\"\"",
            "fi",
            "",
            "if ! command -v curl >/dev/null 2>&1; then",
            "  echo '{\"success\":false,\"error\":\"curl not installed\"}' >&2; exit 1",
            "fi",
            "",
            "AUTH=\"\"",
            "if [ -n \"\${BRIDGE_KEY}\" ]; then AUTH=\"Authorization: Bearer \${BRIDGE_KEY}\"; fi",
            "",
            "case \"\$1\" in",
            "  install-apk)",
            "    [ -z \"\$2\" ] && { echo 'Usage: tianxuan-host install-apk <path>' >&2; exit 1; }",
            "    TARGET=\"\$2\"",
            "    if [ ! -f \"\${TARGET}\" ]; then",
            "      STRIPPED=\"\${TARGET#/}\"",
            "      if [ -f \"\${STRIPPED}\" ]; then",
            "        TARGET=\"\${STRIPPED}\"",
            "      elif [ -f \"./\$STRIPPED\" ]; then",
            "        TARGET=\"./\$STRIPPED\"",
            "      elif [ -f \"build/\$STRIPPED\" ]; then",
            "        TARGET=\"build/\$STRIPPED\"",
            "      else",
            "        BN=\"\$(basename \"\$TARGET\")\"",
            "        FOUND=\"\$(find . -maxdepth 6 -name \"\$BN\" -type f 2>/dev/null | head -n 1)\"",
            "        if [ -z \"\$FOUND\" ] && [ -d /workspace ]; then",
            "          FOUND=\"\$(find /workspace -maxdepth 7 -name \"\$BN\" -type f 2>/dev/null | head -n 1)\"",
            "        fi",
            "        if [ -z \"\$FOUND\" ]; then",
            "          FOUND=\"\$(find . -maxdepth 6 -name \"*.apk\" -type f 2>/dev/null | head -n 1)\"",
            "        fi",
            "        if [ -n \"\$FOUND\" ] && [ -f \"\$FOUND\" ]; then",
            "          TARGET=\"\$FOUND\"",
            "        fi",
            "      fi",
            "    fi",
            "    [ ! -f \"\${TARGET}\" ] && { echo \"{\\\"success\\\":false,\\\"error\\\":\\\"Not found: \$2\\\"}\" >&2; exit 1; }",
            "    if command -v realpath >/dev/null 2>&1; then",
            "      ABS_TARGET=\"\$(realpath \"\$TARGET\")\"",
            "    elif command -v readlink >/dev/null 2>&1; then",
            "      ABS_TARGET=\"\$(readlink -f \"\$TARGET\" 2>/dev/null || echo \"\$TARGET\")\"",
            "    else",
            "      ABS_TARGET=\"\$TARGET\"",
            "    fi",
            "    if [ -n \"\${AUTH}\" ]; then",
            "      curl -s -X POST \"\${BRIDGE_URL}/api/install-apk\" -H 'Content-Type: application/json' -H \"\${AUTH}\" -d \"{\\\"path\\\":\\\"\${ABS_TARGET}\\\"}\"",
            "    else",
            "      curl -s -X POST \"\${BRIDGE_URL}/api/install-apk\" -H 'Content-Type: application/json' -d \"{\\\"path\\\":\\\"\${ABS_TARGET}\\\"}\"",
            "    fi",
            "    echo \"\"",
            "    ;;",
            "  shell)",
            "    [ -z \"\$2\" ] && { echo 'Usage: tianxuan-host shell <command>' >&2; exit 1; }",
            "    shift; CMD=\"\$*\"",
            "    if command -v jq >/dev/null 2>&1; then",
            "      BODY=\$(jq -nc --arg c \"\${CMD}\" '{command:\$c}')",
            "    else",
            "      ESC=\$(printf '%s' \"\${CMD}\" | sed 's/\\\\/\\\\\\\\/g; s/\"/\\\\\"/g' | tr '\\n' ' ')",
            "      BODY=\"{\\\"command\\\":\\\"\${ESC}\\\"}\"",
            "    fi",
            "    if [ -n \"\${AUTH}\" ]; then",
            "      curl -s -X POST \"\${BRIDGE_URL}/api/shell\" -H 'Content-Type: application/json' -H \"\${AUTH}\" -d \"\${BODY}\"",
            "    else",
            "      curl -s -X POST \"\${BRIDGE_URL}/api/shell\" -H 'Content-Type: application/json' -d \"\${BODY}\"",
            "    fi",
            "    echo \"\"",
            "    ;;",
            "  logcat)",
            "    shift",
            "    PKG=\"\${1:-}\"",
            "    TAG=\"\${2:-}\"",
            "    PRIO=\"\${3:-V}\"",
            "    KW=\"\${4:-}\"",
            "    LINES=\"\${5:-200}\"",
            "    if command -v jq >/dev/null 2>&1; then",
            "      BODY=\$(jq -nc --arg p \"\$PKG\" --arg t \"\$TAG\" --arg pr \"\$PRIO\" --arg k \"\$KW\" --arg l \"\$LINES\" '{package:\$p, tag:\$t, priority:\$pr, keyword:\$k, lines:(\$l|tonumber)}')",
            "    else",
            "      BODY=\"{\\\"package\\\":\\\"\$PKG\\\",\\\"tag\\\":\\\"\$TAG\\\",\\\"priority\\\":\\\"\$PRIO\\\",\\\"keyword\\\":\\\"\$KW\\\",\\\"lines\\\":\$LINES}\"",
            "    fi",
            "    if [ -n \"\${AUTH}\" ]; then",
            "      curl -s -X POST \"\${BRIDGE_URL}/api/logcat\" -H 'Content-Type: application/json' -H \"\${AUTH}\" -d \"\${BODY}\"",
            "    else",
            "      curl -s -X POST \"\${BRIDGE_URL}/api/logcat\" -H 'Content-Type: application/json' -d \"\${BODY}\"",
            "    fi",
            "    echo \"\"",
            "    ;;",
            "  health|status)",
            "    if [ -n \"\${AUTH}\" ]; then curl -s \"\${BRIDGE_URL}/api/health\" -H \"\${AUTH}\"; else curl -s \"\${BRIDGE_URL}/api/health\"; fi",
            "    echo \"\"",
            "    ;;",
            "  *)",
            "    echo \"TianXuan Host Bridge CLI\"",
            "    echo \"  tianxuan-host install-apk <path>   Install APK on host (wireless ADB or system dialog)\"",
            "    echo \"  tianxuan-host shell <command>      Run host shell (wireless ADB or Shizuku/root)\"",
            "    echo \"  tianxuan-host logcat [pkg] [tag] [prio] [kw] [lines] Capture logcat via wireless ADB\"",
            "    echo \"  tianxuan-host health               Bridge health and ADB status\"",
            "    exit 1;;",
            "esac",
        ).joinToString("\n") + "\n"

        val TIANXUAN_ANDROID_EXEC_SCRIPT = listOf(
            "#!/bin/sh",
            "# TianXuan Android Binary Executor",
            "# Sets up correct linker env for Android system binaries in PRoot sandbox.",
            "# Usage: tianxuan-android-exec /system/bin/settings put global captive_portal_http_url ''",
            "",
            "[ -z \"\$1\" ] && { echo 'Usage: tianxuan-android-exec <binary> [args...]' >&2; exit 1; }",
            "export LD_LIBRARY_PATH=/system/lib64:/system/lib:/vendor/lib64:/vendor/lib",
            "export ANDROID_DATA=/data",
            "export ANDROID_ROOT=/system",
            "unset LD_PRELOAD",
            "exec \"\$@\"",
        ).joinToString("\n") + "\n"
    }
}
