package top.wkbin.tianxuan.harness.ptc

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * JSON 工具调用序列运行时（对齐 DSH ptc-runtime）。
 *
 * 天玄的 PTC 运行时以 JSON 工具调用数组为程序格式：
 * 程序 = `[{"tool": "read", "args": {"path": "/foo"}}, ...]`
 *
 * 运行时通过 [PtcDispatcher] 调度子调用：并行类调用并发执行，
 * 独占类调用等待池排空后独占运行。结果按提交序写入。
 */
class JsonPtcRuntime(
    private val defaultTimeoutMs: Long = DEFAULT_PTC_TIMEOUT_MS,
    private val maxTimeoutMs: Long = MAX_PTC_TIMEOUT_MS,
) : PtcRuntime {

    override val language: String = "json"

    override fun resolve(request: PtcRunRequest): PtcRunSpec {
        val timeout = request.timeoutMs?.let {
            require(it > 0) { "timeoutMs must be positive" }
            minOf(it, maxTimeoutMs)
        } ?: defaultTimeoutMs
        return PtcRunSpec(
            program = request.program,
            bindings = request.bindings,
            cwd = request.cwd ?: "/root",
            timeoutMs = timeout,
            maxParallel = request.maxParallel.coerceAtLeast(1),
        )
    }

    override suspend fun run(spec: PtcRunSpec): PtcRunResult {
        val logs = mutableListOf<String>()

        // 解析程序 JSON
        val programArray: List<JsonObject> = try {
            val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            json.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(JsonObject.serializer()),
                spec.program,
            )
        } catch (e: Exception) {
            return PtcRunResult(
                error = PtcRunFailure(
                    PtcRunFailure.FailureKind.EXCEPTION,
                    "Failed to parse program JSON: ${e.message}",
                ),
                logs = listOf("Parse error: ${e.message}"),
            )
        }

        if (programArray.isEmpty()) {
            return PtcRunResult(
                value = buildJsonArray {},
                logs = listOf("Empty program, no operations executed"),
            )
        }

        // 从绑定命名空间构建工具查找表
        val toolFunctions = mutableMapOf<String, PtcBindingFunction>()
        for (namespace in spec.bindings) {
            for ((name, fn) in namespace.functions) {
                toolFunctions[name] = fn
            }
        }

        val dispatcher = PtcDispatcher(spec.maxParallel)

        // 收集调用结果（按提交序）
        val orderedResults = arrayOfNulls<Result<JsonElement>>(programArray.size)

        kotlinx.coroutines.coroutineScope {
            // 提交所有调用
            for ((index, call) in programArray.withIndex()) {
                val toolName = call["tool"]?.jsonPrimitive?.contentOrNull
                val args = call["args"]?.jsonObject ?: JsonObject(emptyMap())

                if (toolName == null) {
                    orderedResults[index] = Result.failure(
                        IllegalArgumentException("[$index] missing tool name")
                    )
                    continue
                }

                val fn = toolFunctions[toolName]
                if (fn == null) {
                    orderedResults[index] = Result.failure(
                        IllegalArgumentException("[$index] unknown tool: $toolName")
                    )
                    continue
                }

                val mode = if (toolName in EXCLUSIVE_TOOLS) {
                    PtcDispatcher.ExecutionMode.EXCLUSIVE
                } else {
                    PtcDispatcher.ExecutionMode.PARALLEL
                }

                dispatcher.submit(mode) {
                    orderedResults[index] = runCatching { fn(args) }
                }
            }

            // 并发执行所有提交的调用
            dispatcher.runAll(this)
        }

        // 取消任何剩余调用
        dispatcher.cancel()

        // 聚合结果
        val resultsJson = mutableListOf<JsonObject>()
        var allSuccess = true

        for ((index, result) in orderedResults.withIndex()) {
            val r = result ?: Result.failure(IllegalStateException("[$index] never executed"))
            val toolName = programArray[index]["tool"]?.jsonPrimitive?.contentOrNull ?: "unknown"

            if (r.isSuccess) {
                val value = r.getOrNull()
                resultsJson.add(buildJsonObject {
                    put("tool", toolName)
                    put("success", true)
                    put("result", value ?: JsonPrimitive(""))
                })
                logs.add("[$index] $toolName: success")
            } else {
                allSuccess = false
                val error = r.exceptionOrNull()?.message ?: "unknown error"
                resultsJson.add(buildJsonObject {
                    put("tool", toolName)
                    put("success", false)
                    put("error", error)
                })
                logs.add("[$index] $toolName: failed - $error")
            }
        }

        return PtcRunResult(
            value = buildJsonArray { resultsJson.forEach { add(it) } },
            logs = logs,
            error = if (!allSuccess) {
                PtcRunFailure(
                    PtcRunFailure.FailureKind.EXCEPTION,
                    "One or more tool calls failed (see logs)",
                )
            } else null,
        )
    }

    companion object {
        /** 默认独占类工具（基线命令、长执行）。 */
        private val EXCLUSIVE_TOOLS = setOf(
            "base", "process", "host", "build_script", "download",
        )
    }
}
