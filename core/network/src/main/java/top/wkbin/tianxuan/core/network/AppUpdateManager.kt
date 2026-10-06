package top.wkbin.tianxuan.core.network

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import top.wkbin.tianxuan.core.model.AppUpdateInfo
import java.io.File
import java.io.FileOutputStream

/**
 * 天玄 · 应用版本更新管理器 (GitHub Releases API)
 */
class AppUpdateManager(
    private val context: Context,
    private val httpClient: OkHttpClient,
) {
private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * 更新源。仓库坐标从 `assets/update_source.properties` 读取（键 `update.repo`），
     * 缺失时回落到 [DEFAULT_REPO]。这样换发布仓库只需改一个资源文件，
     * 不必改动本文件，也不必依赖 BuildConfig 开关。
     */
    private val repo: String = runCatching {
    context.assets.open("update_source.properties")
      .bufferedReader()
      .use { it.readText() }
    .lineSequence()
      .map { it.trim() }
      .firstOrNull { it.startsWith("update.repo") }
      ?.substringAfter('=', "")
      ?.trim()
      ?.takeIf { it.contains('/') }
    }.getOrNull() ?: DEFAULT_REPO

    private val releasesApi: String = "https://api.github.com/repos/$repo/releases/latest"

    private val repoUrl: String get() = "https://github.com/$repo"

    companion object {
    /** 未配置时的兜底仓库 */
    const val DEFAULT_REPO = "xiaozhe7772222/tianxuan"
        const val QQ_GROUP_ID = "964382207"

        /** 仓库主页，供「关于」页与更新失败时的回退链接使用 */
        val DEFAULT_REPO_URL: String get() = "https://github.com/$DEFAULT_REPO"
    }

    /**
     * 向 GitHub 请求最新 Release 信息并与当前版本比对
     */
    suspend fun checkUpdate(currentVersionName: String): Result<AppUpdateInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(releasesApi)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "TianXuan-App/${currentVersionName}")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("GitHub 响应错误 HTTP ${response.code}")
                }
                val body = response.body.string()
                val jsonElement = json.parseToJsonElement(body).jsonObject

                val tagName = jsonElement["tag_name"]?.jsonPrimitive?.content.orEmpty()
                val latestVersion = tagName.removePrefix("v").trim()
                val title = jsonElement["name"]?.jsonPrimitive?.content ?: tagName
                val bodyText = jsonElement["body"]?.jsonPrimitive?.content.orEmpty()
                val htmlUrl = jsonElement["html_url"]?.jsonPrimitive?.content ?: repoUrl
                val publishedAt = jsonElement["published_at"]?.jsonPrimitive?.content.orEmpty()

                // 查找 assets 中的 apk 文件
                var apkUrl: String? = null
                var apkSize: Long? = null
                val assets = jsonElement["assets"]?.jsonArray
                if (assets != null) {
                    for (asset in assets) {
                        val assetObj = asset.jsonObject
                        val name = assetObj["name"]?.jsonPrimitive?.content.orEmpty()
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            apkUrl = assetObj["browser_download_url"]?.jsonPrimitive?.content
                            apkSize = assetObj["size"]?.jsonPrimitive?.longOrNull
                            break
                        }
                    }
                }

                val hasUpdate = isNewerVersion(latestVersion, currentVersionName)

                AppUpdateInfo(
                    currentVersion = currentVersionName,
                    latestVersion = latestVersion.ifBlank { currentVersionName },
                    hasUpdate = hasUpdate,
                    releaseTitle = title,
                    releaseNotes = bodyText,
                    releaseUrl = htmlUrl,
                    apkDownloadUrl = apkUrl,
                    apkSizeBytes = apkSize,
                    publishedAt = publishedAt,
                )
            }
        }
    }

    /**
     * 读取内置 assets 中的 release_notes.md，用于离线或首次打开时瞬时展示
     */
    fun getBundledReleaseNotes(): String {
        return runCatching {
            context.assets.open("release_notes.md").bufferedReader().use { it.readText() }
        }.getOrDefault("")
    }

    /**
     * 向 GitHub 请求指定版本（tag）的 Release 说明
     */
    suspend fun fetchReleaseNotes(versionName: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val clean = versionName.substringBefore('-').trim()
            val tag = if (clean.startsWith("v")) clean else "v$clean"
            val request = Request.Builder()
                .url("https://api.github.com/repos/$repo/releases/tags/$tag")
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "TianXuan-App/$versionName")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("GitHub 响应错误 HTTP ${response.code}")
                }
                val body = response.body.string()
                val jsonElement = json.parseToJsonElement(body).jsonObject
                jsonElement["body"]?.jsonPrimitive?.content.orEmpty()
            }
        }
    }

    /**
     * 获取当前版本的更新说明：优先返回内置资源，若有网络且远端有效则返回远端内容
     */
    suspend fun getOrFetchCurrentReleaseNotes(currentVersionName: String): String {
        val bundled = getBundledReleaseNotes()
        val remote = fetchReleaseNotes(currentVersionName).getOrNull()
        return if (!remote.isNullOrBlank()) remote else bundled
    }

    /**
     * 下载 APK 文件并报告下载进度
     */
    suspend fun downloadApk(
        downloadUrl: String,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(downloadUrl)
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                throw IllegalStateException("下载失败 HTTP ${response.code}")
            }

            val body = response.body
            val contentLength = body.contentLength().takeIf { it > 0 }
            val downloadDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val apkFile = File(downloadDir, "tianxuan-latest.apk")
            if (apkFile.exists()) apkFile.delete()

            body.byteStream().use { input ->
                FileOutputStream(apkFile).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var downloaded = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        onProgress(downloaded, contentLength)
                    }
                    output.flush()
                }
            }

            apkFile
        }
    }

    /**
     * 调起系统安装器安装 APK
     */
    fun installApk(apkFile: File) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile,
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * 语义化版本比对：latest > current 返回 true
     */
    private fun isNewerVersion(latest: String, current: String): Boolean {
        if (latest.isBlank() || current.isBlank()) return false
        val latestParts = latest.split('.').mapNotNull { it.takeWhile { c -> c.isDigit() }.toIntOrNull() }
        val currentParts = current.split('.').mapNotNull { it.takeWhile { c -> c.isDigit() }.toIntOrNull() }

        val maxLen = maxOf(latestParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }
}
