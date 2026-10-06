package top.wkbin.tianxuan.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 平板常驻会话栏的布局偏好（宽度 + 是否收起）。
 *
 * 单独成文件的原因：承载偏好的 SettingsDataStore.kt 已触及 architecture-policy.json
 * 的文件尺寸棘轮上限（基线只许下调、不许上涨），因此新增偏好不再继续堆进该文件，
 * 而是以 Context 扩展的形式独立存放——与 ChatRoundCollapsePreferences.kt 同一模式。
 *
 * 为什么必须持久化：分栏宽度是用户反复调出来的手感值，每次进 App 都回到默认等于没做这个功能。
 */
private val sessionsPaneWidthKey = intPreferencesKey("chat_sessions_pane_width_dp")

/** 会话栏是否处于收起状态（收起后只留窄条）。 */
private val sessionsPaneCollapsedKey = booleanPreferencesKey("chat_sessions_pane_collapsed")

/**
 * 会话栏宽度（dp）。
 *
 * 不在这里做区间收敛：收敛规则属于 UI 度量（ui.components 的 TianXuanPaneMetrics），
 * 存进来的是用户的原始意图值。读取侧（resolveSessionsPaneWidth）按当时窗口宽度收敛，
 * 这样窗口变大时宽度还能进一步放宽，而不是被旧窗口的上限永久锁死。
 */
val Context.sessionsPaneWidth: Flow<Int>
    get() = settingsDataStore.data.map { it[sessionsPaneWidthKey] ?: 0 }

/** 写入会话栏宽度（dp）。0 表示尚未拖动过，由读取侧回落到默认宽度。 */
suspend fun Context.setSessionsPaneWidth(value: Int) {
    settingsDataStore.edit { it[sessionsPaneWidthKey] = value }
}

/** 会话栏是否已收起。缺省 false（展开）。 */
val Context.sessionsPaneCollapsed: Flow<Boolean>
    get() = settingsDataStore.data.map { it[sessionsPaneCollapsedKey] ?: false }

/** 写入会话栏收起状态。 */
suspend fun Context.setSessionsPaneCollapsed(value: Boolean) {
    settingsDataStore.edit { it[sessionsPaneCollapsedKey] = value }
}