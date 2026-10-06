package top.wkbin.tianxuan.ui.terminal

import org.koin.compose.viewmodel.koinViewModel
import top.wkbin.tianxuan.ui.components.RuntimeAlertDialog

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import top.wkbin.tianxuan.ui.components.RuntimeIconButton as IconButton
import top.wkbin.tianxuan.ui.components.RuntimeButton as Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import top.wkbin.tianxuan.ui.components.RuntimeTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import top.wkbin.tianxuan.feature.terminal.R
import top.wkbin.tianxuan.runtime.terminal.TerminalSessionHandle
import top.wkbin.tianxuan.ui.components.RuntimeIcon
import top.wkbin.tianxuan.ui.components.RuntimeIconName
import top.wkbin.tianxuan.ui.components.RuntimeTopBar
import top.wkbin.tianxuan.ui.components.SpotlightGuideOverlay
import top.wkbin.tianxuan.ui.components.rememberSpotlightAnchor
import top.wkbin.tianxuan.ui.components.spotlightAnchor
import kotlin.math.roundToInt

private const val MIN_TERMINAL_FONT_SIZE_SP = 10f
private const val MAX_TERMINAL_FONT_SIZE_SP = 24f

/**
 * 天玄 · 矩阵控制台 — Termux TerminalView + PRoot argv via TerminalSession JNI.
 * Input is owned by TerminalView (same as Android-PRoot-Engine TerminalBridge).
 */
@Composable
fun TerminalScreen(
    onBack: () -> Unit,
    project: String = "",
    showBackButton: Boolean = true,
    viewModel: TerminalViewModel = koinViewModel(),
) {
    val error by viewModel.error.collectAsStateWithLifecycle()
    val handles by viewModel.handles.collectAsStateWithLifecycle()
    val activeId by viewModel.activeId.collectAsStateWithLifecycle()
    val workspaces by viewModel.workspaces.collectAsStateWithLifecycle()
    val distributionName by viewModel.distributionName.collectAsStateWithLifecycle()
    val installedDistros by viewModel.installedDistros.collectAsStateWithLifecycle()
    val configuredFontSize by viewModel.terminalFontSize.collectAsStateWithLifecycle()
    val colorScheme by viewModel.terminalColorScheme.collectAsStateWithLifecycle()
    val hapticsEnabled by viewModel.terminalHapticsEnabled.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val density = LocalDensity.current

    var fontSizeSp by remember { mutableFloatStateOf(configuredFontSize.toFloat()) }
    var sessionToClose by remember { mutableStateOf<String?>(null) }

    val sysSurfaceLowest = MaterialTheme.colorScheme.surfaceContainerLowest
    val sysSurfaceHigh = MaterialTheme.colorScheme.surfaceContainerHigh
    val sysOnSurface = MaterialTheme.colorScheme.onSurface
    val sysOutline = MaterialTheme.colorScheme.outlineVariant
    val (termBg, termHeaderBg, termTextDefault, termBorder) = remember(colorScheme, sysSurfaceLowest, sysSurfaceHigh, sysOnSurface, sysOutline) {
        when (colorScheme) {
            // obsidian 是默认方案：终端恒为深底浅字（与设置页预览卡一致），
            // 不能落入 else 跟随系统主题——浅色主题下会是浅底 + Termux 默认白字，不可读。
            "obsidian" -> listOf(Color(0xFF0F1117), Color(0xFF171B26), Color(0xFFE2E2E9), Color(0xFF282A36))
            "matrix" -> listOf(Color(0xFF0A0F0D), Color(0xFF101B14), Color(0xFF10B981), Color(0xFF1A3324))
            "amber" -> listOf(Color(0xFF140F0A), Color(0xFF1F170F), Color(0xFFF59E0B), Color(0xFF3B2B1B))
            "aurora" -> listOf(Color(0xFF0D1424), Color(0xFF141F36), Color(0xFF38BDF8), Color(0xFF1E3A5F))
            else -> listOf(sysSurfaceLowest, sysSurfaceHigh, sysOnSurface, sysOutline)
        }
    }

    val bridge = remember(context) {
        TianXuanTerminalBridge(context, viewModel.sessionClientRouter)
    }
    // Attach before AndroidView layout so the first PTY callbacks aren't dropped.
    bridge.attachToRouter()

    DisposableEffect(bridge) {
        bridge.onFontScale = { increase ->
            fontSizeSp = (fontSizeSp + if (increase) 1f else -1f)
                .coerceIn(MIN_TERMINAL_FONT_SIZE_SP, MAX_TERMINAL_FONT_SIZE_SP)
            viewModel.setTerminalFontSize(fontSizeSp.roundToInt())
        }
        onDispose {
            bridge.onFontScale = null
            bridge.detachFromRouter()
        }
    }

    // Match Android-PRoot-Engine / Termux Activity soft-input flags while this screen is visible.
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        val previous = window?.attributes?.softInputMode
        window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN,
        )
        onDispose {
            if (previous != null) window.setSoftInputMode(previous)
        }
    }

    val activeHandle = handles.firstOrNull { it.id == activeId }

    val copyScreen = {
        val session = bridge.currentSession() ?: activeHandle?.termuxSession
        val text = session?.emulator?.screen?.transcriptText?.trim().orEmpty()
        if (text.isNotBlank()) {
            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                .setPrimaryClip(ClipData.newPlainText(context.getString(R.string.terminal_clipboard_label), text))
            Toast.makeText(context, context.getString(R.string.terminal_copied), Toast.LENGTH_SHORT).show()
        }
    }

    val pasteToTerminal = {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
        if (text.isNotBlank()) bridge.sendString(text)
    }

    var isLeaving by remember { mutableStateOf(false) }
    var showSessions by remember { mutableStateOf(false) }
    val firstUseGuidesShown by viewModel.firstUseGuidesShown.collectAsStateWithLifecycle()
    val sessionsAnchor = rememberSpotlightAnchor()
    var showCreateSession by remember { mutableStateOf(false) }

    LaunchedEffect(project) {
        viewModel.initialize(project)
    }

    LaunchedEffect(configuredFontSize) {
        fontSizeSp = configuredFontSize.toFloat()
    }

    val navigateBack = {
        if (!isLeaving) {
            isLeaving = true
            onBack()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                RuntimeTopBar(
                    title = if (project.isNotBlank()) stringResource(R.string.terminal_project_title, project) else stringResource(R.string.terminal_console_title),
                    onBack = if (showBackButton) navigateBack else null,
                    statusText = stringResource(R.string.terminal_engine_status, distributionName),
                ) {
                    IconButton(
                        onClick = { showSessions = true },
                        modifier = Modifier.spotlightAnchor(sessionsAnchor),
                        contentDescription = stringResource(R.string.terminal_sessions_desc),
                    ) {
                        RuntimeIcon(RuntimeIconName.List, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    color = termBg,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, termBorder),
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(termHeaderBg)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                TerminalDot(Color(0xFFFF3366))
                                TerminalDot(Color(0xFFFFB300))
                                TerminalDot(Color(0xFF00E676))
                            }
                            Text(
                                stringResource(
                                    R.string.terminal_pty_header,
                                    distributionName.substringBefore(" (").uppercase(),
                                    Build.SUPPORTED_ABIS.firstOrNull()?.uppercase() ?: "UNKNOWN",
                                ),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 1.sp,
                                ),
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    stringResource(R.string.terminal_copy),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable(onClick = copyScreen)
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = termTextDefault.copy(alpha = 0.6f),
                                )
                                Text(
                                    stringResource(R.string.terminal_paste),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable(onClick = pasteToTerminal)
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = termTextDefault.copy(alpha = 0.6f),
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(4.dp),
                        ) {
                            when {
                                error != null -> Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Text(
                                        error.orEmpty(),
                                        color = MaterialTheme.colorScheme.error,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                    Button(
                                        onClick = { viewModel.retryInitialize(project) },
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                    ) {
                                        RuntimeIcon(RuntimeIconName.Refresh, Modifier.size(14.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(stringResource(R.string.terminal_retry))
                                    }
                                }
                                activeHandle == null -> Text(
                                    stringResource(R.string.terminal_starting),
                                    Modifier.padding(12.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    fontFamily = FontFamily.Monospace,
                                )
                                else -> {
                                    val session = activeHandle.termuxSession
                                    val fontPx = with(density) { fontSizeSp.sp.toPx().roundToInt().coerceAtLeast(8) }
                                    // key(session): recreate view only on session switch — avoids
                                    // Compose update{} calling updateSize every frame (that resets mTopRow).
                                    key(activeHandle.id) {
                                        var appliedFontPx by remember { mutableStateOf(fontPx) }
                                        AndroidView(
                                            factory = { ctx ->
                                                TianXuanTerminalHost(ctx).also { host ->
                                                    // Client before attachSession (updateSize → onEmulatorSet).
                                                    bridge.terminalView = host.terminalView
                                                    host.terminalView.setTextSize(fontPx)
                                                    appliedFontPx = fontPx
                                                    host.terminalView.attachSession(session)
                                                }
                                            },
                                            update = { host ->
                                                val view = host.terminalView
                                                if (bridge.terminalView !== view) {
                                                    bridge.terminalView = view
                                                }
                                                if (appliedFontPx != fontPx) {
                                                    view.setTextSize(fontPx)
                                                    appliedFontPx = fontPx
                                                }
                                            },
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // PC-standard two-row ExtraKeys (Android-PRoot-Engine):
                // Row1: ESC TAB / - ~ HOME ▲ END
                // Row2: CTRL ALT | ^C ^D ◀ ▼ ▶
                var ctrlActive by remember { mutableStateOf(false) }
                var altActive by remember { mutableStateOf(false) }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        ExtraKey("ESC", Modifier.weight(1f), isAccent = true, hapticsEnabled = hapticsEnabled) {
                            bridge.sendEscape()
                        }
                        ExtraKey("TAB", Modifier.weight(1f), isAccent = true, hapticsEnabled = hapticsEnabled) {
                            bridge.sendTab()
                        }
                        ExtraKey("/", Modifier.weight(1f), hapticsEnabled = hapticsEnabled) {
                            bridge.sendString("/")
                        }
                        ExtraKey("-", Modifier.weight(1f), hapticsEnabled = hapticsEnabled) {
                            bridge.sendString("-")
                        }
                        ExtraKey("~", Modifier.weight(1f), hapticsEnabled = hapticsEnabled) {
                            bridge.sendString("~")
                        }
                        ExtraKey("HOME", Modifier.weight(1f), hapticsEnabled = hapticsEnabled) {
                            bridge.sendHome()
                        }
                        ExtraKey("▲", Modifier.weight(1f), hapticsEnabled = hapticsEnabled) {
                            bridge.sendArrowUp()
                        }
                        ExtraKey("END", Modifier.weight(1f), hapticsEnabled = hapticsEnabled) {
                            bridge.sendEnd()
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        ExtraKey(
                            "CTRL",
                            Modifier.weight(1f),
                            isAccent = true,
                            isActive = ctrlActive,
                            hapticsEnabled = hapticsEnabled,
                        ) {
                            bridge.toggleControlKey()
                            ctrlActive = bridge.isControlKeyActive()
                        }
                        ExtraKey(
                            "ALT",
                            Modifier.weight(1f),
                            isActive = altActive,
                            hapticsEnabled = hapticsEnabled,
                        ) {
                            bridge.toggleAltKey()
                            altActive = bridge.isAltKeyActive()
                        }
                        ExtraKey("|", Modifier.weight(1f), hapticsEnabled = hapticsEnabled) {
                            bridge.sendString("|")
                        }
                        ExtraKey("^C", Modifier.weight(1f), isDanger = true, hapticsEnabled = hapticsEnabled) {
                            bridge.sendSigInt()
                        }
                        ExtraKey("^D", Modifier.weight(1f), hapticsEnabled = hapticsEnabled) {
                            bridge.sendEof()
                        }
                        ExtraKey("◀", Modifier.weight(1f), hapticsEnabled = hapticsEnabled) {
                            bridge.sendArrowLeft()
                        }
                        ExtraKey("▼", Modifier.weight(1f), hapticsEnabled = hapticsEnabled) {
                            bridge.sendArrowDown()
                        }
                        ExtraKey("▶", Modifier.weight(1f), hapticsEnabled = hapticsEnabled) {
                            bridge.sendArrowRight()
                        }
                    }
                }
            }
        }

        if ("terminal_sessions" !in firstUseGuidesShown) {
            SpotlightGuideOverlay(
                anchor = sessionsAnchor,
                title = stringResource(R.string.terminal_guide_title),
                message = stringResource(R.string.terminal_guide_message),
                onDismiss = { viewModel.markFirstUseGuideShown("terminal_sessions") },
            )
        }
    }

    if (showSessions) {
        SessionListDialog(
            handles = handles,
            activeId = activeId,
            onDismiss = { showSessions = false },
            onSwitch = { id -> viewModel.switchSession(id); showSessions = false },
            onCreate = {
                showSessions = false
                showCreateSession = true
            },
            onClose = { id -> showSessions = false; sessionToClose = id },
        )
    }

    sessionToClose?.let { closingId ->
        CloseSessionConfirmDialog(
            onConfirm = {
                sessionToClose = null
                val wasOnlySession = handles.size == 1
                viewModel.closeSession(closingId) { success ->
                    if (success && wasOnlySession) {
                        Toast.makeText(context, context.getString(R.string.terminal_reset_complete), Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onDismiss = { sessionToClose = null },
        )
    }

    if (showCreateSession) {
        CreateTerminalDialog(
            workspaces = workspaces,
            installedDistros = installedDistros,
            nextSessionIndex = handles.size + 1,
            onDismiss = { showCreateSession = false },
            onCreate = { label, workDir, distroId ->
                showCreateSession = false
                viewModel.createSession(label, workDir, distroId)
            },
        )
    }
}

@Composable
private fun CreateTerminalDialog(
    workspaces: List<top.wkbin.tianxuan.runtime.WorkspaceProject>,
    installedDistros: List<top.wkbin.tianxuan.core.model.InstalledDistro> = emptyList(),
    nextSessionIndex: Int,
    onDismiss: () -> Unit,
    onCreate: (label: String, workingDirectory: String, distroId: String?) -> Unit,
) {
    val defaultLabel = stringResource(R.string.terminal_default_label, nextSessionIndex)
    var label by remember(nextSessionIndex) { mutableStateOf(defaultLabel) }
    var selectedDir by remember { mutableStateOf("/root") }
    var selectedDistroId by remember { mutableStateOf(installedDistros.firstOrNull { it.isActive }?.id ?: installedDistros.firstOrNull()?.id ?: "ubuntu") }
    val quickLabels = listOf(
        stringResource(R.string.terminal_quick_main),
        stringResource(R.string.terminal_quick_build),
        stringResource(R.string.terminal_quick_service),
        stringResource(R.string.terminal_quick_git),
        stringResource(R.string.terminal_quick_python),
    )

    RuntimeAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RuntimeIcon(RuntimeIconName.Terminal, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.terminal_new_session), fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (installedDistros.size > 1) {
                    Text(stringResource(R.string.terminal_target_distribution), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        installedDistros.forEach { d ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (selectedDistroId == d.id) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = BorderStroke(
                                    1.dp,
                                    if (selectedDistroId == d.id) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else Color.Transparent,
                                ),
                                modifier = Modifier.clickable { selectedDistroId = d.id },
                            ) {
                                Text(
                                    d.displayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (selectedDistroId == d.id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                    androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }

                Text(stringResource(R.string.terminal_label), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                androidx.compose.material3.OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    placeholder = { Text(stringResource(R.string.terminal_label_hint)) },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                // å¿«æ·é¢„è®¾æ ‡ç­¾
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    quickLabels.forEach { tag ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (label == tag) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(
                                1.dp,
                                if (label == tag) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else Color.Transparent,
                            ),
                            modifier = Modifier.clickable { label = tag },
                        ) {
                            Text(
                                tag,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (label == tag) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                }

                androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Text(stringResource(R.string.terminal_initial_cwd), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    item {
                        TerminalDirOption(
                            name = stringResource(R.string.terminal_root_directory),
                            path = "/root",
                            selected = selectedDir == "/root",
                            onSelect = { selectedDir = "/root" },
                        )
                    }
                    items(workspaces.size) { index ->
                        val ws = workspaces[index]
                        TerminalDirOption(
                            name = ws.name,
                            path = ws.linuxPath,
                            selected = selectedDir == ws.linuxPath,
                            onSelect = { selectedDir = ws.linuxPath },
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(label.ifBlank { defaultLabel }, selectedDir, selectedDistroId) },
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(stringResource(R.string.terminal_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.terminal_cancel)) }
        },
    )
}

@Composable
private fun TerminalDirOption(
    name: String,
    path: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainerLow,
                RoundedCornerShape(8.dp),
            )
            .border(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(8.dp),
            )
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier.size(10.dp).clip(CircleShape).background(
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
        Column {
            Text(name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal))
            Text(path, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SessionListDialog(
    handles: List<TerminalSessionHandle>,
    activeId: String?,
    onDismiss: () -> Unit,
    onSwitch: (String) -> Unit,
    onCreate: () -> Unit,
    onClose: (String) -> Unit,
) {
    RuntimeAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RuntimeIcon(RuntimeIconName.Terminal, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.terminal_sessions), fontWeight = FontWeight.Bold)
                }
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Text(
                        stringResource(R.string.terminal_active_sessions, handles.size),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (handles.size == 1) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            RuntimeIcon(RuntimeIconName.Alert, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(
                                stringResource(R.string.terminal_only_session_hint),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(handles.size) { index ->
                        val handle = handles[index]
                        val active = handle.id == activeId
                        val distroName = runCatching {
                            top.wkbin.tianxuan.runtime.DistributionCatalog.require(handle.distributionId).displayName
                        }.getOrDefault(handle.distributionId)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (active) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLowest,
                            border = BorderStroke(
                                1.dp,
                                if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSwitch(handle.id) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Box(
                                    Modifier.size(8.dp).clip(CircleShape).background(
                                        if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    ),
                                )
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            handle.label,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                            modifier = Modifier.weight(1f, fill = false),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Surface(
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                            shape = RoundedCornerShape(4.dp),
                                        ) {
                                            Text(
                                                distroName,
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                color = MaterialTheme.colorScheme.primary,
                                                maxLines = 1,
                                                softWrap = false,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                            )
                                        }
                                        if (active) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(4.dp),
                                            ) {
                                                Text(
                                                    stringResource(R.string.terminal_current),
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                    color = MaterialTheme.colorScheme.primary,
                                                    maxLines = 1,
                                                    softWrap = false,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        handle.workingDirectory,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (handles.size == 1) {
                                    IconButton(
                                        onClick = { onClose(handle.id) },
                                        contentDescription = stringResource(R.string.terminal_reset_session_desc),
                                    ) {
                                        RuntimeIcon(RuntimeIconName.PowerSettingsNew, Modifier.size(18.dp), MaterialTheme.colorScheme.primary)
                                    }
                                } else {
                                    IconButton(
                                        onClick = { onClose(handle.id) },
                                        contentDescription = stringResource(R.string.terminal_close_session_desc),
                                    ) {
                                        RuntimeIcon(RuntimeIconName.Close, Modifier.size(16.dp), MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onCreate, shape = RoundedCornerShape(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    RuntimeIcon(RuntimeIconName.Plus, Modifier.size(16.dp), MaterialTheme.colorScheme.onPrimary)
                    Text(stringResource(R.string.terminal_new))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.terminal_close)) }
        },
    )
}

/**
 * å…³é—­ç»ˆç«¯ä¼šè¯äºŒæ¬¡ç¡®è®¤å¼¹çª—ï¼ˆæ ·å¼å¯¹é½ DistroManagementScreen çš„å€’è®¡æ—¶ç¡®è®¤å¼¹çª—ï¼‰ï¼š
 * å…³é—­ä¼šæ€æ­»ä¼šè¯ä¸­è¿è¡Œçš„æ‰€æœ‰è¿›ç¨‹ï¼Œå±žç ´åæ€§æ“ä½œã€‚
 */
@Composable
private fun CloseSessionConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var countdown by remember { mutableStateOf(3) }

    LaunchedEffect(Unit) {
        while (countdown > 0) {
            delay(1000)
            countdown--
        }
    }

    RuntimeAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.terminal_close_confirm_title),
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            Text(
                text = stringResource(R.string.terminal_close_confirm_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = countdown == 0,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    disabledContainerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.3f),
                ),
            ) {
                Text(
                    text = if (countdown > 0) stringResource(R.string.terminal_close_confirm_countdown, countdown)
                    else stringResource(R.string.terminal_close_confirm),
                    color = if (countdown == 0) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onError.copy(alpha = 0.6f),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.terminal_cancel)) }
        },
    )
}

@Composable
private fun ExtraKey(
    label: String,
    modifier: Modifier = Modifier,
    isAccent: Boolean = false,
    isDanger: Boolean = false,
    isActive: Boolean = false,
    hapticsEnabled: Boolean = true,
    onClick: () -> Unit,
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val bg = when {
        isActive && isAccent -> MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
        isActive -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.22f)
        isDanger -> MaterialTheme.colorScheme.errorContainer
        isAccent -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val textCol = when {
        isActive && isAccent -> MaterialTheme.colorScheme.primary
        isActive -> MaterialTheme.colorScheme.tertiary
        isDanger -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    val borderCol = when {
        isActive && isAccent -> MaterialTheme.colorScheme.primary
        isActive -> MaterialTheme.colorScheme.tertiary
        isDanger -> MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
        else -> MaterialTheme.colorScheme.outlineVariant
    }

    Surface(
        modifier = modifier
            // Fixed short height → wider-than-tall keys (not square), saves console space.
            .height(28.dp)
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = {
                if (hapticsEnabled) {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                }
                onClick()
            }),
        color = bg,
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, borderCol),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    lineHeight = 10.sp,
                ),
                color = textCol,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun TerminalDot(color: Color) {
    Box(Modifier.size(8.dp).background(color, CircleShape))
}


