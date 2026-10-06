package top.wkbin.tianxuan.harness.workflow

import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import top.wkbin.tianxuan.core.model.workflow.FailurePolicy
import top.wkbin.tianxuan.core.model.workflow.NodeExecutionOutput
import top.wkbin.tianxuan.core.model.workflow.NodeRunStatus
import top.wkbin.tianxuan.core.model.workflow.WorkflowApprovalDecision
import top.wkbin.tianxuan.core.model.workflow.WorkflowApprovalRequest
import top.wkbin.tianxuan.core.model.workflow.WorkflowDefinition
import top.wkbin.tianxuan.core.model.workflow.WorkflowEdgeCondition
import top.wkbin.tianxuan.core.model.workflow.WorkflowNode
import top.wkbin.tianxuan.core.model.workflow.WorkflowNodeType
import top.wkbin.tianxuan.core.model.workflow.WorkflowNodeRunState
import top.wkbin.tianxuan.core.model.workflow.WorkflowRunStatus
import top.wkbin.tianxuan.core.model.workflow.WorkflowRuntimeContext
import top.wkbin.tianxuan.core.model.workflow.WorkflowRuntimeState
import top.wkbin.tianxuan.core.model.workflow.WorkflowValidator

class WorkflowRunHandle internal constructor(
    val state: StateFlow<WorkflowRuntimeState>,
    val approvalRequest: StateFlow<WorkflowApprovalRequest?>,
    private val cancelAction: () -> Unit,
    private val decisionAction: (String, WorkflowApprovalDecision) -> Boolean,
) {
    fun cancel() = cancelAction()
    fun decide(nodeId: String, approved: Boolean, variables: Map<String, String> = emptyMap()): Boolean =
        decisionAction(nodeId, WorkflowApprovalDecision(approved, variables))
}

class WorkflowScheduler(
    executors: Set<@JvmSuppressWildcards NodeExecutor>,
    private val approvalBroker: WorkflowApprovalBroker,
) {
    private val executorMap = executors
        .flatMap { executor -> executor.supportedTypes.map { it to executor } }
        .also { pairs -> require(pairs.map { it.first }.distinct().size == pairs.size) { "同一工作流节点类型注册了多个执行器" } }
        .toMap()

    fun execute(
        definition: WorkflowDefinition,
        initialVariables: Map<String, String>,
        workspacePath: String,
        scope: CoroutineScope,
    ): WorkflowRunHandle {
        val executionId = "wf_${UUID.randomUUID()}"
        val mutableState = MutableStateFlow(WorkflowRuntimeState.initial(executionId, definition))
        val jobRef = AtomicReference<Job?>()
        // cancel() 与 jobRef.set() 之间的竞态标志：set 之前 cancel 只能取到 null 导致 job 未取消，
        // 置位后由 set 完成侧再检查一次补 cancel 兜底
        val cancelled = AtomicBoolean(false)
        val job = scope.launch {
            runWorkflow(executionId, definition, initialVariables, workspacePath, mutableState)
        }
        jobRef.set(job)
        if (cancelled.get()) job.cancel(CancellationException("用户取消工作流"))
        job.invokeOnCompletion { cause ->
            approvalBroker.cancelExecution(executionId)
            if (cause is CancellationException) mutableState.update { current ->
                if (current.status in setOf(WorkflowRunStatus.SUCCESS, WorkflowRunStatus.FAILED, WorkflowRunStatus.CANCELLED)) current
                else current.copy(status = WorkflowRunStatus.CANCELLED, finishedAt = System.currentTimeMillis())
            }
        }
        return WorkflowRunHandle(
            state = mutableState.asStateFlow(),
            // 按本执行过滤审批请求：全局单值会串扰并发工作流（A 的 UI 可能显示 B 的请求）
            approvalRequest = approvalBroker.currentRequestFor(executionId),
            cancelAction = {
                approvalBroker.cancelExecution(executionId)
                cancelled.set(true)
                jobRef.get()?.cancel(CancellationException("用户取消工作流"))
            },
            decisionAction = { nodeId, decision -> approvalBroker.decide(executionId, nodeId, decision) },
        )
    }

    private suspend fun runWorkflow(
        executionId: String,
        definition: WorkflowDefinition,
        initialVariables: Map<String, String>,
        workspacePath: String,
        state: MutableStateFlow<WorkflowRuntimeState>,
    ) {
        val startedAt = System.currentTimeMillis()
        val validation = WorkflowValidator.validate(definition)
        if (validation.isNotEmpty()) {
            state.update { it.copy(status = WorkflowRunStatus.FAILED, startedAt = startedAt, finishedAt = startedAt, error = validation.joinToString { issue -> issue.message }) }
            return
        }
        var context = WorkflowRuntimeContext(
            executionId = executionId,
            workflowId = definition.id,
            workspacePath = workspacePath,
            globalVariables = definition.defaultVariables + initialVariables,
        )
        state.update { current ->
            current.copy(
                status = WorkflowRunStatus.RUNNING,
                startedAt = startedAt,
                nodeStates = current.nodeStates.mapValues { (_, value) -> value.copy(status = NodeRunStatus.PENDING) },
                context = context,
            )
        }

        try {
            val nodesById = definition.nodes.associateBy { it.id }
            val incoming = definition.edges.groupBy { it.toNodeId }
            val pending = nodesById.keys.toMutableSet()
            while (pending.isNotEmpty()) {
                val settled = context.nodeOutputs.keys
                val resolvable = pending.filter { nodeId -> incoming[nodeId].orEmpty().all { it.fromNodeId in settled } }
                check(resolvable.isNotEmpty()) { "调度器无法继续，请检查工作流依赖" }

                val skipped = resolvable.filter { nodeId ->
                    val edges = incoming[nodeId].orEmpty()
                    edges.isNotEmpty() && edges.none { edge ->
                        context.nodeOutputs[edge.fromNodeId]?.let { WorkflowEdgeCondition.matches(edge, it) } ?: false
                    }
                }
                skipped.forEach { nodeId ->
                    val output = NodeExecutionOutput(NodeRunStatus.SKIPPED, textOutput = "上游分支未选择此节点")
                    context = context.withOutput(nodeId, output)
                    updateNode(state, nodeId, output.status, output.textOutput, output)
                    pending.remove(nodeId)
                }

                val ready = resolvable.filterNot(skipped::contains).mapNotNull(nodesById::get)
                if (ready.isEmpty()) continue
                val waveContext = context
                val results = coroutineScope {
                    ready.map { node -> async {
                        val nodeContext = waveContext.copy(upstreamNodeIds = incoming[node.id].orEmpty()
                            .filter { edge -> waveContext.nodeOutputs[edge.fromNodeId]?.let { WorkflowEdgeCondition.matches(edge, it) } == true }
                            .map { it.fromNodeId }.distinct())
                        node.id to executeNode(node, nodeContext, state)
                    } }.awaitAll()
                }
                results.forEach { (nodeId, output) ->
                    context = context.withOutput(nodeId, output)
                    pending.remove(nodeId)
                }
                state.update { it.copy(context = context) }

                val abort = results.firstOrNull { (nodeId, output) ->
                    output.status == NodeRunStatus.FAILED && nodesById[nodeId]?.failurePolicy != FailurePolicy.CONTINUE
                }
                if (abort != null) {
                    pending.forEach { nodeId -> updateNode(state, nodeId, NodeRunStatus.CANCELLED, "因上游失败而取消") }
                    state.update {
                        it.copy(
                            status = WorkflowRunStatus.FAILED,
                            context = context,
                            finishedAt = System.currentTimeMillis(),
                            error = abort.second.error ?: "节点 ${abort.first} 执行失败",
                        )
                    }
                    return
                }
            }

            val hasFailure = context.nodeOutputs.values.any { it.status == NodeRunStatus.FAILED }
            state.update {
                it.copy(
                    status = if (hasFailure) WorkflowRunStatus.FAILED else WorkflowRunStatus.SUCCESS,
                    context = context,
                    finishedAt = System.currentTimeMillis(),
                    error = context.nodeOutputs.values.firstOrNull { output -> output.status == NodeRunStatus.FAILED }?.error,
                )
            }
        } catch (cancelled: CancellationException) {
            state.update { current ->
                current.copy(
                    status = WorkflowRunStatus.CANCELLED,
                    finishedAt = System.currentTimeMillis(),
                    nodeStates = current.nodeStates.mapValues { (_, nodeState) ->
                        if (nodeState.status in ACTIVE_STATUSES) nodeState.copy(status = NodeRunStatus.CANCELLED) else nodeState
                    },
                )
            }
            throw cancelled
        } catch (error: Throwable) {
            state.update { it.copy(status = WorkflowRunStatus.FAILED, finishedAt = System.currentTimeMillis(), error = error.message ?: error::class.java.simpleName) }
        }
    }

    private suspend fun executeNode(
        node: WorkflowNode,
        context: WorkflowRuntimeContext,
        state: MutableStateFlow<WorkflowRuntimeState>,
    ): NodeExecutionOutput {
        val executor = executorMap[node.type]
            ?: return NodeExecutionOutput(NodeRunStatus.FAILED, exitCode = 127, error = "没有 ${node.type} 节点执行器")
                .also { updateNode(state, node.id, it.status, it.error.orEmpty(), it) }
        var attempts = 0
        val maxAttempts = if (node.failurePolicy == FailurePolicy.RETRY_ONCE) 2 else 1
        var output: NodeExecutionOutput
        do {
            attempts++
            updateNode(state, node.id, NodeRunStatus.RUNNING, if (attempts > 1) "正在重试" else "正在执行")
            val started = System.nanoTime()
            // 人工等待类节点（HUMAN_APPROVAL）默认不限时等待用户决定：默认 300s 超时会在用户
            // 迟迟未批准时把节点误判 FAILED，此后 decide() 永远无效。
            // 显式限时走 config["timeoutSeconds"]：WorkflowValidation（清单外）强制
            // timeoutSeconds ∈ 1..3600，无法用 0/-1 哨兵区分“未显式配置”，故另辟 config 通道。
            val timeoutMs = if (node.type == WorkflowNodeType.HUMAN_APPROVAL) {
                node.config["timeoutSeconds"]?.toLongOrNull()?.takeIf { it > 0 }?.times(1_000L)
            } else {
                node.timeoutSeconds * 1_000L
            }
            val onProgress: suspend (NodeRunStatus, String) -> Unit = { status, message ->
                updateNode(state, node.id, status, message)
                state.update { current ->
                    val waiting = current.nodeStates.values.any { it.status == NodeRunStatus.WAITING_APPROVAL }
                    current.copy(status = if (waiting) WorkflowRunStatus.WAITING_APPROVAL else WorkflowRunStatus.RUNNING)
                }
            }
            output = try {
                if (timeoutMs == null) {
                    executor.execute(node, context, onProgress)
                } else {
                    withTimeoutOrNull(timeoutMs) { executor.execute(node, context, onProgress) }
                        ?: NodeExecutionOutput(NodeRunStatus.FAILED, exitCode = 124, error = "节点超时（${timeoutMs / 1_000} 秒）")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                NodeExecutionOutput(NodeRunStatus.FAILED, exitCode = -1, error = error.message ?: error::class.java.simpleName)
            }
            if (output.durationMs == 0L) output = output.copy(durationMs = (System.nanoTime() - started) / 1_000_000L)
        } while (output.status == NodeRunStatus.FAILED && attempts < maxAttempts)

        if (output.status == NodeRunStatus.FAILED && node.failurePolicy == FailurePolicy.ASK_USER) {
            updateNode(state, node.id, NodeRunStatus.WAITING_APPROVAL, "执行失败，等待决定是否重试")
            // ASK_USER 等待期间 workflow 级状态也应反映 WAITING_APPROVAL（此前只有节点级）
            state.update { current ->
                val waiting = current.nodeStates.values.any { it.status == NodeRunStatus.WAITING_APPROVAL }
                current.copy(status = if (waiting) WorkflowRunStatus.WAITING_APPROVAL else WorkflowRunStatus.RUNNING)
            }
            val decision = approvalBroker.await(
                WorkflowApprovalRequest(context.executionId, node.id, "重试“${node.title}”？", output.error.orEmpty()),
            )
            if (decision.approved) return executeNode(node.copy(failurePolicy = FailurePolicy.ABORT), context, state)
        }
        updateNode(state, node.id, output.status, output.error ?: output.textOutput.lineSequence().lastOrNull().orEmpty(), output)
        state.update { current ->
            val waiting = current.nodeStates.values.any { it.status == NodeRunStatus.WAITING_APPROVAL }
            current.copy(status = if (waiting) WorkflowRunStatus.WAITING_APPROVAL else WorkflowRunStatus.RUNNING)
        }
        return output
    }

    private fun updateNode(
        state: MutableStateFlow<WorkflowRuntimeState>,
        nodeId: String,
        status: NodeRunStatus,
        message: String,
        output: NodeExecutionOutput? = null,
    ) {
        state.update { current ->
            current.copy(nodeStates = current.nodeStates + (nodeId to WorkflowNodeRunState(status, message, output ?: current.nodeStates[nodeId]?.output)))
        }
    }

    private fun WorkflowRuntimeContext.withOutput(nodeId: String, output: NodeExecutionOutput) = copy(
        globalVariables = globalVariables + output.variables,
        nodeOutputs = nodeOutputs + (nodeId to output),
    )

    private companion object {
        val ACTIVE_STATUSES = setOf(NodeRunStatus.PENDING, NodeRunStatus.RUNNING, NodeRunStatus.STREAMING, NodeRunStatus.WAITING_APPROVAL)
    }
}
