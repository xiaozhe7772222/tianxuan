package top.wkbin.tianxuan.harness.events

import kotlinx.coroutines.flow.first
import top.wkbin.tianxuan.core.common.logging.AppLogger
import top.wkbin.tianxuan.core.datastore.AgentPreferences

/** Agent 侧结构化事件日志（ToolCall / ModelRequest / Steering…），受用户开关门控。 */
class AgentEventLogger(
    private val preferences: AgentPreferences,
    private val logger: AppLogger,
) {
    suspend fun log(sessionId: String, tag: String, message: String, throwable: Throwable? = null) {
        val enabled = runCatching { preferences.agentLoggingEnabled.first() }.getOrDefault(false)
        if (enabled) {
            logger.logAgent(sessionId, tag, message, throwable)
        }
    }
}
