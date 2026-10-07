package top.wkbin.tianxuan.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 凭据生成与弱口令判定。
 *
 * 守卫的意图：防止有人日后「为了方便」又把默认口令加回来。
 * 这类回退在code review 里很难被肉眼发现——它看起来只是「恢复默认值」，
 * 但实际等于把局域网可访问的控制台重新交给弱口令。
 */
class CcSwitchCredentialsTest {

    @Test
    fun `generated password has the expected length`() {
        assertEquals(CcSwitchCredentials.PASSWORD_LENGTH, CcSwitchCredentials.generatePassword().length)
    }

    @Test
    fun `generated passwords never repeat`() {
        // 连生成两次都不相同的概率可忽略。若这条挂了，说明随机源退化成固定种子。
        val samples = List(50) { CcSwitchCredentials.generatePassword() }
        assertEquals("50 次生成出现了重复，随机源可能已退化", 50, samples.toSet().size)
    }

    @Test
    fun `generated password only uses the unambiguous alphabet`() {
        val password = CcSwitchCredentials.generatePassword()
        // 字符集刻意剔除 0/O/1/l/I：口令要靠人眼从界面抄走，混淆字符会让人
        // 敲错后以为服务坏了。若此处失败，说明字符集被改宽了。
        for (ch in password) {
            assertTrue("出现了易混字符 '$ch'", ch in "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789")
        }
    }

    @Test
    fun `legacy weak passwords are still detected`() {
        // 存量用户（升级前就已装机）的口令文件里就是这些值，必须能识别出来并提示。
        for (weak in listOf("admin123", "admin", "123456", "password", "")) {
            assertTrue("未识别出弱口令 '$weak'", CcSwitchCredentials.isKnownWeakPassword(weak))
        }
    }

    @Test
    fun `generated strong password is not flagged as weak`() {
        assertFalse(CcSwitchCredentials.isKnownWeakPassword(CcSwitchCredentials.generatePassword()))
    }

    @Test
    fun `shellQuote wraps plain values in single quotes`() {
        assertEquals("'abc'", shellQuoteForSingleLine("abc"))
        // 空串也要产出成对的引号，否则 printf 会少一个位置参数而整体错位。
        assertEquals("''", shellQuoteForSingleLine(""))
    }

    @Test
    fun `shellQuote neutralises command substitution and separators`() {
        // 这些字符在双引号里仍有特殊含义（$() 与反引号会被求值），单引号里则全部失效。
        // 旧实现把口令插进双引号串，正是这一组字符构成了命令注入面。
        val hostile = listOf(
            "pa`id`ss",
            "pa\$(id)ss",
            "p; touch /tmp/PWNED; #",
            "p|cat /etc/shadow",
            "p&&whoami",
            "p\nwhoami",
        )
        for (value in hostile) {
            val quoted = shellQuoteForSingleLine(value)
            assertTrue("未以单引号收尾：$quoted", quoted.startsWith("'") && quoted.endsWith("'"))
            // 引号内部不得出现未折叠的单引号：折叠后每个 ' 都变成 '\''
            val inner = quoted.substring(1, quoted.length - 1)
            assertEquals(
                "内层出现了未折叠的单引号，会提前闭合引用：$quoted",
                -1,
                inner.indexOf("'").takeIf { idx ->
                    // 允许出现在 '\'' 三元组里：' 之后紧跟 \' 才算折叠
                    !(idx + 2 < inner.length && inner.startsWith("\\''", idx))
                } ?: -1,
            )
        }
    }

    @Test
    fun `write script never embeds the raw password into the command line`() {
        // 这条是修复本身的守卫：回退成 `printf '%s' "$password"` 时，口令会以裸值
        // 出现在脚本里，本断言立即变红。前几条只测 shellQuoteForSingleLine 本身，
        // 就算 writeScript 不再调用它也照样通过——必须直接断言产物。
        //
        // 注意不能用 `script.contains(hostile)` 这种子串判定：折叠后的
        // `'pa$(id)ss'` 同样包含 `pa$(id)ss`，子串匹配对两种情况都为真，
        // 等于守卫永远通过。必须按结构判定——取出 printf 的参数位置逐字符比对。
        val hostile = "pa\$(id)ss"
        val script = buildWriteScript(hostile)
        val printfLine = script.lines().single { it.contains("printf") && it.contains("web_password") }
        val argument = printfLine
            .substringAfter("printf '%s' ")
            .substringBefore(" > ")
        assertEquals(
            "printf 的实参不是折叠后的口令，存在命令注入面",
            shellQuoteForSingleLine(hostile),
            argument,
        )
        // 折叠结果必须以单引号开头结尾，否则上面的 equals 也可能被「裸值恰好
        // 等于折叠式」这种巧合骗过。
        assertTrue("实参未被单引号包裹: $argument", argument.startsWith("'") && argument.endsWith("'"))
        assertFalse(
            "脚本仍在双引号内插值变量 password: $script",
            printfLine.contains("\"\$password\""),
        )
    }

    @Test
    fun `write script executes the password as a literal under a real shell`() {
        // 端到端：按 ProotCommandBuilder 的方式以 `sh -lc <commandLine>` 单参数执行，
        // 确认注入命令没有被执行、且落盘口令与输入逐字节一致。
        val sandbox = java.nio.file.Files.createTempDirectory("ccswitch-test").toFile()
        try {
            val hostile = "pa\$(touch $sandbox/PWNED)ss"
            val script = buildWriteScript(hostile)
                .replace("\${HOME:-/root}", sandbox.absolutePath)
            val process = ProcessBuilder("/bin/sh", "-lc", script)
                .redirectErrorStream(true)
                .start()
            process.inputStream.readBytes()
            assertTrue("sh -lc 执行失败: $script", process.waitFor() == 0)
            assertFalse("注入命令被执行了", java.io.File(sandbox, "PWNED").exists())
            assertEquals(
                "落盘口令与输入不一致",
                hostile,
                java.io.File(sandbox, ".cc-switch/web_password").readText(),
            )
        } finally {
            sandbox.deleteRecursively()
        }
    }

    @Test
    fun `shellQuote preserves the literal value under a real shell`() {
        // 端到端：把折叠结果喂给真实 shell，确认还原出的字节与原值完全一致。
        // 口令里的 quote 是最容易折叠错的一类输入。
        val samples = listOf(
            "Kx7mQp2RtZvNb4Hd",
            "pa`id`ss",
            "pa\$(id)ss",
            "p; touch /tmp/PWNED; #",
            "it's a 'quoted' pass",
            "trailing'",
            "'leading",
            "back\\slash",
        )
        for (value in samples) {
            val script = "printf '%s' " + shellQuoteForSingleLine(value)
            val process = ProcessBuilder("/bin/sh", "-c", script).redirectErrorStream(true).start()
            val out = process.inputStream.readBytes().toString(Charsets.UTF_8)
            assertTrue("shell 执行失败：$script", process.waitFor() == 0)
            assertEquals("折叠后还原的字节与原值不一致：$value", value, out)
        }
    }
}