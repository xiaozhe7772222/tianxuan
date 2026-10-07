package top.wkbin.tianxuan.harness

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RtkCommandOptimizerTest {

    private companion object {
        /** 与 RtkCommandOptimizer.RTK_BINARY 对齐；测试只断言脚本文本形状。 */
        const val RTK_BINARY_PATH = "/opt/tianxuan/bin/rtk"
    }

    @Test
    fun `eligible git command is wrapped with a same-shell fallback`() {
        val prepared = RtkCommandOptimizer.prepare("git status --short", enabled = true)

        assertTrue(prepared.commandLine.contains("$RTK_BINARY_PATH\" rewrite \"\$TIANXUAN_AGENT_COMMAND\""))
        assertTrue(prepared.commandLine.contains("else\n        eval \"\$TIANXUAN_AGENT_COMMAND\""))
        assertEquals("0", prepared.environment["RTK_TEE"])
        // 命令必须经环境变量下发，不能内联进脚本文本
        assertEquals("git status --short", prepared.environment[RtkCommandOptimizer.COMMAND_ENV])
    }

    @Test
    fun `shell syntax and file reads remain raw`() {
        assertEquals(
            "git status && git log --oneline",
            RtkCommandOptimizer.prepare("git status && git log --oneline", enabled = true).commandLine,
        )
        assertEquals("cat build.gradle.kts", RtkCommandOptimizer.prepare("cat build.gradle.kts", enabled = true).commandLine)
    }

    @Test
    fun `disabled setting bypasses RTK`() {
        val prepared = RtkCommandOptimizer.prepare("./gradlew test", enabled = false)

        assertEquals("./gradlew test", prepared.commandLine)
        assertFalse(prepared.environment.isNotEmpty())
    }

    /**
     * 生成的脚本文本里绝不能出现原始命令。
     *
     * 回归背景：原实现把命令直接内联进 `else $command; fi` 与 `if [ -x ... ]` 两处，
     * 命令自带的引号会与包装脚本的语法互相干扰：
     *
     * - `git commit -m 'fix: it's done'`（未配对单引号）→ `else ...; fi` 配对错位 → 语法错误退出码 2；
     * - `rg -n "TODO" src`（成对双引号）→ 同样让 `fi` 配对错位 → 命令根本无法执行；
     * - `echo it's fine` → 上游引号解析被污染，`fi` 之后的内容被读进新字符串，
     *   落进 else **无引号裸执行**。
     *
     * 这三类命令在 Agent 场景里都极其常见，而受影响的恰是**回退路径**——
     * 本该最稳的那条。
     *
     * 只挑**确实会被改写**的命令来断言（即 isEligible 为真）：被退回原样的命令
     * 本就不会内联，拿它们断言会掩盖真实覆盖范围。
     */
    @Test
    fun `generated script never embeds the raw command text`() {
        val eligibleCommandsWithQuotes = listOf(
            "git commit -m 'fix: it's done'",
            "rg -n \"TODO\" src",
            "grep -rn \"a'b\" src",
            "find . -name \"*.kt\"",
            "ls -la \"my dir\"",
            "git status --short",
        )
        eligibleCommandsWithQuotes.forEach { command ->
            val prepared = RtkCommandOptimizer.prepare(command, enabled = true)
            assertTrue(
                "该命令本应被改写，若此处为原样说明 isEligible 判定变了：$command",
                prepared.commandLine.contains(RTK_BINARY_PATH),
            )
            assertFalse(
                "包装脚本不得内联命令文本（命令含引号会破坏脚本语法）：$command",
                prepared.commandLine.contains(command),
            )
            assertEquals(
                "命令必须原样经环境变量下发",
                command,
                prepared.environment[RtkCommandOptimizer.COMMAND_ENV],
            )
        }
    }

    /**
     * 含引号的命令必须照常改写，而不是被 isEligible 退回原样。
     *
     * 若哪天有人为了绕开引号问题而把引号加进 unsupportedShellSyntax，
     * 这条会失败——那是退让而不是修复：commit message、grep 模式、带空格的路径
     * 都离不开引号，退回原样等于让这几类命令永远享受不到改写。
     */
    @Test
    fun `commands with quotes are still optimized rather than excluded`() {
        val command = "git commit -m 'wip: don't merge'"
        val prepared = RtkCommandOptimizer.prepare(command, enabled = true)

        assertTrue("含引号的命令应当照常改写", prepared.commandLine.contains("$RTK_BINARY_PATH\" rewrite"))
        assertEquals(command, prepared.environment[RtkCommandOptimizer.COMMAND_ENV])
    }
    @Test
    fun `find with a quoted name pattern is optimized`() {
        val prepared = RtkCommandOptimizer.prepare("find . -name '*.kt'", enabled = true)

        // 通配符与引号都不再阻止改写：命令整体经环境变量下发，不需要转义
        assertTrue(prepared.commandLine.contains("rewrite \"\$TIANXUAN_AGENT_COMMAND\""))
        assertEquals("find . -name '*.kt'", prepared.environment[RtkCommandOptimizer.COMMAND_ENV])
    }

    @Test
    fun `long listing keeps using RTK because -l is not a machine readable flag for ls`() {
        val prepared = RtkCommandOptimizer.prepare("ls -la src", enabled = true)

        assertTrue(prepared.commandLine.contains("rewrite \"\$TIANXUAN_AGENT_COMMAND\""))
        assertEquals("ls -la src", prepared.environment[RtkCommandOptimizer.COMMAND_ENV])
    }

    @Test
    fun `machine readable output is never rewritten`() {
        val untouched = listOf(
            "git status --porcelain",
            "git log --pretty=oneline",
            "git diff --numstat",
            "grep -l TODO src",
            "grep -rnl TODO src",
            "rg --files-with-matches TODO",
            "rg -c TODO",
            "rg -q TODO",
            "find . -name '*.kt' -print0",
            "find . -name '*.kt' -exec rm {} +",
            "ls -1",
            "ls -la1",
        )

        untouched.forEach { command ->
            assertEquals(command, RtkCommandOptimizer.prepare(command, enabled = true).commandLine)
        }
    }

    @Test
    fun `commands without an rtk equivalent stay raw`() {
        // wc 会丢掉单文件路径并把总计行改写成 Σ；du/yarn/bun/mvnw 在 RTK 里没有子命令。
        listOf("wc -l build.gradle.kts", "du -sh build", "yarn test", "bun install", "./mvnw verify").forEach { command ->
            assertEquals(command, RtkCommandOptimizer.prepare(command, enabled = true).commandLine)
        }
    }

    @Test
    fun `only a single line rtk rewrite is evaluated`() {
        val prepared = RtkCommandOptimizer.prepare("ls -la", enabled = true)

        // 只有以 "rtk " 开头的单行输出才会进入 eval，避免未来版本把诊断文本写到 stdout。
        assertTrue(prepared.commandLine.contains("\"rtk \"*) ;;"))
        assertTrue(prepared.commandLine.contains("wc -l)\" -eq 0 ]"))
    }
}
