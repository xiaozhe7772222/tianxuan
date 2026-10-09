# 我把 Linux 沙箱、大模型 Agent 和编译器全塞进了安卓手机：天玄 (TianXuan) 开源了！

> **“须弥纳于芥子，天玄纳于掌中。”**  
> 在 Android 严格受限的沙盒与权限边界内，构筑一方可运行、可观测、可自愈、可演进的掌上 Linux 世界。

---

![配图 01：头图 / 封面图](https://your-image-url/tianxuan-cover.png)
> 💡 *【建议配图 01】：天玄 Logo 配合科技感暗黑背景的头图，或者真机展示天玄启动界面的精美海报图。*

---

## 📱 01. 楔子：你的旗舰机，算力过剩了吗？

看看你手上的主力机：**骁龙 8 Gen 3/4 或天玑 9300** 芯片、**16GB 甚至 24GB LPDDR5X 内存**、UFS 4.0 极速存储……论单核算力、多核并发与内存吞吐，现在的旗舰机性能完全不输给几年前的轻薄笔记本。

然而现实往往很讽刺：
- 这么强的移动算力怪兽，绝大多数时候只是在刷短视频、刷信息流、玩两局手游；
- PC 端的 AI 研发工具如火如荼（Cursor、Claude Code、Windsurf、Cline），而手机上的各种“AI 助手”，绝大部分依然只是千篇一律的 **“API 聊天套壳”**。大模型输出了大段天花乱坠的代码，既落不了盘，也跑不起来，更无法进行环境自我验证；
- 移动端老牌神器 Termux 确实硬核，但它是一个纯粹的终端，缺少大模型因果链驱动循环、缺少现代移动 IDE 工作区、代码 Diff 和可视化任务看板。

**难道在手机上，大模型就只能当个“陪聊工具人”？**

为了打破这个现状，我们开发并正式开源了这一款面向移动端的全功能 AI 研发运行时——**天玄 (TianXuan)**。

它不搞空头套壳，而是直接把 **免 Root Linux 系统、智能体引擎 (Agent Harness)、C/JNI 原生 PTY 终端与项目工作区** 深度融合。

- 🌐 **GitHub 仓库**：[https://github.com/wkbin/tianxuan](https://github.com/wkbin/tianxuan)
- 📦 **技术规格**：`Android 10+ (SDK 29+)` · 纯 `arm64-v8a` · `Kotlin 2.4` · `Jetpack Compose` · `Material 3 Expressive`

---

## 🌌 02. 何为天玄：不仅仅是终端，更是因果闭环

《列子·汤问》云：“渤海之东……其中有大壑焉，实惟无底之谷，其下无底，名曰归墟。八纮九野之水，天汉之流，莫不注之，而无增无减焉。”

**天玄**取意于此：它让大模型、系统工具、Linux 沙箱与项目工程共享同一个**同构执行上下文**。

用户的每一句自然语言指令，都将在天玄中历经真正的工程闭环：

```text
人的意图 (Intent) ─► 步骤规划 (TaskPlanCard) ─► Linux / MCP / 浏览器工具分派
                             ▲                               │
                             │                               ▼
                      失败自动分析修正 ◄────────────── 真实环境运行验证 (Verification)
```

![配图 02：天玄整体界面与因果闭环演示](https://your-image-url/tianxuan-overview.png)
> 💡 *【建议配图 02】：天玄的主界面或者双栏/分屏视图，展示左侧大模型对话拆解任务、右侧终端或代码浏览的视觉全貌。*

---

## ⚡ 03. 核心硬核特性技术拆解

### 1. 免 Root 用户态 Linux 沙箱（10+ 主流发行版）
手机没 Root 也能跑完整 Linux？没错！
- **PRoot 用户态拦截**：底层通过 `ptrace` 劫持系统调用并做路径重写，无需任何 Root 权限即可提供完整的 Linux 根文件系统；
- **全系主流发行版**：支持 Ubuntu 24.04、Debian 12、Kali、Arch Linux、Alpine、Fedora 等一键拉取与无缝切换；
- **OCI Registry 校验与双向挂载**：支持安全的镜像哈希校验；可直接将手机 `/sdcard/Download` 等目录安全挂载进沙箱，外部下载的文件沙箱秒读，沙箱构建出的产物宿主秒存。

![配图 03：Linux 沙箱环境与监控面板](https://your-image-url/proot-dashboard.png)
> 💡 *【建议配图 03】：沙箱管理界面截屏，展示 Ubuntu 等发行版的运行状态、CPU/内存占用指示器。*

---

### 2. 真正的自主 Agent 智能体循环（Harness Engine）
天玄内置了一套严肃的自研智能体驱动引擎：
- **主流协议通吃**：深度兼容 OpenAI 标准接口与 Anthropic Messages 协议（完美适配 DeepSeek-R1、Claude 3.7、SiliconFlow 等中转平台）；
- **思考链深度感知**：完整解析 `reasoning_content`，大模型缜密的思考推理过程流式动态展开；
- **动态 TaskPlanCard**：自动解析模型输出的 `- [ ]` 步骤清单，转化为带有触觉反馈的动态进度卡片，当前干到哪一步一目了然；
- **原子工具矩阵**：大模型拥有查看目录、文件读写、精准局部替换 (Edit)、代码符号搜索 (Grep/Find)、沙箱 Shell 命令执行等全套工具；
- **沙箱离线小模型**：支持在沙箱内部署 `llama.cpp`，离线运行 Qwen2.5-Coder、Llama-3 等 GGUF 编程小模型，断网也能自主写代码！

![配图 04：智能体思考链与 TaskPlanCard 任务执行](https://your-image-url/agent-task-plan.png)
> 💡 *【建议配图 04】：智枢对话界面截屏，展现 DeepSeek/Claude 的思考折叠卡片、动态 TaskPlanCard 步骤条和工具调用卡片。*

---

### 3. 会话派生 (SessionFork) 与磁盘级快照安全网
让大模型在手机上直接改写代码，搞砸了怎么办？
- **SessionFork 会话树派生**：支持像 Git 创建分支一样随时从某一轮对话派生分支，或者一键「撤回到此轮」（Rewind）；
- **Checkpoints 磁盘快照**：在智能体每轮开始修改前，自动对工作区创建磁盘级状态快照。一旦 Agent 写偏或改出重大故障，毫秒级无损回滚，没有任何后顾之忧。

---

### 4. 原生 C/JNI PTY 终端（Matrix Terminal）
绝非市场上某些简陋的 `TextView` 模拟输入框！
- **C 语言底层桥接**：自研 `libtianxuan_pty.so`，基于 POSIX `openpty`/`forkpty` 直连 Linux 子进程生命周期；
- **增量 ANSI/VT100 状态机**：支持流式色彩高亮、光标精准定位与全屏 TUI 交互；
- **定制触觉按键条 (ExtraKeys)**：专为移动端设计的辅助键盘，提供带振动反馈的 `Ctrl`、`Alt`、`Tab`、`Esc`、方向键以及历史命令轮盘，在手机上敲 Vim / Nano 同样顺畅。

![配图 05：原生 PTY 终端与触觉按键条](https://your-image-url/pty-terminal.png)
> 💡 *【建议配图 05】：全屏终端截屏，展示彩色 neofetch、htop 运行界面，以及底部专属的 ExtraKeys 辅助虚拟按键条。*

---

### 5. 掌上移动工作区与真机本地编译构建
- **多元导入**：支持一键 Clone GitHub 仓库（带实时流式下载进度百分比）、本地 ZIP 安全解压（内置严格防 Zip-Slip 路径穿越校验）；
- **专业级代码查看**：语法高亮文件树与专业级行级 Diff 对比；
- **沙箱直接构建 APK**：你甚至可以在沙箱内配好 JDK、Android SDK 或 Flutter，手机本地敲击 `./gradlew assembleDebug`，构建产出的 APK 可以直接拉起系统安装器安装上机！

![配图 06：工程代码行级 Diff 比对与 APK 构建交付](https://your-image-url/workspace-diff-build.png)
> 💡 *【建议配图 06】：工作区界面截屏，展示红绿相间的清晰 Diff 对比图，或者构建完成直接点击安装 APK 的交付卡片。*

---

### 6. 内置浏览器、CDP 断点与 Worker 级 Hook
天玄内置了供大模型与开发者使用的独立 Browser 模块：
- **In-process Browser MCP 服务**：大模型可以直接打开网页、截取 DOM 树、截屏分析页面视觉渲染；
- **深度逆向与调试**：支持脚本注入式 Hook、CDP 断点断流、Worker 级别的 Fetch 流量拦截，配合可视化的网络请求时间线，网页抓取与动态逆向尽在掌握。

![配图 07：内置浏览器调试面板与网络请求时间线](https://your-image-url/browser-devtools.png)
> 💡 *【建议配图 07】：内置浏览器开启状态下的截图，展示网络请求瀑布流或脚本 Hook 拦截面板。*

---

### 7. 无线 ADB 秒级免切屏诊断
Android 开发者专属的提效神器：
- **通知栏免切屏配对**：以前配对无线 ADB，切屏去查配对码再切回来端口经常失效。天玄支持在**系统通知栏直接输入配对码**，秒级完成本机或局域网无线配对；
- **mDNS 自动发现**：局域网调试端口自动嗅探；
- **全栈日志流**：PRoot 系统日志与 Android 系统 `logcat` 日志混合实时过滤，系统 Intent 一键诊断。

![配图 08：通知栏免切屏无线 ADB 配对与诊断](https://your-image-url/wireless-adb.png)
> 💡 *【建议配图 08】：截取下拉通知栏直接输入无线 ADB 配对码，或者 ADB 日志工作台界面的清晰截图。*

---

### 8. 桌面全局悬浮小窗（智枢随行）
不仅局限在 App 内部！天玄支持**系统级前台悬浮窗**。当你正在浏览器查资料、在 GitHub 手机端看 Issue、或者在玩其他 App 时，随时贴边呼出智枢小窗，一边看屏幕一边让 AI 帮你跑脚本查数据，实现真·跨应用多任务并发。

![配图 09：桌面全局悬浮小窗跨应用协同](https://your-image-url/floating-window.png)
> 💡 *【建议配图 09】：手机桌面或其他应用界面上，悬浮着天玄“智枢小窗”进行问答与指令交互的场景。*

---

## 📐 04. 架构设计与工程之美

天玄不仅仅功能极客，在 Android 工程实现上也严格遵循 Clean Architecture 与工业级模块化规范：

```text
TianXuan/
├── app/                  # 宿主壳工程：JNI C/C++ 桥接、Koin 装配、前台保活 Service、悬浮窗服务
├── core/
│   ├── model/           # 纯 Kotlin 数据模型 (Pure Kotlin，严禁任何平台与框架依赖)
│   ├── database/        # Room 数据库：会话树、消息因果链、Checkpoints 快照、执行审计
│   ├── datastore/       # Jetpack DataStore：用户设置、挂载点配置
│   └── network/         # OkHttp 客户端、响应式 SSE 流式解析器
├── runtime/              # Linux 沙箱核心：PRoot 挂载、进程生命周期托管、PTY 会话、工作区构建
│   └── browser/         # 内置浏览器：WebView 实例池、CDP 拦截器、In-process Browser MCP
├── harness/              # 智能体引擎：Agent 循环、工具调度分派、子智能体协作、会话派生
├── tools/                # 生态中心：插件 Registry、Recipe 安装事务、依赖解析
└── feature/              # Jetpack Compose 业务特性层 (M3 Expressive 设计规范)
    ├── chat/            # 智枢对话与 Diff / TaskPlan 渲染
    ├── terminal/        # 原生终端与触觉按键条
    ├── workspace/       # 工程浏览、Diff 对比与 APK 交付
    └── settings/        # 模型配置、无线 ADB 诊断工作台
```

- **纯正现代技术栈**：基于 `Kotlin 2.4` + `Jetpack Compose` 构建，界面完全采用 **Material 3 Expressive**（玄统 / 澄明主题）设计规范；
- **前台保活与稳定性**：Foreground Service (FGS) 搭配 CPU 唤醒锁与 Wi-Fi 锁，即便退到后台或息屏，大型编译构建长任务也绝不掉线中断；
- **严苛架构红线**：禁止平台侵入底层 Model，严格的 UI/ViewModel 单向数据流，保证数万行代码的可扩展性与测试覆盖。

---

## 💡 05. 那些只有天玄能搞定的“极客场景”

1. **地铁通勤途中，突发生产环境 Bug？**  
   不用掏电脑，在手机上打开天玄，让智枢从 GitHub Clone 报错仓库，AI 自动定位并改写代码，弹出行级 Diff 确认无误，沙箱内执行测试通过，直接 Git Push 提交热修复 PR。
2. **长途差旅的高铁/飞机上，断网没信号？**  
   在沙箱内通过 `llama.cpp` 跑本地端侧模型，依然可以自主写 Python、Bash 脚本、处理离线文件与数据。
3. **日常移动端逆向与自动化？**  
   内置无线 ADB 监控当前应用，拉起内置浏览器跑脚本 Hook 抓取目标动态数据，直接输出结构化 CSV 报表。

---

## 🚀 06. 掌中天玄，七星为枢

天玄现已全面开源。我们相信：**限制从未真正消失；但自由可以来自——身处限制之中，仍有能力去构筑、去验证属于自己的世界。**

不管你是：
- 想一探 **端侧 AI 智能体与移动 OS 深度结合** 的极客探索者；
- 苦于移动端开发能力受限、想寻找 **掌上移动工作站** 的程序员；
- 还是想深入学习 **Jetpack Compose + JNI C/C++ + Linux PRoot** 工业级架构的 Android 开发者。

天玄都为你准备了一份热气腾腾、扎扎实实的代码库！

- ⭐️ **GitHub 源码直达**：[https://github.com/wkbin/tianxuan](https://github.com/wkbin/tianxuan)
- 📥 **最新 Release APK**：[https://github.com/wkbin/tianxuan/releases](https://github.com/wkbin/tianxuan/releases)

---

### 💬 互动时刻：
> 你觉得未来移动设备有可能承担我们 30% 甚至 50% 的轻度编程与运维工作吗？你在手机上折腾过最硬核的操作是什么？欢迎在评论区一起交流探讨！如果天玄对你有启发，别忘了去 GitHub 点个 ⭐️ **Star** 支持一下！
