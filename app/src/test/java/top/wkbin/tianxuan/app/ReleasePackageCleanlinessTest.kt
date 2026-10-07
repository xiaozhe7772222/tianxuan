package top.wkbin.tianxuan.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 发布包洁净度守卫。
 *
 * 背景：v0.21.0 与 v0.21.1 两个内测包里都打进了 LeakCanary。真实后果有两个，
 * 都是用户在设备上直接看到的、不是理论风险：
 *
 * 1. 桌面多出一个「Leaks」小鸟图标应用。LeakCanary 自己注册了带 LAUNCHER
 *    intent-filter 的 LeakLauncherActivity，内测群里用户会当成多余 App。
 * 2. 它靠 manifest 里的 ContentProvider（PlumberInstaller /
 *    MainProcessAppWatcherInstaller）在进程 attachBaseContext 阶段自动初始化，
 *    并注册自己的 ComponentCallbacks2 与独立分析进程；宿主 Application
 *    实现了 WorkManager 的 Configuration.Provider，两者存在冲突面，
 *    是「打开即闪退」的高概率来源。
 *
 * 「内测期需要排障所以打开」是个经不起追问的理由：release 包是给用户装的，
 * 排障应该用 debug 包或天玄自研的 crashReporter。因此这里把
 * `tianxuan.leakcanary` 的默认值锁成 false，并用测试守住——改配置的人
 * 不会记得去看这段历史，但测试会拦住他。
 */
class ReleasePackageCleanlinessTest {

    // app 模块的测试工作目录是 <repo>/app，故仓库根为其上一级。
    // 写成 "../.." 会指到仓库外面，FileNotFoundException 掩盖掉真正的断言结果。
    private val repoRoot = File("..").canonicalFile
    private val gradleProperties = File(repoRoot, "gradle.properties")
    private val appBuildFile = File(repoRoot, "app/build.gradle.kts")
    private val versionCatalog = File(repoRoot, "gradle/libs.versions.toml")
    private val releaseManifest = File(repoRoot, "app/src/main/AndroidManifest.xml")

    @Test
    fun guardLocatesBuildFilesFromTestWorkingDirectory() {
        // 防呆：路径一旦算错，下面每个测试都会抛 FileNotFoundException 而不是给出
        // 有意义的断言失败——看上去像守卫生效了，实际一条断言都没跑。
        listOf(gradleProperties to "gradle.properties",
            appBuildFile to "app/build.gradle.kts",
            versionCatalog to "gradle/libs.versions.toml",
            releaseManifest to "app/src/main/AndroidManifest.xml").forEach { (file, name) ->
            assertTrue("未能定位 $name（repoRoot=$repoRoot）", file.isFile)
        }
    }

    @Test
    fun leakcanaryIsDisabledForReleaseByDefault() {
        assertEquals(
            "release 包不得默认携带 LeakCanary：它会往桌面塞一个 Leaks 图标入口，"
                + "且靠 ContentProvider 在启动阶段自动初始化，与宿主 Application 冲突。"
                + "需要时用 -Ptianxuan.leakcanary=true 临时开启。",
            "false", gradlePropertyValue("tianxuan.leakcanary"),
        )
    }

    /**
     * 读取 gradle.properties 的**有效配置值**：跳过注释行与空行。
     *
     * 直接对整个文件跑正则会把注释里的示例值当配置读出来——
     * 上面那行 `# 需要临时开启：./gradlew assembleRelease -Ptianxuan.leakcanary=true`
     * 就会被 `tianxuan\.leakcanary\s*=\s*(\S+)` 匹配成 true，于是「配置明明是 false、
     * 测试却说该true」，守卫生效的方向完全反了。这类测试比没有测试更坏：
     * 它让人以为配置已生效。
     */
    private fun gradlePropertyValue(key: String): String? =
        gradleProperties.readLines()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() && !it.startsWith("#") && it.startsWith("$key=") }
            ?.substringAfter("$key=")
            ?.trim()
            ?.lowercase()

    @Test
    fun leakcanaryBuildFlagDefaultsToFalseWhenPropertyAbsent() {
        // 即使 gradle.properties 被删掉这一行，build.gradle.kts 里的兜底也必须是 false。
        // 两处默认值必须一致，否则「哪一处生效」要看 Gradle 的属性注入时机，
        // 出现过实际带着 LeakCanary 构建出包、配置却写着 false 的情况。
        val script = appBuildFile.readText()
        assertTrue("app/build.gradle.kts 应仍通过属性开关控制 LeakCanary", script.contains("tianxuan.leakcanary"))
        val fallback = Regex("""tianxuanLeakCanary[\s\S]{0,220}?:\s*false""").find(script)
        assertTrue(
            "app/build.gradle.kts 中 tianxuanLeakCanary 的兜底默认值必须是 false",
            fallback != null,
        )
    }

    @Test
    fun leakcanaryStaysScopedToDebugVariant() {
        val script = appBuildFile.readText()
        // 允许：debug 恒开（排障主力），release 仅在显式传参时开。
        assertTrue("debug 变体应保留 LeakCanary（内测排障主力）", script.contains("debugImplementation(libs.leakcanary.android)"))
        assertTrue(
            "release 变体必须在开关为真时才引入 LeakCanary",
            Regex("""if\s*\(\s*tianxuanLeakCanary\s*\)\s*\{[\s\S]{0,120}?releaseImplementation\(libs\.leakcanary\.android\)""")
                .find(script) != null,
        )
        // 不允许：开关块**之外**还有裸的 releaseImplementation(leakcanary)。
        // 注意不能简单匹配「行首 releaseImplementation」——条件引入那一行
        // 本身就以它开头（只是缩进更深），那样断言会对着正确的写法报错，
        // 逼人去删掉唯一的开关、把 LeakCanary 改成无条件引入。守卫一旦指错方向，
        // 比没有守卫更危险。
        val strayRelease = appBuildFile.readLines()
            .mapIndexed { index, line -> index to line.trim() }
            .filter { (_, line) -> line.startsWith("releaseImplementation(libs.leakcanary") }
            .filter { (index, _) -> !isGuardedByLeakCanaryFlag(index) }
        assertTrue(
            "releaseImplementation(leakcanary) 必须包在 if (tianxuanLeakCanary) 里，"
                + "否则 LeakCanary 无条件进入发布包（命中行号：${strayRelease.joinToString { "${it.first + 1}" }}）",
            strayRelease.isEmpty(),
        )
    }

    /**
     * 判断第 [target] 行是否受 `if (tianxuanLeakCanary)` 开关保护。
     *
     * 做法：向上找最近一行「实质性代码」（跳过空行、注释、大括号），
     * 它必须是开关块的 `if (...) {` 或其续行。之所以不靠大括号计数推断，
     * 是因为那会把嵌套块里的依赖也当成被保护——一旦误判，守卫就会对着
     * 正确写法报错。向上找控制语句是直接得多、也更符合意图的做法。
     */
    private fun isGuardedByLeakCanaryFlag(target: Int): Boolean {
        val lines = appBuildFile.readLines()
        for (i in target - 1 downTo 0) {
            val code = lines[i].substringBefore("//").trim()
            if (code.isEmpty() || code == "{" || code == "}") continue
            // 命中开关声明；`{` 落在下一行也算同一控制语句。
            if (code.startsWith("if (tianxuanLeakCanary)") || code == "if (tianxuanLeakCanary)") return true
            // 遇到别的实质性控制语句/依赖声明即判定为「未被保护」。
            return false
        }
        return false
    }

    @Test
    fun leakcanaryIsNotDeclaredAsRuntimeDependency() {
        // implementation 会把 LeakCanary 带进所有变体，包括 debug 构建的发布分支，
        // 一旦某天 release 忘了删就会复发。必须是 debugImplementation + 条件 release。
        val script = appBuildFile.readText()
        val lines = script.lines()
        lines.forEachIndexed { index, line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("implementation(libs.leakcanary")) {
                throw AssertionError(
                    "第 ${index + 1} 行用了 implementation(libs.leakcanary...)：" +
                        "它会进所有变体，release 必然带上 LeakCanary。应用 debugImplementation + 条件 releaseImplementation。",
                )
            }
        }
    }

    @Test
    fun versionCatalogKeepsLeakCanaryAvailableForDebug() {
        // 反向约束：不能为了关掉 release 顺手把依赖本身也删了——debug 排障仍需要它。
        val catalog = versionCatalog.readText()
        assertTrue("libs.versions.toml 应保留 leakcanary-android 定义供 debug 使用",
            catalog.contains("leakcanary-android"))
    }

    @Test
    fun releaseManifestNeverDeclaresLeakCanaryComponents() {
        // 源码 manifest 里显式声明 LeakCanary 组件，等于绕过开关硬塞进 release。
        val manifest = releaseManifest.readText()
        listOf("LeakLauncherActivity", "LeakActivity", "PlumberInstaller", "leakcanary")
            .forEach { marker ->
                assertFalse(
                    "app/src/main/AndroidManifest.xml 不得声明 LeakCanary 组件（$marker）：" +
                        "manifest 声明不受 -Ptianxuan.leakcanary 开关控制，会直接打进所有变体",
                    manifest.contains(marker),
                )
            }
    }

    @Test
    fun releaseManifestKeepsLeakCanaryEnabledFlagDocumentedAsOptional() {
        // 防回归说明：若将来确实要在 release 带 LeakCanary，必须同时满足——
        // 用户可见的桌面入口（Leaks 图标）与启动崩溃都不得出现。
        // 这里只做提醒型断言：确认源码 manifest 没有 tools:node="remove" 之类
        // 试图在 release 里局部摘除组件的写法（局部摘除需要 manifest 占位符，
        // 极易与开关逻辑互相掩盖，导致「配置说关了、包里还在」）。
        val manifest = releaseManifest.readText()
        assertFalse(
            "不得用 tools:node=remove 在源码 manifest 里局部摘除 LeakCanary 组件：" +
                "它会与 tianxuan.leakcanary 开关互相掩盖，出现「配置为 false、包里仍有」的假象",
            manifest.contains("tools:node=\"remove\"") && manifest.contains("leakcanary"),
        )
    }

    private fun assertNull(message: String, value: Any?) {
        assertTrue(message, value == null)
    }
}