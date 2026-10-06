package top.wkbin.tianxuan.ui.chat

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import top.wkbin.tianxuan.core.datastore.sessionsPaneCollapsed
import top.wkbin.tianxuan.core.datastore.sessionsPaneWidth
import top.wkbin.tianxuan.core.datastore.setSessionsPaneCollapsed
import top.wkbin.tianxuan.core.datastore.setSessionsPaneWidth

/**
 * 平板常驻会话栏的布局状态。
 *
 * 为什么单独成类而不是塞进 ChatViewModel：
 * 1. ChatViewModel 已触及文件尺寸棘轮上限（基线只许下调），新增状态无处安放；
 * 2. 这些状态描述的是「窗口怎么分栏」，属于 UI 布局关注点，与「用户在跟模型聊什么」
 *    是两件事，混在同一个 ViewModel 里反而模糊了职责边界。
 *
 * 只存两件事：用户拖定的宽度、是否收起。
 * 宽度不在这里做区间收敛（那是 UI 度量的职责，见 resolveSessionsPaneWidth），
 * 存用户的原始意图值即可——这样换更大的屏幕时宽度还能进一步放宽。
 *
 * @param scope 写入用的协程作用域。传入组合作用域（rememberCoroutineScope），
 *        写入随界面销毁而取消；不用 application 级作用域，避免界面销毁后还在写盘。
 */
class SessionsPaneLayoutState(
    context: Context,
    private val scope: CoroutineScope,
) {
    private val appContext = context.applicationContext

    /** 用户上次拖定的会话栏宽度（dp）；0 表示未拖过，由 UI 回落到默认宽度。 */
    val paneWidth: StateFlow<Int> = appContext.sessionsPaneWidth
        .stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), 0)

    /** 会话栏是否已收起。 */
    val collapsed: StateFlow<Boolean> = appContext.sessionsPaneCollapsed
        .stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    /**
     * 记录拖拽结果。
     *
     * 只在分隔条手势结束时调用——拖拽过程中每帧写 DataStore 会造成无谓的磁盘写入。
     */
    fun setPaneWidth(widthDp: Int) {
        scope.launch { appContext.setSessionsPaneWidth(widthDp) }
    }

    /** 记录收起/展开状态。 */
    fun setCollapsed(value: Boolean) {
        scope.launch { appContext.setSessionsPaneCollapsed(value) }
    }

    private companion object {
        /**
         * 与仓库其余 StateFlow 一致的 5 秒订阅超时。
         *
         * 略长于默认值是为了避免旋转屏幕时偏好读取被反复取消重建。
         */
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/** 组合侧入口：状态实例随 Composition 存活，写入随组合作用域销毁而取消。 */
@Composable
internal fun rememberSessionsPaneLayoutState(context: Context): SessionsPaneLayoutState {
    val scope = rememberCoroutineScope()
    return remember(context.applicationContext) { SessionsPaneLayoutState(context, scope) }
}

/** 当前会话栏宽度（dp）。 */
@Composable
internal fun SessionsPaneLayoutState.paneWidthDp(): Int {
    val width by paneWidth.collectAsStateWithLifecycle()
    return width
}

/** 会话栏是否已收起。 */
@Composable
internal fun SessionsPaneLayoutState.isCollapsed(): Boolean {
    val value by collapsed.collectAsStateWithLifecycle()
    return value
}