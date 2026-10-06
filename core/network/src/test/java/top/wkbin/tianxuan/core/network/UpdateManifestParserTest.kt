package top.wkbin.tianxuan.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 版本比对与清单解析的不变量测试。
 *
 * 这两处直接决定「是否提示更新」与「下载链接是否正确」，
 * 一旦回归会导致用户装不上新包，故必须有测试兜住。
 */
class UpdateManifestParserTest {

    // ---------- 版本比对 ----------

    @Test
    fun `新版本大于当前版本时判定为有更新`() {
        assertTrue(UpdateManifestParser.isNewerVersion("0.21.0", "0.20.0"))
        assertTrue(UpdateManifestParser.isNewerVersion("1.0.0", "0.99.99"))
        assertTrue(UpdateManifestParser.isNewerVersion("0.20.1", "0.20.0"))
    }

    @Test
    fun `相同或更低的版本判定为无更新`() {
        assertFalse(UpdateManifestParser.isNewerVersion("0.20.0", "0.20.0"))
        assertFalse(UpdateManifestParser.isNewerVersion("0.19.9", "0.20.0"))
        assertFalse(UpdateManifestParser.isNewerVersion("0.9.0", "0.20.0"))
    }

    @Test
    fun `位数不同时按缺失位补零比较`() {
        assertTrue(UpdateManifestParser.isNewerVersion("0.21", "0.20.9"))
        assertFalse(UpdateManifestParser.isNewerVersion("0.20", "0.20.0"))
        assertFalse(UpdateManifestParser.isNewerVersion("0.20.0", "0.20"))
    }

    @Test
    fun `带预发布后缀时只比较前置数字段`() {
        // 0.20.1-dev 相对 0.20.0 应视为更新
        assertTrue(UpdateManifestParser.isNewerVersion("0.20.1-dev", "0.20.0"))
        // 0.20.0-dev 相对 0.20.0 不算更新（同一版本号的预发布）
        assertFalse(UpdateManifestParser.isNewerVersion("0.20.0-dev", "0.20.0"))
        assertFalse(UpdateManifestParser.isNewerVersion("0.20.0-rc1", "0.20.0"))
    }

    @Test
    fun `空版本号不判定为更新`() {
        assertFalse(UpdateManifestParser.isNewerVersion("", "0.20.0"))
        assertFalse(UpdateManifestParser.isNewerVersion("0.20.0", ""))
        assertFalse(UpdateManifestParser.isNewerVersion("", ""))
    }

    @Test
    fun `无数字的版本号不抛异常`() {
        // 曾经因 mapNotNull 后 getOrElse 的边界处理不当而崩溃，此处锁死
        UpdateManifestParser.isNewerVersion("unknown", "0.20.0")
        UpdateManifestParser.isNewerVersion("0.20.0", "unknown")
        UpdateManifestParser.isNewerVersion("a.b.c", "x.y.z")
    }

    // ---------- 清单解析 ----------

    private val githubStyle = """
        {
          "tag_name": "v0.21.0",
          "name": "天玄 0.21.0",
          "body": "北斗为纲",
          "html_url": "https://github.com/o/r/releases/tag/v0.21.0",
          "published_at": "2026-10-07T00:00:00Z",
          "assets": [
            {"name": "sha256sums.txt", "size": 100, "browser_download_url": "https://x/sums.txt"},
            {"name": "tianxuan-v0.21.0.apk", "size": 51349736,
             "browser_download_url": "https://x/tianxuan.apk"}
          ]
        }
    """.trimIndent()

    private val distStyle = """
        {
          "version": "0.21.0",
          "tag_name": "v0.21.0",
          "name": "天玄 0.21.0",
          "published_at": "2026-10-07T00:00:00Z",
          "assets": [
            {"name": "tianxuan-v0.21.0.apk", "size": 51349736,
             "sha256": "abc123", "browser_download_url": "https://d/apk/0.21.0"}
          ]
        }
    """.trimIndent()

    @Test
    fun `解析 GitHub 风格的清单`() {
        val info = UpdateManifestParser.parseLatest(githubStyle, "0.20.0", "https://fallback")
        requireNotNull(info)
        assertEquals("0.21.0", info.latestVersion)
        assertEquals("0.20.0", info.currentVersion)
        assertTrue(info.hasUpdate)
        assertEquals("天玄 0.21.0", info.releaseTitle)
        assertEquals("北斗为纲", info.releaseNotes)
        assertEquals("https://x/tianxuan.apk", info.apkDownloadUrl)
        assertEquals(51349736L, info.apkSizeBytes)
    }

    @Test
    fun `解析自建服务风格的清单`() {
        val info = UpdateManifestParser.parseLatest(distStyle, "0.20.0", "https://fallback")
        requireNotNull(info)
        assertEquals("0.21.0", info.latestVersion)
        assertTrue(info.hasUpdate)
        // 无 body 时应回落到备用链接而不是空串
        assertEquals("https://fallback", info.releaseUrl)
        assertEquals("https://d/apk/0.21.0", info.apkDownloadUrl)
    }

    @Test
    fun `跳过非 apk 资产`() {
        val info = UpdateManifestParser.parseLatest(githubStyle, "0.20.0", "https://f")
        // 第一个资产是 sha256sums.txt，不应被当作安装包
        assertTrue(info!!.apkDownloadUrl!!.endsWith(".apk"))
    }

    @Test
    fun `无 apk 资产时下载地址为空而非报错`() {
        val noApk = """{"tag_name":"v0.21.0","assets":[{"name":"notes.txt","size":1}]}"""
        val info = UpdateManifestParser.parseLatest(noApk, "0.20.0", "https://f")
        requireNotNull(info)
        assertNull(info.apkDownloadUrl)
        assertTrue(info.hasUpdate)
    }

    @Test
    fun `无 assets 字段时也能解析`() {
        val bare = """{"tag_name":"v0.21.0","name":"天玄"}"""
        val info = UpdateManifestParser.parseLatest(bare, "0.20.0", "https://f")
        requireNotNull(info)
        assertEquals("0.21.0", info.latestVersion)
        assertNull(info.apkDownloadUrl)
    }

    @Test
    fun `空清单视为无效并返回 null`() {
        // 自建服务无版本时返回空 tag_name，此时应让调用方回退到下一个候选源
        val empty = """{"tag_name":"","name":"","assets":[]}"""
        assertNull(UpdateManifestParser.parseLatest(empty, "0.20.0", "https://f"))
    }

    @Test
    fun `非法 JSON 返回 null 而不抛异常`() {
        assertNull(UpdateManifestParser.parseLatest("not json", "0.20.0", "https://f"))
        assertNull(UpdateManifestParser.parseLatest("", "0.20.0", "https://f"))
        assertNull(UpdateManifestParser.parseLatest("[]", "0.20.0", "https://f"))
    }

    @Test
    fun `解析 tag 更新说明`() {
        assertEquals("北斗为纲", UpdateManifestParser.parseTagNotes(githubStyle))
        assertNull(UpdateManifestParser.parseTagNotes("""{"tag_name":"v1"}"""))
        assertNull(UpdateManifestParser.parseTagNotes("garbage"))
    }
}