package top.wkbin.tianxuan.harness.ptc

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * PTC (Practical Tool Calls) 类型系统（对齐 DSH ptc-runtime/src/types.ts）。
 *
 * PTC 模式让模型一次提交一个"程序"，程序内可按序调用多个已注册工具，
 * harness 按序执行并聚合结果。减少模型与工具的往返轮次。
 *
 * 天玄当前没有 JS/TS 引擎（Android 无 V8/QuickJS），PTC 运行时
 * 以 JSON 工具调用序列为程序格式：程序是一个 JSON 数组，
 * 每项是 `{ "tool": "...", "args": {...} }`，运行时逐项调度执行。
 *
 * @see PtcDispatcher 调度器实现
 */

/** 无损 JSON 值（对齐 DSH PtcJsonValue）。 */
typealias PtcJsonValue = JsonElement

/** 宿主侧异步可调用函数（对齐 DSH PtcBindingFunction）。 */
typealias PtcBindingFunction = suspend (args: JsonObject) -> PtcJsonValue

/**
 * 一个命名绑定命名空间组（对齐 DSH PtcBindingNamespace），
 * 运行时暴露给程序作为一个全局对象（如 `tools`）。
 */
data class PtcBindingNamespace(
    /** 程序可见的全局标识符。 */
    val global: String,
    /** 可调用成员，按程序调用的精确名键控。 */
    val functions: Map<String, PtcBindingFunction>,
)

/**
 * 调用者输入的一次程序（对齐 DSH PtcRunRequest）。
 */
data class PtcRunRequest(
    /** 程序源（JSON 工具调用数组）。 */
    val program: String,
    /** 主机函数暴露给程序，每命名空间一个全局对象。 */
    val bindings: List<PtcBindingNamespace>,
    /** 挂载文件系统中的工作目录。 */
    val cwd: String? = null,
    /** 经过执行预算（毫秒）。null 使用默认值。 */
    val timeoutMs: Long? = null,
    /** 最大并行子调用数。 */
    val maxParallel: Int = 1,
)

/**
 * 完全解析的执行输入（对齐 DSH PtcRunSpec）。
 */
data class PtcRunSpec(
    val program: String,
    val bindings: List<PtcBindingNamespace>,
    val cwd: String,
    val timeoutMs: Long,
    val maxParallel: Int,
)

/**
 * 文件限制（对齐 DSH PtcRunSandbox）。
 */
@Serializable
data class PtcRunSandbox(
    /** 文件效果模式。 */
    val mode: String,
    /** 程序失败文本匹配了后端诊断。 */
    val denied: Boolean = false,
    /** 选择的限制后端报告的完整性。 */
    val enforcement: String? = null,
)

/**
 * 运行失败的原因（对齐 DSH PtcRunFailure）。
 */
@Serializable
data class PtcRunFailure(
    /** 失败类。 */
    val kind: FailureKind,
    /** 人类可读的详情，适合反馈给模型自纠正。 */
    val message: String,
) {
    @Serializable
    enum class FailureKind {
        EXCEPTION,
        TIMEOUT,
        ABORT,
        WORKER_EXIT,
        INVALID_OUTPUT,
        OUTPUT_LIMIT,
        PROTOCOL,
        SANDBOX_UNAVAILABLE,
    }
}

/**
 * 一次运行的结果（对齐 DSH PtcRunResult）。
 *
 * 错误是已解析结果上的字段，永远不是 run() 的拒绝。
 */
@Serializable
data class PtcRunResult(
    /** 应用的文件策略和观察到的拒绝。 */
    val sandbox: PtcRunSandbox? = null,
    /** 程序的完成值（跨过无损 JSON 边界时）。 */
    val value: JsonElement? = null,
    /** 捕获的文本日志。 */
    val logs: List<String> = emptyList(),
    /** 失败时存在。 */
    val error: PtcRunFailure? = null,
)

/**
 * PTC 运行时接口（对齐 DSH PtcRuntime）。
 *
 * 天玄的运行时以 JSON 工具调用序列为程序格式，不支持 JS/TS 源代码。
 * resolve() 验证输入并补全目录与截止时间；run() 执行程序。
 */
interface PtcRuntime {
    /** 运行时语言标识（对齐 DSH language）。 */
    val language: String

    /**
     * 解析调用者输入为完全规格化的执行规格。
     */
    fun resolve(request: PtcRunRequest): PtcRunSpec

    /**
     * 执行一个程序。错误在结果的 error 字段中返回，不作为拒绝。
     */
    suspend fun run(spec: PtcRunSpec): PtcRunResult

    /**
     * 释放运行时持有的与 stateOwner 相关的内存状态。
     * 无状态实现可空操作。
     */
    fun release(stateOwner: Any) {}
}

/**
 * 默认 PTC 超时（毫秒）。
 */
const val DEFAULT_PTC_TIMEOUT_MS = 30_000L

/**
 * 最大 PTC 超时（毫秒）。
 */
const val MAX_PTC_TIMEOUT_MS = 120_000L

/**
 * 默认最大并行子调用数。
 */
const val DEFAULT_MAX_PARALLEL = 4
