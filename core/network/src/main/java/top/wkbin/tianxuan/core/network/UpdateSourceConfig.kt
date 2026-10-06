package top.wkbin.tianxuan.core.network

import android.content.Context

/**
 * 更新源配置。读 `assets/update_source.properties`。
 *
 * 换发布源（仓库 / 自建服务 / 镜像）只改该资源文件，不动 Kotlin 代码。
 * 私有仓库的 Releases API 对未认证请求返回 404，故默认走自建服务；
 * 其字段与 GitHub Releases API 同构，两端可无缝互换。
 */
data class UpdateSourceConfig(
    /** owner/repo，用于拼 GitHub API 与展示链接 */
    val repo: String,
    /** 版本清单完整地址（加载时已定稿） */
    val manifestUrl: String,
    /** 备用清单地址，按序尝试 */
    val fallbackUrls: List<String>,
    /** 版本详情页 */
    val versionsUrl: String,
    /** 开源仓库主页 */
    val repoUrl: String,
) {
    /** 实际请求的清单地址列表，首项优先 */
    val manifestCandidates: List<String>
        get() = (listOf(manifestUrl) + fallbackUrls)
            .filter { it.startsWith("http") }
            .distinct()

    /** GitHub Releases 兜底端点，仅当仓库可匿名访问时有效 */
    val githubLatestApi: String get() = "https://api.github.com/repos/$repo/releases/latest"

    companion object {
        const val ASSET_PATH = "update_source.properties"
        const val DEFAULT_REPO = "xiaozhe7772222/tianxuan"

        /** 自建服务的默认基址（经 nginx 反代到子路径） */
        const val DEFAULT_BASE_URL = "https://124.222.37.253"

        fun load(context: Context): UpdateSourceConfig {
            val props = runCatching {
                context.assets.open(ASSET_PATH)
                    .bufferedReader()
                    .use { it.readLines() }
                    .mapNotNull { line ->
                        val t = line.trim()
                        if (t.isEmpty() || t.startsWith("#") || t.startsWith("!")) null
                        else t.substringBefore('#').trim()
                            .takeIf { it.contains('=') }
                            ?.let { it.substringBefore('=').trim() to it.substringAfter('=').trim() }
                    }
                    .toMap()
            }.getOrDefault(emptyMap())

            val repo = props["update.repo"]?.takeIf { it.contains('/') } ?: DEFAULT_REPO
            val prefix = props["update.manifestPrefix"]
                ?.let { p -> if (p.isBlank()) "" else "/" + p.trim('/') + "/" }
                ?: "/tx-update/"
            val base = (props["update.baseUrl"]?.takeIf { it.startsWith("http") }
                ?: DEFAULT_BASE_URL).trimEnd('/')

            return UpdateSourceConfig(
                repo = repo,
                manifestUrl = props["update.manifestUrl"]?.takeIf { it.startsWith("http") }
                    ?: "$base$prefix" + "latest",
                fallbackUrls = props["update.fallbackUrls"].orEmpty()
                    .split(',')
                    .map { it.trim() }
                    .filter { it.startsWith("http") },
                versionsUrl = props["update.versionsUrl"]?.takeIf { it.startsWith("http") }
                    ?: "$base$prefix" + "versions",
                repoUrl = props["update.repoUrl"]?.takeIf { it.startsWith("http") }
                    ?: "https://github.com/$repo",
            )
        }
    }
}