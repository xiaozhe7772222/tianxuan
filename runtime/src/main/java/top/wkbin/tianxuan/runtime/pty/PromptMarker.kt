package top.wkbin.tianxuan.runtime.pty

/**
 * 终端 readiness 协议常量（对齐 DSH terminal-bash/src/sanitize.ts）。
 *
 * DSH 在 bash 中通过 PROMPT_COMMAND 发射 OSC `133;D;$?` + BEL 标记，
 * 在 PowerShell 中通过 prompt 函数发射同一标记。
 * 标记后的可打印提示语固定为 [CONTROLLED_PROMPT]，readiness 检测器通过
 * 追踪标记+提示语的到来判定 shell 回到就绪状态。
 */

/** OSC 标记前缀，由受控 shell 在每个提示符前发射。 */
const val PROMPT_MARKER_PREFIX = "133;D;"

/** 标记后紧随的可打印提示语。 */
const val CONTROLLED_PROMPT = "tx> "

/** bash PROMPT_COMMAND 环境变量值：发射标记 + 重设 PS1。 */
val BASH_PROMPT_COMMAND = """printf "\033]133;D;%s\007" "$?"; PS1='${CONTROLLED_PROMPT}'"""

/** bash PS1 环境变量值：受控提示语。 */
const val BASH_PS1 = CONTROLLED_PROMPT

/** 受控 shell 环境变量覆盖（bash 方言）。 */
fun controlledShellEnv(sessionId: String): Map<String, String> = mapOf(
    "TERM" to "dumb",
    "PAGER" to "cat",
    "GIT_PAGER" to "cat",
    "TIANXUAN_SHELL" to "1",
    "TIANXUAN_SESSION_ID" to sessionId,
    "PS1" to CONTROLLED_PROMPT,
    "PROMPT_COMMAND" to BASH_PROMPT_COMMAND,
    "BASH_SILENCE_DEPRECATION_WARNING" to "1",
)
