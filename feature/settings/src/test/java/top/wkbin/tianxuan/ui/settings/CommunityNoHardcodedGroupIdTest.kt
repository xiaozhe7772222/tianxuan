package top.wkbin.tianxuan.ui.settings

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 群号硬编码守卫。
 *
 * 群号的唯一来源是 `core:model`的 [top.wkbin.tianxuan.core.model.Community]。
 * 本测试扫描本模块的源码与资源，确保没有把群号当字面量再写一遍 ——
 * 这类副本不会编译报错，只会在改号时静默失效，让用户加错群。
 *
 * 允许出现的位置仅两类：
 * 1. 对 [top.wkbin.tianxuan.core.model.Community] 常量的引用；
 * 2. Android 资源占位符（百分号 + 序号 + 美元符 + s），群号由运行时注入。
 */
class CommunityNoHardcodedGroupIdTest {

    /**
     * 模块目录。Gradle 跑测试时 user.dir 是仓库根，不是模块目录，
     * 所以要从包路径反推（或直接用相对路径），否则扫不到任何文件、
     * 断言会以「没找到」的形式误导排查方向。
     */
    private val moduleDir: File = File(System.getProperty("user.dir")).let { root ->
        val direct = File(root, "feature/settings")
        if (direct.isDirectory) direct else File(root, ".")
    }

    /** 允许出现的数字串：非 QQ 号的普通数字（版本号、行数等） */
    private val benignNumbers = setOf("0", "1", "400", "10086")

    /** Android 资源占位符。字符拼接而成，避免美元符号被 Kotlin 当字符串模板 */
    private val DOLLAR = 36.toChar()
    private val PLACEHOLDER = Regex("%" + "\\d+" + "\\" + DOLLAR + "s")

    private fun scanDirs(): List<File> = listOf(File(moduleDir, "src/main/java"), File(moduleDir, "src/main/res"))
        .filter { it.isDirectory }

    private fun filesToScan(): List<File> = scanDirs().flatMap { dir ->
        dir.walkTopDown().filter { it.isFile && it.extension in setOf("kt", "xml") }.toList()
    }

    @Test
    fun `源码与资源中不存在硬编码的群号`() {
        val offenders = mutableListOf<String>()
        filesToScan().forEach { f ->
            f.readLines().forEachIndexed { i, line ->
                val code = line.substringBefore("//").trim()
                if (code.isEmpty()) return@forEachIndexed
                // 只在「看起来像群号」的上下文里报警：含群/QQ/加群语义
                val looksLikeGroupContext =
                    Regex("(群号|QQ群|QQ 群|加群|交流群|uin=|group|qq_group)", RegexOption.IGNORE_CASE)
                        .containsMatchIn(code)
                if (!looksLikeGroupContext) return@forEachIndexed
                // 命中的是占位符或对常量的引用则放行
                if (PLACEHOLDER.containsMatchIn(code)) return@forEachIndexed
                if (Regex("(Community\\.|QQ_GROUP_ID)").containsMatchIn(code)) return@forEachIndexed
                Regex("""\d{6,}""").findAll(code).forEach { m ->
                    if (m.value in benignNumbers) return@forEach
                    offenders += "${f.name}:${i + 1}  ${line.trim()}"
                }
            }
        }
        assertTrue(
            "以下位置疑似硬编码群号，请改用 Community.QQ_GROUP_ID 或占位符：\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    private val QQ_GROUP_RES = Regex("""<string name="settings_dynamic_qq_[a-z]+"[^>]*>([^<]*)<""")

    @Test
    fun `群号资源使用占位符而非内嵌数字`() {
        val resDir = File(moduleDir, "src/main/res")
        assertTrue("资源目录不存在: $resDir", resDir.isDirectory)
        var checked = 0
        resDir.walkTopDown().filter { it.isFile && it.extension == "xml" }.forEach { f ->
            QQ_GROUP_RES.findAll(f.readText()).forEach { m ->
                checked++
                val body = m.groupValues[1]
                assertTrue(
                    "群号资源 ${m.groupValues[0]} 必须用占位符形式，不能内嵌数字：$body",
                    PLACEHOLDER.containsMatchIn(body),
                )
            }
        }
        assertTrue("未找到群号资源，请确认 settings_dynamic_qq_* 是否被误删", checked >= 4)
    }
}