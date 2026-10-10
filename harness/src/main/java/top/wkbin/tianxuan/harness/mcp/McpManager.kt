package top.wkbin.tianxuan.harness.mcp

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import top.wkbin.tianxuan.core.common.logging.AppLogger
import top.wkbin.tianxuan.core.database.McpServerRepository
import top.wkbin.tianxuan.core.model.McpConnectionState
import top.wkbin.tianxuan.core.model.McpServerConfig
import top.wkbin.tianxuan.core.model.McpToolInfo
import top.wkbin.tianxuan.core.model.McpTransportType
import top.wkbin.tianxuan.harness.events.AgentEventLogger
import top.wkbin.tianxuan.runtime.LinuxRuntime
import kotlin.time.Duration.Companion.milliseconds

/** Thin MCP registry coordinator; transports own protocol and process details. */
class McpManager(
    private val repository: McpServerRepository,
    private val stdio: McpStdioTransport,
    private val http: McpHttpTransport,
    private val commandBuilder: McpCommandBuilder,
    private val linuxRuntime: LinuxRuntime,
    private val logger: AppLogger,
    private val agentEventLogger: AgentEventLogger,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private data class CachedTools(val fingerprint: String, val tools: List<McpToolInfo>)
    private val cache = ConcurrentHashMap<String, CachedTools>()
    private val discoveryMutexes = ConcurrentHashMap<String, Mutex>()
    private val lastErrors = ConcurrentHashMap<String, String>()
    /**
     * 工具发现失败冷却：server id → 冷却截止时间戳 / 连续失败次数。
     *
     * 失败后仅移除缓存、下一轮继续重试，会让持续故障的服务（实测某 sqlite MCP 累计 1,940 次
     * 发现失败、其中 79 次是 8s 超时）在每一轮对话和每次子智能体初始化时都重新空耗一遍超时。
     * 传输层各自有冷却，但 SSE/STDIO 之外的失败路径（如 tools/list 报错）不写冷却，
     * 因此在管理器这一层统一兜底：指数退避，成功即清零。
     */
    private val discoveryCooldownUntil = ConcurrentHashMap<String, Long>()
    private val discoveryFailureStreak = ConcurrentHashMap<String, Int>()
    /** B1: server id → 最近一次 executeTool 绑定使用的 workspace，供 discover/check 路径复用 */
    private val lastBoundWorkspaces = ConcurrentHashMap<String, String>()
    private val _connectionStates = MutableStateFlow<Map<String, McpConnectionState>>(emptyMap())
    val connectionStates: StateFlow<Map<String, McpConnectionState>> = _connectionStates.asStateFlow()

    /** MCP 服务监控快照：每个 server 的连接状态、工具数、最近错误。 */
    data class McpMonitorInfo(
        val serverId: String,
        val serverName: String,
        val state: McpConnectionState,
        val toolCount: Int,
        val lastError: String?,
        val enabled: Boolean,
        val cooldownRemainingMs: Long?,
    )

    suspend fun monitorSnapshot(): List<McpMonitorInfo> = withContext(Dispatchers.IO) {
        repository.servers.first().map { server ->
            McpMonitorInfo(
                serverId = server.id,
                serverName = server.name,
                state = _connectionStates.value[server.id] ?: McpConnectionState.UNKNOWN,
                toolCount = cache[server.id]?.tools?.size ?: 0,
                lastError = getLastError(server.id),
                enabled = server.isEnabled,
                cooldownRemainingMs = remainingDiscoveryCooldownMs(server.id),
            )
        }
    }

    /**
     * 最近错误。冷却期内额外带上剩余时间：状态只显示 OFFLINE 时，用户无法区分
     * "退避中、稍后自动重试"与"真的连不上"，只会反复手动刷新。
     */
    fun getLastError(serverId: String): String? {
        val error = lastErrors[serverId] ?: return null
        val remaining = remainingDiscoveryCooldownMs(serverId) ?: return error
        return "退避中，${remaining / 1000 + 1}s 后自动重试（$error）"
    }

    suspend fun checkConnection(server: McpServerConfig): Boolean = withContext(Dispatchers.IO) {
        // B1: 与 executeTool 一致先做 workspace 绑定再建 transport，
        // 保证 check 与执行路径看到相同 fingerprint，避免误判连接失效而重启进程
        val bound = boundConfig(server)
        withTimeoutOrNull(DISCOVERY_TIMEOUT_MS.milliseconds) { transport(bound).check(bound) } ?: false
    }

    suspend fun invalidateServer(serverId: String) {
        cache.remove(serverId)
        lastErrors.remove(serverId)
        clearDiscoveryCooldown(serverId)
        repository.servers.first().firstOrNull { it.id == serverId }?.let { closeTransportConnection(it) }
        _connectionStates.update { it + (serverId to McpConnectionState.UNKNOWN) }
    }

    suspend fun refreshConnections() = withContext(Dispatchers.IO) {
        val servers = repository.servers.first()
        servers.filterNot { it.isEnabled }.forEach { server ->
            cache.remove(server.id)
            lastErrors.remove(server.id)
            clearDiscoveryCooldown(server.id)
            closeTransportConnection(server)
        }
        // 用户手动刷新是明确的重试意图：清掉退避，不让冷却把这次刷新变成空操作。
        servers.filter { it.isEnabled }.forEach { clearDiscoveryCooldown(it.id) }
        _connectionStates.value = servers.associate { it.id to if (it.isEnabled) McpConnectionState.CHECKING else McpConnectionState.UNKNOWN }
        coroutineScope {
            servers.filter { it.isEnabled }.map { server ->
                launch {
                    val online = checkConnection(server)
                    val state = if (online) McpConnectionState.ONLINE else McpConnectionState.OFFLINE
                    _connectionStates.update { it + (server.id to state) }
                    // 恢复在线必须清掉旧错误：否则状态点显示在线、错误栏仍挂着故障描述，
                    // 用户会被误导反复手动刷新。
                    if (online) lastErrors.remove(server.id)
                }
            }.joinAll()
        }
    }

    /** 开关仅影响当前服务，避免重新启动和探测列表里的其他 MCP 服务。 */
    suspend fun refreshConnection(serverId: String) = withContext(Dispatchers.IO) {
        val server = repository.servers.first().firstOrNull { it.id == serverId } ?: return@withContext
        if (!server.isEnabled) {
            cache.remove(serverId)
            lastErrors.remove(serverId)
            lastBoundWorkspaces.remove(serverId)
            clearDiscoveryCooldown(serverId)
            _connectionStates.update { it + (serverId to McpConnectionState.UNKNOWN) }
            closeTransportConnection(server)
            return@withContext
        }
        clearDiscoveryCooldown(serverId)
        _connectionStates.update { it + (serverId to McpConnectionState.CHECKING) }
        val online = checkConnection(server)
        if (online) lastErrors.remove(serverId)
        _connectionStates.update {
            it + (serverId to if (online) McpConnectionState.ONLINE else McpConnectionState.OFFLINE)
        }
    }

    /**
     * 按需发现单个启用服务的工具清单（use_capability inspect 的兜底路径）：
     * 缓存为空时才真正连接并发现——这是 inspect 唯一会拉起进程的场景
     * （模型无从得知未连接服务的工具名，必须给它完整清单）。失败（冷却/超时）
     * 返回空列表，调用方用 getLastError 给出可读原因。
     */
    suspend fun discoverServerTools(serverId: String): List<McpToolInfo> = withContext(Dispatchers.IO) {
        val server = repository.servers.first().firstOrNull { it.id == serverId && it.isEnabled }
            ?: return@withContext emptyList()
        discoverServerIfNeeded(server)
    }

    suspend fun getActiveMcpTools(): List<McpToolInfo> = withContext(Dispatchers.IO) {
        sweepDisabledServers()
        val enabledServers = repository.servers.first().filter { it.isEnabled }
        if (enabledServers.isEmpty()) return@withContext emptyList()

        coroutineScope {
            enabledServers.map { server -> async { discoverServerIfNeeded(server) } }
        }.awaitAll().flatten()
    }

    /** 禁用服务的清理：清缓存/冷却并关闭传输连接（不产生任何新进程）。 */
    private suspend fun sweepDisabledServers() {
        repository.servers.first().filterNot { it.isEnabled }.forEach { server ->
            cache.remove(server.id)
            lastBoundWorkspaces.remove(server.id)
            clearDiscoveryCooldown(server.id)
            closeTransportConnection(server)
        }
    }

    /**
     * 单服务的按需发现（use_capability 延迟连接的核心）：缓存命中直接返回；
     * 未缓存才真正连接并发现。list/inspect 永远不调用本方法——服务器进程
     * 只在第一次真实调用其工具（executeCapabilityTool）时启动。
     */
    private suspend fun discoverServerIfNeeded(server: McpServerConfig): List<McpToolInfo> = withContext(Dispatchers.IO) {
        if (server.transportType == McpTransportType.STDIO) {
            val linuxReady = awaitLinuxRuntimeReady(linuxRuntime.state)
            if (!linuxReady) {
                lastErrors[server.id] = RUNTIME_NOT_READY_MSG
                state(server.id, McpConnectionState.OFFLINE)
                logger.w("MCP[${server.name}] 工具发现推迟：Linux runtime 尚未就绪，下一轮对话将重试")
                return@withContext emptyList()
            }
        }
        // B1: 用绑定后的配置计算 fingerprint 并发现，与 executeTool 路径一致，避免指纹乒乓
        val bound = boundConfig(server)
        val fingerprint = fingerprint(bound)
        cache[server.id]?.takeIf { it.fingerprint == fingerprint }?.tools
            ?: discoveryMutexes.getOrPut(server.id) { Mutex() }.withLock {
                cache[server.id]?.takeIf { it.fingerprint == fingerprint }?.tools
                    ?.let { return@withLock it }
                // 退避窗口内直接跳过：持续故障的服务不该在每轮对话和每次
                // 子智能体初始化时都重新空耗一遍发现超时。
                remainingDiscoveryCooldownMs(server.id)?.let { remaining ->
                    state(server.id, McpConnectionState.OFFLINE)
                    logger.w(
                        "MCP[${server.name}] 工具发现冷却中（剩余 ${remaining / 1000}s，" +
                            "连续失败 ${discoveryFailureStreak[server.id] ?: 0} 次），本轮跳过；" +
                            "最近错误：${lastErrors[server.id] ?: "未知"}",
                    )
                    return@withLock emptyList<McpToolInfo>()
                }
                // 总超时兜底：沙箱会话拉起或 MCP 进程挂起时不能阻塞每轮对话（挂起是无日志的），
                // 超时按失败处理，本轮不注入该服务工具，退避到期后重试。
                agentEventLogger.log(DISCOVERY_LOG_SESSION, "McpDiscovery", "MCP[${server.name}] 工具发现开始（transport=${server.transportType}）")
                val startedAt = System.currentTimeMillis()
                cancellableResult { discoverWithTimeout(bound) }.onSuccess {
                    agentEventLogger.log(
                        DISCOVERY_LOG_SESSION,
                        "McpDiscovery",
                        "MCP[${server.name}] 发现 ${it.size} 个工具，耗时 ${System.currentTimeMillis() - startedAt}ms",
                    )
                    cache[server.id] = CachedTools(fingerprint, it)
                    lastErrors.remove(server.id)
                    clearDiscoveryCooldown(server.id)
                    state(server.id, McpConnectionState.ONLINE)
                }.onFailure {
                    agentEventLogger.log(
                        DISCOVERY_LOG_SESSION,
                        "McpDiscovery",
                        "MCP[${server.name}] 工具发现失败，耗时 ${System.currentTimeMillis() - startedAt}ms：${it.message ?: it::class.simpleName}",
                        it,
                    )
                    val msg = it.message ?: "工具发现异常"
                    lastErrors[server.id] = msg
                    val cooldownMs = recordDiscoveryFailure(server.id)
                    // 静默失败会让"模型不调用 MCP 工具"无从排查，这里必须留下线索；
                    // 冷却期内的重复失败只记一行，不再打整段堆栈刷屏。
                    val inCooldown = msg.contains("冷却中")
                    logger.w(
                        "MCP[${server.name}] 工具发现失败，本轮对话不注入该服务的工具" +
                            "（后续 ${cooldownMs / 1000}s 内不再重试）: $msg",
                        if (inCooldown) null else it,
                    )
                    cache.remove(server.id)
                    state(server.id, McpConnectionState.OFFLINE)
                }.getOrDefault(emptyList())
            }
    }

    /**
     * use_capability 的 list/inspect 数据源：**绝不启动服务器进程**。
     * 返回启用服务摘要（缓存命中数可能小于实际工具数——未连接的服务为 0）。
     */
    suspend fun enabledServerSummaries(): List<EnabledServerSummary> = withContext(Dispatchers.IO) {
        sweepDisabledServers()
        repository.servers.first().filter { it.isEnabled }.map { server ->
            EnabledServerSummary(
                id = server.id,
                name = server.name,
                cachedToolCount = cache[server.id]?.tools?.size ?: 0,
                connected = connectionStates.value[server.id] == McpConnectionState.ONLINE,
            )
        }
    }

    /** use_capability 的 inspect 数据源：只读缓存，绝不触发发现/连接。 */
    fun cachedToolsOf(serverId: String): List<McpToolInfo> = cache[serverId]?.tools.orEmpty()

    /**
     * use_capability 的 call 执行路径：按 (serverId, 工具名) 解析并执行。
     * 未连接的服务在此处按需启动并发现（这是唯一会拉起进程的能力入口）。
     */
    suspend fun executeCapabilityTool(
        serverId: String,
        toolName: String,
        arguments: JsonObject,
        workspace: String = "",
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val server = repository.servers.first().firstOrNull { it.id == serverId && it.isEnabled }
            ?: return@withContext false to "未找到或未启用的 MCP 服务：$serverId"
        val bound = commandBuilder.bindWorkspaceRepository(server, workspace)
        lastBoundWorkspaces[server.id] = workspace
        val tools = discoverServerIfNeeded(server)
        val tool = tools.firstOrNull { it.name == toolName }
            ?: return@withContext false to buildString {
                append("MCP[${server.name}] 没有名为 `$toolName` 的工具。")
                val available = tools.take(12).joinToString("、") { it.name }
                if (available.isNotBlank()) {
                    append("可用：$available")
                    if (tools.size > 12) append(" 等 ${tools.size} 个")
                    append("。")
                }
                append("可用 use_capability(action=\"inspect\", server=\"$serverId\") 查看工具清单与参数。")
            }
        cancellableResult { transport(bound).execute(bound, tool.name, arguments) }
            .onFailure { logger.e("MCP[${server.name}] 工具 ${tool.name} 执行异常: ${it.message}", it) }
            .onSuccess { (ok, output) ->
                if (!ok) logger.w("MCP[${server.name}] 工具 ${tool.name} 返回错误: ${secretRedactor.redact(output.take(500))}")
            }
            .getOrElse { false to "MCP 工具执行异常：${it.message ?: it::class.simpleName}" }
    }

    suspend fun discoverTools(server: McpServerConfig): List<McpToolInfo> = withContext(Dispatchers.IO) {
        val bound = boundConfig(server)
        transport(bound).discover(bound)
    }

    suspend fun testServer(server: McpServerConfig): Result<List<McpToolInfo>> = withContext(Dispatchers.IO) {
        // B1: 测试路径同样按绑定后配置发现并写缓存，保证缓存 fingerprint 与其他路径一致
        val bound = boundConfig(server)
        // 设置页手动测试不受冷却限制，成功时也顺带清掉对话路径的退避。
        clearDiscoveryCooldown(server.id)
        cancellableResult { discoverWithTimeout(bound) }
        .onSuccess {
            cache[server.id] = CachedTools(fingerprint(bound), it)
            state(server.id, McpConnectionState.ONLINE)
            // 测试成功 = 服务实际可用，旧错误与退避一并清除（getLastError 会持续显示旧故障误导用户）。
            lastErrors.remove(server.id)
            clearDiscoveryCooldown(server.id)
        }
        .onFailure { cache.remove(server.id); state(server.id, McpConnectionState.OFFLINE) }
    }

    suspend fun executeTool(
        fullToolName: String,
        arguments: JsonObject,
        workspace: String = "",
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        if (!fullToolName.startsWith("mcp__")) return@withContext false to "无效的 MCP 工具名称：$fullToolName"
        val servers = repository.servers.first().filter { it.isEnabled }
        val parsedServerId = fullToolName.removePrefix("mcp__").substringBefore("__")
        val candidateServers = servers.filter { server ->
            server.id == parsedServerId || McpToolApiName.isServerPrefix(fullToolName, server.id)
        }
        val tools = if (candidateServers.size == 1) {
            discoverServerIfNeeded(candidateServers.single())
        } else {
            candidateServers.flatMap { discoverServerIfNeeded(it) }
        }
        val tool = tools.firstOrNull { McpToolApiName.matches(it, fullToolName) }
            ?: return@withContext false to "未找到 MCP 工具：$fullToolName"
        val server = servers.firstOrNull { it.id == tool.serverId }
            ?: return@withContext false to "未找到 MCP 服务：${tool.serverId}"
        val bound = commandBuilder.bindWorkspaceRepository(server, workspace)
        // B1: 记录本次 workspace，使后续 discover/check 路径使用同一绑定配置，
        // 各路径 fingerprint 一致，避免 STDIO 进程被指纹乒乓反复重启
        lastBoundWorkspaces[server.id] = workspace
        return@withContext cancellableResult { transport(bound).execute(bound, tool.name, arguments) }
            .onFailure { logger.e("MCP[${server.name}] 工具 ${tool.name} 执行异常: ${it.message}", it) }
            .onSuccess { (ok, output) ->
                if (!ok) logger.w("MCP[${server.name}] 工具 ${tool.name} 返回错误: ${secretRedactor.redact(output.take(500))}")
            }
            .getOrElse { false to "MCP 工具执行异常：${it.message ?: it::class.simpleName}" }
    }

    private suspend fun discoverWithTimeout(server: McpServerConfig): List<McpToolInfo> =
        withTimeoutOrNull(DISCOVERY_TIMEOUT_MS.milliseconds) { transport(server).discover(server) }
            ?: error("工具发现超时（${DISCOVERY_TIMEOUT_MS / 1000}s）：沙箱会话或 MCP 进程可能已挂起")

    private suspend fun <T> cancellableResult(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (throwable: Throwable) {
        Result.failure(throwable)
    }

    private fun state(id: String, state: McpConnectionState) { _connectionStates.update { it + (id to state) } }

    /** use_capability list 的单服务摘要：不启动进程，工具数为缓存命中数。 */
    data class EnabledServerSummary(
        val id: String,
        val name: String,
        val cachedToolCount: Int,
        val connected: Boolean,
    )

    /**
     * 仍在冷却期时返回剩余毫秒；否则返回 null 表示可以尝试发现。
     *
     * 纯读：过期项不在这里清理（读路径带副作用会让并发下的判定依赖调用顺序），
     * 到期后自然不再命中，下次失败或成功时会被覆盖/清除。
     */
    private fun remainingDiscoveryCooldownMs(serverId: String): Long? =
        discoveryCooldownUntil[serverId]?.minus(System.currentTimeMillis())?.takeIf { it > 0L }

    /** 记录一次发现失败并推进退避，返回本次生效的冷却时长。 */
    private fun recordDiscoveryFailure(serverId: String): Long {
        val streak = discoveryFailureStreak.merge(serverId, 1) { old, delta -> old + delta } ?: 1
        val cooldown = mcpDiscoveryCooldownMs(streak)
        discoveryCooldownUntil[serverId] = System.currentTimeMillis() + cooldown
        return cooldown
    }

    private fun clearDiscoveryCooldown(serverId: String) {
        discoveryCooldownUntil.remove(serverId)
        discoveryFailureStreak.remove(serverId)
    }

    /**
     * B1: 按 executeTool 最近一次使用的 workspace 绑定配置，使 discover/check/test
     * 与执行路径计算 fingerprint 时看到同一份配置（否则 STDIO 指纹乒乓导致进程反复重启）。
     */
    private fun boundConfig(server: McpServerConfig): McpServerConfig =
        commandBuilder.bindWorkspaceRepository(server, lastBoundWorkspaces[server.id] ?: "")

    /** B10: 禁用/删除的 server 关闭其常驻传输资源（STDIO 进程 / HTTP legacy SSE 会话） */
    private suspend fun closeTransportConnection(server: McpServerConfig) {
        when (server.transportType) {
            McpTransportType.STDIO -> stdio.closeConnection(server.id)
            McpTransportType.SSE -> http.closeSession(server.id)
        }
    }

    private fun fingerprint(server: McpServerConfig): String =
        "${server.transportType}|${server.serverUrl.trim()}|${server.command}|${server.args}|${server.env.toSortedMap()}"
    private fun transport(server: McpServerConfig): McpTransport = when (server.transportType) {
        McpTransportType.STDIO -> stdio
        McpTransportType.SSE -> http
    }

    private companion object {
        // 工具错误输出可能含敏感信息（环境变量/密钥片段），日志前先脱敏。
        private val secretRedactor = top.wkbin.tianxuan.core.security.SecretRedactor()

        /** 单服务器工具发现总超时：覆盖沙箱会话拉起 + initialize + tools/list，超时即本轮跳过注入。 */
        const val DISCOVERY_TIMEOUT_MS = 8_000L

        /** 工具发现无会话上下文，agent 事件日志用占位 sessionId。 */
        const val DISCOVERY_LOG_SESSION = "-"

        const val RUNTIME_NOT_READY_MSG = "Linux runtime is not ready. Call initialize() first."
    }
}

/** 首次发现失败后的冷却时长。 */
internal const val MCP_DISCOVERY_COOLDOWN_BASE_MS = 30_000L

/** 冷却上限：持续故障的服务最多每 5 分钟重试一次。 */
internal const val MCP_DISCOVERY_COOLDOWN_MAX_MS = 5 * 60_000L

/**
 * 发现失败的指数退避：30s → 1min → 2min → 4min → 5min（上限）。
 *
 * 一次性故障只推迟半分钟，用户几乎察觉不到；长期故障的服务不再每轮对话都空耗一遍
 * 8s 发现超时，启动与子智能体初始化因此不被拖慢。
 */
internal fun mcpDiscoveryCooldownMs(failureStreak: Int): Long {
    if (failureStreak <= 1) return MCP_DISCOVERY_COOLDOWN_BASE_MS
    val shift = (failureStreak - 1).coerceAtMost(MAX_COOLDOWN_SHIFT)
    return (MCP_DISCOVERY_COOLDOWN_BASE_MS shl shift).coerceAtMost(MCP_DISCOVERY_COOLDOWN_MAX_MS)
}

private const val MAX_COOLDOWN_SHIFT = 16
