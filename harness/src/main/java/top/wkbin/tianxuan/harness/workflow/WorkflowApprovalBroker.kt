package top.wkbin.tianxuan.harness.workflow

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import top.wkbin.tianxuan.core.model.workflow.WorkflowApprovalDecision
import top.wkbin.tianxuan.core.model.workflow.WorkflowApprovalRequest

class WorkflowApprovalBroker() {
    private val waiting = linkedMapOf<String, Pair<WorkflowApprovalRequest, CompletableDeferred<WorkflowApprovalDecision>>>()

    // 按 executionId 隔离的当前审批请求流：并发工作流互不串扰（A 的 UI 不会显示 B 的请求）
    private val requestsByExecution = ConcurrentHashMap<String, MutableStateFlow<WorkflowApprovalRequest?>>()

    // 兼容旧签名：保留“最早仍在等待”的全局单值视图（原有语义，feature 层旧调用方仍可用，
    // 自行按 executionId 过滤）；需要按执行隔离的调用方请改用 [currentRequestFor]
    private val _currentRequest = MutableStateFlow<WorkflowApprovalRequest?>(null)
    val currentRequest: StateFlow<WorkflowApprovalRequest?> = _currentRequest.asStateFlow()

    fun currentRequestFor(executionId: String): StateFlow<WorkflowApprovalRequest?> =
        requestsByExecution.getOrPut(executionId) { MutableStateFlow(null) }

    suspend fun await(request: WorkflowApprovalRequest): WorkflowApprovalDecision {
        val key = key(request.executionId, request.nodeId)
        val deferred = CompletableDeferred<WorkflowApprovalDecision>()
        synchronized(waiting) {
            check(key !in waiting) { "审批请求已存在：$key" }
            waiting[key] = request to deferred
            requestsByExecution.getOrPut(request.executionId) { MutableStateFlow(null) }.value = request
            _currentRequest.value = waiting.values.firstOrNull()?.first
        }
        return try {
            deferred.await()
        } finally {
            synchronized(waiting) {
                waiting.remove(key)
                val remainingForExecution = waiting.values.firstOrNull { it.first.executionId == request.executionId }?.first
                requestsByExecution[request.executionId]?.value = remainingForExecution
                _currentRequest.value = waiting.values.firstOrNull()?.first
            }
        }
    }

    fun decide(executionId: String, nodeId: String, decision: WorkflowApprovalDecision): Boolean =
        synchronized(waiting) { waiting[key(executionId, nodeId)]?.second?.complete(decision) == true }

    fun cancelExecution(executionId: String) {
        synchronized(waiting) {
            val keys = waiting.filterValues { it.first.executionId == executionId }.keys.toList()
            keys.forEach { waiting.remove(it)?.second?.cancel() }
            // 移除该执行的隔离流条目，避免 map 随执行次数无限增长（handle 持有的旧引用不受影响）
            requestsByExecution.remove(executionId)?.value = null
            _currentRequest.value = waiting.values.firstOrNull()?.first
        }
    }

    private fun key(executionId: String, nodeId: String) = "$executionId:$nodeId"
}
