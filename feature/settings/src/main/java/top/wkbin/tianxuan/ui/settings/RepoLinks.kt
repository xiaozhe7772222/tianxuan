package top.wkbin.tianxuan.ui.settings

import top.wkbin.tianxuan.core.network.AppUpdateManager

/**
 * 天玄官方仓库坐标。
 *
 * 与 `core/network` 的 AppUpdateManager 及 `assets/update_source.properties` 同源，
 * 避免「关于」页与「检查更新」两处链接漂移到不同仓库。
 */
internal val REPO_URL: String = AppUpdateManager.DEFAULT_REPO_URL