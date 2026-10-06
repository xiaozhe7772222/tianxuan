package top.wkbin.tianxuan.core.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import top.wkbin.tianxuan.core.model.AppUpdateInfo

/**
 * 版本清单解析。字段与 GitHub Releases API 同构，
 * 因此自建服务与 GitHub 可共用同一套解析逻辑。
 */
internal object UpdateManifestParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** 解析 latest 清单；返回 null 表示该地址不是可用的版本清单 */
    fun parseLatest(body: String, currentVersionName: String, fallbackUrl: String): AppUpdateInfo? {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        val tagName = root.str("tag_name")
        if (tagName.isBlank()) return null

        val latestVersion = tagName.removePrefix("v").trim()
        val apkUrl: String?
        val apkSize: Long?
        val assets = root["assets"]?.jsonArray
        if (assets != null) {
            var u: String? = null
            var s: Long? = null
            for (asset in assets) {
                val obj = asset.jsonObject
                val name = obj.str("name")
                if (name.endsWith(".apk", ignoreCase = true)) {
                    u = obj.str("browser_download_url")
                    s = obj["size"]?.jsonPrimitive?.longOrNull
                    break
                }
            }
            apkUrl = u
            apkSize = s
        } else {
            apkUrl = null
            apkSize = null
        }

        return AppUpdateInfo(
            currentVersion = currentVersionName,
            latestVersion = latestVersion.ifBlank { currentVersionName },
            hasUpdate = isNewerVersion(latestVersion, currentVersionName),
            releaseTitle = root["name"]?.jsonPrimitive?.content ?: tagName,
            releaseNotes = root.str("body"),
            releaseUrl = root.str("html_url").ifBlank { fallbackUrl },
            apkDownloadUrl = apkUrl,
            apkSizeBytes = apkSize,
            publishedAt = root.str("published_at"),
        )
    }

    /** 解析单个 tag 的清单（取更新说明用）；失败返回 null */
    fun parseTagNotes(body: String): String? {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        return root.str("body").takeIf { it.isNotBlank() }
    }

    private fun JsonObject.str(key: String): String =
        this[key]?.jsonPrimitive?.content.orEmpty()

    /**
     * 语义化版本比对：latest > current 返回 true。
     * 非数字后缀（如 `-dev`、`-rc1`）按其前置数字段参与比较。
     */
    fun isNewerVersion(latest: String, current: String): Boolean {
        if (latest.isBlank() || current.isBlank()) return false
        val a = parts(latest)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val l = a.getOrElse(i) { 0 }
            val c = b.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }

    private fun parts(v: String): List<Int> =
        v.split('.').mapNotNull { it.takeWhile { c -> c.isDigit() }.toIntOrNull() }
}