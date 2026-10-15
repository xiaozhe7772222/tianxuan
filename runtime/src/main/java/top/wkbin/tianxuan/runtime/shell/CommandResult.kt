package top.wkbin.tianxuan.runtime.shell

/**
 * Shell 命令执行结果。对齐 DeepSeek Harness shell/bash-local 的 ShellRunResult：
 * timedOut / aborted 是互斥的 first-cause 分类（谁先杀的），让模型能区分
 * "命令太慢"（timedOut）和"用户取消"（aborted）。
 */
data class CommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val durationMs: Long,
    /** 命令是否因超时被杀（自身 timer 先到期）。对齐 DSH ShellRunResult.timedOut。 */
    val timedOut: Boolean = false,
    /** 命令是否因上游取消被杀（caller cancellation 先到）。对齐 DSH ShellRunResult.aborted。 */
    val aborted: Boolean = false,
) {
    val isSuccess: Boolean get() = exitCode == 0 && !timedOut && !aborted
}
