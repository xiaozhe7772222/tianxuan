package top.wkbin.tianxuan.di.harness

import org.koin.dsl.module
import android.content.Context
import java.io.File
import top.wkbin.tianxuan.harness.AgentContextExecutor
import top.wkbin.tianxuan.harness.ApprovalPolicyEngine
import top.wkbin.tianxuan.harness.BuildScriptToolExecutor
import top.wkbin.tianxuan.harness.HarnessLoop
import top.wkbin.tianxuan.harness.HarnessPathResolver
import top.wkbin.tianxuan.harness.knowledge.EmbeddingClient
import top.wkbin.tianxuan.harness.knowledge.KnowledgeManager
import top.wkbin.tianxuan.harness.HarnessProviderRunner
import top.wkbin.tianxuan.harness.HarnessToolRoundRunner
import top.wkbin.tianxuan.harness.HarnessWorkspaceRecommendations
import top.wkbin.tianxuan.harness.ProviderClient
import top.wkbin.tianxuan.harness.ProviderResponseNormalizer
import top.wkbin.tianxuan.harness.SubagentOrchestrator
import top.wkbin.tianxuan.harness.ToolExecutor
import top.wkbin.tianxuan.harness.ToolRoundDispatcher
import top.wkbin.tianxuan.harness.TurnRunner
import top.wkbin.tianxuan.harness.agent.AgentMcpBootstrap
import top.wkbin.tianxuan.harness.approval.ApprovalResumePolicy
import top.wkbin.tianxuan.harness.approval.SessionApprovalGrants
import top.wkbin.tianxuan.harness.browser.BrowserMcpBootstrap
import top.wkbin.tianxuan.harness.checkpoint.ConversationRewinder
import top.wkbin.tianxuan.harness.checkpoint.RewindController
import top.wkbin.tianxuan.harness.checkpoint.SessionForkConversationRewinder
import top.wkbin.tianxuan.harness.compaction.BranchSummarizer
import top.wkbin.tianxuan.harness.compaction.CompactionManager
import top.wkbin.tianxuan.harness.compaction.CompactionSummarizer
import top.wkbin.tianxuan.harness.dual.DualAgentCoordinator
import top.wkbin.tianxuan.harness.dual.PlannerPromptBuilder
import top.wkbin.tianxuan.harness.events.AgentEventLogger
import top.wkbin.tianxuan.harness.events.CapabilityEventWriter
import top.wkbin.tianxuan.harness.events.HarnessEventBus
import top.wkbin.tianxuan.harness.mcp.LinuxMcpStdioChannelFactory
import top.wkbin.tianxuan.harness.mcp.McpCommandBuilder
import top.wkbin.tianxuan.harness.mcp.McpHttpTransport
import top.wkbin.tianxuan.harness.mcp.McpManager
import top.wkbin.tianxuan.harness.mcp.McpStdioChannelFactory
import top.wkbin.tianxuan.harness.mcp.McpStdioTransport
import top.wkbin.tianxuan.harness.mcp.McpWorkspaceRecommender
import top.wkbin.tianxuan.harness.mcp.oauth.McpOAuthCoordinator
import top.wkbin.tianxuan.harness.mcp.oauth.McpOAuthTokenProvider
import top.wkbin.tianxuan.harness.mcp.server.HarnessToolProvider
import top.wkbin.tianxuan.harness.mcp.server.McpResourceDispatcher
import top.wkbin.tianxuan.harness.mcp.server.McpServerModule.provideBrowserMcpResources
import top.wkbin.tianxuan.harness.mcp.server.McpServerModule.provideBrowserMcpTools
import top.wkbin.tianxuan.harness.mcp.server.McpServerRuntime
import top.wkbin.tianxuan.harness.mcp.server.McpToolDispatcher
import top.wkbin.tianxuan.harness.operation.OperationCoordinator
import top.wkbin.tianxuan.harness.projection.CurrentSessionTracker
import top.wkbin.tianxuan.harness.projection.LiveMessagePort
import top.wkbin.tianxuan.harness.projection.SessionMessageProjector
import top.wkbin.tianxuan.harness.projection.SessionStateMirrors
import top.wkbin.tianxuan.harness.prompt.DefaultPrivilegeSectionRenderer
import top.wkbin.tianxuan.harness.prompt.MemoryRecallSelector
import top.wkbin.tianxuan.harness.prompt.PrivilegeSectionRenderer
import top.wkbin.tianxuan.harness.prompt.PromptAssetLoader
import top.wkbin.tianxuan.harness.prompt.PromptRouter
import top.wkbin.tianxuan.harness.prompt.SystemPromptBuilder
import top.wkbin.tianxuan.harness.queue.PromptQueueManager
import top.wkbin.tianxuan.harness.recovery.RecoveryManager
import top.wkbin.tianxuan.harness.session.ApiContextAssembler
import top.wkbin.tianxuan.harness.session.LaneManager
import top.wkbin.tianxuan.harness.session.SessionModelSwitcher
import top.wkbin.tianxuan.harness.session.SessionTreeStore
import top.wkbin.tianxuan.harness.session.SessionTurnCoordinator
import top.wkbin.tianxuan.harness.session.SessionTurnCoordinatorImpl
import top.wkbin.tianxuan.harness.skill.SkillEvolutionAdvisor
import top.wkbin.tianxuan.harness.subagent.SubagentLaneRunner
import top.wkbin.tianxuan.harness.task.AgentStateMachine
import top.wkbin.tianxuan.harness.workflow.AgentNodeExecutor
import top.wkbin.tianxuan.harness.workflow.ApprovalNodeExecutor
import top.wkbin.tianxuan.harness.workflow.ConditionNodeExecutor
import top.wkbin.tianxuan.harness.workflow.DelayNodeExecutor
import top.wkbin.tianxuan.harness.workflow.HarnessWorkflowAgentExecutionPort
import top.wkbin.tianxuan.harness.workflow.HostActionNodeExecutor
import top.wkbin.tianxuan.harness.workflow.LinuxNodeExecutor
import top.wkbin.tianxuan.harness.workflow.NodeExecutor
import top.wkbin.tianxuan.harness.workflow.PassthroughNodeExecutor
import top.wkbin.tianxuan.harness.workflow.ProactiveWorkflowAdvisor
import top.wkbin.tianxuan.harness.workflow.SetVariableNodeExecutor
import top.wkbin.tianxuan.harness.workflow.WorkflowAgentExecutionPort
import top.wkbin.tianxuan.harness.workflow.WorkflowApprovalBroker
import top.wkbin.tianxuan.harness.workflow.WorkflowGuiPilot
import top.wkbin.tianxuan.harness.workflow.WorkflowRunManager
import top.wkbin.tianxuan.harness.workflow.WorkflowScheduleRepository
import top.wkbin.tianxuan.harness.workflow.WorkflowScheduler
import top.wkbin.tianxuan.harness.workflow.WorkflowSignalBus
import top.wkbin.tianxuan.runtime.browser.tools.BrowserMcpResources
import top.wkbin.tianxuan.runtime.browser.tools.BrowserMcpTools
import org.koin.core.qualifier.named

/** 被控端（独立 MCP server）在 Koin 中的限定符：与浏览器自环的默认定义互不覆盖。 */
private const val AGENT_MCP_DISPATCHER = "agentMcpToolDispatcher"
private const val AGENT_MCP_RUNTIME = "agentMcpServerRuntime"

/** Dependency registrations owned by the harness module. */
val harnessModule = module {
    single<BrowserMcpResources> { provideBrowserMcpResources(registry = get()) }

    single<BrowserMcpTools> { provideBrowserMcpTools(registry = get(), browserPrefs = get()) }

    single<AgentContextExecutor> { AgentContextExecutor(agentContextDao = get(), json = get()) }

    single<ApprovalPolicyEngine> { ApprovalPolicyEngine(pathResolver = get()) }

    single<BuildScriptToolExecutor> { BuildScriptToolExecutor(repository = get()) }

    single<SessionTurnCoordinator> {
        SessionTurnCoordinatorImpl(
            preferences = get(),
            logger = get(),
        )
    }

    single<HarnessLoop> {
        HarnessLoop(
            workspaceRecommendations = get(),
            providerRunner = get(),
            toolRoundRunner = get(),
            foregroundLauncher = get(),
            providerClient = get(),
            toolExecutor = get(),
            toolRoundDispatcher = get(),
            messageStore = get(),
            sessionDao = get(),
            modelRepository = get(),
            settingsDataStore = get(),
            json = get(),
            logger = get(),
            approvalRepository = get(),
            operationCoordinator = get(),
            recoveryManager = get(),
            promptQueueManager = get(),
            sessionTracker = get(),
            stateMirrors = get(),
            messageProjector = get(),
            agentEventLogger = get(),
            resumePolicy = get(),
            sessionApprovalGrants = get(),
            agentTaskStateMachine = get(),
            turnRunner = get(),
            rewindController = get(),
            branchSummarizer = get(),
            skillEvolutionAdvisor = get(),
            turnCoordinator = get(),
        )
    }

    single<HarnessPathResolver> { HarnessPathResolver() }

    factory<HarnessProviderRunner> {
        HarnessProviderRunner(
            providerClient = get(),
            messageStore = get(),
            operationCoordinator = get(),
            stateMirrors = get(),
            messageProjector = get(),
            capabilityWriter = get(),
            agentEventLogger = get(),
            contextAssembler = get(),
            compactionManager = get(),
        )
    }

    factory<HarnessToolRoundRunner> {
        HarnessToolRoundRunner(
            toolExecutor = get(),
            sessionDao = get(),
            json = get(),
            operationCoordinator = get(),
            messageProjector = get(),
            stateMirrors = get(),
            agentEventLogger = get(),
            toolRoundDispatcher = get(),
        )
    }

    factory<HarnessWorkspaceRecommendations> {
        HarnessWorkspaceRecommendations(
            recommender = get(),
            servers = get(),
            paths = get(),
            logger = get(),
        )
    }

    single<ProviderClient> {
        ProviderClient(
            okHttpClient = get(),
            providerRepository = get(),
            modelDao = get(),
            mcpManager = get(),
            settingsDataStore = get(),
            json = get(),
        )
    }

    single<ProviderResponseNormalizer> { ProviderResponseNormalizer(json = get()) }

    single<SubagentOrchestrator> {
        SubagentOrchestrator(
            sessionDao = get(),
            laneManager = get(),
            laneRunner = get(),
            subagentRepository = get(),
            promptAssets = get(),
            agentContextRepo = get(),
            fileAccess = get(),
            providerClient = get(),
            logger = get(),
        )
    }

    single<ToolExecutor> {
        ToolExecutor(
            fileAccess = get(),
            linuxRuntime = get(),
            pathResolver = get(),
            approvalPolicyEngine = get(),
            secretRedactor = get(),
            fileDownloader = get(),
            linuxEnvironmentManager = get(),
            approvalRepository = get(),
            sessionDao = get(),
            subagentOrchestrator = get(),
            mcpManager = get(),
            contextExecutor = get(),
            messageStore = get(),
            eventBus = get(),
            privilegeManager = get(),
            androidAppManager = get(),
            androidAppRepository = get(),
            shizukuApis = get(),
            hostGuiController = get(),
            virtualDisplayCoordinator = get(),
            virtualScreenToolkit = get(),
            buildScriptToolExecutor = get(),
            promptRouter = get(),
            checkpointStore = get(),
            dualAgentCoordinator = get(),
            embeddedAdbManager = get(),
            workflowSignals = get(),
            sessionApprovalGrants = get(),
            compactionManager = get(),
            providerClient = get(),
            skillRepository = get(),
            settingsDataStore = get(),
        )
    }

    single<ToolRoundDispatcher> { ToolRoundDispatcher() }

    single<TurnRunner> { TurnRunner(normalizer = get()) }

    single<ApprovalResumePolicy> { ApprovalResumePolicy(sessionDao = get(), operationCoordinator = get()) }

    single<SessionApprovalGrants> { SessionApprovalGrants() }

    single<BrowserMcpBootstrap> {
        BrowserMcpBootstrap(
            context = get(),
            runtime = lazy { get<McpServerRuntime>() },
            registry = get(),
            browserPrefs = get(),
        )
    }

    single<RewindController> {
        RewindController(
            store = get(),
            fileAccess = get(),
            conversationRewinder = get(),
        )
    }

    single<SessionForkConversationRewinder> {
        SessionForkConversationRewinder(
            sessionDao = get(),
            runtimeRepo = get(),
            checkpointStore = get(),
        )
    }

    single<BranchSummarizer> {
        BranchSummarizer(
            repository = get(),
            json = get(),
            logger = get(),
            summarizer = get(),
        )
    }

    single<CompactionManager> {
        CompactionManager(
            repository = get(),
            json = get(),
            sessionStore = get(),
            summarizer = get(),
        )
    }

    single<CompactionSummarizer> { CompactionSummarizer(providerClient = get()) }

    single<PrivilegeSectionRenderer> { get<DefaultPrivilegeSectionRenderer>() }

    single<LiveMessagePort> { get<SessionMessageProjector>() }

    single<ConversationRewinder> { get<SessionForkConversationRewinder>() }

    single<DualAgentCoordinator> {
        DualAgentCoordinator(
            providerClient = get(),
            laneRunner = get(),
            promptBuilder = get(),
            sessionRepository = get(),
            eventBus = get(),
        )
    }

    single<PlannerPromptBuilder> { PlannerPromptBuilder() }

    single<AgentEventLogger> { AgentEventLogger(preferences = get(), logger = get()) }

    single<CapabilityEventWriter> {
        CapabilityEventWriter(
            port = get(),
            skillRepository = get(),
            mcpServerRepository = get(),
            mcpManager = get(),
        )
    }

    single<HarnessEventBus> { HarnessEventBus() }

    single<LinuxMcpStdioChannelFactory> { LinuxMcpStdioChannelFactory(linuxRuntime = get(), commandBuilder = get()) }

    single<McpCommandBuilder> { McpCommandBuilder() }

    single<McpHttpTransport> {
        McpHttpTransport(
            client = get(),
            json = get(),
            logger = get(),
            oauthTokens = get(),
            spillDirectory = File(get<Context>().cacheDir, "tianxuan_mcp_spills"),
        )
    }

    single<McpManager> {
        McpManager(
            repository = get(),
            stdio = get(),
            http = get(),
            commandBuilder = get(),
            linuxRuntime = get(),
            logger = get(),
            agentEventLogger = get(),
        )
    }

    single<McpStdioChannelFactory> { get<LinuxMcpStdioChannelFactory>() }

    single<McpStdioTransport> {
        McpStdioTransport(
            json = get(),
            commandBuilder = get(),
            channelFactory = get(),
        )
    }

    single<McpWorkspaceRecommender> { McpWorkspaceRecommender() }

    single<McpOAuthCoordinator> { McpOAuthCoordinator(credentials = get(), client = get()) }

    single<McpOAuthTokenProvider> { McpOAuthTokenProvider(credentials = get(), coordinator = get()) }

    single<McpResourceDispatcher> { McpResourceDispatcher(browserResources = get()) }

    single<McpServerRuntime> { McpServerRuntime(toolDispatcher = get(), resourceDispatcher = get()) }

    single<McpToolDispatcher> { McpToolDispatcher(browserTools = get()) }

    // ===== MCP 被控端（外部 AI 客户端控制本 App）：独立端点 + 独立 dispatcher =====
    // 工具集复用 ProviderClient.TOOLS，经 HarnessToolProvider 按能力分层暴露；
    // 写入/执行层开关由 AgentMcpBootstrap 从偏好实时刷新。
    single<HarnessToolProvider> { HarnessToolProvider(executor = get()) }

    single(named(AGENT_MCP_DISPATCHER)) {
        McpToolDispatcher(
            browserTools = get(),
            extraProviders = listOf(get<HarnessToolProvider>()),
        )
    }

    single(named(AGENT_MCP_RUNTIME)) {
        McpServerRuntime(
            toolDispatcher = get(named(AGENT_MCP_DISPATCHER)),
            resourceDispatcher = get(),
            serverName = McpServerRuntime.AGENT_SERVER_NAME,
        )
    }

    // runtime/toolProvider 均为 Lazy：被控端默认关闭，其构造图（ToolExecutor 全家桶）不应在启动时展开
    single<AgentMcpBootstrap> {
        AgentMcpBootstrap(
            runtime = lazy { get<McpServerRuntime>(named(AGENT_MCP_RUNTIME)) },
            toolProvider = lazy { get<HarnessToolProvider>() },
            prefs = get(),
            foregroundLauncher = get(),
        )
    }

    single<OperationCoordinator> {
        OperationCoordinator(
            repository = get(),
            json = get(),
            eventBus = get(),
        )
    }

    single<CurrentSessionTracker> { CurrentSessionTracker() }

    single<SessionMessageProjector> { SessionMessageProjector(store = get(), tracker = get()) }

    single<SessionStateMirrors> { SessionStateMirrors(tracker = get()) }

    single<MemoryRecallSelector> { MemoryRecallSelector(agentContextDao = get()) }

    single<DefaultPrivilegeSectionRenderer> {
        DefaultPrivilegeSectionRenderer(
            context = get(),
            privilegeManager = get(),
            promptAssets = get(),
        )
    }

    single<PromptAssetLoader> { PromptAssetLoader(context = get()) }

    single<PromptRouter> { PromptRouter(promptAssets = get()) }

    single<SystemPromptBuilder> {
        SystemPromptBuilder(
            context = get(),
            settingsDataStore = get(),
            skillRepository = get(),
            toolRepository = get(),
            agentContextDao = get(),
            subagentRepository = get(),
            mcpServerRepository = get(),
            promptAssets = get(),
            fileAccess = get(),
            privilegeRenderer = get(),
            promptRouter = get(),
        )
    }

    single<PromptQueueManager> {
        PromptQueueManager(
            repository = get(),
            json = get(),
            sessionStore = get(),
            logger = get(),
        )
    }

    single<RecoveryManager> {
        RecoveryManager(
            repository = get(),
            coordinator = get(),
            approvalRepository = get(),
            json = get(),
            eventBus = get(),
        )
    }

    factory<ApiContextAssembler> {
        ApiContextAssembler(
            compactionManager = get(),
            settingsDataStore = get(),
            systemPromptBuilder = get(),
            sessionStore = get(),
            memoryRecallSelector = get(),
            agentApprovalRepository = get(),
            knowledgeManager = getOrNull<KnowledgeManager>(),
        )
    }

    single<EmbeddingClient> {
        EmbeddingClient(
            okHttpClient = get(),
            json = get(),
        )
    }

    single<KnowledgeManager> {
        KnowledgeManager(
            repository = get(),
            embeddingClient = get(),
            providerRepository = get(),
            aiModelRepository = get(),
            settingsDataStore = get(),
        )
    }

    single<LaneManager> { LaneManager(repository = get(), treeStore = get()) }

    single<SessionModelSwitcher> {
        SessionModelSwitcher(
            sessionDao = get(),
            modelDao = get(),
            settingsDataStore = get(),
            compactionManager = get(),
            providerClient = get(),
            messagePort = get(),
        )
    }

    single<SessionTreeStore> {
        SessionTreeStore(
            repository = get(),
            json = get(),
            logger = get(),
        )
    }

    single<SkillEvolutionAdvisor> {
        SkillEvolutionAdvisor(
            providerClient = get(),
            skillRepository = get(),
            settingsDataStore = get(),
            sessionDao = get(),
            projector = get(),
            logger = get(),
        )
    }

    single<SubagentLaneRunner> {
        SubagentLaneRunner(
            context = get(),
            providerClient = get(),
            toolExecutor = { get<ToolExecutor>() },
            treeStore = get(),
            operations = get(),
            settingsDataStore = get(),
            promptAssets = get(),
            json = get(),
        )
    }

    single<AgentStateMachine> { AgentStateMachine(repository = get(), logger = get()) }

    factory<PassthroughNodeExecutor> { PassthroughNodeExecutor() }

    factory<ApprovalNodeExecutor> { ApprovalNodeExecutor(broker = get()) }

    factory<LinuxNodeExecutor> { LinuxNodeExecutor(linuxRuntime = get()) }

    factory<AgentNodeExecutor> { AgentNodeExecutor(agentExecution = get()) }

    factory<ConditionNodeExecutor> { ConditionNodeExecutor() }

    factory<DelayNodeExecutor> { DelayNodeExecutor() }

    factory<SetVariableNodeExecutor> { SetVariableNodeExecutor() }

    factory<HostActionNodeExecutor> {
        HostActionNodeExecutor(
            appContext = get(),
            gui = get(),
            privilegeManager = get(),
            linuxRuntime = get(),
            guiPilot = get(),
        )
    }

    single<WorkflowSignalBus> { WorkflowSignalBus() }

    single<ProactiveWorkflowAdvisor> { ProactiveWorkflowAdvisor(signalBus = get(), workflows = get()) }

    single<HarnessWorkflowAgentExecutionPort> {
        HarnessWorkflowAgentExecutionPort(
            sessions = get(),
            models = get(),
            laneRunner = get(),
            subagentOrchestrator = get(),
        )
    }

    single<WorkflowApprovalBroker> { WorkflowApprovalBroker() }

    factory<WorkflowAgentExecutionPort> { get<HarnessWorkflowAgentExecutionPort>() }

    single<WorkflowGuiPilot> {
        WorkflowGuiPilot(
            gui = get(),
            privilegeManager = get(),
            providerClient = get(),
            hud = get(),
        )
    }

    single<WorkflowRunManager> {
        WorkflowRunManager(
            scheduler = get(),
            repository = get(),
            linuxRuntime = get(),
            json = get(),
        )
    }

    single<WorkflowScheduleRepository> {
        WorkflowScheduleRepository(
            store = get(),
            workflowRepository = get(),
            dispatcher = get(),
        )
    }

    single<WorkflowScheduler> { WorkflowScheduler(executors = get(named("workflowExecutors")), approvalBroker = get()) }

    single<Set<NodeExecutor>>(named("workflowExecutors")) {
        setOf(get<PassthroughNodeExecutor>(), get<ApprovalNodeExecutor>(), get<LinuxNodeExecutor>(), get<AgentNodeExecutor>(), get<ConditionNodeExecutor>(), get<DelayNodeExecutor>(), get<SetVariableNodeExecutor>(), get<HostActionNodeExecutor>())
    }
}
