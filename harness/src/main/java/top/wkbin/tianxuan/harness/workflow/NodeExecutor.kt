package top.wkbin.tianxuan.harness.workflow

import top.wkbin.tianxuan.core.model.workflow.NodeExecutionOutput
import top.wkbin.tianxuan.core.model.workflow.NodeRunStatus
import top.wkbin.tianxuan.core.model.workflow.WorkflowNode
import top.wkbin.tianxuan.core.model.workflow.WorkflowNodeType
import top.wkbin.tianxuan.core.model.workflow.WorkflowRuntimeContext

interface NodeExecutor {
    val supportedTypes: Set<WorkflowNodeType>

    suspend fun execute(
        node: WorkflowNode,
        context: WorkflowRuntimeContext,
        onProgress: suspend (NodeRunStatus, String) -> Unit,
    ): NodeExecutionOutput
}

