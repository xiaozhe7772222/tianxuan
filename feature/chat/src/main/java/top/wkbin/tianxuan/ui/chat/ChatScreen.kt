package top.wkbin.tianxuan.ui.chat

import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import top.wkbin.tianxuan.ui.components.RuntimeIconButton as IconButton
import androidx.compose.ui.text.style.TextOverflow
import top.wkbin.tianxuan.harness.workflow.ProactiveWorkflowSuggestion
import top.wkbin.tianxuan.ui.components.RuntimeIcon
import top.wkbin.tianxuan.ui.components.RuntimeIconName
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import top.wkbin.tianxuan.ui.components.RuntimeAlertDialog
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.BackHandler
import top.wkbin.tianxuan.ui.components.RuntimeButton as Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import top.wkbin.tianxuan.ui.components.RuntimeTextButton as TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import top.wkbin.tianxuan.feature.chat.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.wkbin.tianxuan.core.database.AiModelEntity
import top.wkbin.tianxuan.core.model.ApprovalMode
import top.wkbin.tianxuan.core.model.RunMode
import top.wkbin.tianxuan.harness.AssistantText
import top.wkbin.tianxuan.harness.HarnessMessage
import top.wkbin.tianxuan.harness.ToolCall
import top.wkbin.tianxuan.harness.ToolResult
import top.wkbin.tianxuan.harness.UserMessage
import top.wkbin.tianxuan.runtime.WorkspaceProject
import top.wkbin.tianxuan.ui.components.MainDestination
import top.wkbin.tianxuan.ui.components.RuntimeBottomBar
import top.wkbin.tianxuan.ui.components.liquidGlassContent
import top.wkbin.tianxuan.ui.theme.LocalLiquidGlassBackdrop
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.lazy.LazyListState
import top.wkbin.tianxuan.core.model.QuickPhrase
import top.wkbin.tianxuan.core.database.AgentApprovalRequestEntity
import top.wkbin.tianxuan.core.database.AgentPlanEntity
import top.wkbin.tianxuan.harness.QueuedPrompt
import top.wkbin.tianxuan.harness.compaction.CompactionSnapshot
import top.wkbin.tianxuan.harness.mcp.McpWorkspaceRecommender
import top.wkbin.tianxuan.runtime.ProjectType
import top.wkbin.tianxuan.ui.chat.ChatRenderItem
import top.wkbin.tianxuan.ui.chat.projectChatMessages

// 悬浮玻璃底栏实际占高 = 上下 8dp padding + 64dp 条体 = 80dp，留 2dp 微缝防贴死
private val AgentBottomBarHeight = 82.dp

// 浏览器 URL 栏贴合版：与底栏总占位一致（8 + 64 + 8 = 80dp），零缝隙直接贴在底栏上方
private val BrowserBottomBarHeight = 80.dp

/** 会改动工作区文件的工具：这些工具执行后「仓库」入口按需高亮提示新改动 */
private val REPOSITORY_HIGHLIGHT_TOOLS = setOf(
    top.wkbin.tianxuan.harness.HarnessTool.WRITE,
    top.wkbin.tianxuan.harness.HarnessTool.EDIT,
    top.wkbin.tianxuan.harness.HarnessTool.BASE,
    top.wkbin.tianxuan.harness.HarnessTool.PROCESS,
)

@Composable
private fun chatBottomInsets(bottomBarHeight: Dp): WindowInsets {
    val bottomBarInsets = WindowInsets.navigationBars.add(WindowInsets(bottom = bottomBarHeight))
    return bottomBarInsets.union(WindowInsets.ime)
}

/**
 * 天玄 · 智枢对话界面 (TianXuan Agent)
 * 智能结对编程、工具自动化调用与代码生成
 */
@OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)
@Composable
fun ChatScreen(
    onNavigate: (MainDestination) -> Unit,
    onOpenFile: ((projectName: String, relativePath: String) -> Unit)? = null,
    terminalPane: (@Composable (project: String) -> Unit)? = null,
    browserPane: (@Composable (onExit: (() -> Unit)?) -> Unit)? = null,
    browserActivityTick: Long = 0L,
    browserBackPressed: (() -> Boolean)? = null,
    onOpenRepository: ((projectName: String) -> Unit)? = null,
    viewModel: ChatViewModel = koinViewModel(),
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val running by viewModel.running.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val input by viewModel.input.collectAsStateWithLifecycle()
    val pendingAttachments by viewModel.pendingAttachments.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val thinkingLive by viewModel.thinkingLive.collectAsStateWithLifecycle()
    val thinkingExpanded by viewModel.thinkingExpanded.collectAsStateWithLifecycle()
    val thinkingAutoTranslate by viewModel.thinkingAutoTranslate.collectAsStateWithLifecycle()
    val chatRoundCollapse by viewModel.chatRoundCollapse.collectAsStateWithLifecycle()
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val workspaces by viewModel.workspaces.collectAsStateWithLifecycle()
    val models by viewModel.models.collectAsStateWithLifecycle()
    val providerModelIds by viewModel.providerModelIds.collectAsStateWithLifecycle()
    val discoveringProviderModels by viewModel.discoveringProviderModels.collectAsStateWithLifecycle()
    val providerModelDiscoveryError by viewModel.providerModelDiscoveryError.collectAsStateWithLifecycle()
    val modelPickerProfileId by viewModel.modelPickerProfileId.collectAsStateWithLifecycle()
    val workspace by viewModel.workspace.collectAsStateWithLifecycle()
    val mcpRecommendations by viewModel.mcpRecommendations.collectAsStateWithLifecycle()
    val workflowSuggestions by viewModel.workflowSuggestions.collectAsStateWithLifecycle()
    val hiddenSkillSuggestions by viewModel.hiddenSkillSuggestions.collectAsStateWithLifecycle()
    val sessionProjectType by viewModel.projectType.collectAsStateWithLifecycle()
    val matchingCommands by viewModel.matchingCommands.collectAsStateWithLifecycle()
    val matchingMentions by viewModel.matchingMentions.collectAsStateWithLifecycle()
    val attachedMentions by viewModel.attachedMentions.collectAsStateWithLifecycle()
    val queuedPrompts by viewModel.queuedPrompts.collectAsStateWithLifecycle()
    val sendMode by viewModel.sendMode.collectAsStateWithLifecycle()
    val branches by viewModel.branches.collectAsStateWithLifecycle()
    val runtimeEvents by viewModel.runtimeEvents.collectAsStateWithLifecycle()
    val currentSessionId by viewModel.currentSessionId.collectAsStateWithLifecycle()
    val sessionRunStates by viewModel.sessionRunStates.collectAsStateWithLifecycle()
    val activeDistroId by viewModel.activeDistroId.collectAsStateWithLifecycle()
    val installedDistros by viewModel.installedDistros.collectAsStateWithLifecycle()
    val allSkills by viewModel.allSkills.collectAsStateWithLifecycle()
    val pinnedCapabilities by viewModel.pinnedCapabilities.collectAsStateWithLifecycle()
    val pinnedMentionIds by viewModel.pinnedMentionIds.collectAsStateWithLifecycle()
    val memories by viewModel.memories.collectAsStateWithLifecycle()
    val scratchpads by viewModel.scratchpads.collectAsStateWithLifecycle()
    val mcpServers by viewModel.mcpServers.collectAsStateWithLifecycle()
    val mcpConnectionStates by viewModel.mcpConnectionStates.collectAsStateWithLifecycle()
    val initializing by viewModel.initializing.collectAsStateWithLifecycle()
    val pendingApprovals by viewModel.pendingApprovals.collectAsStateWithLifecycle()
    val activePlan by viewModel.activePlan.collectAsStateWithLifecycle()
    val activeCompaction by viewModel.activeCompaction.collectAsStateWithLifecycle()
    val contextUsage by viewModel.contextUsage.collectAsStateWithLifecycle()
    val quickPhrases by viewModel.quickPhrases.collectAsStateWithLifecycle()
    val onboardingPrivilege by viewModel.privilegeOnboarding.collectAsStateWithLifecycle()
    val attachmentsProcessing by viewModel.attachmentsProcessing.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val subagentResult by viewModel.subagentResult.collectAsStateWithLifecycle()

    // 弹窗开关与编辑目标：用 rememberSaveable 保存，旋转 / 进程重建后不丢失
    var showSessions by rememberSaveable { mutableStateOf(false) }
    var showNewSession by rememberSaveable { mutableStateOf(false) }
    var showModels by rememberSaveable { mutableStateOf(false) }
    var showApprovalModes by rememberSaveable { mutableStateOf(false) }
    var showRunModes by rememberSaveable { mutableStateOf(false) }
    var showSkillsMcpSheet by rememberSaveable { mutableStateOf(false) }
    var showBranches by rememberSaveable { mutableStateOf(false) }
    var showRuntimeTimeline by rememberSaveable { mutableStateOf(false) }
    var showMemorySheet by rememberSaveable { mutableStateOf(false) }
    var showFloatingPermissionDialog by rememberSaveable { mutableStateOf(false) }
    var branchFromMessageId by rememberSaveable { mutableStateOf<String?>(null) }
    // 编辑目标消息只保存 id，避免把不可保存的实体放进状态保存器
    var editTargetMessageId by rememberSaveable { mutableStateOf<String?>(null) }
    val editTargetMessage = remember(messages, editTargetMessageId) {
        messages.filterIsInstance<UserMessage>().firstOrNull { it.id == editTargetMessageId }
    }

    val listState = rememberLazyListState()
    val context = LocalContext.current

    val currentSession = remember(sessions, currentSessionId) { sessions.firstOrNull { it.id == currentSessionId } }
    val boundModelProfile = remember(models, currentSession?.modelId) {
        currentSession?.modelId?.let { id -> models.firstOrNull { it.id == id } }
    }
    val activeModel = remember(models, boundModelProfile, currentSession?.modelVariant) {
        val profile = boundModelProfile ?: models.firstOrNull { it.isActive }
        profile?.copy(
            model = currentSession?.modelVariant
                ?.takeIf { boundModelProfile != null && it.isNotBlank() }
                ?: profile.model.substringBefore(',').trim(),
        )
    }
    val currentApprovalMode = remember(currentSession?.approvalMode) { ApprovalMode.fromId(currentSession?.approvalMode) }
    val currentRunMode = remember(currentSession?.runMode) { RunMode.fromId(currentSession?.runMode) }
    val activeWorkspaceProject = remember(workspace, workspaces) {
        workspaces.firstOrNull { it.linuxPath == workspace }
    }
    val effectiveWorkspaceProject = remember(activeWorkspaceProject, sessionProjectType) {
        val override = when (sessionProjectType.uppercase()) {
            "ANDROID" -> ProjectType.ANDROID
            "FLUTTER" -> ProjectType.FLUTTER
            "REVERSE" -> ProjectType.REVERSE
            "GENERAL" -> ProjectType.GENERAL
            else -> null
        }
        activeWorkspaceProject?.let { project -> override?.let { project.copy(projectType = it) } ?: project }
    }
    val distroDisplayName = remember(activeDistroId) {
        runCatching { top.wkbin.tianxuan.runtime.DistributionCatalog.require(activeDistroId).displayName }
            .getOrDefault(activeDistroId)
    }

    // 性能：不再直接依赖 `messages` 整个 list 引用（流式输出时每个 token 块都会换新引用，
    // 导致这几处 O(n) 派生每帧重算）。改为依赖「由消息列表推导出的、真正影响结果的稳定键」。
    val toolResults = remember(
        // ToolResult 的数量 + 末条内容长度：只要没有新增/更新工具结果，就不需要重算映射。
        messages.count { it is ToolResult },
        messages.lastOrNull { it is ToolResult }?.let { (it as ToolResult).output.length },
    ) {
        messages.filterIsInstance<ToolResult>().associateBy { it.toolCallId }
    }
    val lastAssistantMessageId = remember(
        // 只有「新增了一条 AssistantText」才会改变结果，故依赖其数量与末条 id。
        messages.count { it is AssistantText },
        messages.lastOrNull { it is AssistantText }?.id,
    ) {
        messages.filterIsInstance<AssistantText>().lastOrNull()?.id
    }
    // 两者语义不同（实时思考高亮 vs 重新生成定位），但取值一致，复用同一计算结果
    val liveThinkingMessageId = lastAssistantMessageId
    val currentBranch = remember(branches) { branches.firstOrNull { it.isCurrent } }

    // Agent 联动：上次访问「仓库」页之后 agent 又写过文件（write/edit/base/process），
    // 则工作条「仓库」入口高亮，提示有新改动可提交；进入仓库页即熄灭。
    var lastRepositoryVisitAt by rememberSaveable { mutableStateOf(0L) }
    val repositoryHighlight = remember(messages, lastRepositoryVisitAt) {
        messages.filterIsInstance<ToolCall>().any { call ->
            call.createdAt > lastRepositoryVisitAt && call.tool in REPOSITORY_HIGHLIGHT_TOOLS
        }
    }

    val isImeVisible = WindowInsets.isImeVisible
    val coroutineScope = rememberCoroutineScope()

    // Navigation3 removes inactive tab content from composition. Persist this marker with the
    // entry so returning to 智枢 does not perform a second, redundant scrollToItem during the
    // tab transition.
    var initialPositionedSessionKey by rememberSaveable { mutableStateOf<String?>(null) }
    // 会话键只需首条消息 id，依赖单值而非整个 list。
    val currentSessionKey = remember(messages.firstOrNull()?.id) { messages.firstOrNull()?.id ?: "" }

    val lastMessageSignature = remember(messages) {
        val last = messages.lastOrNull()
        when (last) {
            is AssistantText -> "${last.id}:${last.reasoning?.length ?: 0}:${last.text.length}"
            is ToolCall -> "${last.id}:${last.args.hashCode()}"
            is ToolResult -> "${last.id}:${last.output.length}"
            is UserMessage -> "${last.id}:${last.text.length}"
            else -> "${messages.size}"
        }
    }

    // 🌟 1. 消息发送与流式输出跟随滚动
    // 仅当用户本来就停在底部附近时才跟随输出滚动；上滑阅读历史时不打扰，
    // 重新滑回底部（或新发消息）后恢复自动跟随。
    LaunchedEffect(messages.size, lastMessageSignature) {
        if (messages.isNotEmpty()) {
            delay(30)
            if (initialPositionedSessionKey != currentSessionKey) {
                initialPositionedSessionKey = currentSessionKey
                listState.safeScrollToLastItem()
            } else {
                val layoutInfo = listState.layoutInfo
                val totalCount = layoutInfo.totalItemsCount
                val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()
                val isNearBottom = lastVisible == null || lastVisible.index >= totalCount - 3
                if (isNearBottom) {
                    listState.safeScrollToLastItem()
                }
            }
        }
    }

    // 🌟 2. 软键盘弹起时自动平滑滚动定位到最后一条消息。
    // delay 之后必须重新读 totalItemsCount：IME insets 会触发 relayout，
    // 未测量完成时 count 为 0，animateScrollToItem(-1) 会直接崩。
    LaunchedEffect(isImeVisible) {
        if (!isImeVisible) return@LaunchedEffect
        delay(80)
        listState.safeScrollToLastItem(animated = true)
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val appContext = LocalContext.current.applicationContext
    val grantLabel = stringResource(R.string.chat_snackbar_grant)
    val gotItLabel = stringResource(R.string.chat_snackbar_got_it)
    LaunchedEffect(Unit) {
        viewModel.permissionRequests.collect { req ->
            val actionLabel = when (req.permission) {
                "WRITE_SETTINGS" -> grantLabel
                else -> gotItLabel
            }
            val result = snackbarHostState.showSnackbar(
                message = req.reason,
                actionLabel = actionLabel,
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed && req.permission == "WRITE_SETTINGS") {
                runCatching {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_WRITE_SETTINGS,
                        Uri.parse("package:${appContext.packageName}")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    appContext.startActivity(intent)
                }
            }
        }
    }

    // VM 层轻量通知（附件处理失败 / 模型档案已存在 / 新会话已创建等）以 Toast 上抛
    LaunchedEffect(notice) {
        notice?.let {
            Toast.makeText(appContext, it, Toast.LENGTH_SHORT).show()
            viewModel.clearNotice()
        }
    }

    // rewind 完成：Snackbar 附「撤销回滚」动作（Toast 无法承载动作）
    val rewindUndoLabel = stringResource(R.string.chat_rewind_undo)
    LaunchedEffect(Unit) {
        viewModel.rewindCompletedEvents.collect { message ->
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = rewindUndoLabel,
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoLastRewind()
            }
        }
    }

    // 切换会话后自动关闭遗留的子智能体成果抽屉，避免跨会话展示旧 lane
    LaunchedEffect(currentSessionId) {
        val opened = subagentResult
        if (opened != null && opened.sessionId != currentSessionId) viewModel.closeSubagentResult()
    }

    // 模型·权限·主线·轮次 工具条：单栏浏览器分页模式下随对话页一起滑走（浏览器页全屏），
    // 双栏/纯对话模式固定在顶部。提出为局部 lambda 避免三处重复传参。
    // onOpenBrowser 非空时工具条末尾追加"浏览器"入口（agent 有新动态时高亮）。
    // onOpenRepository 非空即追加"仓库"入口（Git 分支管理）；未绑定项目时点击给提示而非隐藏。
    val noProjectHint = stringResource(R.string.chat_repository_no_project)
    val chatTopBar: @Composable (onOpenBrowser: (() -> Unit)?, browserHighlight: Boolean) -> Unit =
        { onOpenBrowser, browserHighlight ->
            ChatTopBar(
                workspace = workspace,
                distroDisplayName = distroDisplayName,
                activeModel = activeModel,
                approvalMode = currentApprovalMode,
                runMode = currentRunMode,
                currentBranch = currentBranch,
                runtimeEvents = runtimeEvents,
                running = running,
                onShowFloatingPermissionDialog = { showFloatingPermissionDialog = true },
                onOpenSessions = { showSessions = true },
                onOpenModels = { showModels = true },
                onOpenApprovalModes = { showApprovalModes = true },
                onOpenRunModes = { showRunModes = true },
                onOpenBranches = { showBranches = true },
                onOpenRuntime = { showRuntimeTimeline = true },
                onOpenBrowser = onOpenBrowser,
                browserHighlight = browserHighlight,
                onOpenRepository = onOpenRepository?.let { open ->
                    {
                        val project = activeWorkspaceProject
                        if (project != null) {
                            lastRepositoryVisitAt = System.currentTimeMillis()
                            open(project.name)
                        } else {
                            android.widget.Toast.makeText(appContext, noProjectHint, android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                repositoryHighlight = repositoryHighlight,
            )
        }

    // 模型回复里的 /workspace、/attachments 等沙箱路径在此翻译为宿主真实文件，
    // 否则 Coil 会按 Android 根文件系统路径加载而必然失败。
    CompositionLocalProvider(LocalSandboxHostRoots provides viewModel.sandboxHostRoots) {
    // 会话栏形态自适应（平板常驻可拖拽分栏 / 窄屏抽屉）由 ChatSessionsPane 收口，本文件不感知形态。
    ChatSessionsPane(
        sessions = sessions,
        currentSessionId = currentSessionId,
        workspaces = workspaces,
        sessionRunStates = sessionRunStates,
        drawerVisible = showSessions,
        onDrawerVisibleChange = { showSessions = it },
        onSwitch = viewModel::switchSession,
        onNew = { showNewSession = true },
        onCreateInWorkspace = { ws ->
            viewModel.createSession(title = "", workspace = ws.linuxPath, projectType = ws.projectType.name)
        },
        onDelete = viewModel::deleteSession,
        onRename = viewModel::renameSession,
        onOpenSkills = { showSkillsMcpSheet = true },
        onOpenRuntime = { showRuntimeTimeline = true },
    ) { paneModifier ->
    Scaffold(
        modifier = paneModifier,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val isDualPane = maxWidth >= 720.dp

            val knownMentionNames = remember(allSkills, mcpServers) {
                (allSkills.filter { it.isEnabled }.flatMap { skill ->
                    listOfNotNull(skill.name, skill.id, skill.triggerCommand?.removePrefix("/"))
                } + mcpServers.filter { it.isEnabled }.flatMap { listOf(it.name, it.id) })
                    .filter { it.isNotBlank() }
                    .distinct()
            }

            val bottomInsets = chatBottomInsets(AgentBottomBarHeight)

            // 对话主内容：双栏左栏与单栏整页共用（仅 modifier 不同）
            val chatContent: @Composable (Modifier) -> Unit = { paneModifier ->
                ChatPaneContent(
                    onboardingPrivilege = onboardingPrivilege,
                    modifier = paneModifier,
                    messages = messages,
                    listState = listState,
                    running = running,
                    status = status,
                    thinkingExpanded = thinkingExpanded,
                    thinkingAutoTranslate = thinkingAutoTranslate,
                    chatRoundCollapse = chatRoundCollapse,
                    thinkingLive = thinkingLive,
                    liveThinkingMessageId = liveThinkingMessageId,
                    onNavigateToSettings = viewModel::navigateToAgentSettings,
                    toolResults = toolResults,
                    workspace = workspace,
                    workspaceProject = effectiveWorkspaceProject,
                    onOpenFile = onOpenFile,
                    onEditMessage = { editTargetMessageId = it.id },
                    onDeleteMessage = viewModel::deleteMessage,
                    onRewindMessage = viewModel::rewindToMessage,
                    error = error,
                    onClearError = viewModel::clearError,
                    matchingCommands = matchingCommands,
                    matchingMentions = matchingMentions,
                    attachedMentions = attachedMentions,
                    knownMentionNames = knownMentionNames,
                    queuedPrompts = queuedPrompts,
                    onEditQueuedPrompt = viewModel::editQueuedPrompt,
                    onRemoveQueuedPrompt = viewModel::removeQueuedPrompt,
                    onConvertToSteer = viewModel::convertQueuedPromptToSteer,
                    sendMode = sendMode,
                    input = input,
                    onInputChanged = viewModel::onInputChanged,
                    onApplyCommand = viewModel::applySlashCommand,
                    onApplyMention = viewModel::applyMention,
                    onRemoveMention = viewModel::removeMention,
                    attachments = pendingAttachments,
                    attachmentsProcessing = attachmentsProcessing,
                    onAttachmentsPicked = viewModel::onAttachmentsPicked,
                    onRemoveAttachment = viewModel::removeAttachment,
                    onSend = viewModel::sendFromComposer,
                    onStop = viewModel::stop,
                    lastAssistantMessageId = lastAssistantMessageId,
                    onRegenerate = viewModel::regenerateLast,
                    onCreateBranch = { branchFromMessageId = it },
                    onRetryTool = viewModel::retryToolCall,
                    initializing = initializing,
                    pinnedCapabilities = pinnedCapabilities,
                    onOpenSkillsMcp = { showSkillsMcpSheet = true },
                    onUnpinMention = viewModel::unpinMention,
                    activePlan = activePlan,
                    pendingApprovals = pendingApprovals,
                    onResolveApproval = viewModel::resolveApproval,
                    onResolveQuestion = viewModel::resolveQuestion,
                    contextUsage = contextUsage,
                    activeModel = activeModel,
                    onUpdateReasoning = viewModel::updateActiveModelReasoning,
                    quickPhrases = quickPhrases,
                    onSelectPhrase = viewModel::applyQuickPhrase,
                    activeCompaction = activeCompaction,
                    mcpRecommendations = mcpRecommendations,
                    onEnableMcpRecommendation = viewModel::enableMcpRecommendation,
                    onDismissMcpRecommendation = viewModel::dismissMcpRecommendation,
                    workflowSuggestions = workflowSuggestions,
                    onLaunchWorkflowSuggestion = viewModel::launchWorkflowSuggestion,
                    onDismissWorkflowSuggestion = viewModel::dismissWorkflowSuggestion,
                    hiddenSkillSuggestions = hiddenSkillSuggestions,
                    onApplySkillSuggestion = viewModel::applySkillSuggestion,
                    onDismissSkillSuggestion = viewModel::dismissSkillSuggestion,
                    onViewSubagentLanes = { showBranches = true },
                    subagentBranches = branches,
                    onOpenSubagentBranch = viewModel::openSubagentResult,
                )
            }

            if (isDualPane && terminalPane != null) {
                Column(modifier = Modifier.fillMaxSize()) {
                    chatTopBar(null, false)
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .liquidGlassContent()
                            .padding(horizontal = 12.dp)
                            .windowInsetsPadding(bottomInsets),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                    // 左栏：Agent 对话与指令区
                    chatContent(
                        Modifier
                            .weight(0.48f)
                            .fillMaxHeight()
                    )

                    VerticalDivider(
                        modifier = Modifier.fillMaxHeight().padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )

                    // 右栏：实时演武终端 (Terminal) / 浏览器联动视窗（可切换）
                    var rightPaneBrowser by rememberSaveable { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .weight(0.52f)
                            .fillMaxHeight()
                            .padding(top = 4.dp, bottom = 4.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                                RoundedCornerShape(14.dp),
                            ),
                    ) {
                        if (browserPane != null) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    TextButton(onClick = { rightPaneBrowser = false }) {
                                        Text(
                                            "终端",
                                            fontWeight = if (rightPaneBrowser) FontWeight.Normal else FontWeight.Bold,
                                            color = if (rightPaneBrowser) {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            } else {
                                                MaterialTheme.colorScheme.primary
                                            },
                                        )
                                    }
                                    TextButton(onClick = { rightPaneBrowser = true }) {
                                        Text(
                                            "浏览器",
                                            fontWeight = if (rightPaneBrowser) FontWeight.Bold else FontWeight.Normal,
                                            color = if (rightPaneBrowser) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                        )
                                    }
                                }
                                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                    if (rightPaneBrowser) {
                                        browserPane(null)
                                    } else {
                                        terminalPane(workspace)
                                    }
                                }
                            }
                        } else {
                            terminalPane(workspace)
                        }
                    }
                    }
                }
            } else if (browserPane != null) {
                // 单栏：通过顶部入口直接切换，禁用横向手势和滑动过渡。
                val pagerState = rememberPagerState(initialPage = 0) { 2 }
                var lastSeenBrowserTick by remember { mutableStateOf(0L) }
                LaunchedEffect(pagerState.currentPage, browserActivityTick) {
                    if (pagerState.currentPage == 1) lastSeenBrowserTick = browserActivityTick
                }
                // 浏览器页系统返回：优先回退 WebView 历史，耗尽后切回对话页
                BackHandler(enabled = pagerState.currentPage == 1) {
                    if (browserBackPressed?.invoke() != true) {
                        coroutineScope.launch { pagerState.scrollToPage(0) }
                    }
                }
                Box(modifier = Modifier.fillMaxSize()) {
                    HorizontalPager(state = pagerState, userScrollEnabled = false) { page ->
                        if (page == 0) {
                            // 对话页：顶部工具条（模型·权限·主线·轮次·浏览器）属于对话的一部分。
                            Column(modifier = Modifier.fillMaxSize()) {
                                chatTopBar(
                                    {
                                        coroutineScope.launch { pagerState.scrollToPage(1) }
                                    },
                                    browserActivityTick > lastSeenBrowserTick,
                                )
                                chatContent(
                                    Modifier
                                        .fillMaxSize()
                                        .liquidGlassContent()
                                        .padding(horizontal = 12.dp)
                                        .windowInsetsPadding(bottomInsets)
                                )
                            }
                        } else {
                            // 浏览器页：全屏，仅保留自己的极薄状态栏（末尾带"返回对话"）+ URL 栏；
                            // 底部用贴合版 insets（无呼吸空间），URL 栏直接贴在底部导航栏上方
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .statusBarsPadding()
                                    .windowInsetsPadding(chatBottomInsets(BrowserBottomBarHeight))
                            ) {
                                browserPane(
                                    {
                                        coroutineScope.launch { pagerState.scrollToPage(0) }
                                    }
                                )
                            }
                        }
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    chatTopBar(null, false)
                    chatContent(
                        Modifier
                            .fillMaxSize()
                            .liquidGlassContent()
                            .padding(horizontal = 12.dp)
                            .windowInsetsPadding(bottomInsets)
                    )
                }
            }

            if (LocalLiquidGlassBackdrop.current == null) {
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                    RuntimeBottomBar(MainDestination.Agent, onNavigate)
                }
            }
        }
    }

    // 编辑并重新发送对话框
    editTargetMessage?.let { target ->
        EditAndResendDialog(
            originalText = target.text,
            onDismiss = { editTargetMessageId = null },
            onConfirm = { newText ->
                viewModel.editAndResend(target.id, newText)
                editTargetMessageId = null
            },
        )
    }

    // 智枢悬浮窗权限申请提示弹窗
    if (showFloatingPermissionDialog) {
        RuntimeAlertDialog(
            onDismissRequest = { showFloatingPermissionDialog = false },
            title = {
                Text(
                    stringResource(R.string.chat_floating_permission_title),
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    stringResource(R.string.chat_floating_permission_message),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showFloatingPermissionDialog = false
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}"),
                        )
                        context.startActivity(intent)
                    },
                ) {
                    Text(stringResource(R.string.chat_floating_permission_grant))
                }
            },
            dismissButton = {
                top.wkbin.tianxuan.ui.components.RuntimeTextButton(
                    onClick = { showFloatingPermissionDialog = false },
                ) {
                    Text(stringResource(R.string.chat_floating_permission_cancel))
                }
            },
        )
    }

    } // ChatSessionsPaneHost 的 content lambda 结束

    if (showNewSession) {
        NewSessionDialog(
            workspaces = workspaces,
            onDismiss = { showNewSession = false },
            onCreate = { title, selected, selectedType ->
                showNewSession = false
                viewModel.createSession(title = title, workspace = selected, projectType = selectedType.name)
            },
        )
    }

    if (showModels) {
        ModelDialog(
            models = models,
            selectedProfileId = activeModel?.id,
            selectedModelVariant = activeModel?.model,
            providerModelIds = providerModelIds,
            discoveringProviderModels = discoveringProviderModels,
            providerModelDiscoveryError = providerModelDiscoveryError,
            modelPickerProfileId = modelPickerProfileId,
            onDismiss = {
                viewModel.closeProviderModelPicker()
                showModels = false
            },
            onSelectProfile = { id -> viewModel.setActiveModel(id) },
            onSelectSubModel = { id, subModel -> viewModel.selectModel(id, subModel) },
            onOpenModelPicker = viewModel::openProviderModelPicker,
            onCloseModelPicker = viewModel::closeProviderModelPicker,
            onRefreshModels = viewModel::discoverProviderModels,
            onSwitchModel = viewModel::switchModelInProfile,
            onAdd = { name, provider, model, baseUrl -> viewModel.addModel(name, provider, model, baseUrl) },
            onDelete = viewModel::deleteModel,
        )
    }

    if (showSkillsMcpSheet) {
        // 打开挂载面板时自动探测一次 MCP 连通性
        LaunchedEffect(Unit) { viewModel.refreshMcpConnections() }
        SkillsAndMcpSheet(
            allSkills = allSkills,
            mcpServers = mcpServers,
            mcpConnectionStates = mcpConnectionStates,
            pinnedMentionIds = pinnedMentionIds,
            onDismiss = { showSkillsMcpSheet = false },
            onToggleSkill = { id, enabled ->
                viewModel.setSkillEnabled(id, enabled)
            },
            onToggleMcpServer = { id, enabled ->
                viewModel.setMcpServerEnabled(id, enabled)
            },
            onTogglePin = viewModel::togglePinMention,
            onNavigateToSettings = {
                showSkillsMcpSheet = false
                onNavigate(MainDestination.Settings)
            },
        )
    }

    if (showBranches) {
        BranchBrowserSheet(
            branches = branches,
            running = running,
            onDismiss = { showBranches = false },
            onSwitch = { branch ->
                viewModel.switchBranch(branch)
                showBranches = false
            },
            // 成果抽屉叠在分支列表之上，关闭后自然回到列表，再关闭即回主会话
            onOpenSubagent = viewModel::openSubagentResult,
        )
    }

    // 子智能体完整调研成果（独立 lane 的只读 transcript）
    subagentResult?.let { state ->
        SubagentResultSheet(
            state = state,
            onRefresh = viewModel::refreshSubagentResult,
            onDismiss = viewModel::closeSubagentResult,
        )
    }

    if (showRuntimeTimeline) {
        RuntimeTimelineSheet(
            events = runtimeEvents,
            messages = messages,
            memories = memories,
            scratchpads = scratchpads,
            onDeleteMemory = viewModel::deleteMemory,
            onDeleteScratchpad = viewModel::deleteScratchpad,
            onClearScratchpads = viewModel::clearScratchpads,
            onNavigateToMessage = { messageId ->
                showRuntimeTimeline = false
                coroutineScope.launch {
                    // 关键修复：旧实现用过滤后的 messages.indexOfFirst，缺少 LazyColumn 头部偏移补偿
                    // 与上界保护，sheet 打开后消息变化时可能传入越界索引（LazyListState 抛
                    // IllegalArgumentException）。此处改用与 ChatMessageList 完全一致的投影（含折叠开关）。
                    val renderItems = projectChatMessages(messages, toolResults, collapseEnabled = chatRoundCollapse)
                    val targetIndex = renderItems.indexOfFirst { item ->
                        item is ChatRenderItem.MessageItem && item.message.id == messageId
                    }
                    if (targetIndex < 0) return@launch
                    // LazyColumn 头部偏移：(init ? 1 : empty ? 1 : 0) + (compaction ? 1 : 0)。
                    // activePlan 不是独立头部 —— 不要再加 1。
                    val headerOffset = (if (initializing) 1 else 0) +
                        (if (!initializing && messages.isEmpty()) 1 else 0) +
                        (if (activeCompaction != null) 1 else 0)
                    listState.safeScrollToItem(targetIndex + headerOffset, animated = true)
                }
            },
            onDismiss = { showRuntimeTimeline = false },
        )
    }

    if (showApprovalModes) {
        ApprovalModeSheet(
            currentApprovalMode = currentApprovalMode,
            onSelect = { mode ->
                viewModel.setCurrentSessionApprovalMode(mode)
                showApprovalModes = false
            },
            onDismiss = { showApprovalModes = false },
        )
    }

    if (showRunModes) {
        RunModeSheet(
            currentRunMode = currentRunMode,
            onSelect = { mode ->
                viewModel.setCurrentSessionRunMode(mode)
                showRunModes = false
            },
            onDismiss = { showRunModes = false },
        )
    }

    if (showMemorySheet) {
        SessionMemorySheet(
            memories = memories,
            scratchpads = scratchpads,
            onDeleteMemory = viewModel::deleteMemory,
            onDeleteScratchpad = viewModel::deleteScratchpad,
            onClearScratchpads = viewModel::clearScratchpads,
            onDismiss = { showMemorySheet = false },
        )
    }

    branchFromMessageId?.let { messageId ->
        CreateBranchDialog(
            messageId = messageId,
            onDismiss = { branchFromMessageId = null },
            onCreate = { id, name ->
                viewModel.createBranch(id, name)
                branchFromMessageId = null
            },
        )
    }
    } // CompositionLocalProvider(LocalSandboxHostRoots)
}

@Composable
private fun ChatPaneContent(
    modifier: Modifier = Modifier,
    messages: List<HarnessMessage>,
    onboardingPrivilege: OnboardingPrivilege? = null,
    listState: LazyListState,
    running: Boolean,
    status: String?,
    thinkingExpanded: Boolean,
    thinkingAutoTranslate: Boolean = false,
    chatRoundCollapse: Boolean = false,
    thinkingLive: Boolean,
    liveThinkingMessageId: String?,
    onNavigateToSettings: (() -> Unit)? = null,
    toolResults: Map<String, ToolResult>,
    workspace: String,
    workspaceProject: WorkspaceProject? = null,
    onOpenFile: ((projectName: String, relativePath: String) -> Unit)?,
    onEditMessage: (UserMessage) -> Unit,
    onDeleteMessage: (String) -> Unit,
    onRewindMessage: (String, top.wkbin.tianxuan.harness.checkpoint.RewindScope) -> Unit = { _, _ -> },
    error: String?,
    onClearError: () -> Unit,
    matchingCommands: List<SlashCommandItem>,
    matchingMentions: List<MentionItem> = emptyList(),
    attachedMentions: List<MentionItem> = emptyList(),
    knownMentionNames: List<String> = emptyList(),
    queuedPrompts: List<QueuedPrompt>,
    onEditQueuedPrompt: (QueuedPrompt) -> Unit,
    onRemoveQueuedPrompt: (QueuedPrompt) -> Unit,
    onConvertToSteer: (QueuedPrompt) -> Unit = {},
    sendMode: ComposerSendMode,
    input: String,
    onInputChanged: (String) -> Unit,
    onApplyCommand: (SlashCommandItem) -> Unit,
    onApplyMention: (MentionItem) -> Unit = {},
    onRemoveMention: (MentionItem) -> Unit = {},
    attachments: List<ChatAttachment> = emptyList(),
    attachmentsProcessing: Boolean = false,
    onAttachmentsPicked: (List<Uri>, Boolean) -> Unit = { _, _ -> },
    onRemoveAttachment: (ChatAttachment) -> Unit = {},
    onSend: () -> Unit,
    onStop: () -> Unit,
    lastAssistantMessageId: String?,
    onRegenerate: () -> Unit,
    onCreateBranch: (String) -> Unit,
    onRetryTool: (String) -> Unit,
    initializing: Boolean = false,
    pinnedCapabilities: List<MentionItem> = emptyList(),
    onOpenSkillsMcp: () -> Unit = {},
    onUnpinMention: (String) -> Unit = {},
    activeModel: AiModelEntity? = null,
    onUpdateReasoning: (mode: String?, effort: String?) -> Unit = { _, _ -> },
    pendingApprovals: List<AgentApprovalRequestEntity> = emptyList(),
    activePlan: AgentPlanEntity? = null,
    activeCompaction: CompactionSnapshot? = null,
    mcpRecommendations: List<McpWorkspaceRecommender.Recommendation> = emptyList(),
    onEnableMcpRecommendation: (String) -> Unit = {},
    onDismissMcpRecommendation: (String) -> Unit = {},
    workflowSuggestions: List<ProactiveWorkflowSuggestion> = emptyList(),
    onLaunchWorkflowSuggestion: (ProactiveWorkflowSuggestion) -> Unit = {},
    onDismissWorkflowSuggestion: (String) -> Unit = {},
    onResolveApproval: (String, Boolean, Boolean) -> Unit = { _, _, _ -> },
    onResolveQuestion: (String, String) -> Unit = { _, _ -> },
    contextUsage: ContextUsage = ContextUsage(),
    quickPhrases: List<QuickPhrase> = emptyList(),
    onSelectPhrase: (QuickPhrase) -> Unit = {},
    onViewSubagentLanes: () -> Unit = {},
    subagentBranches: List<top.wkbin.tianxuan.harness.session.ConversationBranch> = emptyList(),
    onOpenSubagentBranch: (top.wkbin.tianxuan.harness.session.ConversationBranch) -> Unit = {},
    hiddenSkillSuggestions: Set<String> = emptySet(),
    onApplySkillSuggestion: (top.wkbin.tianxuan.harness.SkillSuggestion, Boolean) -> Unit = { _, _ -> },
    onDismissSkillSuggestion: (String) -> Unit = {},
) {
    Column(modifier = modifier) {
        ChatMessageList(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            listState = listState,
            messages = messages,
            toolResults = toolResults,
            initializing = initializing,
            running = running,
            imageGenerationModel = activeModel?.imageGenerationEnabled == true,
            status = status,
            workspace = workspace,
            workspaceProject = workspaceProject,
            onboardingPrivilege = onboardingPrivilege,
            thinkingExpanded = thinkingExpanded,
            thinkingAutoTranslate = thinkingAutoTranslate,
            chatRoundCollapse = chatRoundCollapse,
            thinkingLive = thinkingLive,
            liveThinkingMessageId = liveThinkingMessageId,
            lastAssistantMessageId = lastAssistantMessageId,
            onNavigateToSettings = onNavigateToSettings,
            knownMentionNames = knownMentionNames,
            quickPhrases = quickPhrases,
            onSelectPhrase = onSelectPhrase,
            onSelectCommand = onApplyCommand,
            onEditMessage = onEditMessage,
            onDeleteMessage = onDeleteMessage,
            onRewindMessage = onRewindMessage,
            onCreateBranch = onCreateBranch,
            onRegenerate = onRegenerate,
            onRetryTool = onRetryTool,
            onOpenFile = onOpenFile,
            activeCompaction = activeCompaction,
            activePlan = activePlan,
            pendingApprovals = pendingApprovals,
            onResolveApproval = onResolveApproval,
            onResolveQuestion = onResolveQuestion,
            onViewSubagentLanes = onViewSubagentLanes,
            subagentBranches = subagentBranches,
            onOpenSubagent = onOpenSubagentBranch,
            hiddenSkillSuggestions = hiddenSkillSuggestions,
            onApplySkillSuggestion = onApplySkillSuggestion,
            onDismissSkillSuggestion = onDismissSkillSuggestion,
        )

        activePlan?.let { plan ->
            val steps = remember(plan.stepsJson) { PlanStepParser.parse(plan.stepsJson) }
            if (steps.isNotEmpty()) {
                StickyPlanBar(
                    goal = plan.goal,
                    steps = steps,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
        }

        error?.let {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.errorContainer,
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onClearError) {
                        Text(stringResource(R.string.chat_close), color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        }

        McpRecommendationBanner(
            recommendations = mcpRecommendations,
            onEnable = onEnableMcpRecommendation,
            onDismiss = onDismissMcpRecommendation,
        )

        ProactiveWorkflowBanner(
            suggestions = workflowSuggestions,
            onLaunch = onLaunchWorkflowSuggestion,
            onDismiss = onDismissWorkflowSuggestion,
        )

        ChatComposer(
            listState = listState,
            running = running,
            initializing = initializing,
            status = status,
            messages = messages,
            toolResults = toolResults,
            workspace = workspace,
            input = input,
            onInputChanged = onInputChanged,
            onSend = onSend,
            onStop = onStop,
            sendMode = sendMode,
            matchingCommands = matchingCommands,
            onApplyCommand = onApplyCommand,
            matchingMentions = matchingMentions,
            onApplyMention = onApplyMention,
            attachments = attachments,
            attachmentsProcessing = attachmentsProcessing,
            onAttachmentsPicked = onAttachmentsPicked,
            onRemoveAttachment = onRemoveAttachment,
            queuedPrompts = queuedPrompts,
            onEditQueuedPrompt = onEditQueuedPrompt,
            onRemoveQueuedPrompt = onRemoveQueuedPrompt,
            onConvertToSteer = onConvertToSteer,
            knownMentionNames = knownMentionNames,
            attachedMentions = attachedMentions,
            onRemoveMention = onRemoveMention,
            pinnedCapabilities = pinnedCapabilities,
            onUnpinMention = onUnpinMention,
            onOpenSkillsMcp = onOpenSkillsMcp,
            activeModel = activeModel,
            onUpdateReasoning = onUpdateReasoning,
            contextUsage = contextUsage,
        )
    }
}

/** 工作区感知的 MCP 预设推荐横幅：每条推荐展示启用理由，可一键启用或忽略。 */
@Composable
private fun McpRecommendationBanner(
    recommendations: List<McpWorkspaceRecommender.Recommendation>,
    onEnable: (String) -> Unit,
    onDismiss: (String) -> Unit,
) {
    if (recommendations.isEmpty()) return
    recommendations.forEach { recommendation ->
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        recommendation.presetName,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        recommendation.reason,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                TextButton(onClick = { onEnable(recommendation.presetId) }) {
                    Text(stringResource(R.string.chat_enable), color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
                TextButton(onClick = { onDismiss(recommendation.presetId) }) {
                    Text(stringResource(R.string.chat_ignore), color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        }
    }
}

/** Event-driven workflow actions shown directly above the composer as interactive pills. */
@Composable
private fun ProactiveWorkflowBanner(
    suggestions: List<ProactiveWorkflowSuggestion>,
    onLaunch: (ProactiveWorkflowSuggestion) -> Unit,
    onDismiss: (String) -> Unit,
) {
    if (suggestions.isEmpty()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        suggestions.forEach { suggestion ->
            Surface(
                modifier = Modifier.clip(RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.90f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.55f)),
                tonalElevation = 2.dp,
            ) {
                Row(
                    modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    RuntimeIcon(RuntimeIconName.Hub, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Column(
                        modifier = Modifier
                            .clickable { onLaunch(suggestion) }
                            .padding(end = 4.dp),
                    ) {
                        Text(
                            text = if (suggestion.projectName.isNotBlank()) "${suggestion.projectName} · ${suggestion.title}" else suggestion.title,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            maxLines = 1,
                        )
                        if (suggestion.description.isNotBlank()) {
                            Text(
                                text = suggestion.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.75f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Button(
                        onClick = { onLaunch(suggestion) },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(28.dp),
                    ) {
                        Text("运行", style = MaterialTheme.typography.labelSmall)
                    }
                    IconButton(
                        onClick = { onDismiss(suggestion.workflowId) },
                        modifier = Modifier.size(24.dp),
                    ) {
                        RuntimeIcon(
                            RuntimeIconName.Close,
                            Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.65f),
                        )
                    }
                }
            }
        }
    }
}
