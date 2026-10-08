package top.wkbin.tianxuan.ui.navigation

import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import top.wkbin.tianxuan.ui.chat.ChatScreen
import top.wkbin.tianxuan.ui.chat.ChatViewModel
import top.wkbin.tianxuan.ui.components.MainDestination
import top.wkbin.tianxuan.ui.components.RuntimeBottomBar
import top.wkbin.tianxuan.ui.components.RuntimeNavRail
import top.wkbin.tianxuan.ui.components.rememberWidthClass
import top.wkbin.tianxuan.ui.theme.LocalLiquidGlassBackdrop
import top.wkbin.tianxuan.ui.developer.DeveloperScreen
import top.wkbin.tianxuan.ui.developer.AdbLogcatScreen
import top.wkbin.tianxuan.ui.preview.LiquidGlassCatalogScreen
import top.wkbin.tianxuan.ui.home.HomeScreen
import top.wkbin.tianxuan.ui.settings.AgentSettingsScreen
import top.wkbin.tianxuan.ui.settings.ModelEditorScreen
import top.wkbin.tianxuan.ui.settings.ModelProfilesScreen
import top.wkbin.tianxuan.ui.settings.LocalLlmScreen
import top.wkbin.tianxuan.ui.settings.SettingsScreen
import top.wkbin.tianxuan.ui.settings.SettingsViewModel
import top.wkbin.tianxuan.ui.iteration.CustomIterationScreen
import top.wkbin.tianxuan.ui.terminal.TerminalScreen
import top.wkbin.tianxuan.ui.browser.BrowserScreen
import top.wkbin.tianxuan.ui.workspace.CodeEditorScreen
import top.wkbin.tianxuan.ui.workspace.WorkspaceExplorerScreen
import top.wkbin.tianxuan.ui.workspace.WorkspaceScreen
import kotlinx.serialization.Serializable

@Serializable
sealed interface AppDestination : NavKey

@Serializable data object HomeDestination : AppDestination
@Serializable data object AgentDestination : AppDestination
@Serializable data object WorkspaceDestination : AppDestination
@Serializable data object WorkshopSettingsDestination : AppDestination
@Serializable data object WorkshopEnvironmentSettingsDestination : AppDestination
@Serializable data object WorkshopSigningSettingsDestination : AppDestination
@Serializable data class WorkshopScriptEditorDestination(val type: String) : AppDestination
@Serializable data class WorkspaceExplorerDestination(val projectName: String, val initialPath: String = "") : AppDestination
@Serializable data class CodeEditorDestination(val projectName: String, val relativePath: String) : AppDestination
@Serializable data object SettingsDestination : AppDestination
@Serializable data object SettingsSearchDestination : AppDestination
@Serializable data object AgentEcoSettingsDestination : AppDestination
@Serializable data object LinuxEnvSettingsDestination : AppDestination
@Serializable data object AppearanceSettingsDestination : AppDestination
@Serializable data object SystemDevSettingsDestination : AppDestination
@Serializable data object AboutCommunityDestination : AppDestination
@Serializable data object SponsorDestination : AppDestination
@Serializable data object AgentSettingsDestination : AppDestination
@Serializable data object AgentSubagentSettingsDestination : AppDestination
@Serializable data object AgentSkillSettingsDestination : AppDestination
@Serializable data object McpSettingsDestination : AppDestination
@Serializable data object ToolCenterDestination : AppDestination
@Serializable data object CcSwitchDestination : AppDestination
@Serializable data class ToolDetailDestination(val toolId: String) : AppDestination
@Serializable data object DistroManagementDestination : AppDestination
@Serializable data object StorageMountSettingsDestination : AppDestination
@Serializable data object StorageUsageDestination : AppDestination
@Serializable data object AppManagementDestination : AppDestination
@Serializable data object EnvironmentVariableSettingsDestination : AppDestination
@Serializable data object SshSettingsDestination : AppDestination
@Serializable data object FtpSettingsDestination : AppDestination
@Serializable data object ModelProfilesDestination : AppDestination
@Serializable data object LocalLlmDestination : AppDestination
@Serializable data class ModelEditorDestination(val modelId: String? = null) : AppDestination
@Serializable data object QuickPhrasesDestination : AppDestination
@Serializable data object StatsDestination : AppDestination
@Serializable data object PermissionGuideDestination : AppDestination
@Serializable data object DeveloperDestination : AppDestination
@Serializable data object LiquidGlassCatalogDestination : AppDestination
@Serializable data object AdbLogcatDestination : AppDestination
@Serializable data object A2uiPocDestination : AppDestination
@Serializable data object KnowledgeDestination : AppDestination
@Serializable data object CustomIterationDestination : AppDestination
@Serializable data class TerminalDestination(val toolId: String = "", val project: String = "") : AppDestination
@Serializable data object BrowserDestination : AppDestination
@Serializable data class GitRepositoryDestination(val projectName: String) : AppDestination
@Serializable data class WorkflowDestination(
    val projectName: String = "",
    val workflowId: String? = null,
    val initialVariables: Map<String, String> = emptyMap(),
    // 通知栏深链：进入工作流页后直接定位到该执行的运行视图
    val executionId: String? = null,
) : AppDestination

/**
 * 天玄核心导航分发系统
 * 采用 Navigation 3，为每个 Tab 独立维护持久回退栈与状态生命周期
 */
@Composable
fun TianXuanNavHost(
    globalNavigationBus: top.wkbin.tianxuan.core.common.navigation.GlobalNavigationBus? = null,
) {
    // Root tab entries are removed from composition when another tab becomes active. Keep the
    // conversation owner at the Activity scope so switching back to 智枢 does not rebuild Koin's
    // graph, restore the latest session, and restart its initialization skeleton on every visit.
    val chatViewModel: ChatViewModel = koinViewModel()
    // 浏览器引擎是全局单例：BrowserViewModel 同样挂到 Activity 作用域，
    // 使智枢内嵌浏览器面板与独立浏览器页共享同一份 tab/URL/共浏览状态。
    val browserViewModel: top.wkbin.tianxuan.ui.browser.BrowserViewModel = koinViewModel()
    val browserUiState by browserViewModel.uiState.collectAsStateWithLifecycle()
    // SettingsViewModel owns dozens of eagerly shared DataStore/database streams and performs
    // repository initialization. Let the settings navigation graph share the Activity-scoped
    // instance instead of constructing that whole graph once for every Navigation3 entry.
    val settingsViewModel: SettingsViewModel = koinViewModel()
    val homeStack = rememberNavBackStack(HomeDestination)
    val agentStack = rememberNavBackStack(AgentDestination)
    val workspaceStack = rememberNavBackStack(WorkspaceDestination)
    val settingsStack = rememberNavBackStack(SettingsDestination)
    var pendingHealingTask by remember { mutableStateOf<HealingTask?>(null) }
    var selectedMain by rememberSaveable { mutableStateOf(MainDestination.Home) } // 默认进入天玄开辟主界
    /** Programmatic stack mutation (bus / workflow). */
    fun NavBackStack<NavKey>.pushRaw(destination: NavKey) {
        if (lastOrNull() == destination) return
        add(destination)
    }

    LaunchedEffect(chatViewModel) {
        chatViewModel.workflowLaunchRequests.collect { request ->
            selectedMain = MainDestination.Agent
            agentStack.pushRaw(WorkflowDestination(request.projectName, request.workflowId, request.initialVariables))
        }
    }

    LaunchedEffect(globalNavigationBus) {
        globalNavigationBus?.events?.collect { target ->
            when (target) {
                top.wkbin.tianxuan.core.common.navigation.AppNavigationTarget.AdbLogcat -> {
                    selectedMain = MainDestination.Settings
                    if (settingsStack.lastOrNull() != AdbLogcatDestination) {
                        if (settingsStack.lastOrNull() == SettingsDestination) {
                            settingsStack.pushRaw(SystemDevSettingsDestination)
                        }
                        if (settingsStack.lastOrNull() == SystemDevSettingsDestination) {
                            settingsStack.pushRaw(AdbLogcatDestination)
                        } else if (settingsStack.lastOrNull() != AdbLogcatDestination) {
                            settingsStack.pushRaw(AdbLogcatDestination)
                        }
                    }
                    globalNavigationBus.clearLatest(target)
                }
                is top.wkbin.tianxuan.core.common.navigation.AppNavigationTarget.WorkflowRun -> {
                    // 工作流通知点入：切到智枢栈并打开运行页
                    selectedMain = MainDestination.Agent
                    agentStack.pushRaw(WorkflowDestination(executionId = target.executionId))
                    globalNavigationBus.clearLatest(target)
                }
                top.wkbin.tianxuan.core.common.navigation.AppNavigationTarget.AgentSettings -> {
                    selectedMain = MainDestination.Settings
                    if (settingsStack.lastOrNull() != AgentSettingsDestination) {
                        settingsStack.pushRaw(AgentSettingsDestination)
                    }
                    globalNavigationBus.clearLatest(target)
                }
            }
        }
    }

    val activeStack = when (selectedMain) {
        MainDestination.Home -> homeStack
        MainDestination.Agent -> agentStack
        MainDestination.Workspace -> workspaceStack
        MainDestination.Settings -> settingsStack
    }

    fun navigateMain(destination: MainDestination) {
        // Tab swaps are instantaneous (key(selectedMain)); do not transition-lock them.
        selectedMain = destination
    }

    fun NavBackStack<NavKey>.push(from: NavKey, destination: NavKey) {
        if (lastOrNull() == from && lastOrNull() != destination) {
            add(destination)
        }
    }

    fun popBack() {
        if (activeStack.size <= 1) return
        activeStack.removeLastOrNull()
    }

    @Composable
    fun GuardedEntry(
        destination: NavKey,
        content: @Composable () -> Unit,
    ) {
        val isActive = destination == activeStack.lastOrNull()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (isActive) Modifier
                    else Modifier.pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
                )
        ) {
            content()
        }
    }

    val appEntryProvider: (NavKey) -> NavEntry<NavKey> = entryProvider {
            entry<HomeDestination> {
                GuardedEntry(HomeDestination) {
                    HomeScreen(
                        onNavigate = ::navigateMain,
                        onOpenTerminal = { homeStack.push(HomeDestination, TerminalDestination()) },
                        onOpenToolCenter = { homeStack.push(HomeDestination, ToolCenterDestination) },
                    )
                }
            }
            entry<AgentDestination> {
                GuardedEntry(AgentDestination) {
                    LaunchedEffect(pendingHealingTask) {
                        pendingHealingTask?.let { task ->
                            chatViewModel.startHealingTask(task.title, task.prompt)
                            pendingHealingTask = null
                        }
                    }
                    ChatScreen(
                        viewModel = chatViewModel,
                        onNavigate = ::navigateMain,
                        // 内嵌终端面板非独立导航节点，无返回目标：隐藏顶栏返回箭头，避免点击无反馈
                        terminalPane = { project -> TerminalScreen(onBack = {}, project = project, showBackButton = false) },
                        // 内嵌浏览器面板：与独立浏览器页共享 Activity 级 BrowserViewModel，
                        // 手机端左右滑动切换对话/浏览器，宽屏双栏可切"终端/浏览器"
                        browserPane = { onExit ->
                            top.wkbin.tianxuan.ui.browser.BrowserPane(
                                viewModel = browserViewModel,
                                onExit = onExit,
                            )
                        },
                        browserActivityTick = browserUiState.activityTick,
                        browserBackPressed = { browserViewModel.handleBackImmediate() },
                        onOpenFile = { projectName, relativePath ->
                            agentStack.push(AgentDestination, CodeEditorDestination(projectName, relativePath))
                        },
                        onOpenRepository = { projectName ->
                            agentStack.push(AgentDestination, GitRepositoryDestination(projectName))
                        },
                    )
                }
            }
            entry<WorkspaceDestination> {
                GuardedEntry(WorkspaceDestination) {
                    WorkspaceScreen(
                        onNavigate = ::navigateMain,
                        onOpenExplorer = { projectName -> workspaceStack.push(WorkspaceDestination, WorkspaceExplorerDestination(projectName)) },
                        onOpenTerminal = { project -> workspaceStack.push(WorkspaceDestination, TerminalDestination(project = project)) },
                        onOpenToolCenter = { workspaceStack.push(WorkspaceDestination, ToolCenterDestination) },
                        onOpenWorkshopSettings = { workspaceStack.push(WorkspaceDestination, WorkshopSettingsDestination) },
                        onOpenWorkflows = { projectName -> workspaceStack.push(WorkspaceDestination, WorkflowDestination(projectName)) },
                    )
                }
            }
            entry<WorkflowDestination> { destination ->
                GuardedEntry(destination) {
                    top.wkbin.tianxuan.ui.workflow.WorkflowScreen(
                        projectName = destination.projectName,
                        initialWorkflowId = destination.workflowId,
                        initialVariables = destination.initialVariables,
                        initialExecutionId = destination.executionId,
                        onBack = ::popBack,
                    )
                }
            }
            entry<WorkshopSettingsDestination> {
                GuardedEntry(WorkshopSettingsDestination) {
                    top.wkbin.tianxuan.ui.workspace.WorkshopSettingsScreen(
                        onBack = ::popBack,
                        onOpenEnvironment = { workspaceStack.push(WorkshopSettingsDestination, WorkshopEnvironmentSettingsDestination) },
                        onOpenSigning = { workspaceStack.push(WorkshopSettingsDestination, WorkshopSigningSettingsDestination) },
                        onEditScript = { type -> workspaceStack.push(WorkshopSettingsDestination, WorkshopScriptEditorDestination(type.name)) },
                    )
                }
            }
            entry<WorkshopEnvironmentSettingsDestination> {
                GuardedEntry(WorkshopEnvironmentSettingsDestination) {
                    top.wkbin.tianxuan.ui.workspace.WorkshopEnvironmentSettingsScreen(onBack = ::popBack)
                }
            }
            entry<WorkshopSigningSettingsDestination> {
                GuardedEntry(WorkshopSigningSettingsDestination) {
                    top.wkbin.tianxuan.ui.workspace.WorkshopSigningScreen(onBack = ::popBack)
                }
            }
            entry<WorkshopScriptEditorDestination> { destination ->
                GuardedEntry(destination) {
                    top.wkbin.tianxuan.ui.workspace.WorkshopScriptEditorScreen(
                        type = top.wkbin.tianxuan.ui.workspace.WorkshopScriptType.valueOf(destination.type),
                        onBack = ::popBack,
                    )
                }
            }
            entry<WorkspaceExplorerDestination> { destination ->
                GuardedEntry(destination) {
                    WorkspaceExplorerScreen(
                        projectName = destination.projectName,
                        initialPath = destination.initialPath,
                        onBack = ::popBack,
                        onOpenFile = { relativePath ->
                            workspaceStack.push(destination, CodeEditorDestination(destination.projectName, relativePath))
                        },
                        onOpenTerminal = { project ->
                            workspaceStack.push(destination, TerminalDestination(project = project))
                        },
                    )
                }
            }
            entry<CodeEditorDestination> { destination ->
                GuardedEntry(destination) {
                    CodeEditorScreen(
                        projectName = destination.projectName,
                        relativePath = destination.relativePath,
                        onBack = ::popBack,
                    )
                }
            }
            entry<SettingsDestination> {
                GuardedEntry(SettingsDestination) {
                    SettingsScreen(
                        onNavigate = ::navigateMain,
                        onOpenAgentEco = { settingsStack.push(SettingsDestination, AgentEcoSettingsDestination) },
                        onOpenLinuxEnv = { settingsStack.push(SettingsDestination, LinuxEnvSettingsDestination) },
                        onOpenAppearance = { settingsStack.push(SettingsDestination, AppearanceSettingsDestination) },
                        onOpenSystemDev = { settingsStack.push(SettingsDestination, SystemDevSettingsDestination) },
                        onOpenAboutCommunity = { settingsStack.push(SettingsDestination, AboutCommunityDestination) },
                        onOpenSearch = { settingsStack.push(SettingsDestination, SettingsSearchDestination) },
                        onOpenA2uiPoc = { settingsStack.push(SettingsDestination, A2uiPocDestination) },
                        onOpenKnowledge = { settingsStack.push(SettingsDestination, KnowledgeDestination) },
                        viewModel = settingsViewModel,
                    )
                }
            }
            entry<SettingsSearchDestination> {
                GuardedEntry(SettingsSearchDestination) {
                    top.wkbin.tianxuan.ui.settings.search.SettingsSearchScreen(
                        onBack = ::popBack,
                        onNavigateToTarget = { target ->
                            when (target) {
                                top.wkbin.tianxuan.ui.settings.search.SettingsSearchTarget.MODEL_PROFILES ->
                                    settingsStack.push(SettingsSearchDestination, ModelProfilesDestination)
                                top.wkbin.tianxuan.ui.settings.search.SettingsSearchTarget.MODEL_EDITOR_NEW ->
                                    settingsStack.push(SettingsSearchDestination, ModelEditorDestination())
                                top.wkbin.tianxuan.ui.settings.search.SettingsSearchTarget.LOCAL_LLM ->
                                    settingsStack.push(SettingsSearchDestination, LocalLlmDestination)
                                top.wkbin.tianxuan.ui.settings.search.SettingsSearchTarget.QUICK_PHRASES ->
                                    settingsStack.push(SettingsSearchDestination, QuickPhrasesDestination)
                                top.wkbin.tianxuan.ui.settings.search.SettingsSearchTarget.STATS ->
                                    settingsStack.push(SettingsSearchDestination, StatsDestination)
                                top.wkbin.tianxuan.ui.settings.search.SettingsSearchTarget.TOOL_CENTER ->
                                    settingsStack.push(SettingsSearchDestination, ToolCenterDestination)
                                top.wkbin.tianxuan.ui.settings.search.SettingsSearchTarget.CC_SWITCH ->
                                    settingsStack.push(SettingsSearchDestination, CcSwitchDestination)
                                top.wkbin.tianxuan.ui.settings.search.SettingsSearchTarget.AGENT_EXECUTION ->
                                    settingsStack.push(SettingsSearchDestination, AgentSettingsDestination)
                                top.wkbin.tianxuan.ui.settings.search.SettingsSearchTarget.AGENT_SUBAGENTS ->
                                    settingsStack.push(SettingsSearchDestination, AgentSubagentSettingsDestination)
                                top.wkbin.tianxuan.ui.settings.search.SettingsSearchTarget.AGENT_SKILLS ->
                                    settingsStack.push(SettingsSearchDestination, AgentSkillSettingsDestination)
                                top.wkbin.tianxuan.ui.settings.search.SettingsSearchTarget.MCP_SETTINGS ->
                                    settingsStack.push(SettingsSearchDestination, McpSettingsDestination)
                                top.wkbin.tianxuan.ui.settings.search.SettingsSearchTarget.DISTRO_MANAGEMENT ->
                                    settingsStack.push(SettingsSearchDestination, DistroManagementDestination)
                                top.wkbin.tianxuan.ui.settings.search.SettingsSearchTarget.STORAGE_USAGE ->
                                    settingsStack.push(SettingsSearchDestination, StorageUsageDestination)
                                top.wkbin.tianxuan.ui.settings.search.SettingsSearchTarget.STORAGE_MOUNTS ->
                                    settingsStack.push(SettingsSearchDestination, StorageMountSettingsDestination)
                                top.wkbin.tianxuan.ui.settings.search.SettingsSearchTarget.ENV_VARS ->
                                    settingsStack.push(SettingsSearchDestination, EnvironmentVariableSettingsDestination)
                                top.wkbin.tianxuan.ui.settings.search.SettingsSearchTarget.SSH_SETTINGS ->
                                    settingsStack.push(SettingsSearchDestination, SshSettingsDestination)
                                top.wkbin.tianxuan.ui.settings.search.SettingsSearchTarget.FTP_SETTINGS ->
                                    settingsStack.push(SettingsSearchDestination, FtpSettingsDestination)
                                top.wkbin.tianxuan.ui.settings.search.SettingsSearchTarget.WEB_CHAT,
                                top.wkbin.tianxuan.ui.settings.search.SettingsSearchTarget.PRIVILEGE_MODE ->
                                    settingsStack.push(SettingsSearchDestination, LinuxEnvSettingsDestination)
                                top.wkbin.tianxuan.ui.settings.search.SettingsSearchTarget.APP_MANAGEMENT ->
                                    settingsStack.push(SettingsSearchDestination, AppManagementDestination)
                                top.wkbin.tianxuan.ui.settings.search.SettingsSearchTarget.APPEARANCE_SETTINGS,
                                top.wkbin.tianxuan.ui.settings.search.SettingsSearchTarget.THEME_MODE,
                                top.wkbin.tianxuan.ui.settings.search.SettingsSearchTarget.DYNAMIC_COLOR,
                                top.wkbin.tianxuan.ui.settings.search.SettingsSearchTarget.LIQUID_GLASS,
                                top.wkbin.tianxuan.ui.settings.search.SettingsSearchTarget.FONT_SCALE,
                                top.wkbin.tianxuan.ui.settings.search.SettingsSearchTarget.TERMINAL_SETTINGS,
                                top.wkbin.tianxuan.ui.settings.search.SettingsSearchTarget.LANGUAGE_SETTINGS ->
                                    settingsStack.push(SettingsSearchDestination, AppearanceSettingsDestination)
                                top.wkbin.tianxuan.ui.settings.search.SettingsSearchTarget.BATTERY_OPTIMIZATION,
                                top.wkbin.tianxuan.ui.settings.search.SettingsSearchTarget.PHANTOM_PROCESS ->
                                    settingsStack.push(SettingsSearchDestination, SystemDevSettingsDestination)
                                top.wkbin.tianxuan.ui.settings.search.SettingsSearchTarget.DEVELOPER_OPTIONS ->
                                    settingsStack.push(SettingsSearchDestination, DeveloperDestination)
                                top.wkbin.tianxuan.ui.settings.search.SettingsSearchTarget.ADB_LOGCAT ->
                                    settingsStack.push(SettingsSearchDestination, AdbLogcatDestination)
                                top.wkbin.tianxuan.ui.settings.search.SettingsSearchTarget.CUSTOM_ITERATION ->
                                    settingsStack.push(SettingsSearchDestination, CustomIterationDestination)
                                top.wkbin.tianxuan.ui.settings.search.SettingsSearchTarget.PERMISSION_GUIDE ->
                                    settingsStack.push(SettingsSearchDestination, PermissionGuideDestination)
                                top.wkbin.tianxuan.ui.settings.search.SettingsSearchTarget.WORKSHOP_SETTINGS -> {
                                    selectedMain = MainDestination.Workspace
                                    workspaceStack.pushRaw(WorkshopSettingsDestination)
                                }
                                top.wkbin.tianxuan.ui.settings.search.SettingsSearchTarget.WORKSHOP_ENVIRONMENT -> {
                                    selectedMain = MainDestination.Workspace
                                    workspaceStack.pushRaw(WorkshopSettingsDestination)
                                    workspaceStack.pushRaw(WorkshopEnvironmentSettingsDestination)
                                }
                                top.wkbin.tianxuan.ui.settings.search.SettingsSearchTarget.WORKSHOP_SIGNING -> {
                                    selectedMain = MainDestination.Workspace
                                    workspaceStack.pushRaw(WorkshopSettingsDestination)
                                    workspaceStack.pushRaw(WorkshopSigningSettingsDestination)
                                }
                                top.wkbin.tianxuan.ui.settings.search.SettingsSearchTarget.WORKFLOWS -> {
                                    selectedMain = MainDestination.Workspace
                                    workspaceStack.pushRaw(WorkflowDestination())
                                }
                                top.wkbin.tianxuan.ui.settings.search.SettingsSearchTarget.NAV_HOME ->
                                    selectedMain = MainDestination.Home
                                top.wkbin.tianxuan.ui.settings.search.SettingsSearchTarget.NAV_AGENT_CHAT ->
                                    selectedMain = MainDestination.Agent
                                top.wkbin.tianxuan.ui.settings.search.SettingsSearchTarget.NAV_WORKSPACE ->
                                    selectedMain = MainDestination.Workspace
                                top.wkbin.tianxuan.ui.settings.search.SettingsSearchTarget.NAV_TERMINAL -> {
                                    activeStack.pushRaw(TerminalDestination())
                                }
                                top.wkbin.tianxuan.ui.settings.search.SettingsSearchTarget.NAV_BROWSER -> {
                                    activeStack.pushRaw(BrowserDestination)
                                }
                                top.wkbin.tianxuan.ui.settings.search.SettingsSearchTarget.ABOUT_COMMUNITY,
                                top.wkbin.tianxuan.ui.settings.search.SettingsSearchTarget.ABOUT_UPDATE ->
                                    settingsStack.push(SettingsSearchDestination, AboutCommunityDestination)
                                top.wkbin.tianxuan.ui.settings.search.SettingsSearchTarget.ABOUT_SPONSOR ->
                                    settingsStack.push(SettingsSearchDestination, SponsorDestination)
                            }
                        }
                    )
                }
            }
            entry<AppearanceSettingsDestination> {
                GuardedEntry(AppearanceSettingsDestination) {
                    top.wkbin.tianxuan.ui.settings.AppearanceSettingsScreen(
                        onBack = ::popBack,
                        viewModel = settingsViewModel,
                    )
                }
            }
            entry<AgentEcoSettingsDestination> {
                GuardedEntry(AgentEcoSettingsDestination) {
                    top.wkbin.tianxuan.ui.settings.AgentEcoSettingsScreen(
                        onBack = ::popBack,
                        onOpenModelProfiles = { settingsStack.push(AgentEcoSettingsDestination, ModelProfilesDestination) },
                        onOpenLocalLlm = { settingsStack.push(AgentEcoSettingsDestination, LocalLlmDestination) },
                        onOpenCcSwitch = { settingsStack.push(AgentEcoSettingsDestination, CcSwitchDestination) },
                        onOpenToolCenter = { settingsStack.push(AgentEcoSettingsDestination, ToolCenterDestination) },
                        onOpenAgentSettings = { settingsStack.push(AgentEcoSettingsDestination, AgentSettingsDestination) },
                        onOpenSubagentSettings = { settingsStack.push(AgentEcoSettingsDestination, AgentSubagentSettingsDestination) },
                        onOpenSkillSettings = { settingsStack.push(AgentEcoSettingsDestination, AgentSkillSettingsDestination) },
                        onOpenMcpSettings = { settingsStack.push(AgentEcoSettingsDestination, McpSettingsDestination) },
                        onOpenQuickPhrases = { settingsStack.push(AgentEcoSettingsDestination, QuickPhrasesDestination) },
                        onOpenStats = { settingsStack.push(AgentEcoSettingsDestination, StatsDestination) },
                        viewModel = settingsViewModel,
                    )
                }
            }
            entry<LinuxEnvSettingsDestination> {
                GuardedEntry(LinuxEnvSettingsDestination) {
                    top.wkbin.tianxuan.ui.settings.LinuxEnvironmentSettingsScreen(
                        onBack = ::popBack,
                        onOpenDistroManagement = { settingsStack.push(LinuxEnvSettingsDestination, DistroManagementDestination) },
                        onOpenStorageMounts = { settingsStack.push(LinuxEnvSettingsDestination, StorageMountSettingsDestination) },
                        onOpenStorageUsage = { settingsStack.push(LinuxEnvSettingsDestination, StorageUsageDestination) },
                        onOpenAppManagement = { settingsStack.push(LinuxEnvSettingsDestination, AppManagementDestination) },
                        onOpenEnvironmentVariables = { settingsStack.push(LinuxEnvSettingsDestination, EnvironmentVariableSettingsDestination) },
                        onOpenSshSettings = { settingsStack.push(LinuxEnvSettingsDestination, SshSettingsDestination) },
                        onOpenFtpSettings = { settingsStack.push(LinuxEnvSettingsDestination, FtpSettingsDestination) },
                        viewModel = settingsViewModel,
                    )
                }
            }
            entry<SystemDevSettingsDestination> {
                GuardedEntry(SystemDevSettingsDestination) {
                    top.wkbin.tianxuan.ui.settings.SystemDevSettingsScreen(
                        onBack = ::popBack,
                        onOpenDeveloper = { settingsStack.push(SystemDevSettingsDestination, DeveloperDestination) },
                        onOpenAdbLogcat = { settingsStack.push(SystemDevSettingsDestination, AdbLogcatDestination) },
                        onOpenCustomIteration = { settingsStack.push(SystemDevSettingsDestination, CustomIterationDestination) },
                        onOpenPermissionGuide = { settingsStack.push(SystemDevSettingsDestination, PermissionGuideDestination) },
                        viewModel = settingsViewModel,
                    )
                }
            }
            entry<PermissionGuideDestination> {
                GuardedEntry(PermissionGuideDestination) {
                    top.wkbin.tianxuan.ui.settings.permission.PermissionGuideScreen(
                        onBack = ::popBack,
                    )
                }
            }
            entry<AboutCommunityDestination> {
                GuardedEntry(AboutCommunityDestination) {
                    top.wkbin.tianxuan.ui.settings.AboutCommunityScreen(
                        onBack = ::popBack,
                        onOpenSponsor = { settingsStack.push(AboutCommunityDestination, SponsorDestination) },
                        viewModel = settingsViewModel,
                    )
                }
            }
            entry<SponsorDestination> {
                GuardedEntry(SponsorDestination) {
                    top.wkbin.tianxuan.ui.settings.SponsorScreen(onBack = ::popBack)
                }
            }
            entry<DistroManagementDestination> {
                GuardedEntry(DistroManagementDestination) {
                    top.wkbin.tianxuan.ui.settings.DistroManagementScreen(
                        onBack = ::popBack,
                        viewModel = settingsViewModel,
                    )
                }
            }
            entry<AgentSettingsDestination> {
                GuardedEntry(AgentSettingsDestination) {
                    AgentSettingsScreen(
                        onBack = ::popBack,
                        category = top.wkbin.tianxuan.ui.settings.AgentSettingsCategory.EXECUTION,
                        viewModel = settingsViewModel,
                    )
                }
            }
            entry<AgentSubagentSettingsDestination> {
                GuardedEntry(AgentSubagentSettingsDestination) {
                    AgentSettingsScreen(onBack = ::popBack, category = top.wkbin.tianxuan.ui.settings.AgentSettingsCategory.SUBAGENTS, viewModel = settingsViewModel)
                }
            }
            entry<AgentSkillSettingsDestination> {
                GuardedEntry(AgentSkillSettingsDestination) {
                    AgentSettingsScreen(onBack = ::popBack, category = top.wkbin.tianxuan.ui.settings.AgentSettingsCategory.SKILLS, viewModel = settingsViewModel)
                }
            }
            entry<McpSettingsDestination> {
                GuardedEntry(McpSettingsDestination) {
                    top.wkbin.tianxuan.ui.settings.McpSettingsScreen(
                        onBack = ::popBack,
                        viewModel = settingsViewModel,
                    )
                }
            }
            entry<ToolCenterDestination> {
                GuardedEntry(ToolCenterDestination) {
                    top.wkbin.tianxuan.ui.settings.ToolCenterScreen(
                        onBack = ::popBack,
                        onLaunchPty = { toolId -> activeStack.push(ToolCenterDestination, TerminalDestination(toolId = toolId)) },
                        onOpenToolDetail = { toolId -> activeStack.push(ToolCenterDestination, ToolDetailDestination(toolId = toolId)) },
                        onStartAiHealing = { toolId, toolName, logs ->
                            val prompt = top.wkbin.tianxuan.ui.settings.ToolSelfHealingHelper.buildHealingPrompt(toolId, toolName, logs)
                            pendingHealingTask = HealingTask("🔧 自愈: $toolName", prompt)
                            selectedMain = MainDestination.Agent
                        },
                    )
                }
            }
            entry<CcSwitchDestination> {
                GuardedEntry(CcSwitchDestination) {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    top.wkbin.tianxuan.ui.settings.CcSwitchScreen(
                        onBack = ::popBack,
                        onLaunchTerminal = { executable -> activeStack.push(CcSwitchDestination, TerminalDestination(toolId = executable)) },
                        onOpenBrowser = { url ->
                            val targetUrl = url.ifBlank { "http://127.0.0.1:19870" }
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(targetUrl)).apply {
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            runCatching {
                                context.startActivity(intent)
                            }.onFailure {
                                android.widget.Toast.makeText(context, "无法唤起外部浏览器: ${it.localizedMessage}", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                    )
                }
            }
            entry<ToolDetailDestination> { destination ->
                GuardedEntry(destination) {
                    top.wkbin.tianxuan.ui.settings.ToolDetailScreen(
                        toolId = destination.toolId,
                        onBack = ::popBack,
                        onLaunchTerminal = { toolId -> activeStack.push(destination, TerminalDestination(toolId = toolId)) },
                        onStartAiHealing = { toolId, toolName, logs ->
                            val prompt = top.wkbin.tianxuan.ui.settings.ToolSelfHealingHelper.buildHealingPrompt(toolId, toolName, logs)
                            pendingHealingTask = HealingTask("🔧 自愈: $toolName", prompt)
                            selectedMain = MainDestination.Agent
                        },
                    )
                }
            }
            entry<StorageMountSettingsDestination> {
                GuardedEntry(StorageMountSettingsDestination) {
                    top.wkbin.tianxuan.ui.settings.StorageMountSettingsScreen(
                        onBack = ::popBack,
                        viewModel = settingsViewModel,
                    )
                }
            }
            entry<StorageUsageDestination> {
                GuardedEntry(StorageUsageDestination) {
                    top.wkbin.tianxuan.ui.settings.StorageUsageScreen(onBack = ::popBack)
                }
            }
            entry<AppManagementDestination> {
                GuardedEntry(AppManagementDestination) {
                    top.wkbin.tianxuan.ui.settings.AppManagementScreen(onBack = ::popBack)
                }
            }
            entry<EnvironmentVariableSettingsDestination> {
                GuardedEntry(EnvironmentVariableSettingsDestination) {
                    top.wkbin.tianxuan.ui.settings.EnvironmentVariableSettingsScreen(
                        onBack = ::popBack,
                        viewModel = settingsViewModel,
                    )
                }
            }
            entry<SshSettingsDestination> {
                GuardedEntry(SshSettingsDestination) {
                    top.wkbin.tianxuan.ui.settings.SshSettingsScreen(onBack = ::popBack)
                }
            }
            entry<FtpSettingsDestination> {
                GuardedEntry(FtpSettingsDestination) {
                    top.wkbin.tianxuan.ui.settings.FtpSettingsScreen(onBack = ::popBack)
                }
            }
            entry<ModelProfilesDestination> {
                GuardedEntry(ModelProfilesDestination) {
                    ModelProfilesScreen(
                        onBack = ::popBack,
                        onCreate = { settingsStack.push(ModelProfilesDestination, ModelEditorDestination()) },
                        onEdit = { modelId -> settingsStack.push(ModelProfilesDestination, ModelEditorDestination(modelId)) },
                        viewModel = settingsViewModel,
                    )
                }
            }
            entry<LocalLlmDestination> {
                GuardedEntry(LocalLlmDestination) {
                    LocalLlmScreen(
                        onBack = ::popBack,
                        onOpenEngine = { settingsStack.push(LocalLlmDestination, ToolDetailDestination("llama-cpp")) },
                    )
                }
            }
            entry<ModelEditorDestination> { destination ->
                GuardedEntry(destination) {
                    ModelEditorScreen(
                        modelId = destination.modelId,
                        onBack = ::popBack,
                        onSaved = ::popBack,
                        viewModel = settingsViewModel,
                    )
                }
            }
            entry<QuickPhrasesDestination> {
                GuardedEntry(QuickPhrasesDestination) {
                    top.wkbin.tianxuan.ui.settings.QuickPhrasesScreen(
                        onBack = ::popBack,
                        viewModel = settingsViewModel,
                    )
                }
            }
            entry<StatsDestination> {
                GuardedEntry(StatsDestination) {
                    top.wkbin.tianxuan.ui.settings.stats.StatsScreen(onBack = ::popBack)
                }
            }
            entry<DeveloperDestination> {
                GuardedEntry(DeveloperDestination) {
                    DeveloperScreen(
                        onBack = ::popBack,
                        onOpenCatalog = { settingsStack.push(DeveloperDestination, LiquidGlassCatalogDestination) },
                    )
                }
            }
            entry<LiquidGlassCatalogDestination> {
                GuardedEntry(LiquidGlassCatalogDestination) {
                    LiquidGlassCatalogScreen(onBack = ::popBack)
                }
            }
            entry<AdbLogcatDestination> {
                GuardedEntry(AdbLogcatDestination) {
                    AdbLogcatScreen(onBack = ::popBack)
                }
            }
            entry<A2uiPocDestination> {
                GuardedEntry(A2uiPocDestination) {
                    top.wkbin.tianxuan.feature.a2uipoc.A2uiPocScreen(onBack = ::popBack)
                }
            }
            entry<KnowledgeDestination> {
                GuardedEntry(KnowledgeDestination) {
                    top.wkbin.tianxuan.ui.knowledge.KnowledgeScreen(onBack = ::popBack)
                }
            }
            entry<CustomIterationDestination> {
                GuardedEntry(CustomIterationDestination) {
                    CustomIterationScreen(
                        onBack = ::popBack,
                        onNavigateToChat = { prompt ->
                            pendingHealingTask = HealingTask("🚀 自定义迭代", prompt)
                            selectedMain = MainDestination.Agent
                        },
                    )
                }
            }
            entry<TerminalDestination> { destination ->
                GuardedEntry(destination) {
                    TerminalScreen(onBack = ::popBack, project = destination.project)
                }
            }
            entry<BrowserDestination> {
                GuardedEntry(BrowserDestination) {
                    BrowserScreen(onBack = ::popBack, viewModel = browserViewModel)
                }
            }
            entry<GitRepositoryDestination> { destination ->
                GuardedEntry(destination) {
                    top.wkbin.tianxuan.ui.git.GitScreen(
                        projectName = destination.projectName,
                        onBack = ::popBack,
                    )
                }
            }
    }

    val density = LocalDensity.current
    val liquidGlassBackdrop = LocalLiquidGlassBackdrop.current
    // 宽屏（平板横屏）常驻侧栏，窄屏用底部栏。分支与叠放都在外壳里。
    val widthClass = rememberWidthClass()
    // 键盘弹出的判定只对底部栏有意义——侧栏不受 IME 影响。
    val showLiquidBottomBar = !widthClass.usesPermanentNav &&
        liquidGlassBackdrop != null &&
        activeStack.size == 1 &&
        WindowInsets.ime.getBottom(density) == 0
    // Hoist decorators so tab switches (key below) do not drop entry Saveable/ViewModel state.
    // Explicit <NavKey>: outside NavDisplay's parameter context, listOf cannot infer T.
    val entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator<NavKey>(), rememberViewModelStoreNavEntryDecorator<NavKey>())

    AdaptiveNavigationScaffold(
        selected = selectedMain,
        onNavigate = ::navigateMain,
        widthClass = widthClass,
        hasLiquidBackdrop = liquidGlassBackdrop != null,
        bottomBarVisible = showLiquidBottomBar,
    ) { paneModifier ->
        // App background under NavDisplay so a rare uncovered frame never shows window black.
        Surface(
            modifier = paneModifier,
            color = MaterialTheme.colorScheme.background,
        ) {
            // key(selectedMain): swapping the bottom tab replaces NavDisplay instead of
            // animating between two unrelated back stacks (which looked like a page transition).
            key(selectedMain) {
                NavDisplay(
                    backStack = activeStack,
                    modifier = Modifier.fillMaxSize(),
                    onBack = ::popBack,
                    entryDecorators = entryDecorators,
                    entryProvider = appEntryProvider,
                )
            }
        }
    }
}

private data class HealingTask(
    val title: String,
    val prompt: String,
)
