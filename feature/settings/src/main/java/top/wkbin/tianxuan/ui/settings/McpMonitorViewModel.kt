package top.wkbin.tianxuan.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import top.wkbin.tianxuan.harness.mcp.McpManager

/** MCP 服务监控面板 ViewModel：轮询各 server 的连接状态、工具数与最近错误。 */
class McpMonitorViewModel(
    private val mcpManager: McpManager,
) : ViewModel() {

    private val _items = MutableStateFlow<List<McpManager.McpMonitorInfo>>(emptyList())
    val items: StateFlow<List<McpManager.McpMonitorInfo>> = _items.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _refreshing.value = true
            try {
                _items.value = mcpManager.monitorSnapshot()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // 快照失败保持旧列表
            } finally {
                _refreshing.value = false
            }
        }
    }
}
