package top.wkbin.tianxuan.lifecycle

import android.content.Context
import android.net.wifi.WifiManager
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 进程级算力保活协调者（P0 借鉴自 PalmClaw GatewayRuntimeSupervisor）。
 *
 * ## 设计原则
 * 全进程持有**唯一一个** WakeLock 与 WifiLock。任何需要后台保活的模块
 * （Agent 推理、工作流执行、Runtime PRoot 服务）调用 [acquireLease] 申请租约；
 * 当所有租约全部释放后再统一释放锁，彻底消除以下竞态：
 *
 * - AgentFGS 先停 → Agent WakeLock 提前释放 → CPU 被冻结 → 沙箱构建中断
 * - RuntimeFGS 先停 → WifiLock 消失 → Agent 继续跑但 Wi-Fi 断连 → 网络请求超时
 *
 * ## 使用方式
 * ```kotlin
 * val lease = supervisor.acquireLease("agent")
 * // ... 执行需要保活的工作
 * lease.close()   // 或使用 use { } 块
 * ```
 *
 * ## 线程安全
 * 内部 `holders` 集合由 `synchronized(this)` 保护，可安全地从任意线程调用。
 *
 * ## 持有者身份
 * 集合里装的是**每次 acquire 生成的唯一 id**，而不是调用方传来的 [holderId]。
 * 这一点是必需的，不是风格选择：
 *
 * 同一 [holderId] 被两处独立持有是常态。以 Agent 前台服务为例，
 * [acquireLease] 在 `onStartCommand` 与后续运行态观察里都会调用，两次拿到的是
 * 两个**不同的**租约句柄（`powerLease` 只记得住最后一个，早先那个句柄已经无人
 * 引用）。若集合按裸 [holderId] 去重，第二个句柄 `close()` 会把第一个仍在使用
 * 的持有一起抹掉——`holders` 变空 → 立刻 `releaseLocks()` 释放 WakeLock /
 * WifiLock ——于是 Agent 推理或 PRoot 构建会在息屏后被系统冻结。
 * 这正是本类注释开头声称要消除的那类竞态，只是换了个入口。
 *
 * 用唯一 id 而非直接改成计数器，是为了保住 [holderSnapshot] 的诊断价值：
 * 它既给出「有几个在途持有」，也给出「分别是谁申请的」。
 */
class RuntimeLifecycleSupervisor(
    private val context: Context,
) {

    /** 当前是否有任意活跃租约（即锁是否 held）。 */
    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    /**
     * 活跃租约 → 申请方 holderId，所有访问必须持有对象锁。
     *
     * 键形如 `agent#3`（holderId + 自增序号），见类注释「持有者身份」。
     */
    private val holders = mutableMapOf<String, String>()

    /** 租约序号，仅在持有对象锁时递增，用于生成全局唯一的持有 id。 */
    private var nextHolderSeq = 0L

    /** 进程唯一 CPU 唤醒锁，防止息屏后 CPU 休眠冻结推理 / PRoot 进程。 */
    private var wakeLock: PowerManager.WakeLock? = null

    /**
     * 进程唯一 Wi-Fi 锁，防止息屏后无线电源进入省电模式导致沙箱网络断连。
     *
     * `WIFI_MODE_FULL` 已在 API 29 废弃（WIFI_MODE_FULL_HIGH_PERF 取代），
     * 但对于后台数据同步场景在各厂商 ROM 上兼容性最佳；此处保留 @Suppress 标记。
     */
    private var wifiLock: WifiManager.WifiLock? = null

    /**
     * 申请保活租约。
     *
     * - 首个租约申请时自动获取 WakeLock / WifiLock。
     * - 每次调用都产生**独立的**持有：同一 [holderId] 重复申请会各自登记一条，
     *   因此 `close()` 一个句柄只释放它自己那一次持有，不会误伤同名调用方仍在
     *   使用的租约（见类注释「持有者身份」）。
     *
     * @param holderId 申请方标识，用于日志（如 "agent" / "workflow" / "runtime-foreground-service"）。
     * @return 租约句柄；调用 [ProcessingPowerLease.close] 释放。
     */
    fun acquireLease(holderId: String): ProcessingPowerLease {
        val holderKey: String
        val totalHolders: Int
        synchronized(this) {
            val wasEmpty = holders.isEmpty()
            holderKey = "$holderId#${nextHolderSeq++}"
            holders[holderKey] = holderId
            if (wasEmpty) {
                acquireLocks()
            }
            totalHolders = holders.size
        }
        Log.d(TAG, "Lease acquired by '$holderId' (total holders: $totalHolders)")
        return object : ProcessingPowerLease {
            override val holderId: String = holderId
            private var released = false

            override fun close() {
                val remaining: Int
                synchronized(this@RuntimeLifecycleSupervisor) {
                    if (released) return
                    released = true
                    holders.remove(holderKey)
                    if (holders.isEmpty()) {
                        releaseLocks()
                    }
                    remaining = holders.size
                }
                Log.d(TAG, "Lease released by '$holderId' (remaining: $remaining)")
            }
        }
    }

    /** 当前活跃租约数（线程安全快照）。 */
    fun holderCount(): Int = synchronized(this) { holders.size }

    /** 当前活跃申请方标识的快照（仅用于诊断/日志；同名持有会出现多次）。 */
    fun holderSnapshot(): Set<String> = synchronized(this) { holders.values.toSet() }

    // ──────────────────────────────────────────────────────────────
    // 内部：锁管理
    // ──────────────────────────────────────────────────────────────

    private fun acquireLocks() {
        acquireWakeLock()
        acquireWifiLock()
        _isActive.value = true
    }

    private fun releaseLocks() {
        releaseWakeLock()
        releaseWifiLock()
        _isActive.value = false
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        runCatching {
            wakeLock = context.getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
                .also { it.acquire(LOCK_TIMEOUT_MS) }
            Log.i(TAG, "WakeLock acquired (holders: ${holderSnapshot()})")
        }.onFailure { Log.w(TAG, "Failed to acquire WakeLock", it) }
    }

    private fun releaseWakeLock() {
        runCatching {
            wakeLock?.takeIf { it.isHeld }?.release()
            Log.i(TAG, "WakeLock released")
        }.onFailure { Log.w(TAG, "Failed to release WakeLock", it) }
        wakeLock = null
    }

    private fun acquireWifiLock() {
        if (wifiLock?.isHeld == true) return
        runCatching {
            @Suppress("DEPRECATION")
            wifiLock = context.getSystemService(WifiManager::class.java)
                .createWifiLock(WifiManager.WIFI_MODE_FULL, WIFI_LOCK_TAG)
                .also { it.acquire() }
            Log.i(TAG, "WifiLock acquired")
        }.onFailure { Log.w(TAG, "Failed to acquire WifiLock", it) }
    }

    private fun releaseWifiLock() {
        runCatching {
            wifiLock?.takeIf { it.isHeld }?.release()
            Log.i(TAG, "WifiLock released")
        }.onFailure { Log.w(TAG, "Failed to release WifiLock", it) }
        wifiLock = null
    }

    companion object {
        private const val TAG = "RuntimeLifecycleSupervisor"
        private const val WAKE_LOCK_TAG = "tianxuan:lifecycle-supervisor"
        private const val WIFI_LOCK_TAG = "tianxuan:lifecycle-supervisor-wifi"

        /**
         * 兜底超时：8 小时。即使出现 lease 泄漏，系统也会在此之后强制释放，
         * 避免意外永久持锁。正常流程下所有租约释放时会主动 release。
         */
        private const val LOCK_TIMEOUT_MS = 8L * 60 * 60 * 1000
    }
}
