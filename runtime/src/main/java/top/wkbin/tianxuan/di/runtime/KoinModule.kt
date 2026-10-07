package top.wkbin.tianxuan.di.runtime

import org.koin.dsl.module
import top.wkbin.tianxuan.runtime.proot.ProotCommandBuilder
import top.wkbin.tianxuan.core.database.BuildScriptRepository
import top.wkbin.tianxuan.runtime.BackgroundTaskRegistry
import top.wkbin.tianxuan.runtime.DistroConfigurator
import top.wkbin.tianxuan.runtime.ElfInspector
import top.wkbin.tianxuan.runtime.EnvironmentResolver
import top.wkbin.tianxuan.runtime.FtpServiceManager
import top.wkbin.tianxuan.runtime.LinuxEnvironmentManager
import top.wkbin.tianxuan.runtime.LinuxRuntime
import top.wkbin.tianxuan.runtime.LinuxRuntimeImpl
import top.wkbin.tianxuan.runtime.LocalLlmManager
import top.wkbin.tianxuan.runtime.RuntimeHealthChecker
import top.wkbin.tianxuan.runtime.RuntimePathManager
import top.wkbin.tianxuan.runtime.SshServiceManager
import top.wkbin.tianxuan.runtime.StorageManager
import top.wkbin.tianxuan.runtime.WorkspaceFileService
import top.wkbin.tianxuan.runtime.WorkspaceManager
import top.wkbin.tianxuan.runtime.apps.AndroidAppManager
import top.wkbin.tianxuan.runtime.bridge.HostBridge
import top.wkbin.tianxuan.runtime.bridge.adb.EmbeddedAdbManager
import top.wkbin.tianxuan.runtime.build.WorkshopSigningManager
import top.wkbin.tianxuan.runtime.build.WorkspaceBuildRunner
import top.wkbin.tianxuan.runtime.doctor.EnvironmentDoctor
import top.wkbin.tianxuan.runtime.doctor.EnvironmentRepairer
import top.wkbin.tianxuan.runtime.gui.GuiAccessibilityEnabler
import top.wkbin.tianxuan.runtime.gui.HostGuiController
import top.wkbin.tianxuan.runtime.gui.HostGuiToolkit
import top.wkbin.tianxuan.runtime.gui.WorkflowGuiHudBridge
import top.wkbin.tianxuan.runtime.privilege.PrivilegeManager
import top.wkbin.tianxuan.runtime.privilege.ShizukuHostServiceClient
import top.wkbin.tianxuan.runtime.privilege.ShizukuSystemApis
import top.wkbin.tianxuan.runtime.virtualdisplay.VirtualDisplayCoordinator
import top.wkbin.tianxuan.runtime.virtualdisplay.VirtualScreenToolkit
import top.wkbin.tianxuan.runtime.proot.ProotInstaller
import top.wkbin.tianxuan.runtime.pty.NativePtyManager
import top.wkbin.tianxuan.runtime.pty.ScriptPtyManager
import top.wkbin.tianxuan.runtime.rootfs.LxcImagesClient
import top.wkbin.tianxuan.runtime.rootfs.OciRegistryClient
import top.wkbin.tianxuan.runtime.rootfs.RootfsInstaller
import top.wkbin.tianxuan.runtime.rootfs.RootfsValidator
import top.wkbin.tianxuan.runtime.rootfs.TarStreamExtractor
import top.wkbin.tianxuan.runtime.scripts.RuntimeAssetSynchronizer
import top.wkbin.tianxuan.runtime.service.LocalServiceLauncherImpl
import top.wkbin.tianxuan.runtime.shell.ProcessRegistryImpl
import top.wkbin.tianxuan.runtime.shell.ProcessShellExecutor
import top.wkbin.tianxuan.runtime.terminal.TerminalSessionClientRouter
import top.wkbin.tianxuan.runtime.terminal.TerminalSessionManager
import top.wkbin.tianxuan.runtime.webchat.WebChatBridgeServer

/** Dependency registrations owned by the runtime module. */
val runtimeModule = module {
    single<ProotCommandBuilder> { ProotCommandBuilder(environmentResolver = get(), logger = get()) }

    single<BackgroundTaskRegistry> { BackgroundTaskRegistry() }

    single<DistroConfigurator> {
        DistroConfigurator(
            pathManager = get(),
            hostBridge = get(),
            assetSynchronizer = get(),
            logger = get(),
        )
    }

    single<ElfInspector> { ElfInspector() }

    single<EnvironmentResolver> { EnvironmentResolver() }

    single<FtpServiceManager> {
        FtpServiceManager(
            context = get(),
            linuxRuntime = get(),
            preferences = get(),
        )
    }

    single<LinuxEnvironmentManager> { LinuxEnvironmentManager(linuxRuntime = get(), runtimePreferences = get()) }

    single<LinuxRuntimeImpl> {
        LinuxRuntimeImpl(
            pathManager = get(),
            prootInstaller = get(),
            rootfsInstaller = get(),
            prootCommandBuilder = get(),
            ptyManager = get(),
            shellExecutor = get(),
            healthChecker = get(),
            processRegistry = get(),
            settingsDataStore = get(),
            storageMountBindingRepository = get(),
            hostBridge = get(),
            distroConfigurator = get(),
            logger = get(),
        )
    }

    single<LocalLlmManager> {
        LocalLlmManager(
            context = get(),
            pathManager = get(),
            linuxRuntime = get(),
            fileDownloader = get(),
            serviceLauncher = get(),
        )
    }

    single<RuntimeHealthChecker> {
        RuntimeHealthChecker(
            pathManager = get(),
            prootCommandBuilder = get(),
            shellExecutor = get(),
        )
    }

    single<RuntimePathManager> { RuntimePathManager(context = get(), rootfsValidator = get()) }

    single<SshServiceManager> {
        SshServiceManager(
            context = get(),
            linuxRuntime = get(),
            preferences = get(),
            serviceLauncher = get(),
        )
    }

    single<StorageManager> {
        StorageManager(
            context = get(),
            pathManager = get(),
            runtime = lazy { get<LinuxRuntime>() },
        )
    }

    single<WorkspaceFileService> { WorkspaceFileService(pathManager = get(), workspaceRepository = get()) }

    single<WorkspaceManager> {
        WorkspaceManager(
            context = get(),
            pathManager = get(),
            workspaceDao = get(),
            fileService = get(),
            linuxRuntime = lazy { get<LinuxRuntime>() },
            projectTemplateEngine = get(),
            buildScriptRepository = lazy { get<BuildScriptRepository>() },
        )
    }

    single<AndroidAppManager> {
        AndroidAppManager(
            context = get(),
            privilegeManager = get(),
            repository = get(),
        )
    }

    single<HostBridge> {
        HostBridge(
            context = get(),
            logger = get(),
            privilegeManager = get(),
            embeddedAdbManager = get(),
            pathManager = get(),
        )
    }

    single<EmbeddedAdbManager> {
        EmbeddedAdbManager(
            context = get(),
            preferences = get(),
            pathManager = get(),
        )
    }

    single<WorkshopSigningManager> {
        WorkshopSigningManager(
            context = get(),
            linuxRuntime = get(),
            pathManager = get(),
            preferences = get(),
            assetSynchronizer = get(),
        )
    }

    single<WorkspaceBuildRunner> {
        WorkspaceBuildRunner(
            context = get(),
            linuxRuntime = get(),
            embeddedAdbManager = get(),
            assetSynchronizer = get(),
            runtimePreferences = get(),
            workshopPreferences = get(),
            buildScriptRepository = get(),
            signingManager = get(),
            logger = get(),
        )
    }

    single<EnvironmentDoctor> { EnvironmentDoctor(context = get(), linuxRuntime = get()) }

    single<EnvironmentRepairer> { EnvironmentRepairer(linuxRuntime = get(), environmentDoctor = get()) }

    single<GuiAccessibilityEnabler> { GuiAccessibilityEnabler(context = get(), privilegeManager = get()) }

    single<HostGuiController> {
        HostGuiController(
            context = get(),
            privilegeManager = get(),
            toolkit = get(),
            hud = get(),
        )
    }

    single<HostGuiToolkit> {
        HostGuiToolkit(
            context = get(),
            privilegeManager = get(),
            accessibilityEnabler = get(),
        )
    }

    single<WorkflowGuiHudBridge> { WorkflowGuiHudBridge() }

    single<PrivilegeManager> {
        PrivilegeManager(
            context = get(),
            settingsDataStore = get(),
            logger = get(),
            shizukuHostServiceClient = get(),
        )
    }

    // 虚拟屏门面：首次 get 时把 ShellRunner 装配进 ShowerEnvironment（showerclient 注入点）
    single<VirtualDisplayCoordinator> {
        VirtualDisplayCoordinator(
            context = get(),
            privilegeManager = get(),
            logger = get(),
        )
    }

    // 虚拟屏 GUI 原语执行器：复用 GuiPrimitive，定向到指定会话的 Shower 虚拟屏
    single<VirtualScreenToolkit> {
        VirtualScreenToolkit(
            context = get(),
            coordinator = get(),
        )
    }

    single<ShizukuHostServiceClient> { ShizukuHostServiceClient(context = get()) }

    single<ShizukuSystemApis> { ShizukuSystemApis(context = get()) }

    single<ProotInstaller> {
        ProotInstaller(
            pathManager = get(),
            elfInspector = get(),
            logger = get(),
        )
    }

    single<ScriptPtyManager> { ScriptPtyManager() }

    single<NativePtyManager> { NativePtyManager(scriptFallback = get()) }

    single<LxcImagesClient> { LxcImagesClient(http = get(), logger = get()) }

    single<OciRegistryClient> { OciRegistryClient(http = get(), logger = get()) }

    single<RootfsInstaller> {
        RootfsInstaller(
            pathManager = get(),
            tarStreamExtractor = get(),
            rootfsValidator = get(),
            logger = get(),
            ociRegistryClient = get(),
            lxcImagesClient = get(),
        )
    }

    single<RootfsValidator> { RootfsValidator(elfInspector = get()) }

    single<RuntimeAssetSynchronizer> { RuntimeAssetSynchronizer(context = get(), pathManager = get()) }

    single<LocalServiceLauncherImpl> { LocalServiceLauncherImpl(linuxRuntime = get()) }

    single<ProcessRegistryImpl> { ProcessRegistryImpl(pathManager = get(), prootCommandBuilder = get()) }

    single<ProcessShellExecutor> { ProcessShellExecutor(pathManager = get()) }

    single<TerminalSessionClientRouter> { TerminalSessionClientRouter() }

    single<TerminalSessionManager> {
        TerminalSessionManager(
            linuxRuntime = get(),
            terminalSessionDao = get(),
            sessionClientRouter = get(),
        )
    }

    single<WebChatBridgeServer> {
        WebChatBridgeServer(
            context = get(),
            sessions = get(),
            models = get(),
            quickPhrases = get(),
            workspaces = get(),
            workspaceManager = get(),
            workspaceFiles = get(),
            agentGateway = get(),
            logger = get(),
        )
    }

    single<TarStreamExtractor> { TarStreamExtractor(logger = get()) }
}
