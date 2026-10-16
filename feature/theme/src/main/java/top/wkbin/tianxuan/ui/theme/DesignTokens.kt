package top.wkbin.tianxuan.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 天玄设计系统 · 尺寸 / 圆角 / 控件 / 阴影 / 动效 token（「全面重建 UI」M3 的第一步）。
 *
 * 目的：把散落在各屏的魔法数字收敛为唯一真源；M4（导航 shell）与 M5（逐屏重建）统一引用，
 * 保证视觉一致，且批量调参只需改一处。
 *
 * 命名：`TxSpace.*` 间距、`TxRadius.*` 圆角、`TxSize.*` 控件尺寸、`TxElevation.*` 阴影、
 * `TxMotion.*` 动效时长。全部为纯 token，不依赖任何现有主题实现，可安全增量引入。
 */
object TxSpace {
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 32.dp

    /** 屏幕统一外边距（列表/卡片） */
    val screen: Dp = 16.dp

    /** 吸底栏之上的底部安全留白 */
    val bottomInset: Dp = 24.dp
}

object TxRadius {
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 20.dp

    /** 胶囊（全圆角） */
    val pill: Dp = 999.dp
}

object TxSize {
    val iconSm: Dp = 16.dp
    val icon: Dp = 20.dp
    val iconLg: Dp = 24.dp

    /** 最小可点区域（无障碍） */
    val touchTarget: Dp = 48.dp
    val listRow: Dp = 56.dp
    val avatar: Dp = 32.dp
}

object TxElevation {
    val none: Dp = 0.dp
    val low: Dp = 1.dp
    val medium: Dp = 3.dp
    val high: Dp = 6.dp
}

object TxMotion {
    /** 轻反馈：按下/勾选 */
    const val fastMs = 120

    /** 常规转场：展开/切换 */
    const val normalMs = 220

    /** 大面板/全屏转场 */
    const val slowMs = 360
}
