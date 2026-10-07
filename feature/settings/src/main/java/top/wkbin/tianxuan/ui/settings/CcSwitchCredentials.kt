package top.wkbin.tianxuan.ui.settings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.wkbin.tianxuan.runtime.LinuxRuntime
import top.wkbin.tianxuan.runtime.shell.ShellCommand
import java.security.SecureRandom

/**
 * CC-Switch 中枢 Web 控制台的凭据策略。
 *
 * 为什么不留默认口令：这个中枢默认监听 0.0.0.0，且界面优先展示 LAN 地址
 * （见 [CcSwitchUiState.preferredWebUrl]），意味着同一 Wi-Fi 下的任何设备
 * 都能访问。曾经默认 `admin/admin123`，等价于把 provider 的 API Key 配置
 * 对整个局域网公开——那上面存着用户的模型密钥。
 *
 * 因此改为：**首次访问随机生成**，口令只存在于沙箱内的 600 权限文件中，
 * 不进APK、不进源码、不进日志。用户可在界面上一键复制或重置。
 *
 * 随机源用 [SecureRandom] 而非 [kotlin.random.Random]：后者在部分平台上
 * 会退化成 [java.util.Random]（固定种子的线性同余），用它生成口令等于
 * 可预测。
 */
internal object CcSwitchCredentials {

    /** 用户名保持 admin：它是显示名而非秘密，保留可读性便于用户辨认。 */
    const val DEFAULT_USERNAME: String = "admin"

    /**
     * 字符集：去掉 0/O/1/l/I 等易混字符。
     *
     * 口令要靠人眼从界面抄写或粘贴展示，混淆字符会直接导致用户敲错后
     * 以为服务坏了。沙箱内生成用的 tr/dc 字符集与此保持一致。
     */
    internal const val ALPHABET = "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    /** 16 位约 95 bit 熵，足够抵御局域网暴力破解。 */
    internal const val PASSWORD_LENGTH = 16

    private val random = SecureRandom()

    /** 生成一次性随机口令。 */
    fun generatePassword(): String =
        CharArray(PASSWORD_LENGTH) { ALPHABET[random.nextInt(ALPHABET.length)] }
            .concatToString()

    /**
     * 判断口令是否仍在使用历史默认值。
     *
     * 界面上仍需能显示老用户的存量口令（否则他们会以为服务坏了），
     * 但必须显式提示该口令不安全——不能因为要兼容就让弱口令继续静默生效。
     */
    fun isKnownWeakPassword(password: String): Boolean =
        password.isBlank() || password in LEGACY_PASSWORDS

    private val LEGACY_PASSWORDS = setOf("admin123", "admin", "123456", "password")

    /**
     * 读取中枢 Web 控制台凭据；文件不存在时随机生成并落盘。
     *
     * 历史行为是在脚本里 `printf 'admin123'` 写死默认口令，等于把局域网可访问的
     * 控制台密钥交给所有同网设备。现改为随机生成。存量用户的口令文件会被
     * 原样保留，仅当文件缺失或为空才生成新的。
     *
     * 生成动作刻意放在 shell 内一次完成：Kotlin 侧生成随机串再经 shell 传参，
     * 反而多一层转义出错的机会。
     */
    suspend fun read(runtime: LinuxRuntime): Pair<String, String> =
        withContext(Dispatchers.IO) {
            try {
                val res = runtime.execute(ShellCommand(commandLine = READ_SCRIPT, timeoutMs = 5000L))
                if (!res.isSuccess) return@withContext Pair(DEFAULT_USERNAME, "")
                val parts = res.stdout.split(FIELD_SEPARATOR)
                val username = parts.getOrNull(0)?.trim().orEmpty().ifBlank { DEFAULT_USERNAME }
                // 拿不到口令时如实返回空串而非编一个：界面会据此提示重置。
                // 编造一个会让用户拿着不存在的口令去登录，并以为服务坏了。
                Pair(username, parts.getOrNull(1)?.trim().orEmpty())
            } catch (_: Exception) {
                Pair(DEFAULT_USERNAME, "")
            }
        }

    /**
     * 写入新口令。
     *
     * 刻意**不提供默认参数**：过去 `resetWebPassword(newPassword: String = "admin123")`
     * 让界面上那个「重置密码」按钮一键把口令打回弱口令，等于留了一条降级后门。
     */
    suspend fun write(runtime: LinuxRuntime, password: String): Boolean {
        if (password.isBlank()) return false
        val res = runtime.execute(ShellCommand(commandLine = writeScript(password), timeoutMs = 5000L))
        return res.isSuccess
    }

    /**
     * 构造写入口令的脚本。
     *
     * **口令绝不能以插值方式进入命令行。** 旧实现是
     * `printf '%s' "$password"`，即把 Kotlin 变量直接拼进 shell 双引号串；
     * 而 `commandLine` 最终以 `sh -lc <commandLine>` 的形式交给 guest shell
     * （见 ProotCommandBuilder：`add(GUEST_SHELL); add("-lc"); add(commandLine)`），
     * 双引号内的 `$()`、反引号仍会被求值。实测 `pa` 加反引号包裹的 id 加 `ss`
     * 会被执行并回显 `uid=0(root)`——即口令里的一段文本变成沙箱内的命令执行。
     *
     * 改为参数化：口令经 [shellQuoteForSingleLine] 单引号折叠后作为 printf 的
     * 位置参数传入。单引号内除 `'` 外一切字符都是字面量，而 `'` 本身被折叠为
     * `'\''`。
     *
     * 实现委托给顶层 [buildWriteScript]：本对象是 `internal object`，其私有成员
     * 无法从单测触达，而这段拼接一旦回退就是命令注入，必须有测试直接钉住。
     */
    private fun writeScript(password: String): String = buildWriteScript(password)

    /** 用户名口令各占一行输出，用此标记切分。 */
    private const val FIELD_SEPARATOR = "---TIANXUAN_SPLIT---"

    private val READ_SCRIPT = """
        DIR="${'$'}{HOME:-/root}/.cc-switch"
        USER_FILE="${'$'}DIR/web_username"
        PASS_FILE="${'$'}DIR/web_password"
        if [ ! -d "${'$'}DIR" ]; then mkdir -p "${'$'}DIR" 2>/dev/null || true; fi
        if [ ! -s "${'$'}USER_FILE" ]; then
            printf 'admin' > "${'$'}USER_FILE" 2>/dev/null || true
        fi
        if [ ! -s "${'$'}PASS_FILE" ]; then
            # 字符集与 CcSwitchCredentials.ALPHABET 一致：剔除 0/O/1/l/I 等易混字符，
            # 因为口令要靠用户从界面抄走，混淆字符会让人以为服务坏了
            tr -dc '$ALPHABET' < /dev/urandom 2>/dev/null | head -c $PASSWORD_LENGTH > "${'$'}PASS_FILE" || true
            # /dev/urandom 不可用时别留下空文件，否则用户将没有任何可用口令
            if [ ! -s "${'$'}PASS_FILE" ]; then
                printf 'tx' > "${'$'}PASS_FILE" 2>/dev/null || true
                date +%s%s%s%s | md5sum | cut -c1-14 >> "${'$'}PASS_FILE" 2>/dev/null || true
            fi
        fi
        chmod 600 "${'$'}USER_FILE" "${'$'}PASS_FILE" 2>/dev/null || true
        cat "${'$'}USER_FILE" 2>/dev/null || echo "$DEFAULT_USERNAME"
        echo "$FIELD_SEPARATOR"
        cat "${'$'}PASS_FILE" 2>/dev/null || echo ""
    """.trimIndent()
}

/**
 * 单引号折叠实现。提成顶层函数是为了能在没有 `Context`/`LinuxRuntime` 的单测里
 * 直接覆盖——`internal object` 的私有成员无法从测试触达，而这段逻辑一旦被改错
 * 就是命令注入，必须有测试钉住。
 */
internal fun shellQuoteForSingleLine(value: String): String = "'" + value.replace("'", "'\\''") + "'"

/**
 * 写入口令的完整脚本。与 [shellQuoteForSingleLine] 同理提到顶层以便测试。
 *
 * 守卫要点：口令必须以 [shellQuoteForSingleLine] 的结果出现，**不得**以裸值
 * 出现在双引号里。测试会断言生成的脚本中不含未折叠的口令原文。
 */
internal fun buildWriteScript(password: String): String = """
    DIR="${'$'}{HOME:-/root}/.cc-switch"
    mkdir -p "${'$'}DIR" 2>/dev/null || true
    printf '%s' ${shellQuoteForSingleLine(password)} > "${'$'}DIR/web_password" 2>/dev/null || true
    chmod 600 "${'$'}DIR/web_password" 2>/dev/null || true
""".trimIndent()