package top.wkbin.tianxuan.runtime.virtualdisplay

import android.content.Context
import android.provider.Settings
import com.ai.assistance.showerclient.ShowerBinderRegistry
import com.ai.assistance.showerclient.ShowerController
import com.ai.assistance.showerclient.ShowerEnvironment
import com.ai.assistance.showerclient.ShowerServerManager
import com.ai.assistance.showerclient.ShowerVideoRenderer
import top.wkbin.tianxuan.core.common.logging.AppLogger
import top.wkbin.tianxuan.core.model.ExecutionMode
import top.wkbin.tianxuan.runtime.privilege.PrivilegeAvailability
import top.wkbin.tianxuan.runtime.privilege.PrivilegeManager
import java.util.concurrent.ConcurrentHashMap

/**
 * 虚拟屏能力门面（多会话版）。
 *
 * 职责：
 * 1. 构造时把 [TianxuanShowerShellRunner] 装配进 [ShowerEnvironment]——这是 showerclient
 *    "宿主无关"设计的唯一注入点，Koin 单例首次创建即完成装配；
 * 2. 按 [sessionId] 维护相互独立的虚拟屏会话（每会话一块虚拟屏，可多 Agent 并行）；
 * 3. 对上暴露「确保虚拟屏 → 启动应用 → 截图 / 输入 → 关闭」的完整闭环，
 *    GUI 原语执行见 [VirtualScreenToolkit]，Agent 工具入口见 harness ToolExecutor 的
 *    virtual_screen_* 分支。
 *
 * 前置条件：需要 Shizuku（ADB 级）或 Root 特权；PRoot 模式下所有 shell 命令
 * 会在 [PrivilegeManager] 处失败，本门面相应返回 null/false。
 */
class VirtualDisplayCoordinator(
    private val context: Context,
    private val privilegeManager: PrivilegeManager,
    private val logger: AppLogger,
) {

    private val sessions = ConcurrentHashMap<String, ShowerController>()

    /** 最近一次虚拟屏操作失败的精确原因（供工具调用回显，替代笼统的"权限不足"）。 */
    @Volatile
    var lastFailureReason: String? = null

    init {
        ShowerEnvironment.shellRunner = TianxuanShowerShellRunner(privilegeManager, logger)
        // 追加更可靠的 workDir 候选：第三方库默认只有 /data/local/tmp 与
        // /data/data/com.android.shell/files，部分 ROM（小米 Android 16 实测）两者都不可写，
        // 导致 shower-server 无法部署。/dev/shm 是 tmpfs，shell uid 天然可写，可靠兜底。
        ShowerEnvironment.workDirCandidates = ShowerEnvironment.workDirCandidates + listOf(
            "/dev/shm",
            "/sdcard/Android/data/com.android.shell/files",
        )
    }

    /** shower-server Binder 是否已就绪（收到 SHOWER_BINDER_READY 广播且未死亡） */
    val isServerReady: Boolean
        get() = ShowerBinderRegistry.hasAliveService()

    /** 当前活跃会话 ID 集合 */
    val activeSessionIds: Set<String>
        get() = sessions.keys.toSet()

    /** 取指定会话的 controller（不存在则创建，仅持有本地状态，不触发 server 交互） */
    fun controller(sessionId: String): ShowerController =
        sessions.getOrPut(sessionId) { ShowerController() }

    /** 指定会话当前虚拟屏 displayId（未创建为 null） */
    fun getDisplayId(sessionId: String): Int? = sessions[sessionId]?.getDisplayId()

    /** 指定会话的视频流尺寸（未创建或未收到流为 null） */
    fun getVideoSize(sessionId: String): Pair<Int, Int>? = sessions[sessionId]?.getVideoSize()

    /**
     * 确保 shower-server 已启动，并为指定会话创建/复用一块与主屏同尺寸同密度的虚拟屏。
     *
     * @param sessionId 会话 ID；不同会话持有不同虚拟屏
     * @param bitrateKbps H.264 视频流码率；null 使用 server 默认值
     * @return 虚拟屏 displayId；server 启动失败或建屏失败返回 null
     */
    suspend fun ensureVirtualDisplay(
        sessionId: String = DEFAULT_SESSION_ID,
        bitrateKbps: Int? = null,
    ): Int? {
        val controller = controller(sessionId)

        // 1. 先检查宿主特权通道状态：若 effectiveMode 不是 SHIZUKU/ROOT（启动时被降级到
        //    PRoot，或 Shizuku 授权后未重启刷新），所有 shell 命令都会失败，resolveWorkDir
        //    会误报"候选目录均不可写"。先给出真实原因，避免误导用户查目录权限。
        val priv = privilegeManager.state.value
        if (priv.availability != PrivilegeAvailability.ACTIVE ||
            priv.effectiveMode == ExecutionMode.PROOT
        ) {
            lastFailureReason = buildString {
                append("宿主特权通道未生效：当前 effectiveMode=${priv.effectiveMode}，")
                append("availability=${priv.availability}。原因：${priv.reason}。")
                append("请在天玄设置 → 系统保活与开发者诊断 → 宿主特权模式中切换到 Shizuku，")
                append("或重启天玄 App 让特权状态在冷启动时重新校验。")
                if (!priv.shizukuAvailable) {
                    append("（Shizuku 服务当前不可用：请打开 Shizuku App 并确保服务已启动、权限已授予）")
                }
            }
            logger.w("虚拟屏 ensureVirtualDisplay 被拦：特权通道未生效（$priv）")
            return null
        }

        if (!ShowerServerManager.ensureServerStarted(context)) {
            // 主动诊断：特权通道已生效但 server 启动失败。用同一条 Shizuku/Root 通道直接
            // 探测真实目录可写性与 stderr，区分「通道执行失败」与「目录真的不可写」——
            // 用户实测 /data/local/tmp 可写却判定失败，说明大概率是通道执行的问题。
            val probe = privilegeManager.executeShellCommand(
                "D=/data/local/tmp; touch \$D/.tianxuan_probe 2>&1; E1=\$?; rm -f \$D/.tianxuan_probe 2>/dev/null; echo PROBE_EXIT=\$E1; touch \$D/.tianxuan_probe2 2>&1; echo PROBE2_EXIT=\$?; rm -f \$D/.tianxuan_probe2",
            )
            lastFailureReason = buildString {
                append("shower-server 启动失败：特权通道已生效（${priv.effectiveMode}）但工作目录探测均失败。")
                append("已尝试：${ShowerEnvironment.workDirCandidates}。")
                if (probe.success) {
                    append("主动探测 /data/local/tmp 结果：${probe.stdout.take(200)}（exitCode=${probe.exitCode}）。")
                    append("若 PROBE_EXIT=0 但 server 仍判定不可写，说明 ShowerServerManager 的探测命令在某一步失败，")
                    append("需查看 logcat（tag: ShowerServerManager）。")
                } else {
                    append("特权通道执行探测命令失败（exitCode=${probe.exitCode}）：${probe.stderr.take(300)}。")
                    append("这指向 Shizuku UserService 通道异常（binder 中断/未连接），而非目录权限问题。")
                }
            }
            logger.w("虚拟屏 server 启动失败：工作目录探测均失败；主动探测 exit=${probe.exitCode} stderr=${probe.stderr.take(200)}")
            return null
        }
        val metrics = context.resources.displayMetrics
        val ok = controller.ensureDisplay(
            context = context,
            width = metrics.widthPixels,
            height = metrics.heightPixels,
            dpi = metrics.densityDpi,
            bitrateKbps = bitrateKbps,
        )
        if (!ok) {
            lastFailureReason = "虚拟屏创建失败（shower-server 已启动但 ensureDisplay 失败，session=$sessionId）。可能是屏幕镜像参数不匹配或 Binder 通信中断，请查看 logcat。"
            logger.w("虚拟屏创建失败（session=$sessionId，server 已启动）")
            return null
        }
        lastFailureReason = null
        return controller.getDisplayId()
    }

    /** 在指定会话的虚拟屏上以 shell 身份启动第三方应用（需 Shizuku/Root；普通 App 无法做到） */
    suspend fun launchApp(sessionId: String = DEFAULT_SESSION_ID, packageName: String): Boolean =
        controller(sessionId).launchApp(packageName)

    /** 指定会话虚拟屏整屏截图（PNG 字节；server 内部为 scrcpy 式临时镜像抓帧） */
    suspend fun requestScreenshot(
        sessionId: String = DEFAULT_SESSION_ID,
        timeoutMs: Long = SCREENSHOT_TIMEOUT_MS,
    ): ByteArray? = controller(sessionId).requestScreenshot(timeoutMs)

    /**
     * 显示指定会话的虚拟屏可视化悬浮窗（视频流 + 触摸回传）。
     *
     * @return false 表示缺少「显示在其他应用上层」权限（Settings.canDrawOverlays），
     *         调用方应引导用户到系统设置开启后重试
     */
    fun showOverlay(sessionId: String = DEFAULT_SESSION_ID): Boolean {
        if (!Settings.canDrawOverlays(context)) return false
        if (getDisplayId(sessionId) == null) return false
        VirtualDisplayHud.show(context, sessionId, this)
        return true
    }

    /** 隐藏虚拟屏可视化悬浮窗（未显示时为幂等空操作）。 */
    fun hideOverlay() {
        VirtualDisplayHud.hide()
    }

    /**
     * 销毁指定会话的虚拟屏并释放本地状态；server 进程由其空闲看护（15s 无客户端）自行退出。
     */
    suspend fun closeSession(sessionId: String) {
        if (VirtualDisplayHud.showingSessionId == sessionId) {
            hideOverlay()
        }
        val controller = sessions.remove(sessionId) ?: return
        runCatching { controller.shutdown() }
            .onFailure { logger.w("关闭虚拟屏会话失败 (session=$sessionId): ${it.message}") }
    }

    /** 关闭全部会话（App 退出或工作流整体结束时使用） */
    suspend fun closeAllSessions() {
        for (sessionId in sessions.keys.toList()) {
            closeSession(sessionId)
        }
    }

    /**
     * 内存水位哨兵联动：丢弃所有会话视频链路的等待缓冲（可再生数据）。
     * 真正的缓冲在 showerclient 渲染器内，此处仅转发，避免 app 层直接依赖 showerclient。
     */
    fun trimVideoBuffers() {
        ShowerVideoRenderer.trimAllPending()
    }

    private companion object {
        const val SCREENSHOT_TIMEOUT_MS = 3000L
        const val DEFAULT_SESSION_ID = "default"
    }
}
