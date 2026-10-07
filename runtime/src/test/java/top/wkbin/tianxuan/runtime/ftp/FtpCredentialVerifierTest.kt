package top.wkbin.tianxuan.runtime.ftp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

/**
 * FTP 口令比对。
 *
 * 重心在「拒绝」这一侧，与 [FtpBounceGuardTest] 同构：放行的场景反而很少出错，
 * 真正会出事故的是「本该拒绝却放行」。其中最致命的一条是期望口令未设置时
 * 放行任意口令——那等于向同网段开放无密码的 rootfs 读写。
 */
class FtpCredentialVerifierTest {

    @Test
    fun `correct password matches`() {
        assertTrue(FtpCredentialVerifier.passwordMatches("s3cret", "s3cret"))
    }

    @Test
    fun `wrong password is rejected`() {
        assertFalse(FtpCredentialVerifier.passwordMatches("s3cret", "s3cret "))
        assertFalse(FtpCredentialVerifier.passwordMatches("s3cret", "S3cret"))
        assertFalse(FtpCredentialVerifier.passwordMatches("", "s3cret"))
        assertFalse(FtpCredentialVerifier.passwordMatches("s3cret", "s3cre"))
        assertFalse(FtpCredentialVerifier.passwordMatches("s3cre", "s3cret"))
    }

    @Test
    fun `unset expected password rejects every candidate`() {
        // 决定性守卫：修复前是 `isNullOrBlank() || pass == password`，
        // 未设密码时放行一切；免密访问必须走 anonymousEnabled 这一条显式入口。
        assertFalse(FtpCredentialVerifier.passwordMatches("anything", null))
        assertFalse(FtpCredentialVerifier.passwordMatches("", null))
        assertFalse(FtpCredentialVerifier.passwordMatches("anything", ""))
        assertFalse(FtpCredentialVerifier.passwordMatches("anything", "   "))
        // 候选侧即使为空白也不能「撞上」空白期望口令
        assertFalse(FtpCredentialVerifier.passwordMatches("   ", "   "))
    }

    @Test
    fun `comparison is byte exact for unicode and control characters`() {
        // 逐字节比较，不做 trim / 折叠 / 归一化：任何「顺手做的宽容」都是口令面扩大
        assertTrue(FtpCredentialVerifier.passwordMatches("口令-ÆØÅ", "口令-ÆØÅ"))
        assertFalse(FtpCredentialVerifier.passwordMatches("口令", "口令\n"))
        assertFalse(FtpCredentialVerifier.passwordMatches("口\uFB01令", "口fi令"))
    }

    @Test
    fun `lookalike prefixes are not accepted`() {
        // 最容易被草率的 startsWith/contains 判断放过的形态
        assertFalse(FtpCredentialVerifier.passwordMatches("s3cret", "s3cret-and-more"))
        assertFalse(FtpCredentialVerifier.passwordMatches("s3cret-and-more", "s3cret"))
    }

    @Test
    fun `server delegates password comparison to this verifier`() {
        // 源码级守卫：AndroidFtpServer 不得退回 `pass == config.password`
        // 或 `isNullOrBlank() ||` 这类放行式判定，否则恒定时间比较被绕过。
        // 注意必须剔除注释行：修复说明里逐字引用了旧的坏判定，直接扫描会把文档当成代码。
        val source = repoFile("runtime/src/main/java/top/wkbin/tianxuan/runtime/ftp/AndroidFtpServer.kt")
        val start = source.indexOf("private fun handlePass(")
        val end = source.indexOf("private fun handleMode(")
        assertTrue("源码里已找不到 handlePass", start >= 0)
        assertTrue("handlePass 与 handleMode 的边界丢失，守卫区间不可信", end > start)
        val code = source.substring(start, end)
            .lines()
            .filterNot { it.trimStart().startsWith("//") }
            .joinToString("\n")
        assertTrue(
            "handlePass 未委托给 FtpCredentialVerifier",
            code.contains("FtpCredentialVerifier.passwordMatches("),
        )
        assertFalse("handlePass 又出现了非常量时间的 == 比较", code.contains("== config.password"))
        assertFalse("handlePass 又出现了未设密码即放行的判定", code.contains("isNullOrBlank() ||"))
    }

    private fun repoFile(relative: String): String {
        var dir = Paths.get(System.getProperty("user.dir")).toAbsolutePath()
        while (dir.parent != null && !Files.exists(dir.resolve("settings.gradle.kts"))) {
            dir = dir.parent
        }
        return Files.readString(dir.resolve(relative))
    }
}
