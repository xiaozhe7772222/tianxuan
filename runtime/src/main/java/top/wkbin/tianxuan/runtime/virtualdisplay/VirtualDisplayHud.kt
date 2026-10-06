package top.wkbin.tianxuan.runtime.virtualdisplay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.ai.assistance.showerclient.ShowerController
import com.ai.assistance.showerclient.ShowerLog
import com.ai.assistance.showerclient.ui.ShowerSurfaceView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong

/**
 * 虚拟屏悬浮窗（可视化层，Operit VirtualDisplayOverlay 的轻量替代）。
 *
 * 设计取舍：
 * - WindowManager 单例悬浮窗而非前台 Service：无通知、无 FGS 类型约束，
 *   进程存活期间持续显示，隐藏即回收；
 * - 视频渲染复用 showerclient 的 [ShowerSurfaceView]（自动等待 video size、
 *   绑定 MediaCodec 解码器与二进制帧回调）；
 * - 触摸回传：手指在悬浮窗上的操作按「视图坐标 → 视频坐标」等比映射后
 *   经 Binder 注入虚拟屏，用于人工干预 AI 的自动化过程；
 * - 纯代码构建 View（runtime 模块有 resourcePrefix 约束，避免引入布局资源）；
 *   配色克制：深灰底 + 白字，不引入主题色。
 */
object VirtualDisplayHud {

    private const val TAG = "VirtualDisplayHud"

    /** 悬浮窗宽度（dp）；高度按视频宽高比自适应。 */
    private const val WINDOW_WIDTH_DP = 300
    /** 标题条高度（dp）。 */
    private const val TITLE_BAR_HEIGHT_DP = 32
    /** 初始视频区高度（dp）：按常见 19.5:9 屏比预估，video size 就绪后校正。 */
    private const val INITIAL_VIDEO_HEIGHT_DP = 640
    /** 圆角半径（px），与天玄深色卡片风格一致的轻圆角。 */
    private const val CORNER_RADIUS_PX = 28

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * 挂载代际计数：show 的 attach 经 mainHandler 异步执行，若期间发生 hide
     * （或新一轮 show），迟到的 attach 必须放弃挂载，否则会出现"已 hide 但窗口
     * 又弹回来"的竞态。
     */
    private val generation = AtomicLong()
    private var rootView: View? = null
    private var windowManager: WindowManager? = null
    private var scope: CoroutineScope? = null

    /** 当前展示的会话 ID（null = 未显示）。 */
    @Volatile
    var showingSessionId: String? = null
        private set

    /**
     * 显示指定会话的虚拟屏悬浮窗。
     * 调用前需确保会话已建屏（[VirtualDisplayCoordinator.ensureVirtualDisplay]）。
     */
    @Synchronized
    fun show(context: Context, sessionId: String, coordinator: VirtualDisplayCoordinator) {
        if (showingSessionId == sessionId && rootView != null) {
            ShowerLog.d(TAG, "show: 悬浮窗已在展示 session=$sessionId")
            return
        }
        hide()
        val gen = generation.incrementAndGet()
        val appContext = context.applicationContext
        mainHandler.post {
            if (generation.get() != gen) {
                ShowerLog.d(TAG, "show: 丢弃迟到的挂载请求（已被 hide 或新一轮 show 取代）")
                return@post
            }
            runCatching { attach(appContext, sessionId, controller = coordinator.controller(sessionId)) }
                .onFailure { ShowerLog.e(TAG, "show: 悬浮窗挂载失败", it) }
        }
    }

    /** 隐藏并释放悬浮窗。 */
    @Synchronized
    fun hide() {
        generation.incrementAndGet()
        showingSessionId = null
        scope?.cancel()
        scope = null
        val view = rootView ?: return
        rootView = null
        mainHandler.post {
            runCatching {
                windowManager?.removeView(view)
            }.onFailure { ShowerLog.w(TAG, "hide: 移除悬浮窗失败（可能已随窗口销毁）: ${it.message}") }
            windowManager = null
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attach(context: Context, sessionId: String, controller: ShowerController) {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        val density = context.resources.displayMetrics.density
        val px = { dp: Int -> (dp * density).toInt() }

        // ---- 根容器：深灰圆角卡片 ----
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#EE1C1C1E"))
                cornerRadius = CORNER_RADIUS_PX.toFloat()
            }
        }

        // ---- 标题条：拖动区 + 会话名 + 关闭按钮 ----
        val titleBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(px(12), px(4), px(4), px(4))
        }
        val title = TextView(context).apply {
            text = "虚拟屏 · $sessionId"
            setTextColor(Color.WHITE)
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val closeButton = TextView(context).apply {
            text = "✕"
            setTextColor(Color.parseColor("#AAAAAA"))
            textSize = 14f
            setPadding(px(12), px(4), px(12), px(4))
            setOnClickListener { hide() }
        }
        titleBar.addView(title)
        titleBar.addView(closeButton)

        // 标题条拖动悬浮窗
        val windowParamsRef = arrayOfNulls<WindowManager.LayoutParams>(1)
        var downRawX = 0f
        var downRawY = 0f
        var startWindowX = 0
        var startWindowY = 0
        titleBar.setOnTouchListener { _, event ->
            val params = windowParamsRef[0]
            when (event.actionMasked) {
                // 必须消费 DOWN，否则本 View 事件流终止，后续 MOVE 收不到、拖动失效
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startWindowX = params?.x ?: 0
                    startWindowY = params?.y ?: 0
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (params != null) {
                        params.x = startWindowX + (event.rawX - downRawX).toInt()
                        params.y = startWindowY + (event.rawY - downRawY).toInt()
                        runCatching { wm.updateViewLayout(root, params) }
                    }
                    true
                }
                else -> false
            }
        }

        // ---- 视频区：showerclient SurfaceView（自动接 MediaCodec 解码）+ 触摸回传 ----
        val videoView = TouchForwardSurfaceView(context).apply {
            bindController(controller)
            forwardController = controller
            forwardScopeSupplier = { scope }
        }

        root.addView(
            titleBar,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                px(TITLE_BAR_HEIGHT_DP),
            ),
        )
        root.addView(
            videoView,
            LinearLayout.LayoutParams(
                px(WINDOW_WIDTH_DP),
                px(INITIAL_VIDEO_HEIGHT_DP),
            ),
        )

        // video size 就绪后按实际宽高比校正视频区高度（等比、宽度不变）
        scope?.launch {
            var size = controller.getVideoSize()
            var tries = 0
            while (size == null && tries < 50) {
                kotlinx.coroutines.delay(100)
                tries++
                size = controller.getVideoSize()
            }
            val (vw, vh) = size ?: return@launch
            if (vw <= 0 || root.parent == null) return@launch
            val targetHeight = px(WINDOW_WIDTH_DP) * vh / vw
            mainHandler.post {
                runCatching {
                    videoView.layoutParams = (videoView.layoutParams as LinearLayout.LayoutParams)
                        .apply { height = targetHeight }
                    videoView.requestLayout()
                }
            }
        }

        // ---- WindowManager 参数：应用上层、不抢焦点、可触摸 ----
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = px(24)
            y = px(96)
        }
        windowParamsRef[0] = params

        // 先挂载成功再记录状态：addView 失败时保持 rootView/windowManager 为空，
        // 保证后续 hide() 的清理语义一致。
        wm.addView(root, params)
        windowManager = wm
        rootView = root
        showingSessionId = sessionId
        ShowerLog.d(TAG, "attach: 悬浮窗已挂载 session=$sessionId")
    }

    /**
     * [ShowerSurfaceView] 的触摸回传子类：把悬浮窗上的手势按视图→视频坐标
     * 等比映射后经 Binder 注入虚拟屏。
     */
    private class TouchForwardSurfaceView(context: Context) : ShowerSurfaceView(context) {

        var forwardController: ShowerController? = null
        var forwardScopeSupplier: (() -> CoroutineScope?)? = null

        private var downTime = 0L

        /**
         * 公平锁：保证注入顺序 = 事件顺序。每个 MOVE 独立 launch 协程，
         * 无锁时多个注入并发执行会乱序到达虚拟屏，滑动轨迹错乱。
         */
        private val injectMutex = Mutex()

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            val controller = forwardController ?: return true
            val scope = forwardScopeSupplier?.invoke() ?: return true
            val (vw, vh) = controller.getVideoSize() ?: return true
            val w = width.coerceAtLeast(1)
            val h = height.coerceAtLeast(1)
            val x = event.x * (vw - 1) / w
            val y = event.y * (vh - 1) / h

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downTime = event.downTime
                    scope.launch { inject(controller, MotionEvent.ACTION_DOWN, x, y, event) }
                }
                MotionEvent.ACTION_MOVE -> {
                    scope.launch { inject(controller, MotionEvent.ACTION_MOVE, x, y, event) }
                }
                MotionEvent.ACTION_UP -> {
                    scope.launch {
                        inject(controller, MotionEvent.ACTION_UP, x, y, event)
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    scope.launch { inject(controller, MotionEvent.ACTION_CANCEL, x, y, event) }
                }
            }
            return true
        }

        private suspend fun inject(
            controller: ShowerController,
            action: Int,
            x: Float,
            y: Float,
            event: MotionEvent,
        ) {
            injectMutex.withLock {
                runCatching {
                    controller.injectTouchEvent(
                        action = action,
                        x = x,
                        y = y,
                        downTime = downTime,
                        eventTime = event.eventTime,
                        pressure = if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) 0f else 1f,
                        size = 1f,
                        metaState = 0,
                        xPrecision = 1f,
                        yPrecision = 1f,
                        deviceId = 0,
                        edgeFlags = 0,
                    )
                }.onFailure { ShowerLog.w(TAG, "触摸回传失败: ${it.message}") }
            }
        }
    }
}
