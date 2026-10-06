<p align="center">
  <img src="app/src/main/res/drawable/tianxuan_logo.webp" width="96" alt="天玄 Logo" />
</p>

<h1 align="center">天玄 · TianXuan</h1>

<p align="center"><strong>掌中天玄，七星为枢。</strong></p>

<p align="center">
  Android 无 Root Linux Runtime · AI Agent Harness · 可视化自动化工作流 · 原生 PTY 终端 · 移动开发工作区
</p>

<p align="center">
  <code>v0.15.1 stable</code> · <code>Android 10+ (API 29+)</code> · <code>arm64-v8a</code> · <code>Kotlin 2.4.10</code> · <code>Jetpack Compose</code>
</p>

<p align="center">
  <a href="https://github.com/wkbin/tianxuan/releases">Releases</a> ·
  <a href="https://github.com/wkbin/tianxuan/issues">Issues</a> ·
  <strong>简体中文</strong> · <a href="README_EN.md">English</a>
</p>

---

## 🌌 何为天玄

《易·坤卦·文言》："夫玄黄者，天地之杂也，**天玄而地黄**。"《说文》："玄，幽远也。黑而有赤色者为玄。"

**玄**是天的本色——不是纯黑，而是黑中透赤的深空之色，黎明前东方天际那种将明未明的幽蓝。天玄取的是**御**：不以深渊容纳万物，而以天道统御运行。

《史记·天官书》："**斗为帝车，运于中央，临制四乡。分阴阳，建四时，均五行，移节度，定诸纪，皆系于斗。**"

斗为帝车，七星即七根车辕。天玄以**北斗为纲、二十八宿为目、四象为域、三垣为藏**，把这套真实的中国古代天文坐标写进代码本身——而不只写在宣传语里。

| 层 | 义 | 落地 |
|---|---|---|
| **北斗七星** | 斗为帝车，七条主干能力 | 天枢（智枢）· 天璇（智坊）· 天玑（终端）· 天权（工作流）· 玉衡（版本）· 开阳（浏览器）· 摇光（设置） |
| **二十八宿** | 二十八个星区，日月五行的坐标刻度 | 东方青龙＝智能体引擎层 · 北方玄武＝运行时底层 · 西方白虎＝工具与集成层 · 南方朱雀＝表现与体验层 |
| **三垣** | 紫微／太微／天市，天帝居所、朝廷政务、天上街市 | 记忆库三层：紫微垣＝核心记忆 · 太微垣＝工作记忆 · 天市垣＝检索记忆 |

> 二十八宿距度依《淮南子·天文训》「星分度」，四象合计 365 度，箕宿带四分一，得 **365¼ 度**，合周天。每宿绑定一个底层构件与一项可观测指标，日志与诊断面板按宿分区，呈"星图"式可观测性。

天玄在 Android 的应用沙盒与权限边界内，构筑一方**可运行、可观测、可恢复、可演进**的 Linux 与 AI 自动化环境。它不是给大模型套一层聊天界面，也不只是终端模拟器。天玄让 Agent、MCP 工具、Linux 进程、原生 PTY、浏览器、Git 与项目工作区共享执行上下文；可视化工作流则把一次对话扩展为可持久化、可审批、可定时的自动化任务。你可以从一句自然语言意图出发，最终得到真实文件、运行中的进程、经过验证的代码或可安装的 Android / Flutter 构建产物。

> 掌中天玄，七星为枢。

```text
人的意图 ─→ Agent 规划 ─→ Linux / 工具 / MCP / 浏览器 / 宿主能力 ─→ 验证与交付
                  ↑                                                   ↓
                  └──────────── 未完成则继续，必要时审批 ──────────────┘

持久化工作流 ─→ 条件分支 / 并行节点 / 定时计划 / 后台运行 / 人工审批
```

---

## ⚡ 核心能力

| 领域 | 能力概览 |
| :--- | :--- |
| **Linux Runtime** | 基于 **PRoot** 在无 Root Android 上运行 ARM64 Linux；从 OCI Registry 安装并校验 RootFS，支持多发行版共存、切换、回滚、持久化目录绑定与 Android 共享存储挂载。 |
| **Agent Harness** | 支持 OpenAI-compatible Chat Completions、OpenAI Responses API 与 Anthropic Messages API；统一处理流式正文、reasoning、函数工具调用、视觉输入、token usage 与上下文压缩。 |
| **可靠性与恢复** | 持久化会话、任务计划、语义记忆、自动/手动上下文压缩、大工具输出落盘，以及中断后的恢复；支持 SessionFork 对话回退、文件 checkpoint、外部修改冲突检测与最近一次 rewind 撤销。 |
| **子智能体协作** | 支持多子智能体调度、结构化结果裁定、token 预算、写路径租约和超限结果分页落盘，降低并行写入冲突与上下文膨胀。 |
| **可视化工作流** | 持久化 Kotlin DAG、条件边与并行节点；可组合 Bash、后台进程、Agent、Subagent、构建、宿主动作和人工审批，并支持运行历史、后台执行、通知审批及 WorkManager 一次性/间隔/每日计划。 |
| **原生 PTY 终端** | JNI 后端通过 `openpty + fork + setsid + TIOCSCTTY + execve` 创建真实控制终端，产物为 `libpty_native.so`；配合 Termux terminal-emulator / TerminalView 支持作业控制、Ctrl+C、窗口尺寸变化和多会话，原生后端不可用时回退至 `script` 通道。 |
| **移动工作区与构建** | 创建空项目、导入本地 ZIP（含 Zip Slip 防护）或克隆 GitHub 仓库；提供代码树、行级 Diff、沙箱内 Gradle / Flutter 后台构建、APK 签名与安装。 |
| **端侧 Git 工作台** | 基于 JGit 直接管理宿主工作区，支持改动、暂存、提交、分支、提交图、标签、push/pull、推送预览、AI 提交说明与加密 HTTPS Token。 |
| **浏览器与 Web 自动化** | 内置多 Tab WebView 池与 Browser MCP；支持页面 Hook、CDP 断点、Worker 级 Fetch 拦截、网络时间线与调试状态展示。 |
| **MCP 与工具生态** | 内置 Browser、SQLite、Git、APK 审计、CodeGraph、WebSearch 等预设；支持 STDIO、常用 Streamable HTTP 请求-响应与 legacy SSE，远程服务可使用 OAuth Authorization Code + PKCE。 |
| **无线 ADB 与宿主自动化** | mDNS 发现、通知栏配对码输入、Android / PRoot 日志、设备体检与 Intent 诊断；经用户授权后，可通过无线 ADB、Shizuku、Root 或无障碍通道执行不同级别的宿主与 GUI 自动化。 |
| **端侧协作** | 提供全局悬浮助手、带临时 PIN 的局域网 WebChat、FTP 文件传输，以及 FGS、WakeLock 与 Wi-Fi Lock 长任务保活能力。 |

> 当前开发分支在 `v0.15.1` 基础上继续增强 Harness 恢复、MCP OAuth、能力按需发现和后台工作流。稳定版功能与安装包请以 [Releases](https://github.com/wkbin/tianxuan/releases) 页面为准。

---

## 🧠 Agent 可靠性与安全

天玄把 Agent 视为会长期运行并真实修改工作区的执行系统，而不只是一次性的问答接口：

- **分级工具审批**：危险操作可要求确认，会话级授权与全局策略分离。
- **可恢复会话**：流式输出、工具调用、计划、记忆和执行历史持久化，中断后可继续推进。
- **上下文治理**：支持自动/手动压缩、溢出重放、token 预算及大型工具输出落盘。
- **安全回退**：每轮前创建文件 checkpoint；rewind 时检测会话外修改并报告冲突，满足条件时可撤销最近一次文件恢复。
- **受控并行**：子智能体通过写路径租约、结构化结果与调度约束降低覆盖彼此改动的风险。
- **凭据边界**：模型密钥、Git Token 与 OAuth Token 使用应用侧安全存储；敏感信息不应写入工作区或提交到仓库。

---

## 🚀 快速上手

### 1. 安装

在 **ARM64、Android 10+**（推荐 Android 12+）设备上安装 [最新 Release APK](https://github.com/wkbin/tianxuan/releases)。Release 页面同时提供构建产物与 SHA-256，建议安装前校验。

### 2. 初始化 Linux

首次启动后跟随「启程向导」安装 RootFS。默认推荐 Ubuntu 24.04 LTS，也可以选择其他受支持发行版。RootFS 不内置于 APK，首次安装需要网络连接与足够的存储空间。

### 3. 配置模型与工具

在设置中添加模型服务与 API Key。可连接 DeepSeek、Claude、OpenAI、SiliconFlow 等云端服务，也可连接沙箱或局域网内的 OpenAI-compatible 本地推理端点，例如 llama.cpp / Ollama 类型服务。

按需启用 MCP、无线 ADB、悬浮窗、通知、安装 APK、Shizuku 或无障碍权限；未使用相关能力时无需授予。

### 4. 创建工作区

在智坊中新建项目，或从 GitHub / 本地 ZIP 导入已有工程。随后可在智枢中描述目标，让 Agent 读写代码、调用工具、运行测试并交付构建产物；也可以把重复任务编排为可视化工作流。

### 5. 常用入口

- **智枢**：Agent 对话、计划、工具调用、子智能体与代码变更。
- **终端**：进入当前 Linux 发行版的真实 PTY 会话。
- **智坊**：项目管理、源码浏览、Diff、构建与 APK 交付。
- **工作流**：搭建 DAG、运行后台任务、设置审批与计划。
- **Git**：管理改动、分支、提交、标签和远程同步。
- **浏览器**：网页访问、调试、Hook 与 Browser MCP 自动化。
- **无线 ADB**：配对设备、查看日志并执行授权范围内的诊断。

> 长时间运行 Agent、构建或工作流时，建议允许通知并将天玄加入系统电池优化白名单。Android 的后台限制仍可能影响任务时效。

---

## 🐧 支持的 Linux 发行版

当前提供 10 种 ARM64 RootFS：

- Ubuntu 24.04 LTS（默认）
- Debian 12
- Kali Rolling
- Arch Rolling
- Fedora 40
- Alpine 3.19
- AlmaLinux 9
- Rocky Linux 9
- openSUSE Tumbleweed
- Manjaro Rolling

RootFS 通过 OCI 镜像获取，不随 APK 分发。不同发行版的软件可用性与兼容性取决于其 ARM64 仓库及 PRoot 用户态环境。

---

## 🔌 模型与工具协议

### 模型 API

- OpenAI-compatible Chat Completions
- OpenAI Responses API
- Anthropic Messages API
- SSE 流式正文与 reasoning
- Native function calling 与 JSON 文本工具协议
- 视觉输入、usage 统计、模型级上下文与压缩预算

### MCP

- STDIO JSON-RPC
- 常用 Streamable HTTP 请求-响应
- Legacy SSE
- OAuth Authorization Code + PKCE、Token 刷新与回调
- 内置 Browser、SQLite、Git、APK 审计、CodeGraph、WebSearch 预设
- 能力按需发现与延迟连接

---

## 🛠️ 从源码构建

### 标准 APK 构建要求

- **JDK 17**（项目与 CI 基线，推荐 Android Studio JBR）
- **Android SDK**：compileSdk 37 / targetSdk 37 / minSdk 29
- **Gradle Wrapper**：9.7.0
- **Android Gradle Plugin**：9.3.1
- **Kotlin**：2.4.10
- 完整 Git checkout，包括仓库内的本地 Maven AAR 与预编译 ARM64 原生库

普通 APK 构建直接使用仓库内的预编译 native 制品；只有重新编译 PTY 等原生组件时，才额外需要 **Android NDK 30.0.15729638、CMake 3.22.1 与 Ninja**。

### Windows

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"

.\gradlew.bat architectureCheck --console=plain
.\gradlew.bat test --console=plain
.\gradlew.bat assembleDebug --console=plain
```

### macOS / Linux

```bash
export JAVA_HOME="/path/to/jdk-17"

./gradlew architectureCheck --console=plain
./gradlew test --console=plain
./gradlew assembleDebug --console=plain
```

Debug APK 默认输出到：

```text
app/build/outputs/apk/debug/tianxuan-v0.15.1-debug.apk
```

### 可选步骤

正常完整检出后无需手动准备 PRoot。仅当 `app/src/main/jniLibs/arm64-v8a/libproot*.so` 缺失、校验失败，或需要刷新官方 PRoot 制品时运行：

```powershell
.\tools\prepare-proot-runtime.ps1
```

设置 `TIANXUAN_DEV_BUILD=1` 可构建包名为 `top.wkbin.tianxuan.dev` 的 **TianXuanDev** 预览版，与正式包 `top.wkbin.tianxuan` 独立共存：

```powershell
$env:TIANXUAN_DEV_BUILD="1"
.\gradlew.bat assembleDebug --console=plain
```

更多构建、测试、签名与诊断命令见 [docs/COMMANDS.md](docs/COMMANDS.md)。

---

## 📐 模块拓扑

```text
TianXuan/
├── app/                  # 宿主壳、Koin 装配、Manifest、JNI 与前台服务
├── baselineprofile/      # Baseline Profile 与启动性能基准
├── core/
│   ├── model/            # 纯 Kotlin 领域模型
│   ├── common/           # 调度器、日志与公共基础设施
│   ├── database/         # Room：会话、消息、记忆、工作流、OAuth、执行历史
│   ├── datastore/        # DataStore：用户偏好、外观、挂载与引导状态
│   ├── network/          # OkHttp、SSE 与网络策略
│   ├── security/         # 密钥、Token 与安全存储
│   └── browser/          # 浏览器共享模型与协议
├── runtime/              # PRoot、RootFS、PTY、进程、存储与工作区运行时
│   └── browser/          # WebView 池、Hook、CDP、网络时间线与 Browser MCP
├── project-template/     # 项目模板、动态表单与物化引擎
├── harness/              # Agent 循环、Provider、工具、审批、子智能体与 MCP
├── tools/                # Registry、Recipe、安装事务、依赖与 Provider 仓储
└── feature/              # Jetpack Compose 业务特性
    ├── theme/            # 主题与视觉系统
    ├── components/       # 通用组件、图标与 Spotlight 引导
    ├── navigation/       # Navigation3 路由与入口
    ├── home/             # 首页状态与诊断
    ├── chat/             # 智枢对话、计划、Diff 与悬浮助手
    ├── terminal/         # 终端 UI 与多会话
    ├── workspace/        # 智坊、源码浏览、构建与交付
    ├── browser/          # 内置浏览器 UI
    ├── workflow/         # DAG 编辑器、运行、审批与计划
    ├── git/              # JGit 工作台
    ├── settings/         # 模型、MCP、ADB、插件与系统设置
    ├── developer/        # 开发者沙箱与底层诊断
    ├── custom_iteration/ # 自定义迭代能力
    └── onboarding/       # 首次启动与 RootFS 初始化
```

Android 应用业务层以 Kotlin 与 Jetpack Compose 为主；底层还包含 C/JNI、Python、JavaScript、Shell 与 TypeScript 组件。

---

## 📚 文档导航

- 🧭 [AI 语义导航总览](docs/AI_NAVIGATION.md)
- 🏗️ [系统架构与模块拓扑](docs/ARCHITECTURE.md)
- ⏱️ [主要运行时调用链](docs/EXECUTION_TRACES.md)
- 📜 [架构与 UI 设计铁律](docs/ARCHITECTURE_RULES.md)
- 🔀 [可视化工作流](docs/WORKFLOW.md)
- 🌐 [内置浏览器与 Hook 引擎](docs/BROWSER_DESIGN.md)
- 🔌 [插件生态开发指南](docs/PLUGIN_DEVELOPMENT_GUIDELINES.md)
- 📦 [Android 离线插件说明](docs/ANDROID_OFFLINE_PLUGIN.md)
- 💾 [存储管理](docs/STORAGE_MANAGEMENT.md)
- 🧱 [沙箱后端 ADR](docs/ADR_SANDBOX_BACKEND.md)
- 🩺 [已知问题](docs/KNOWN_ISSUES.md)
- 🧪 [构建、测试与调试命令](docs/COMMANDS.md)

---

## ⚠️ 权限、边界与已知限制

- **仅支持 ARM64**：当前只适配 `arm64-v8a`，其他 ABI 不在支持范围内。
- **PRoot 不是虚拟机**：它通过用户态系统调用拦截与路径重写提供 Linux 环境，不具备 KVM、Root 特权或内核模块加载能力。
- **首次安装依赖网络**：RootFS 不随 APK 打包，安装时需要下载 OCI 镜像并占用额外存储空间。
- **宿主能力取决于授权**：无线 ADB、Shizuku、Root 与无障碍提供的权限等级不同；高级宿主动作不会在未授权时自动获得能力。
- **后台任务受 Android 限制**：前台服务与 WorkManager 可提高可靠性，但 Doze、厂商省电策略及新版 Android 的前台服务时限仍可能造成延迟或中断。
- **局域网服务需谨慎开启**：WebChat 与 FTP 只应在可信网络中主动启用，并在使用后关闭。
- **兼容性因设备而异**：复杂 TUI、特定软键盘组合键和重型 C/C++ 交叉编译会受到设备性能、内存与 PRoot 兼容性的影响。
- **保护凭据**：请妥善保存模型 API Key、Git Token 与远程服务凭据，切勿提交到公开仓库。

---

## 🤝 参与贡献

欢迎提交 [Issue](https://github.com/wkbin/tianxuan/issues)、Pull Request，或分享你的真机使用记录。开始修改前建议先阅读 [AI_NAVIGATION.md](docs/AI_NAVIGATION.md) 与对应模块文档，并运行架构检查和相关测试。

---

## 📜 注脚

> 须弥纳于芥子，天玄纳于掌中。

限制从未真正消失；但自由可以来自身处限制之中，仍有能力去构筑、去验证属于自己的世界。
