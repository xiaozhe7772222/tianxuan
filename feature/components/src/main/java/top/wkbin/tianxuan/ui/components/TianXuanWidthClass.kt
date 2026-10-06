package top.wkbin.tianxuan.ui.components

/**
 * 天玄 · 窗口宽度分型。
 *
 * 天玄的目标设备是 11~13 寸平板（横屏为主），而当前 UI 是纯手机形态：
 * 底部 tab 栏 + 单内容区。横屏平板上这个形态的问题很具体——
 * 拇指够不到底栏、键盘弹出后内容区被压得只剩一两行、
 * 会话列表与对话内容只能二选一。
 *
 * 断点取 Material 3 WindowSizeClass 的紧凑/中等分界（600dp）与展开分界（840dp），
 * 不自造数值：Material 的断点是 Android 生态共识，跟 Compose 自带的
 * `calculateWindowSizeClass` 对齐后，将来换官方组件无需重调。
 *
 * 这里刻意不引用 material3-adaptive：本项目内测期不引新依赖（离线包体积与
 * 构建时间是内测期的实际约束），而三栏所需的只是断点判定 + 布局分支，
 * 自行实现成本更低、也不改变依赖图。
 */
enum class TianXuanWidthClass {
    /** 手机竖屏：底部 tab 栏，单内容区。 */
    Compact,

    /** 小平板竖屏 / 分屏窄窗：常驻窄侧栏（图标 + 标签），单内容区。 */
    Medium,

    /** 11~13 寸平板横屏：常驻宽侧栏 + 内容区，列表型页面可再分第三栏。 */
    Expanded;

    /** 是否使用常驻侧栏替代底部栏。Compact 用底栏——窄屏下侧栏会挤掉内容。 */
    val usesPermanentNav: Boolean get() = this != Compact

    /** 侧栏是否显示文字标签。Medium 横屏也未必够宽，只留图标。 */
    val showsNavLabels: Boolean get() = this == Expanded
}

/**
 * 断点常量（dp）。
 *
 * 840 而不是 840+：11 寸平板横屏常见的 1280×800 dp 在中等区间，
 * 但 13 寸的 1366×1024 dp 会落入展开区间。用 840 作为展开线，
 * 使主流 11 寸横屏也能拿到三栏。
 */
object TianXuanBreakpoints {
    /** 中等宽度下界：600dp。低于此用底部栏。 */
    const val MEDIUM_MIN_DP: Int = 600

    /** 展开宽度下界：840dp。到此启用宽侧栏 + 三栏。 */
    const val EXPANDED_MIN_DP: Int = 840

    /** 宽侧栏固定宽度。 */
    const val RAIL_WIDTH_DP: Int = 240

    /** 中等宽度下的窄侧栏宽度（仅图标 + 标签）。 */
    const val COMPACT_RAIL_WIDTH_DP: Int = 88

    /** 第三栏（列表）最小宽度。低于此不展开第三栏，否则内容被压到不可读。 */
    const val LIST_PANE_MIN_DP: Int = 280
}

/**
 * 按可用宽度 dp 判定窗口分型。
 *
 * 传入的是**扣除系统栏与导航栏后的可用宽度**，不是屏幕物理宽度：
 * 平板横屏常有显示切割/摄像头区域，用物理宽度会高估。
 */
fun widthClassOf(availableWidthDp: Int): TianXuanWidthClass = when {
    availableWidthDp < TianXuanBreakpoints.MEDIUM_MIN_DP -> TianXuanWidthClass.Compact
    availableWidthDp < TianXuanBreakpoints.EXPANDED_MIN_DP -> TianXuanWidthClass.Medium
    else -> TianXuanWidthClass.Expanded
}

/**
 * 该宽度下第三栏是否应展开。
 *
 * 判定不看窗口宽度而看「扣掉侧栏后内容区还剩多少」：展开宽度下侧栏占 240dp，
 * 若内容区不足一张列表 + 一个详情，就不该展开——否则详情被压成窄条，
 * 比不做三栏更难用。
 */
fun shouldExpandListPane(
    widthClass: TianXuanWidthClass,
    availableWidthDp: Int,
): Boolean = widthClass == TianXuanWidthClass.Expanded &&
    availableWidthDp - TianXuanBreakpoints.RAIL_WIDTH_DP >=
        TianXuanBreakpoints.LIST_PANE_MIN_DP * 2