package top.wkbin.tianxuan.runtime.proot

import top.wkbin.tianxuan.core.model.StorageMountBinding
import top.wkbin.tianxuan.runtime.shell.ShellCommand
import java.io.File

/**
 * ROOT 模式下的 chroot 命令构建器：以 su 身份 mount --bind 关键虚拟文件系统后
 * chroot 进 rootfs 执行命令，替代 PRoot 的用户态 syscall 拦截。
 *
 * 与 [ProotCommandBuilder] 的对比：
 * - PRoot：用户态 ptrace 拦截每个 syscall → 有性能开销，硬链接靠 link2symlink 模拟
 * - chroot：内核级 mount namespace 切换 → 零拦截开销，原生 ext4 硬链接语义
 *
 * 仅在 ROOT 模式（effectiveMode == ROOT）下使用；SHIZUKU/PROOT 仍走 PRoot。
 */
class ChrootCommandBuilder {

    /**
     * 构建 chroot 执行命令（通过 su -c 以 root 身份执行）。
     *
     * @param rootfsDir rootfs 根目录（已安装的发行版）
     * @param command 要在 chroot 内执行的 ShellCommand
     * @param workspaceDir 宿主工作区目录（bind 到 /workspace）
     * @param homeDir 宿主 home 目录（bind 到 /root）
     * @param optDir 宿主 opt 目录（bind 到 /opt/tianxuan）
     * @param tmpDir 宿主 tmp 目录（bind 到 /tmp）
     * @param attachmentsDir 宿主附件目录（bind 到 /attachments）
     * @param mounts 额外的存储挂载绑定
     * @return 命令参数列表（首个元素为 "su"，第二个为 "-c"，第三个为完整 shell 脚本）
     */
    fun build(
        rootfsDir: File,
        command: ShellCommand,
        workspaceDir: File,
        homeDir: File = File(rootfsDir.parentFile, "home"),
        optDir: File = File(rootfsDir.parentFile, "opt/tianxuan"),
        tmpDir: File = File(rootfsDir.parentFile, "tmp"),
        attachmentsDir: File = File(rootfsDir.parentFile, "attachments"),
        mounts: List<StorageMountBinding> = emptyList(),
    ): List<String> {
        val rootfs = rootfsDir.absolutePath
        val script = buildString {
            // 1. 确保关键虚拟文件系统已 bind mount 到 rootfs（幂等——已挂载时 mount --bind 返回 0）
            append("mount --bind /proc '$rootfs/proc' 2>/dev/null || true; ")
            append("mount --bind /dev '$rootfs/dev' 2>/dev/null || true; ")
            append("mount --bind /sys '$rootfs/sys' 2>/dev/null || true; ")
            append("mount -t devpts devpts '$rootfs/dev/pts' 2>/dev/null || true; ")

            // 2. bind 宿主目录到 rootfs 内的挂载点（与 PRoot -b 对齐）
            append("mount --bind '${tmpDir.absolutePath}' '$rootfs/tmp' 2>/dev/null || true; ")
            append("mount --bind '${workspaceDir.absolutePath}' '$rootfs/workspace' 2>/dev/null || true; ")
            append("mount --bind '${homeDir.absolutePath}' '$rootfs/root' 2>/dev/null || true; ")
            append("mount --bind '${optDir.absolutePath}' '$rootfs/opt/tianxuan' 2>/dev/null || true; ")
            attachmentsDir.mkdirs()
            append("mount --bind '${attachmentsDir.absolutePath}' '$rootfs/attachments' 2>/dev/null || true; ")

            // 3. 用户自定义存储挂载
            mounts.forEach { m ->
                val src = m.hostPath
                val dst = "$rootfs${m.guestPath}"
                append("mkdir -p '$dst' 2>/dev/null; mount --bind '$src' '$dst' 2>/dev/null || true; ")
            }

            // 4. chroot 执行命令：用 /bin/bash -lc 加载登录 shell 环境（PATH/profile）
            //    env -i 清空继承的宿主环境，避免 Android 环境变量污染 chroot 内的 Linux 语义
            val escapedCmd = command.commandLine.replace("'", "'\\''")
            append("chroot '$rootfs' /usr/bin/env -i ")
            append("HOME=/root ")
            append("PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin ")
            append("TERM=xterm-256color ")
            append("LANG=C.UTF-8 ")
            append("/bin/bash -lc '$escapedCmd'")
        }
        return listOf("su", "-c", script)
    }
}
