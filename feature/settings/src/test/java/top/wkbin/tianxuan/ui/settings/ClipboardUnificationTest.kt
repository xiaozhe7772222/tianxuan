package top.wkbin.tianxuan.ui.settings

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 剪贴板复制路径守卫。
 *
 * [copyToClipboard]（见 ClipboardSupport.kt）的类注释把存在理由写得很明确：
 * 这段逻辑曾在三个文件各抄一份，改文案或换标签要改三处、漏一处就出现行为不一致
 * 的复制按钮。但实际排查发现仍有文件手抄第四、第五份，且都退化成
 * `clipboard?.setPrimaryClip(...)` 之后**无条件**弹「已复制」——
 * 拿不到剪贴板服务时会告诉用户复制成功，用户在粘贴处找不到内容只能反复排查。
 *
 * 本测试扫描本模块源码，禁止在 [copyToClipboard] 之外直接调用 `setPrimaryClip`。
 * 需要复制时一律走公共函数；它已内建「剪贴板不可用」的兜底提示。
 *
 * 同理禁止再定义私有的 `copyToClipboard` 重载：同一仓库里出现同名不同签名的实现，
 * 读代码的人无法从调用点判断走的是哪一套。
 */
class ClipboardUnificationTest {

    /**
     * 模块目录。Gradle 跑测试时 user.dir 是仓库根，不是模块目录，
     * 所以要从路径反推，否则扫不到文件、断言会以「没找到」的形式误导排查方向。
     */
    private val moduleDir: File = File(System.getProperty("user.dir")).let { root ->
        val direct = File(root, "feature/settings")
        if (direct.isDirectory) direct else File(root, ".")
    }

    private val sourceDir: File = File(moduleDir, "src/main/java")

    /** 公共实现所在文件，允许它自己调用 setPrimaryClip。 */
    private val allowedFile = "ClipboardSupport.kt"

    /** 去掉行注释后再判断，避免匹配到注释里引用的旧写法。 */
    private fun codeLines(file: File): List<String> =
        file.readLines().map { it.substringBefore("//") }

    @Test
    fun `模块内不存在绕过 copyToClipboard 的 setPrimaryClip 直呼`() {
        assertTrue("源码目录不存在: $sourceDir", sourceDir.isDirectory)
        val offenders = mutableListOf<String>()
        var scanned = 0
        sourceDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { f ->
                scanned++
                if (f.name == allowedFile) return@forEach
                codeLines(f).forEachIndexed { i, line ->
                    if (line.contains("setPrimaryClip")) {
                        offenders += "${f.name}:${i + 1}  ${line.trim()}"
                    }
                }
            }
        assertTrue("未扫描到任何源文件，路径推断可能有误: $sourceDir", scanned > 0)
        assertTrue(
            "以下位置绕过 copyToClipboard 直接写剪贴板，缺少「剪贴板不可用」兜底，\n" +
                "请改用 copyToClipboard(context, text, label, toast)：\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    @Test
    fun `模块内不存在与公共函数同名的私有 copyToClipboard 重载`() {
        val offenders = mutableListOf<String>()
        // 匹配 `private fun ... copyToClipboard` 或 `private fun Context.copyToClipboard`
        val declaration = Regex("private\\s+fun\\s+(\\w+\\.)?copyToClipboard\\s*\\(")
        sourceDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { f ->
                if (f.name == allowedFile) return@forEach
                codeLines(f).forEachIndexed { i, line ->
                    if (declaration.containsMatchIn(line.trim())) {
                        offenders += "${f.name}:${i + 1}  ${line.trim()}"
                    }
                }
            }
        assertTrue(
            "以下位置重新定义了私有的 copyToClipboard，与公共函数同名不同签名，\n" +
                "调用点无法分辨走哪一套，请删除并统一使用 ClipboardSupport 的实现：\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    @Test
    fun `公共实现自身保留了剪贴板不可用的兜底提示`() {
        // 防止有朝一日把公共函数也简化成 `clipboard?.` 静默写法——
        // 那时上面两条扫描会全绿，但兜底已消失。
        //
        // 不能用 `text.contains("clipboard == null")` 判定：退化后的
        // `if (clipboard == null) return` 同样含这个子串，两种形态都为真。
        // 必须按结构判定——null 分支体内必须真的出现提示（Toast）。
        val file = File(sourceDir, "top/wkbin/tianxuan/ui/settings/$allowedFile")
        assertTrue("找不到 $allowedFile，请确认未被移动或改名", file.isFile)
        val lines = file.readLines()
        val guardLine = lines.indexOfFirst { it.contains("clipboard == null") }
        assertTrue(
            "$allowedFile 中未找到 clipboard == null 分支，兜底已被删除",
            guardLine >= 0,
        )
        val guardText = lines[guardLine]
        val indent = guardText.takeWhile { it == ' ' }.length
        val afterGuard = guardText.substringAfter("clipboard == null").trimStart()

        // 形态一：单行 `if (cond) return`。这种写法根本没有分支体，
        // 直接判为静默失败——它一定没有提示用户。
        val singleLineReturn = afterGuard.startsWith(")") && afterGuard.substringAfter(")").trim() == "return"
        assertTrue(
            "clipboard == null 用了单行 return，没有任何提示；" +
                "静默失败会让用户以为已复制，然后在粘贴处找不到内容而反复排查。实际代码：\n$guardText",
            !singleLineReturn,
        )

        // 形态二：带花括号的分支体。只取该 if 的直属分支——从 guardLine 起，
        // 到**首个缩进不足 indent 的右花括号**为止。不能取固定长度窗口：
        // 那会把后续成功路径上的 `Toast.makeText(context, toast, ...)` 一并
        // 纳入，使退化写法因窗口里恰好含 Toast 而误判为通过。
        val closingIndent = " ".repeat(indent)
        val branch = buildList {
            for (i in (guardLine + 1) until lines.size) {
                val line = lines[i]
                if (line.startsWith(closingIndent + "}")) break
                add(line)
            }
        }
        assertTrue(
            "$allowedFile 的 clipboard == null 分支为空或无法解析，请检查实现：\n$guardText",
            branch.isNotEmpty(),
        )
        val branchText = branch.joinToString("\n")
        assertTrue(
            "clipboard == null 分支内没有告知用户剪贴板不可用；" +
                "静默失败会让用户以为已复制，然后在粘贴处找不到内容而反复排查。实际分支代码：\n$branchText",
            branchText.contains("Toast.makeText"),
        )
    }
}
