# 🧭 天玄 (TianXuan / LinuxAIRuntime) — AI 导航入口

> 本文件为导航入口，默认载入。全部细节（技术栈 / 模块拓扑 / 调用链路 / 文件索引 / 铁律 / 命令）在 [`docs/AI_NAVIGATION.md`](docs/AI_NAVIGATION.md)，按需读取。

## 是什么

Android 无 Root 下用 PRoot 跑 Linux 多发行版沙箱 + AI Agent Harness + 原生 PTY 终端。Kotlin 2.4 / Compose / Koin / Room / Navigation3，仅 `arm64-v8a`。

## 模块

`app`(壳/装配/JNI) · `core`(model·common·database·datastore·network·security) · `runtime`(PRoot/RootFS/PTY/工作区) · `tools`(Registry/安装事务/Provider安全) · `harness`(Agent循环/MCP/子智能体) · `feature`(components·theme·home·chat·terminal·workspace·settings·developer·onboarding·custom\_iteration·navigation)

## 动手前先读

| 要做什么                  | 推荐阅读文档                                                                |
| :-------------------- | :-------------------------------------------------------------------- |
| **快速把握全局**            | [`docs/AI_NAVIGATION.md`](docs/AI_NAVIGATION.md)（语义导航总览）              |
| **写代码 / 改模块 / 理拓扑**   | [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)（架构与模块拓扑）               |
| **追踪数据流 / 调用链路**      | [`docs/EXECUTION_TRACES.md`](docs/EXECUTION_TRACES.md)（核心执行时序）        |
| **定位某个类 / 寻找文件**      | [`docs/FILE_INDEX.md`](docs/FILE_INDEX.md)（关键文件索引速查）                  |
| **UI/UX 设计系统与架构铁律**   | [`docs/ARCHITECTURE_RULES.md`](docs/ARCHITECTURE_RULES.md)（设计规范与避坑指南） |
| **构建 / 测试 / 打包 / 调试** | [`docs/COMMANDS.md`](docs/COMMANDS.md)（常用命令速查）                        |

