package top.wkbin.tianxuan.ui.chat

import android.app.Activity
import android.provider.Settings
import top.wkbin.tianxuan.ui.chat.floating.FloatingChatService
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import top.wkbin.tianxuan.ui.components.TianXuanBrandBadge
import top.wkbin.tianxuan.ui.components.RuntimeIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import top.wkbin.tianxuan.feature.chat.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.wkbin.tianxuan.core.database.AiModelEntity
import top.wkbin.tianxuan.core.model.ApprovalMode
import top.wkbin.tianxuan.core.model.RunMode
import top.wkbin.tianxuan.harness.session.ConversationBranch
import top.wkbin.tianxuan.ui.components.RuntimeIcon
import top.wkbin.tianxuan.ui.components.RuntimeIconName
import top.wkbin.tianxuan.harness.events.HarnessEvent

/**
 * 顶部栏：品牌标题/工作区状态行 + 工作台工具条（模型·审批·分支·运行）。
 */
@Composable
internal fun ChatTopBar(
    workspace: String,
    distroDisplayName: String,
    activeModel: AiModelEntity?,
    approvalMode: ApprovalMode,
    runMode: RunMode,
    currentBranch: ConversationBranch?,
    runtimeEvents: List<HarnessEvent>,
    running: Boolean,
    onShowFloatingPermissionDialog: () -> Unit,
    onOpenSessions: () -> Unit,
    onOpenModels: () -> Unit,
    onOpenApprovalModes: () -> Unit,
    onOpenRunModes: () -> Unit,
    onOpenBranches: () -> Unit,
    onOpenRuntime: () -> Unit,
    onOpenBrowser: (() -> Unit)? = null,
    browserHighlight: Boolean = false,
    onOpenRepository: (() -> Unit)? = null,
    repositoryHighlight: Boolean = false,
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        // 第 1 行：品牌 Badge + 标题/工作区 + 右侧模型胶囊与操作按钮
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 12.dp, top = 6.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // 左侧：品牌 Badge + 标题/状态（点击可快速唤出左侧会话抽屉）
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .weight(1f, fill = false)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onOpenSessions)
                    .padding(vertical = 2.dp, horizontal = 4.dp),
            ) {
                TianXuanBrandBadge(28.dp)
                Column {
                    Text(
                        text = stringResource(R.string.chat_title),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(
                            R.string.chat_status,
                            if (workspace.isNotBlank()) workspace else stringResource(R.string.chat_default_workspace),
                            distroDisplayName,
                        ),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // 右侧：小窗 + 会话抽屉
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                // 🌟 1. 智枢悬浮小窗收起按钮 (Collapse to Floating Window)
                IconButton(
                    onClick = {
                        if (Settings.canDrawOverlays(context)) {
                            FloatingChatService.start(context)
                            (context as? Activity)?.moveTaskToBack(true)
                        } else {
                            onShowFloatingPermissionDialog()
                        }
                    },
                    contentDescription = stringResource(R.string.chat_floating_collapse),
                ) {
                    RuntimeIcon(RuntimeIconName.OpenInNew, Modifier.size(19.dp), tint = MaterialTheme.colorScheme.primary)
                }

                // 🌟 2. 会话抽屉/列表（内部包含「新建会话」功能）
                IconButton(
                    onClick = onOpenSessions,
                    contentDescription = stringResource(R.string.chat_open_session_list),
                ) {
                    RuntimeIcon(RuntimeIconName.List, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // 第 2 行：全屏极薄无边框矩形 Dev Toolbar (模型 · 审批 · 分支 · 运行)
        CollapsibleChatWorkbenchStrip(
            activeModel = activeModel,
            approvalMode = approvalMode,
            runMode = runMode,
            currentBranch = currentBranch,
            runtimeEvents = runtimeEvents,
            running = running,
            onOpenModels = onOpenModels,
            onOpenApprovalModes = onOpenApprovalModes,
            onOpenRunModes = onOpenRunModes,
            onOpenBranches = onOpenBranches,
            onOpenRuntime = onOpenRuntime,
            onOpenBrowser = onOpenBrowser,
            browserHighlight = browserHighlight,
            onOpenRepository = onOpenRepository,
            repositoryHighlight = repositoryHighlight,
        )
    }
}
