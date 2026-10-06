package top.wkbin.tianxuan.core.network

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import top.wkbin.tianxuan.core.model.AppUpdateInfo
import top.wkbin.tianxuan.core.model.Community
import java.io.File
import java.io.FileOutputStream

/**
 * 天玄 · 应用版本更新管理器
 *
 * 更新源由 `assets/update_source.properties` 配置（见 [UpdateSourceConfig]）。
 * 私有仓库的 Releases API 对未认证请求返回 404，故默认走自建服务；
 * 其字段与 GitHub Releases API 同构，两端可互换，改源无需改代码。
 */
class AppUpdateManager(
    private val context: Context,
    private val httpClient: OkHttpClient,
) {
    private val config: UpdateSourceConfig = UpdateSourceConfig.load(context)

    private val repo: String get() = config.repo
    private val repoUrl: String get() = config.repoUrl

    companion object {
        const val DEFAULT_REPO = UpdateSourceConfig.DEFAULT_REPO

        /** 内测交流群。转发到 [Community]，避免此处成为第二真源 */
        const val QQ_GROUP_ID = Community.QQ_GROUP_ID

        /** 仓库主页，供「关于」页与更新失败时的回退链接使用 */
        val DEFAULT_REPO_URL: String get() = "https://github.com/$DEFAULT_REPO"
    }

    /**
     * 请求最新版本信息。按 [UpdateSourceConfig.manifestCandidates] 顺序尝试，
     * 全部失败时抛出最后一个异常。GitHub 端点仅在配置为公开仓库时可用。
     */
    suspend fun checkUpdate(currentVersionName: String): Result<AppUpdateInfo> = withContext(Dispatchers.IO) {
        val candidates = buildList {
            addAll(config.manifestCandidates)
            add(config.githubLatestApi)
        }.distinct()

        var lastError: Throwable = IllegalStateException("未配置任何更新源")
        for (url in candidates) {
            val result = runCatching { fetchManifest(url, currentVersionName) }
            result.onSuccess { info ->
                if (info != null) return@withContext Result.success(info)
                lastError = IllegalStateException("清单内容不完整: $url")
            }.onFailure { lastError = it }
        }
        Result.failure(lastError)
    }

    /** 拉取并解析单个清单地址；非版本清单返回 null */
    private fun fetchManifest(url: String, currentVersionName: String): AppUpdateInfo? {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github.v3+json, application/json")
            .header("User-Agent", "TianXuan-App/$currentVersionName")
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("更新源响应 HTTP ${response.code}")
            }
            return UpdateManifestParser.parseLatest(
                body = response.body.string(),
                currentVersionName = currentVersionName,
                fallbackUrl = config.versionsUrl.ifBlank { config.repoUrl },
            )
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
     * 请求指定版本的更新说明。优先自建服务（其 body 取自各版本的 RELEASE_NOTES.md），
     * 失败再试 GitHub tag 端点。
     */
    suspend fun fetchReleaseNotes(versionName: String): Result<String> = withContext(Dispatchers.IO) {
        val clean = versionName.substringBefore('-').trim()
        val tag = if (clean.startsWith("v")) clean else "v$clean"

        val candidates = buildList {
            config.versionsUrl.takeIf { it.isNotBlank() }?.let { add("$it?version=$clean") }
            add("https://api.github.com/repos/$repo/releases/tags/$tag")
        }.distinct()

        var lastError: Throwable = IllegalStateException("未配置更新源")
        for (url in candidates) {
            runCatching {
                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/vnd.github.v3+json, application/json")
                    .header("User-Agent", "TianXuan-App/$versionName")
                    .get()
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IllegalStateException("更新源响应 HTTP ${response.code}")
                    }
                    UpdateManifestParser.parseTagNotes(response.body.string())
                        ?: throw IllegalStateException("清单无更新说明")
                }
            }.onSuccess { notes ->
                return@withContext Result.success(notes)
            }.onFailure { lastError = it }
        }
        Result.failure(lastError)
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
}
