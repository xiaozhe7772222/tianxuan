package top.wkbin.tianxuan.ui.chat

import top.wkbin.tianxuan.harness.A2uiSurfaceBus
import top.wkbin.tianxuan.harness.HarnessLoop

/**
 * A2UI PoC 与聊天会话的桥接：注册官方 parser 深度校验、启动用户交互事件转发
 * （幂等），并把界面事件路由回发起该 surface 的会话。独立成文件是因为
 * ChatViewModel 处于尺寸棘轮基线内（只许缩减），装配代码不能让它增行。
 */
object A2uiChatBridge {

    fun bind(harnessLoop: HarnessLoop) {
        top.wkbin.tianxuan.feature.a2uipoc.A2uiPocInstaller.install()
        // 忙时用 steer 挂到当前运行，避免每次事件都 send() 新建排队任务
        fun dispatch(text: String, sessionId: String) {
            if (sessionId.isBlank()) return
            if (harnessLoop.running.value) harnessLoop.steer(text, targetSessionId = sessionId)
            else harnessLoop.send(text, targetSessionId = sessionId)
        }
        A2uiSurfaceBus.userEventSink = { event -> dispatch(A2uiSurfaceBus.formatUserEvent(event), event.sessionId) }
        // 引擎运行时错误（组件被 Catalog 拒绝等）此前静默吞掉，界面只会转圈；回传让模型自纠
        A2uiSurfaceBus.errorEventSink = { event -> dispatch(A2uiSurfaceBus.formatErrorEvent(event), event.sessionId) }
    }
}
