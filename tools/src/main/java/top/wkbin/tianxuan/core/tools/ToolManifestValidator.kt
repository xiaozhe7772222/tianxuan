package top.wkbin.tianxuan.core.tools

import top.wkbin.tianxuan.core.model.ToolManifest

/**
 * Validates the data-only part of a registry before it can affect the app.
 * Manifests never carry executable shell commands; adapters remain allow-listed
 * in the app and are selected by tool id.
 */
object ToolManifestValidator {
    private val idPattern = Regex("[a-z0-9][a-z0-9-]{1,63}")

    /**
     * `version` 直接参与本地插件的落盘路径（`plugins/<id>/<version>/`），因此必须
     * 用白名单约束字符集，而不是只判非空。此前只做 `isNotBlank()`，形如
     * `"../../shared_prefs/x"` 的版本号会让 `File(localRoot, "$id/$version")`
     * 归一化后**逃出** `files/plugins`，把攻击者可控的 ZIP 内容写到应用私有目录之外
     * （同版本覆盖分支还会先 `renameTo` 把目标搬走、再 `deleteRecursively`）。
     * 白名单同时覆盖 `1.2.0`、`1.2.0-beta.1`、`v2`、`2025.10.07` 等常见写法。
     */
    private val versionPattern = Regex("[A-Za-z0-9][A-Za-z0-9._+-]{0,63}")
    private val allowedDependencies = setOf("node", "python", "git", "curl", "ca-certificates")
    private val allowedLaunchTypes = setOf("one_shot", "pty", "web", "service", "command")
    private val allowedPermissions = setOf("NETWORK", "WORKSPACE_READ", "WORKSPACE_WRITE", "LOCAL_WEB")
    private val allowedUpdateStrategies = setOf("REINSTALL", "IN_PLACE")
    private val allowedInstallMethods = setOf("SCRIPT", "LOCAL_PACKAGE")

    fun validateAll(manifests: List<ToolManifest>): List<ToolManifest> {
        require(manifests.isNotEmpty()) { "工具清单不能为空" }
        manifests.forEach { manifest ->
            require(manifest.schemaVersion == 1) { "不支持的工具清单 Schema：${manifest.schemaVersion}" }
            require(idPattern.matches(manifest.id)) { "非法工具 ID：${manifest.id}" }
            require(manifest.name.isNotBlank()) { "工具名称不能为空：${manifest.id}" }
            require(manifest.description.isNotBlank()) { "工具描述不能为空：${manifest.id}" }
            require(manifest.publisher.length <= 128) { "工具发布者名称过长：${manifest.id}" }
            require(manifest.version.isNotBlank()) { "工具版本不能为空：${manifest.id}" }
            // 锚定匹配：即便版本串两侧被插入空白/控制字符也不会被放行。
            require(versionPattern.matches(manifest.version)) {
                "非法工具版本：${manifest.id} / ${manifest.version}"
            }
            val latest = manifest.latestVersion
            require(latest == null || latest.isNotBlank()) {
                "工具最新版本不能为空：${manifest.id}"
            }
            require(latest == null || versionPattern.matches(latest)) {
                "非法工具最新版本：${manifest.id} / $latest"
            }
            require(manifest.architectures.any { it.equals("ARM64", ignoreCase = true) }) {
                "工具不支持 ARM64：${manifest.id}"
            }
            require(manifest.launchType in allowedLaunchTypes) {
                "不支持的启动类型：${manifest.launchType}"
            }
            require(manifest.updateStrategy in allowedUpdateStrategies) {
                "不支持的更新策略：${manifest.updateStrategy}"
            }
            require(manifest.installMethod in allowedInstallMethods) { "不支持的安装方式：${manifest.installMethod}" }
            require(manifest.source in setOf("REMOTE", "LOCAL")) { "不支持的工具来源：${manifest.source}" }
            if (manifest.source == "LOCAL") {
                require(manifest.offlineOnly) { "本地插件必须声明 offlineOnly=true：${manifest.id}" }
                require(manifest.installMethod == "LOCAL_PACKAGE") { "本地插件必须使用 LOCAL_PACKAGE：${manifest.id}" }
            }
            manifest.permissions.forEach { permission ->
                require(permission in allowedPermissions) { "不支持的工具权限：$permission" }
            }
            manifest.homepage?.let { homepage ->
                require(homepage.startsWith("https://", ignoreCase = true)) {
                    "工具主页必须使用 HTTPS：${manifest.id}"
                }
            }
            manifest.servicePort?.let { port ->
                require(port in 1..65535) { "无效的本地服务端口：${manifest.id}" }
                require(manifest.launchType in setOf("web", "service")) {
                    "只有 web/service 工具可以声明本地服务：${manifest.id}"
                }
                require(manifest.servicePath.startsWith('/') && !manifest.servicePath.contains("..")) {
                    "本地服务路径不安全：${manifest.id}"
                }
            }
            if (!manifest.offlineOnly) {
                manifest.dependencies.forEach { dependency ->
                    val parsed = ManifestDependencyParser.parse(dependency)
                    require(parsed != null && parsed.name in allowedDependencies) {
                        "不支持的依赖类型：$dependency"
                    }
                }
            }
        }
        require(manifests.map { it.id }.toSet().size == manifests.size) { "工具 ID 重复" }
        return manifests
    }
}
