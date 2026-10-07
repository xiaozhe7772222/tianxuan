package top.wkbin.tianxuan.harness

/**
 * Prepares safe, foreground Agent commands for RTK without changing terminal,
 * MCP, process, or file-tool behaviour. RTK itself decides whether a supported
 * command has an equivalent; a missing/incompatible binary always falls back to
 * the exact original command in the same shell invocation.
 *
 * 两条硬约束：
 * 1. 改写只允许影响“给人/模型看”的输出。任何输出会被下游脚本解析的命令
 *    （`ls -1`、`grep -l`、`find -print0`、`git --porcelain` 等）必须原样执行，
 *    否则 Agent 后续的解析步骤会静默拿到压缩后的文本。
 * 2. 改写必须零收益即零成本。RTK 没有对应子命令的可执行文件不进入白名单，
 *    避免为一次注定回退的 `rtk rewrite` 白付两次进程启动。
 */
internal object RtkCommandOptimizer {
    private const val RTK_BINARY = "/opt/tianxuan/bin/rtk"

    /**
     * 承载原始命令的环境变量名。包装脚本只引用它，不内联命令文本——
     * 这样命令里的任何引号、反斜杠、换行都不可能改变脚本的语法结构。
     * 见 [wrapWithFallback] 的说明。
     */
    internal const val COMMAND_ENV = "TIANXUAN_AGENT_COMMAND"

    private val rtkEnvironment = mapOf(
        // Raw failure output can include project secrets and is already returned to the Agent.
        "RTK_TEE" to "0",
        "XDG_CONFIG_HOME" to "/opt/tianxuan/data/rtk/config",
        "XDG_DATA_HOME" to "/opt/tianxuan/data/rtk/data",
    )

    /**
     * 只保留 RTK 真正提供等价子命令、且压缩确有收益的可执行文件。
     *
     * 已移除：`wc`（单文件调用丢弃文件名、总计行被改写成 `Σ`，输出本身就是给脚本读的数字）、
     * `du` / `yarn` / `bun` / `mvnw`（RTK 无对应子命令，改写只会多付两次进程启动）。
     */
    private val supportedCommands = setOf(
        "git", "rg", "grep", "find", "ls", "tree",
        "gradle", "gradlew", "mvn", "mvnd", "cargo", "go", "pytest",
        "npm", "pnpm", "npx",
    )

    /**
     * 只排除会改变命令结构的元字符：管道、串联、重定向、命令替换与换行。
     *
     * 通配符与花括号不在此列：`rtk rewrite` 输出会原样保留原命令的引号，随后的 `eval`
     * 只做一次展开，与直接执行原命令等价。放行它们才能覆盖 Agent 最常用、
     * 同时压缩收益最高的形态（`find . -name '*.kt'`、`rg -n pattern --glob '*.kt'`）。
     */
    private val unsupportedShellSyntax = setOf('&', '|', ';', '\n', '\r', '<', '>', '`', '$')

    /** 与具体命令无关的“机器可解析输出”标志。 */
    private val universalRawOutputFlags = setOf("--json", "-z", "--null", "--print0")

    /** grep 与 rg 共用：这些标志下输出是给脚本读的（退出码、计数、纯文件名列表）。 */
    private val grepRawOutputFlags = setOf(
        "-q", "--quiet", "--silent",
        "-c", "--count",
        "-l", "--files-with-matches",
        "-L", "--files-without-match",
        "-o", "--only-matching",
        "--vimgrep",
    )

    /**
     * 按命令区分的机器可解析标志。必须按命令区分而不是全局匹配：
     * `ls -l` 是压缩收益最高的形态之一，`grep -l` 却只输出文件名列表。
     */
    private val rawOutputFlags = mapOf(
        "ls" to setOf("-1"),
        "grep" to grepRawOutputFlags,
        "rg" to grepRawOutputFlags,
        "find" to setOf("-print0", "-printf", "-fprint", "-fprint0", "-exec", "-execdir", "-ok", "-okdir"),
        "git" to setOf("--porcelain", "--numstat", "--name-only", "--name-status", "--format", "--pretty", "--raw"),
    )

    private val whitespace = Regex("\\s+")

    data class PreparedCommand(
        val commandLine: String,
        val environment: Map<String, String> = emptyMap(),
    )

    fun prepare(command: String, enabled: Boolean): PreparedCommand {
        if (!enabled || !isEligible(command)) return PreparedCommand(command)
        return PreparedCommand(
            commandLine = wrapWithFallback(command),
            environment = rtkEnvironment + (COMMAND_ENV to command),
        )
    }

    private fun isEligible(command: String): Boolean {
        val trimmed = command.trim()
        if (trimmed.isEmpty() || command.any { it in unsupportedShellSyntax }) return false
        val tokens = trimmed.split(whitespace)
        val executable = tokens.first().substringAfterLast('/').lowercase()
        if (executable !in supportedCommands) return false
        return !producesMachineReadableOutput(executable, tokens)
    }

    /**
     * 分词只按空白切分：结构性元字符已在 [isEligible] 前置排除，引号内的空格最多
     * 让某个参数被误判成标志，结果是保守地放弃改写，不会造成语义破坏。
     */
    private fun producesMachineReadableOutput(executable: String, tokens: List<String>): Boolean {
        val flags = rawOutputFlags[executable].orEmpty() + universalRawOutputFlags
        return tokens.drop(1).any { token -> isRawOutputFlag(token, flags) }
    }

    private fun isRawOutputFlag(token: String, flags: Set<String>): Boolean {
        val name = token.substringBefore('=')
        if (name in flags) return true
        val isShortCluster = name.length > 2 && name.startsWith('-') && !name.startsWith("--")
        return isShortCluster && name.drop(1).any { "-$it" in flags }
    }

    /**
     * 生成「有 RTK 就用改写、否则原样执行」的包装脚本。
     *
     * 关键约束：**生成的脚本文本里绝不能出现原始命令**。
     *
     * 曾经这里把 `$command` 直接插进 `else $command; fi` 与 `if [ -x ... ]` 两个分支。
     * 原始命令只是「未经引号的文本」，它自带的引号会与包装脚本的语法互相干扰，
     * 后果随命令内容而变，且都发生在最不该出错的地方——回退路径：
     *
     * - 命令含**未配对单引号**（`git commit -m 'fix: it's done'`、`rg "don't"`）时，
     *   那个 `'` 会破坏 `else ...; fi` 的配对，整条命令以 shell 语法错误失败
     *   （实测退出码 2），而不是去执行用户想跑的东西；
     * - 更隐蔽的是 `$command` 也会污染**上游**的引号解析：`echo it's fine` 的 `'`
     *   让 shell 把它之后的内容读成新字符串，`fi` 的配对随之错位，落进 else
     *   分支被**无引号裸执行**，于是 `echo A; echo B` 输出字面量 `A; echo B`。
     *
     * 即使 `isEligible` 已经挡掉分号、管道等结构性元字符，也挡不住单/双引号——
     * 而引号在真实命令里极常见（commit message、grep 模式、路径）。
     *
     * 修法是让脚本文本与命令内容彻底解耦：命令通过环境变量
     * [COMMAND_ENV] 传入，脚本里只出现 `"$TIANXUAN_AGENT_COMMAND"`。命令文本
     * 从此不再是脚本语法的一部分，无论含什么字符都不会改变脚本结构。
     * 用环境变量而非位置参数，是因为命令执行层只接受 (脚本, 环境) 二元组，
     * 没有 argv 通道可挂。
     *
     * 同时给两个执行分支补上 `eval`：原实现回退分支是 `$command` 裸展开，
     * 单次分词后不再做引号/转义处理，本来就不等价于用户写的命令。
     */
    private fun wrapWithFallback(command: String): String = """
        if [ -x "$RTK_BINARY" ]; then
            _tianxuan_rtk_rewritten="${'$'}("$RTK_BINARY" rewrite "${'$'}$COMMAND_ENV" 2>/dev/null)"
            _tianxuan_rtk_status=${'$'}?
            case "${'$'}_tianxuan_rtk_rewritten" in
                "rtk "*) ;;
                *) _tianxuan_rtk_status=1 ;;
            esac
            if [ "${'$'}_tianxuan_rtk_status" -eq 0 ] && [ "${'$'}(printf '%s' "${'$'}_tianxuan_rtk_rewritten" | wc -l)" -eq 0 ]; then
                eval "${'$'}_tianxuan_rtk_rewritten"
            else
                eval "${'$'}$COMMAND_ENV"
            fi
        else
            eval "${'$'}$COMMAND_ENV"
        fi
    """.trimIndent()
}
