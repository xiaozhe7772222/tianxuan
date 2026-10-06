package top.wkbin.tianxuan.runtime.virtualdisplay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.ai.assistance.shower.IShowerService
import com.ai.assistance.shower.ShowerBinderContainer
import com.ai.assistance.showerclient.ShowerBinderRegistry
import com.ai.assistance.showerclient.ShowerLog

/**
 * 接收 shower-server（app_process 独立进程）通过 IActivityManager 广播交出的
 * IShowerService Binder，并注册到 [ShowerBinderRegistry] 供 ShowerController 使用。
 *
 * 注意：
 * - 必须在 Manifest 中声明 exported=true：广播发送方运行在 shell uid 的独立进程；
 * - [ACTION_SHOWER_BINDER_READY] 字符串硬编码在预编译的 shower-server.jar 内，
 *   与广播协议绑定，禁止改为 tianxuan 包名。
 */
class ShowerBinderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SHOWER_BINDER_READY) return

        // 来源校验：shower-server 经 IActivityManager 发广播，原始调用者是 shell(2000)
        // 或 root(0)。exported=true 无法避免广播可发，但至少拒绝普通三方应用的伪造
        // Binder（防止恶意 App 抢先注册失效/误导性的 IShowerService）。
        // getSentFromUid 为 API 34+；低版本 system_server 中转后无法可靠还原来源，放行。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val sentFromUid = sentFromUid
            if (sentFromUid !in setOf(ROOT_UID, SHELL_UID)) {
                ShowerLog.w(TAG, "拒绝来源不明的 Binder 广播：sentFromUid=$sentFromUid")
                return
            }
        }

        // API 33+ 走类型安全重载；minSdk 29 < 33，需保留旧 API 分支
        val container = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_BINDER_CONTAINER, ShowerBinderContainer::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<ShowerBinderContainer>(EXTRA_BINDER_CONTAINER)
        }
        val binder = container?.binder
        val service = binder?.let { IShowerService.Stub.asInterface(it) }
        val alive = service?.asBinder()?.isBinderAlive == true
        ShowerLog.d(TAG, "onReceive: service=$service alive=$alive")
        ShowerBinderRegistry.setService(service)
    }

    companion object {
        private const val TAG = "TianxuanShowerReceiver"

        /** Linux shell uid：app_process 以 shell 身份运行 shower-server。 */
        private const val SHELL_UID = 2000
        /** Linux root uid：Root 模式下 server 可能以 root 身份运行。 */
        private const val ROOT_UID = 0

        const val ACTION_SHOWER_BINDER_READY =
            "com.ai.assistance.operit.action.SHOWER_BINDER_READY"
        const val EXTRA_BINDER_CONTAINER = "binder_container"
    }
}
