package top.wkbin.tianxuan.runtime.webchat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 配对码比对。
 *
 * 该类的价值全在「拒绝」这一侧，所以测试重心也在反例：
 * 任何一处放宽都会让同网段主机无需配对即可读写工作区。
 */
class PinVerifierTest {

    @Test
    fun `matching pin is accepted`() {
        assertTrue(PinVerifier.matches("123456", "123456"))
    }

    @Test
    fun `wrong pin is rejected`() {
        assertFalse(PinVerifier.matches("123457", "123456"))
        assertFalse(PinVerifier.matches("654321", "123456"))
    }

    @Test
    fun `null candidate is rejected`() {
        // 未携带 token 的请求（如直接 GET /webchat/api/workspaces）必须被拒
        assertFalse(PinVerifier.matches(null, "123456"))
    }

    @Test
    fun `empty candidate is rejected`() {
        // 空 token 不能因为「长度不等」之外的理由通过
        assertFalse(PinVerifier.matches("", "123456"))
    }

    @Test
    fun `empty expected pin rejects everything`() {
        // pin 尚未生成（服务未启动完成）时，空 token 不得通过——
        // 否则服务在启动窗口内等同无鉴权
        assertFalse(PinVerifier.matches("", ""))
        assertFalse(PinVerifier.matches("123456", ""))
        assertFalse(PinVerifier.matches(null, ""))
    }

    @Test
    fun `prefix and extension of the pin are rejected`() {
        // 最容易在「用了 startsWith/contains」时被放过的两类输入
        assertFalse(PinVerifier.matches("12345", "123456"))
        assertFalse(PinVerifier.matches("1234567", "123456"))
        assertFalse(PinVerifier.matches("123456 ", "123456"))
        assertFalse(PinVerifier.matches(" 123456", "123456"))
    }

    @Test
    fun `pin matching is still correct for every digit position`() {
        // 逐位改写：恒定时间实现不能因为提前返回而漏判任何一位的差异
        val pin = "102938"
        for (i in pin.indices) {
            val mutated = pin.toCharArray().also {
                it[i] = if (it[i] == '9') '0' else (it[i] + 1)
            }.concatToString()
            assertFalse("第 $i 位不同也必须被拒绝", PinVerifier.matches(mutated, pin))
        }
    }
}

/**
 * 静态资源判定。
 *
 * 关键不变量：MIME 表同时充当「是否静态资源」，所以任何扩展名分类都必须
 * 在这张表里可见——不能出现「按资源取文件却给了兜底类型」。
 */
class WebChatAssetsTest {

    @Test
    fun `known extensions map to expected mime`() {
        assertTrue(WebChatAssets.mimeTypeOrNull("index.html")!!.startsWith("text/html"))
        assertTrue(WebChatAssets.mimeTypeOrNull("app.js")!!.startsWith("application/javascript"))
        assertTrue(WebChatAssets.mimeTypeOrNull("app.mjs")!!.startsWith("application/javascript"))
        assertTrue(WebChatAssets.mimeTypeOrNull("style.css")!!.startsWith("text/css"))
        assertTrue(WebChatAssets.mimeTypeOrNull("data.json")!!.startsWith("application/json"))
        assertNotNull(WebChatAssets.mimeTypeOrNull("logo.svg"))
        assertNotNull(WebChatAssets.mimeTypeOrNull("icon.png"))
    }

    @Test
    fun `unknown extension returns null so routing can take over`() {
        assertNull(WebChatAssets.mimeTypeOrNull("route"))
        assertNull(WebChatAssets.mimeTypeOrNull("chat/42"))
        assertNull(WebChatAssets.mimeTypeOrNull("archive.zip"))
        assertNull(WebChatAssets.mimeTypeOrNull("lib.wasm"))
    }

    @Test
    fun `blank and root paths are not assets`() {
        // 根路径是前端入口，必须回落到 index.html 而不是当成资源
        assertNull(WebChatAssets.mimeTypeOrNull(""))
        assertNull(WebChatAssets.mimeTypeOrNull("   "))
    }

    @Test
    fun `nested asset path resolves by its last segment`() {
        assertTrue(WebChatAssets.mimeTypeOrNull("assets/css/app.css")!!.startsWith("text/css"))
        assertTrue(WebChatAssets.mimeTypeOrNull("assets/js/app.mjs")!!.startsWith("application/javascript"))
    }

    @Test
    fun `default mime is octet-stream`() {
        assertTrue(WebChatAssets.DEFAULT_MIME == "application/octet-stream")
    }
}
