package top.wkbin.tianxuan

import org.koin.android.ext.android.inject
import org.koin.compose.viewmodel.koinViewModel
import top.wkbin.tianxuan.ui.components.RuntimeAlertDialog
import top.wkbin.tianxuan.ui.onboarding.OnboardingScreen
import top.wkbin.tianxuan.ui.onboarding.OnboardingViewModel

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import top.wkbin.tianxuan.ui.components.RuntimeButton as Button
import top.wkbin.tianxuan.ui.components.RuntimeCircularProgressIndicator as CircularProgressIndicator
import top.wkbin.tianxuan.ui.components.RuntimeLinearProgressIndicator as LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import top.wkbin.tianxuan.ui.components.RuntimeTextButton as TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import top.wkbin.tianxuan.core.datastore.AppearancePreferences
import top.wkbin.tianxuan.core.model.AppUpdateInfo
import top.wkbin.tianxuan.core.network.AppUpdateManager
import top.wkbin.tianxuan.runtime.service.RuntimeServiceController
import top.wkbin.tianxuan.ui.components.RuntimeIcon
import top.wkbin.tianxuan.ui.components.RuntimeIconName
import top.wkbin.tianxuan.ui.components.MarkdownText
import top.wkbin.tianxuan.ui.navigation.TianXuanNavHost
import top.wkbin.tianxuan.ui.theme.TianXuanTheme
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

import top.wkbin.tianxuan.core.common.navigation.AppNavigationTarget
import top.wkbin.tianxuan.core.common.navigation.GlobalNavigationBus
import top.wkbin.tianxuan.harness.mcp.oauth.McpOAuthCoordinator
import top.wkbin.tianxuan.harness.mcp.McpManager
import top.wkbin.tianxuan.service.adb.AdbNotificationManager

class MainActivity : AppCompatActivity() {
    val settingsDataStore: AppearancePreferences by inject()

    val appUpdateManager: AppUpdateManager by inject()

    val runtimeServiceController: RuntimeServiceController by inject()

    val globalNavigationBus: GlobalNavigationBus by inject()

    val adbNotificationManager: AdbNotificationManager by inject()

    val mcpOAuthCoordinator: McpOAuthCoordinator by inject()

    val mcpManager: McpManager by inject()

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) runtimeServiceController.start()
    }

    /** 是否已发起过 POST_NOTIFICATIONS 系统弹窗；与服务启动解耦，避免授权后无法补启。 */
    private var notificationPermissionRequested = false

    /** 控制 SplashScreen 持续显示，直到 onboarding 偏好从 DataStore 加载完成，避免白屏空窗。 */
    private val keepSplashOnScreen: MutableState<Boolean> = mutableStateOf(true)

    private var createdUptimeMs: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        createdUptimeMs = android.os.SystemClock.uptimeMillis()
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { keepSplashOnScreen.value }
        super.onCreate(savedInstanceState)
        handleNavigationIntent(intent)
        enableEdgeToEdge()
        setContent {
            val themeMode by settingsDataStore.themeMode.collectAsStateWithLifecycle(initialValue = "system")
            val themeStyle by settingsDataStore.themeStyle.collectAsStateWithLifecycle(initialValue = "material_you")
            val dynamicColorEnabled by settingsDataStore.dynamicColorEnabled.collectAsStateWithLifecycle(initialValue = true)
            val chengmingBackgroundUri by settingsDataStore.chengmingBackgroundUri.collectAsStateWithLifecycle(initialValue = null)
            val pageScale by settingsDataStore.appFontScale.collectAsStateWithLifecycle(initialValue = 1f)
            val systemDark = isSystemInDarkTheme()
            val systemDensity = LocalDensity.current
            val isDark = when (themeMode) {
                "light" -> false
                "dark" -> true
                else -> systemDark
            }

            CompositionLocalProvider(
                LocalDensity provides Density(
                    density = systemDensity.density * pageScale.coerceIn(0.8f, 1.3f),
                    fontScale = systemDensity.fontScale,
                ),
            ) {
                TianXuanTheme(
                    style = top.wkbin.tianxuan.ui.theme.ThemeStyle.fromId(themeStyle),
                    darkTheme = isDark,
                    dynamicColor = dynamicColorEnabled,
                    backgroundUri = chengmingBackgroundUri,
                ) {
                val onboardingViewModel: OnboardingViewModel = koinViewModel()
                val onboarding by onboardingViewModel.status.collectAsStateWithLifecycle()

                // onboarding 偏好读盘完成后让 SplashScreen 退场；Runtime 恢复已在后台继续，不阻塞首帧。
                // Linux 环境恢复（restoreInstalledState）由 OnboardingViewModel.init 自行发起，不再在此重复触发。
                LaunchedEffect(onboarding.loaded) {
                    if (onboarding.loaded && keepSplashOnScreen.value) {
                        keepSplashOnScreen.value = false
                        android.util.Log.i(
                            "TianXuanStartup",
                            "splash dismissed in ${android.os.SystemClock.uptimeMillis() - createdUptimeMs}ms",
                        )
                    }
                }

                // 启动时静默检查更新
                var updateInfo by remember { mutableStateOf<AppUpdateInfo?>(null) }
                var downloadProgress by remember { mutableStateOf<Float?>(null) }
                var isDownloading by remember { mutableStateOf(false) }
                var downloadFailed by remember { mutableStateOf(false) }
                val scope = rememberCoroutineScope()
                val mainContext = LocalContext.current
                val currentVersionName = remember {
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            mainContext.packageManager.getPackageInfo(
                                mainContext.packageName,
                                PackageManager.PackageInfoFlags.of(0),
                            ).versionName
                        } else {
                            @Suppress("DEPRECATION")
                            mainContext.packageManager.getPackageInfo(mainContext.packageName, 0).versionName
                        }
                    } catch (_: Exception) {
                        null
                    } ?: "0.0.0"
                }

                LaunchedEffect(onboarding.completed) {
                    if (onboarding.completed) {
                        val autoCheck = settingsDataStore.autoCheckUpdates.first()
                        if (autoCheck) {
                            val res = appUpdateManager.checkUpdate(currentVersionName)
                            res.onSuccess { info ->
                                if (info.hasUpdate) updateInfo = info
                            }
                        }
                    }
                }

                updateInfo?.let { info ->
                    RuntimeAlertDialog(
                        onDismissRequest = { if (!isDownloading) updateInfo = null },
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                RuntimeIcon(
                                    name = RuntimeIconName.Refresh,
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Text(stringResource(R.string.tianxuan_update_available, info.latestVersion), fontWeight = FontWeight.Bold)
                            }
                        },
                        text = {
                            Column(
                                modifier = Modifier.verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(8.dp),
                                ) {
                                    Text(
                                        text = stringResource(R.string.tianxuan_update_versions, info.currentVersion, info.latestVersion),
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    )
                                }
                                if (info.releaseNotes.isNotBlank()) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        MarkdownText(
                                            markdown = info.releaseNotes,
                                            modifier = Modifier.padding(12.dp),
                                        )
                                    }
                                }
                                if (isDownloading) {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(stringResource(R.string.tianxuan_update_downloading_package), style = MaterialTheme.typography.labelMedium)
                                        if (downloadProgress != null) {
                                            LinearProgressIndicator(
                                                progress = { downloadProgress ?: 0f },
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                        } else {
                                            LinearProgressIndicator(
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                        }
                                    }
                                }
                                if (downloadFailed) {
                                    // 下载失败必须可见：不再静默停止
                                    Text(
                                        text = stringResource(R.string.tianxuan_update_download_failed),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        },
                        confirmButton = {
                            val apkUrl = info.apkDownloadUrl
                            if (apkUrl != null) {
                                Button(
                                    onClick = {
                                        isDownloading = true
                                        downloadProgress = 0f
                                        downloadFailed = false
                                        scope.launch {
                                            val res = appUpdateManager.downloadApk(apkUrl) { dl, tot ->
                                                if (tot != null && tot > 0) downloadProgress = dl.toFloat() / tot.toFloat()
                                            }
                                            isDownloading = false
                                            res.onSuccess { apkFile ->
                                                downloadProgress = 1f
                                                appUpdateManager.installApk(apkFile)
                                                updateInfo = null
                                            }.onFailure {
                                                // 下载失败：在更新对话框内给出可见的错误文案（跟随现有 UI 模式）
                                                downloadFailed = true
                                            }
                                        }
                                    },
                                    enabled = !isDownloading,
                                ) {
                                    Text(stringResource(if (isDownloading) R.string.tianxuan_downloading else R.string.tianxuan_update_now))
                                }
                            } else {
                                Button(
                                    onClick = {
                                        runCatching {
                                            startActivity(Intent(Intent.ACTION_VIEW,
                                                info.releaseUrl.toUri()))
                                        }
                                        updateInfo = null
                                    },
                                ) {
                                    Text(stringResource(R.string.tianxuan_open_github))
                                }
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = { updateInfo = null },
                                enabled = !isDownloading,
                            ) {
                                Text(stringResource(R.string.tianxuan_later))
                            }
                        },
                    )
                }

                when {
                    !onboarding.loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                    onboarding.completed -> TianXuanNavHost(globalNavigationBus = globalNavigationBus)
                    else -> OnboardingScreen(onboardingViewModel)
                }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 主应用回到前台时，自动关闭智枢桌面悬浮小窗，避免主界面与悬浮窗重叠
        runCatching {
            top.wkbin.tianxuan.ui.chat.floating.FloatingChatService.stop(this)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNavigationIntent(intent)
    }

    private fun handleNavigationIntent(targetIntent: Intent?) {
        val intentToHandle = targetIntent ?: intent ?: return
        val oauthUri = intentToHandle.data
        if (intentToHandle.action == Intent.ACTION_VIEW && oauthUri != null &&
            oauthUri.scheme == "tianxuan" && oauthUri.host == "oauth" && oauthUri.path == "/mcp"
        ) {
            lifecycleScope.launch {
                runCatching { mcpOAuthCoordinator.callback(oauthUri) }
                    .onSuccess { result ->
                        if (result is McpOAuthCoordinator.CallbackResult.Authorized) {
                            lifecycleScope.launch { mcpManager.invalidateServer(result.serverId) }
                        }
                    }
                    .onFailure { /* UI observes server auth state; never log callback code/token. */ }
            }
            return
        }
        val action = intentToHandle.action
        val navigateTo = intentToHandle.getStringExtra("navigate_to")
        val isAdbLogcat = action == "top.wkbin.tianxuan.action.OPEN_ADB_LOGCAT" || navigateTo == "adb_logcat"
        if (isAdbLogcat) {
            globalNavigationBus.navigateTo(top.wkbin.tianxuan.core.common.navigation.AppNavigationTarget.AdbLogcat)
        }
        // 工作流通知点入：打开运行页并定位到对应执行
        if (action == top.wkbin.tianxuan.service.WorkflowForegroundService.ACTION_OPEN_RUN) {
            globalNavigationBus.navigateTo(
                top.wkbin.tianxuan.core.common.navigation.AppNavigationTarget.WorkflowRun(
                    intentToHandle.getStringExtra(top.wkbin.tianxuan.service.WorkflowForegroundService.EXTRA_EXECUTION_ID),
                ),
            )
        }
    }

    override fun onPostResume() {
        super.onPostResume()
        // Runtime permission dialogs are most reliable after the first page is resumed and drawn.
        // First launch also restores onboarding/theme state, so requesting from onCreate can be
        // swallowed by some Android builds before the Activity becomes fully interactive.
        // 每次回到前台都尝试启动保活：用户可能在系统设置里补授了通知权限。
        window.decorView.post {
            if (!isFinishing && !isDestroyed) {
                // MIUI 会在 POST_NOTIFICATIONS 尚未授权时拒绝 START_FOREGROUND AppOp。
                // 先完成权限门，再启动需要常驻通知的 Runtime 服务，避免无通知的幽灵服务
                // 和随后出现的系统转场噪声。
                startRuntimeServiceWhenNotificationAllowed()
            }
        }
    }

    private fun startRuntimeServiceWhenNotificationAllowed() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            runtimeServiceController.start()
        } else if (!notificationPermissionRequested) {
            notificationPermissionRequested = true
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
