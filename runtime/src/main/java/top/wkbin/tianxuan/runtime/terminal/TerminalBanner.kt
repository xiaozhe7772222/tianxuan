package top.wkbin.tianxuan.runtime.terminal

/**
 * 终端登录横幅。由 [top.wkbin.tianxuan.runtime.LinuxRuntimeImpl] 在终端会话启动前
 * 写入发行版 `/opt/tianxuan/motd`，登录 shell 通过 `cat` 打印，绕开命令串转义问题。
 */
internal fun terminalBanner(): String =
    "天玄 · TianXuan Linux AI Runtime\n"
