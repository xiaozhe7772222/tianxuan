# PR 说明：combine 吞更新导致的 UI 不刷新（4 处同源）+ 上游 5xx 未纳入瞬态重试

> **基线**：`wkbin/tianxuan` @ `f82a44f7`（Release v0.16.0）
> **改动**：3 个文件（2 改 + 1 新增测试），+117 / −20 行
> **核验方式**：每一条都用 `grep` 确认上游当前代码仍存在该缺陷，无推测。

---

## 一、combine 丢弃上游导致的 UI 不刷新（P0，4 处同源）

### 根因模式

```kotlin
combine(flowA, flowB, flowC, flowD) { a, _, _, _ -> a }   // 输出恒等于 a
    .distinctUntilChanged()                                // 去重
    .flatMapLatest { /* 重新投影 */ }
```

`combine` 的 lambda 确实**会**因任一上游发射而重跑（这点常被误解），但它输出恒等于 `flowA`；配合下游 `distinctUntilChanged()`，**其余上游的变化被完全吞掉**。

### 缺陷 1 · 分支列表不刷新

**文件**：`feature/chat/ChatViewModel.kt:256-261`（上游当前代码）

```kotlin
) { sessionId, _, _, _ -> sessionId }.mapLatest { sessionId ->
```

四个上游流中，后三个被丢弃：
- `branchMessageRevision`（消息更新）
- `_branchRefresh`（手动刷新计数）
- `branchEventRevision`（子智能体运行事件）

导致 `_branchRefresh++` 与子智能体进展**都无法触发重投影**。而紧邻的注释却写着：

> 「子智能体在独立 lane 中执行时主会话消息不变，用运行事件驱动分支重投影」

**注释声称的能力，实现根本做不到**（注释与实现两张皮）。

**修**：引入 `ProjectionKey` 把四个流压成可比较信号，任一上游变化都产生不同 key → 重新拉取分支。

### 缺陷 2 · 任务看板不刷新

**文件**：`feature/chat/ChatViewModel.kt:288`（上游当前代码）

```kotlin
combine(harnessLoop.currentSessionId, harnessLoop.status) { sessionId, _ -> sessionId }
```

一轮执行内状态会多次变化（工具往返、压缩、等待审批）。`status` 被吞后，看板进度**无法实时刷新**——而该状态流的注释正写着「以 currentSessionId + 运行状态为键重新读取：一轮执行内状态多次变化，借此近似实时刷新看板进度」。

**修**：以 `(sessionId, status)` 作为重投影信号。

### 缺陷 3 · 压缩快照不刷新

**文件**：`feature/chat/ChatViewModel.kt`、`activeCompaction`

上下文压缩恰好伴随状态切换（运行 → 压缩 → 运行），`status` 被吞导致**压缩完成后快照不更新**、提示横幅要等切换会话才出现。

**修**：同上。

### 缺陷 4 · 草稿便签不刷新

**文件**：`feature/chat/ChatViewModel.kt`、`scratchpads`

```kotlin
) { sessionId, _, _ -> sessionId }
```

同源缺陷（`status` + 手动刷新计数被吞）。**修**：同上。

### 验证

4 处修复均通过云端 CI 编译与单测。**未改动任何 UI 文件**——只修数据流的重投影触发条件。

---

## 二、上游 5xx 未纳入瞬态重试预算

**文件**：`harness/HarnessProviderRunner.kt`

### 现状（上游）

`ProviderClient` 对上游 5xx 抛出 `TransientHttpException`，但**重试逻辑从未识别它**——`isTransientConnectionFailure` 只认 `SocketException` / `InterruptedIOException` / `SSLException` / `EOFException`。

后果：

```kotlin
val maxNetworkRetries = maxNetworkRetriesFor(estimatedRequestTokens, retryPolicy.maxRetries)
// 估算输入 ≥ 64K tokens 时被压到 1 次
```

**长会话一次 503 / 524（如 Cloudflare 超时）就让整轮失败**，用户只能手动接续。而这类故障与请求体大小、上下文规模**无关**，本不该受大上下文降级影响。

### 修复

- `isTransientFailure`：白名单加入 `TransientHttpException`；沿 cause 链上溯（最多 10 层，防自引用死循环）
- `effectiveRetryBudget`：瞬态故障保底 `TRANSIENT_MAX_RETRIES = 3` 次，不被降级

### 顺带修正的日志措辞

原日志 `"网络中断重试 $netRetry/$maxNetworkRetries"`。当上限为 3 时，第 4 次失败前会打出「重试 4/3」，实为「第 4 次失败后放弃」，**极易被误读为「已执行第 3 次重试、仍在继续」**。改为「第 N 次失败（重试上限 M 次）」。

### 新增测试

`HarnessTransientFailureTest`（8 例）：5xx / 断线 / 包装 cause / 环形 cause / 普通异常判定，以及大上下文下瞬态保底 3 次。

> 注：其中「环形 cause」一例，首版写法 `it.initCause(it)` 在 JDK 17+ 会直接抛 `IllegalArgumentException`（Java 禁止自引用 cause），测试根本走不到被测逻辑。已改用 `a.cause = b, b.cause = a` 的环形链真实覆盖深度上限。

---

## 验证结果

**未在本地编译**（开发机为 Android 设备，编译开销过大），全部经**云端 CI** 验证。

- **编译（Kotlin + C）**：✅ 0 错误
- **新增测试**：✅ 全绿
- **单元测试**：⚠️ 4 例失败——**基线自带，非本 PR 引入**

### 关于测试结果（请作者留意）

| 测试 | 本 PR 分支 | 纯净上游基线（零源码改动） |
|---|---|---|
| `ToolExecutorTest`（4 例：base tool …） | 4 例失败 | **4 例失败（完全相同）** |

对照实验方式：检出 `f82a44f7`（零源码改动）跑同一 CI，得到**完全相同的 4 个失败**（76 tests completed, 4 failed）。本 PR 未触碰 `ToolExecutor` 及其相关代码。

---

## 兼容性

- 无 API 签名变更，无新增依赖
- 数据流语义变化仅体现在「更及时地触发重投影」，对外行为与 UI 契约不变
- 重试次数增加仅限瞬态故障场景，且上限 3 次，不改变非瞬态故障的既有行为
