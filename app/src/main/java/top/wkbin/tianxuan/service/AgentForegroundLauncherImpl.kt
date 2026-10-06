package top.wkbin.tianxuan.service

import android.content.Context
import top.wkbin.tianxuan.harness.AgentForegroundLauncher

/** 壳层适配：harness 不依赖 app，前台保活服务只能经该桥接接口拉起。 */
class AgentForegroundLauncherImpl(
    private val context: Context,
) : AgentForegroundLauncher {
    override fun start(sessionId: String?) {
        runCatching { AgentForegroundService.start(context, sessionId) }
    }
}
