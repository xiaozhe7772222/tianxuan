# 竞态缺陷台账（v0.21.2 审计批次）

本文件记录在**阅读源码**（而非依赖既有台账）过程中发现并修复的并发/逻辑缺陷。
每条都给出：现象 → 精确触发条件 → 根因 → 修法 → **反向验证**（把修复回退后
测试必须变红）。

原则：只记录能在源码里指出具体交错时序的缺陷。凡是“看着可疑但构造不出
失败序列”的猜测一律不收录，这类条目只会稀释台账的可信度。

---

## D-01　作用域锁回收把同一工作区拆成两把锁

**位置** `harness/.../ToolRoundDispatcher.kt`（`acquireLock` / `releaseLock`）

**现象** 同一工作区的变更类工具本应互斥，但在“最后一名持有者退出”与“新持有者
入场”首尾相接时，两者会各自持有一把**不同的** `Mutex`，互斥静默失效。后果是
同一工作区内的文件写入/命令执行可以并发踩踏——正是分片互斥要防的事。

**触发时序**（原实现用 `ConcurrentHashMap` + 两次独立的分段锁操作）

1. T1 是最后一名持有者，在 `synchronized(slot)` 内把 `holders` 减到 0，判定 `last`，退出该临界区；
2. 调度切走。T2 调 `acquireLock`，`mutationMutexes.compute(key)` 拿到**同一把** slot
   （此时它还在表里），`holders` 回到 1，并随即对 `slot.mutex` 发起 `withLock`；
3. 切回 T1，执行 `mutationMutexes.remove(key, slot)`——键仍指向这把 slot，
   于是 compare-and-remove **成功删除**；
4. T3 再入场，`compute` 查不到条目，**新建 slot2**，对 `slot2.mutex` 发起 `withLock`。

此刻 T2 持 `slot.mutex`、T3 持 `slot2.mutex`，同一 key 对应两把锁。

**根因** “判定计数归零”与“移除表项”分别落在 `synchronized(slot)` 与
`ConcurrentHashMap` 的分段锁下，**是两把不同的锁，无法拼成一个原子区间**。
`remove(key, slot)` 的 compare-and-remove 只能保证“不误删别人新建的 slot”，
挡不住“键仍指向自己、但自己已被别人重新引用”的情形。

**修法** 放弃分段锁，改用**一把显式对象锁**覆盖“建表 / 计数增减 / 回收”的全部读改写：

```kotlin
private val mutationMutexes = HashMap<String, LockSlot>()
private val registrationLock = Any()

private fun acquireLock(scopeKey: String): LockSlot {
    val key = scopeKey.trim().ifBlank { GLOBAL_SCOPE }
    return synchronized(registrationLock) {
        val existing = mutationMutexes[key]
        if (existing != null) { existing.holders++; existing }
        else LockSlot(holders = 1).also { mutationMutexes[key] = it }
    }
}

private fun releaseLock(scopeKey: String, slot: LockSlot) {
    val key = scopeKey.trim().ifBlank { GLOBAL_SCOPE }
    synchronized(registrationLock) {
        slot.holders--
        if (slot.holders <= 0 && mutationMutexes[key] === slot) mutationMutexes.remove(key)
    }
}
```

由此得到一条可断言的结构性不变量：

> **计数 == 0 ⇔ 不在表内**

任何并发入场者要么把计数加在归零判定**之前**（归零不成立，不移除），要么在移除
**之后**看到键已消失并新建一把锁（而前一名持有者已彻底离场）。两种次序都只对应
一个持有者集合，**不存在需要事后撤销的孤儿槽位**。

**代价与边界** 跨工作区的取锁/放锁会短暂串行，但那只是几次哈希表操作（纳秒级）。
真正的互斥等待仍发生在各自的 `LockSlot.mutex` 上，因此不会让一个工作区的长构建
（BASE 超时上限 1 小时）挡住其他工作区的普通写入——分片互斥要保住的性质完好。

**反向验证** `harness/.../ToolRoundDispatcherRegistryInvariantTest.kt`

| 用例 | 断言 |
|---|---|
| `a scope is present in the table exactly while it has holders` | 表项存在性严格跟随计数 |
| `queued holders keep the scope registered and release it only at the end` | 排队者登记但进不去；表项仍只有一条 |
| `mutual exclusion holds under heavy recycling pressure` | 500 轮高频进出无临界区重叠 |
| `cold scope with many simultaneous first arrivals serializes to one lock` | 冷启动 64 路并发无重叠 |
| `handover never splits one scope into two locks` | 交替交接中**同一 key 从不出现两条表项** |
| `distinct scopes never serialize each other` | 反向：不同工作区必须能同时进入 |

把 `releaseLock` 回退为“判定与移除分离”的写法后，
`handover never splits one scope into two locks` **必然失败**（实测：21 个用例中
1 个失败，精确命中该判据）。修复恢复后全绿。

---

## D-02　保活租约同名持有者互相踩踏，WakeLock 提前释放

**位置** `app/.../lifecycle/RuntimeLifecycleSupervisor.kt`

**现象** Agent 推理或 PRoot 构建在息屏后被系统冻结，因为进程级唯一的 WakeLock /
WifiLock 被提前释放了——而释放它的那次 `close()` 并非最后一个持有者。

**触发路径（非假设，是真实代码路径）**
`AgentForegroundService` 在两处调用 `acquireLease()`：`onStartCommand` 里一次
（第 96 行），运行态轮询 `collectLatest` 里又一次（第 108 行）。而
`acquireLease()` 自身有 `if (powerLease != null) return` 守卫，只记得住**最后一个**
句柄；早先那个句柄已经无人引用，却仍然在 supervisor 里登记着一次持有。

原实现把调用方传入的 `holderId` 直接放进 `MutableSet<String>`：

```kotlin
private val holders = mutableSetOf<String>()
...
holders.add(holderId)          // acquire
holders.remove(holderId)       // close —— 按裸 id 去重！
```

于是同一个 `"agent"` 的两次 acquire 在集合里**只占一个元素**，却返回两个句柄。
第二个句柄 `close()` 时 `holders` 变空 → 立即 `releaseLocks()` → WakeLock 与
WifiLock 被释放 → CPU 可被冻结 → 推理与 PRoot 进程中断。

这正是本类注释开头声称要消除的那类竞态，只是换了个入口进来。

**根因** `holderId` 是**申请方标识**（用于日志与诊断），却被当成了**持有实例标识**
（用于去重）。两者不是一回事：同一个申请方可以同时持有多次。

**修法** 每次 acquire 生成唯一持有键，集合改为映射以保住诊断价值：

```kotlin
private val holders = mutableMapOf<String, String>()   // holderKey -> holderId
private var nextHolderSeq = 0L                          // 仅在 registrationLock 下递增
...
val holderKey = "$holderId#${nextHolderSeq++}"
holders[holderKey] = holderId
...
holders.remove(holderKey)   // 只释放自己那一次持有
```

`holderSnapshot()` 改为 `holders.values.toSet()`，同名持有会重复出现——这是**正确**
的语义（“有几个在途持有、分别是谁申请的”），不是回归。

**反向验证** `app/src/test/.../RuntimeLifecycleSupervisorTest.kt`

| 用例 | 断言 |
|---|---|
| `leaseLifecycle_sameHolderIdDoesNotCollide` | 三次同名 acquire → 计数 3；逐个 close 不会连带释放他人 |
| `leaseLifecycle_mixedSameAndDistinctHolders` | 同名 + 异名混用时计数逐一对应 |
| `leaseLifecycle_repeatedCloseOfOneHandleIsIdempotent` | 重复 close 不多扣持有 |

把实现回退为 `MutableSet<String>` + 裸 `holderId` 后，上述**三个用例全部失败**
（实测：6 个用例中 3 个失败）。修复恢复后全绿。

---

## D-03a　星象距度文档与代码/测试算错（算术自相矛盾）

**位置**
- `harness/src/main/assets/prompts/system/astronomy.md:81`
- `core/common/.../astronomy/Mansion.kt:9`、`:35`
- `core/common/src/test/.../AstronomyTest.kt:33`、`:45`
- `README.md:41`

**现象** 文档写「四象合计 365 度，加箕宿所带四分一，得 365¼ 度」，而其给出的
加法式 `75 + 98 + 80 + 112 = 365` 与“得 365¼”自相矛盾：75+98+80+112 确实
等于 365，但要得到 365¼，东方那一路必须是 75¼。

`Mansion.kt` 里 `JIAO12 + KANG9 + DI15 + FANG5 + XIN5 + WEI18 + JI11.25 = 75.25`，
`TOTAL_DEGREES` 实测 365.25；`AstronomyTest.degreesSumToOneFullCircle` 也精确断言
`75.25 / 98 / 80 / 112 / 365.25`。**代码与测试是对的，只有文档与注释算错了。**

**根因** 早先“箕宿补四分之一”的修正只改了代码与断言，漏改了说明文字，
遗留了修正前口径的“365”。这类漂移尤其危险：`astronomy.md` 是**注入给模型的
系统提示词**，错的口径会被模型当事实引用，且不会触发任何编译或测试失败。

**修法** 统一为「东方 75¼（含箕宿所带四分一）、北方 98、西方 80、南方 112，
四象合计 365¼，不再另加」，并说明四分之一已含在 75¼ 之内而非额外追加。

**验证** 用脚本从 `astronomy.md` 的表格里正则抽出 28 宿距度求和，
得 **365.25**，与 `Mansion.TOTAL_DEGREES` 及 `AstronomyTest` 断言一致。

---

## D-03b　RTK 包装脚本内联命令文本，含引号的命令一律失败

**位置** `harness/.../RtkCommandOptimizer.kt`（`wrapWithFallback`）

**现象** 凡是参数里带引号的 Agent 命令，在启用命令输出压缩后**根本无法执行**，
以 shell 语法错误退出码 2 结束。受影响的是**回退路径**——本该最稳的那条。

**实测**（原实现生成的脚本，`RTK_BINARY` 指向不存在的路径以强制走回退分支）

| 命令 | 原实现 | 修复后 |
|---|---|---|
| `echo it's fine` | `exit=2` 语法错误 | eval 的**真实**引号错误（结构不再被破坏） |
| `rg -n "TODO" src` | `exit=2` 语法错误，`fi` 配对错位，`rg` 从未执行 | 正常执行到 `rg` 本体 |
| `find . -name "*.kt"` | `exit=2` 语法错误 | 正常 |
| `ls -la "my dir"` | `exit=2` 语法错误 | 正常 |
| `git commit -m 'fix: it's done'` | `exit=2` 语法错误 | 正常 |

**根因** `$command` 未经引号地内联进两个分支：

```bash
    if [ "$_s" -eq 0 ]; then eval "$_r"; else $command; fi
  else
    $command
  fi
```

原始命令只是「文本」。它自带的引号会与包装脚本的语法互相干扰：

- **成对双引号**（`rg -n "TODO" src`）在 `else ...; fi` 里开启一个字符串，
  把紧随其后的 `; fi` 吞进字符串，`if`/`fi` 配对错位 → 整条命令语法错误；
- **未配对单引号**（`it's`）直接让 shell 读到引号外的 `fi` → 同样是语法错误；
- 更隐蔽的是它还会污染**上游**解析：`echo it's fine` 的 `'` 让 shell 把它之后
  的内容读成新字符串，落进 else 被**无引号裸执行**——`echo A; echo B`
  于是输出字面量 `A; echo B`。

注意 `isEligible` 的 `unsupportedShellSyntax` 只挡 `& | ; \n \r < > \` $`，
**不挡引号**——也不该挡：commit message、grep 模式、带空格的路径都离不开引号，
把引号加进黑名单等于让这几类命令永远享受不到改写（那是退让，不是修复）。

顺带指出原回退分支还有一处不等价：`$command` 是**单次分词后直接执行**，
不再做引号/转义处理，本来就不等价于用户写的命令。

**修法** 让脚本文本与命令内容彻底解耦：命令经环境变量下发，
脚本里只出现 `"$TIANXUAN_AGENT_COMMAND"`。

```kotlin
if [ -x "$RTK_BINARY" ]; then
    _tianxuan_rtk_rewritten="$("$RTK_BINARY" rewrite "$TIANXUAN_AGENT_COMMAND" 2>/dev/null)"
    ...
        eval "$TIANXUAN_AGENT_COMMAND"
    fi
else
    eval "$TIANXUAN_AGENT_COMMAND"
fi
```

命令文本从此**不是脚本语法的一部分**，无论含什么字符都不改变脚本结构。
两个执行分支补上 `eval`，与用户直接敲命令的语义一致。

用环境变量而非位置参数，是因为 `ShellCommand` 只接受 (commandLine, environment)
二元组，没有 argv 通道；`ProotCommandBuilder.shellCommand()` 会把 provider 的
环境条目拼成 `export KEY='...'`（带正确的单引号转义），因此命令文本在那一层
才被安全地包进引号。`provider` 在 `EnvironmentResolver.merge` 中最后合入，
不会被 base/manifest 覆盖。

**反向验证** `harness/src/test/.../RtkCommandOptimizerTest.kt`

| 用例 | 断言 |
|---|---|
| `generated script never embeds the raw command text` | 6 条含引号的**可改写**命令都不得内联；必须原样进环境变量 |
| `commands with quotes are still optimized rather than excluded` | 含引号的命令仍走改写（防止有人把引号塞进黑名单了事） |
| `eligible git command is wrapped with a same-shell fallback` | 包装形状 + 环境变量携带命令 |
| `find with a quoted name pattern is optimized` | 引号与通配符都不再阻止改写 |
| `long listing keeps using RTK because -l is not a machine readable flag for ls` | `ls -la src` 照常改写 |

把 `wrapWithFallback` 回退为内联 `$command` 后，上述**5 个用例失败**
（实测：10 个用例中 5 个失败）。修复恢复后全绿。

---

## D-04　只读用户轮让 checkpoint 轮号互相撞号，撤回漏掉整轮文件改动

**位置** `harness/.../checkpoint/CheckpointStore.kt`（`beginTurn` / `closeTurn`）

**现象** 会话里出现过"只读轮"（用户问一句、模型只回文本、没有任何 `write`/`edit`）
之后，按轮撤回会把**本应撤销的那一轮文件改动留在磁盘上**。不报错、不提示，
用户只看到"文件没有回到那一步"。

**触发条件** 任意一次只读用户轮紧跟在有写入的轮之后。

**根因** 轮号只在"关轮且该轮有写入"时才提交：

```kotlin
fun beginTurn(...) {
    closeTurn(sessionId, state)
    state.activeTurn = state.lastTurn + 1   // 从上一次「已提交」的轮号 +1
    ...
}
private fun closeTurn(sessionId: String, state: SessionState) {
    val active = state.active ?: return
    if (active.isNotEmpty()) {
        state.checkpoints.add(checkpoint)
        if (checkpoint.turn > state.lastTurn) state.lastTurn = checkpoint.turn  // ← 只有有写入才提交
    }
    ...
}
```

`closeTurn` 对空轮直接返回、`lastTurn` 不动，于是 `beginTurn` 用同一个 `lastTurn + 1`
把**同一个轮号分配给两个不同的用户轮**。实测（镜像逻辑复现 + 真实单测）：

| 用户轮 | 行为 | 分配到的轮号 |
|---|---|---|
| 0 | 写 a.txt | 0 |
| 1 | 只读 | 1 |
| 2 | 写 b.txt | **1（撞号）** |
| 3 | 只读 | 2 |
| 4 | 写 c.txt | **2（撞号）** |

`checkpoints` 的轮号序列实测为 `[0, 1, 2]`，而正确序列应是 `[0, 2, 4]`。

**为什么后果是"漏撤"而不是"误删"** 三处口径互相错位：

- UI 侧 `ChatViewModel.rewindToMessage` 用 `anchorMessageId`（消息 id，**准确**）
  定位到目标 `CheckpointMeta`，然后把 `target.turn` 交给 `prepareRewind`；
- `CheckpointStore.planCodeRewind(sessionId, turn)` 用 `checkpoint.turn >= turn`
  比较（**用的是撞了号的轮号**）；
- `SessionForkConversationRewinder` 用 `anchorMessageIdOf(sessionId, turn)`
  取锚点（同样按轮号）。

锚点定位到的是轮 2，但它的 `turn` 已经因为撞号变成了 1，`planCodeRewind(s, 1)`
于是把轮 0 的快照也算进"不早于轮 1"的范围——**把更早的轮初内容当成目标轮初内容**，
目标轮自己的改动因比较方向落在范围外而被跳过。方向恒为少撤，故不会误删现存文件。

**修法** 轮号在**开轮时**分配并立刻提交，空轮同样占用一个轮号：

```kotlin
fun beginTurn(sessionId: String, prompt: String, anchorMessageId: String? = null) {
    val state = stateOf(sessionId)
    closeTurn(sessionId, state)
    state.activeTurn = state.lastTurn + 1
    // 先提交轮号再开轮：空轮同样占用一个轮号，保证 lastTurn 单调、不与其他轮撞号。
    state.lastTurn = state.activeTurn
    ...
}
```

`closeTurn` 中原来的 `if (checkpoint.turn > state.lastTurn) state.lastTurn = checkpoint.turn`
随之删除：轮号已提交，再回写既冗余，又会在空轮时把"已分配但无 checkpoint"的
轮号从单调序列里抹掉。`stateOf` 的磁盘恢复路径仍保留
`restored.maxOfOrNull { it.turn }?.let { if (it > state.lastTurn) state.lastTurn = it }`，
方向正确（取最大值），不会把 `lastTurn` 回退。

**语义澄清** 空轮仍然**不产生 checkpoint**（无写入就没有 pre-image 可存），
这一点是既有约定、且有测试固定；修的只是"轮号必须与对话轮一一对应"这条
被破坏的不变量。

**反向验证** `harness/src/test/.../checkpoint/CheckpointStoreTest.kt`

| 用例 | 断言 |
|---|---|
| `turn numbers stay monotonic across read-only turns` | 轮号序列必须是 `[0, 2, 4]`（只读轮占号但不落 checkpoint） |
| `rewind after read-only turns still reverts the intended turn` | 撤回轮 2 必须同时还原 `a.txt` 与 `b.txt` 到该轮轮初内容 |
| `anchor lookup works for read-only turns` | `anchorMessageIdOf` 在撞号修复后能取到轮 2 的锚点 |

把 `beginTurn` 中新增的 `state.lastTurn = state.activeTurn` 删掉、并恢复
`closeTurn` 里的旧回写后，上述**3 个用例失败**（实测：10 个用例中 3 个失败）。
修复恢复后 11 个用例全绿。

---

## 复核结论：既有台账中已被证伪的条目

以下条目来自既有“天玄改造台账”。**实际读代码后确认其结论有误**，特意记录以免
后续重复投入：

### A-11「`SubagentOrchestrator` 的 `base` 未被写入租约覆盖」——不是缺陷

`SubagentOrchestrator.kt` 第 423、438 行附近的注释**如实披露**了这一设计：
`writePaths` 为空即只读，`["*"]` 即整工作区独占，而 `base` 路径走的是另一条
不参与租约仲裁的路径。这是**经过声明的取舍**，不是遗漏。按现行实现，
把它硬塞进租约体系反而会与 `SubagentConcurrencyGate` 的并发上限冲突。
**结论：保持现状，注释即为文档。**

---

## 方法说明：为什么不做越过不变量的“构造式”测试

早期版本的反向验证测试曾用反射在 `registrationLock` **之外**直接从表里摘除条目，
以制造“误删”交错。那条路是错的：

- 它在实现的不变量**之外**改动状态，测出来的失败是**测量伪影**，
  而不是被测量对象的性质；
- 更糟的是它会把实现推向“事后撤销”这类更脆弱的补偿逻辑——撤销与删除之间
  又有新窗口，竞态只是被推给下一轮。

正确做法是**把不变量本身钉死**（“计数 0 ⇔ 不在表内”、
“同一 key 从不出现两条表项”），让任何未来的实现都必须在同一把锁下
完成判定与回收，而不是靠补偿去掩盖窗口。

---

## 复核批次二：程序化穷举证伪（2026-10-07）

第一版台账的记录方式是“人工读到可疑点就写条目”。这一轮改为**先程序化穷举、
再逐条证伪**：扫描全仓 865 个 Kotlin 源文件，对并发 / 阻塞 / 资源回收 /
穷尽性四个高风险面做模式匹配，得到候选集后**逐条回到源码核实**。

统计基线：

| 指标 | 数量 |
|---|---|
| Kotlin 源文件（排除 `build/`、`generated/`） | 865 |
| 生产代码 `TODO/FIXME/HACK` | **2**（均为有意留档） |
| 生产代码 `GlobalScope` / 裸 `CoroutineScope().launch` 泄漏 | **0** |
| 生产代码空 `catch {}` | **0** |

### F-01「`SessionTreeStore.laneLocks` 永不回收」——证伪

**候选理由**：`deleteSession` 只清 `decodedBranches`，不清 `laneLocks`，
看起来每对 (session × lane) 会永久泄漏一个 `LaneLock` + `Mutex`。

**实际源码**（`SessionTreeStore.kt:45-59`）：

```kotlin
laneLocks.compute(key) { _, current ->
    if (current !== holder) current else holder.takeIf { --it.users > 0 }
}
```

**证伪依据**：`ConcurrentHashMap.compute` 的语义是——回调返回 `null` 即**移除该键**
（这正是 `compute` 区别于 `computeIfPresent` 之处）。计数归零时
`takeIf` 返回 `null`，键被移除。`current !== holder` 分支保留新持有者，
正确处理了“老等待者尚在排队时不得换锁”的 ABA 场景。
**该实现同时满足不泄漏与不换锁两个约束，是正确写法。**

### F-02「`AgentContextExecutor` pinned 预算把列表当字符串」——证伪

**候选理由**：`sumOf { it.value.length }` 的 lambda **未声明参数名**，
而链上前一步 `.filter { it.id != existing?.id }` 用的是实体语义，怀疑 `it` 被
推断为 `List` 本身。

**证伪依据**：逐字节打印该表达式，实为
`getPinnedMemories(...).filter { it.id != existing?.id }.sumOf { it.value.length }`。
`sumOf` 是 `Iterable<T>.sumOf(selector: (T) -> Int)`，`it` 绑定 `AgentMemoryEntity`，
`it.value.length` 取的是实体 `value` 字段长度。**语义正确**，
且 `:harness:compileDebugKotlin` 通过与之互证。

### F-03「`HarnessLoop` 非挂起函数中调用 `join()`」——证伪

**候选理由**：`HarnessLoop.kt:1498` / `:1627` 的
`sessionJobs[sessId]?.takeIf { it.isActive }?.join()` 位于
`fun resolveApproval` / `fun resolveQuestion` 中，而两者签名都不是 `suspend`。

**证伪依据**：该调用位于 `loopScope.launch { ... }` **内部**
（`resolveApproval` 的 `launch` 起于 L1483，`join()` 在 L1498）。
lambda 的接收者是 `CoroutineScope`，其中 `join()` 合法。
行级扫描器看不到作用域嵌套，**这是扫描器的假阳性，不是代码缺陷**。

### F-04「`CountDownLatch` 阻塞协程线程」——证伪

**候选位置**：`HostGuiController.clipboardSet`、`HostGuiToolkit.writeClipboard`、
`VirtualScreenToolkit.writeClipboard` 三处 `CountDownLatch(1)`。

**证伪依据**：这三个都是**同步 API**，等待的是跨进程（`ClipboardManager` /
虚拟屏注入）写入回调完成。调用方是 Android binder / 主线程，**不是协程**；
若改成挂起反而会把同步契约破坏掉。**有意为之，非缺陷。**

### F-05「`ApprovalPolicyEngine.decide` 尾部 `when` 漏 `COMPRESS`」——证伪（列为卫生项）

**候选理由**：程序化穷举给出 `enum members: 20 / covered: 19 / MISSING: ['COMPRESS']`。

**证伪依据**：`COMPRESS` 在 `ApprovalPolicyEngine.kt:93-94` 的只读白名单里
**先行无条件返回**，`when` 分支不可达；且该 `when` **带 `else` 分支**，
所以既不报编译错也无行为差异。`:harness:compileDebugKotlin` EXIT=0 互证。

**处置**：不计为缺陷，但记入卫生待办——`when` 表达式带 `else` 会**掩盖**
未来新增枚举成员的策略遗漏。修法是给 `COMPRESS` 补显式分支、并让 `else`
分支改为显式失败而非静默放行（下批次实施）。

### 本批次方法论教训

三个假阳性（F-03、F-04 及此前的 `COMPRESS` 计数）都源自同一类错误：
**用行级正则去推断块级语义**。`join()` 合不合法取决于它套在哪个 lambda 里；
`it` 绑到谁取决于链上前一个接收者类型；枚举覆盖数取决于注解是否与成员同行。
后续候选集一律**必须回到源码精确确认后才能写进台账**，
扫描器只负责“把候选面缩小”，不负责下结论。

---

## 复核批次三：`McpHttpTransport` 会话互斥表（2026-10-07）

### F-06「`sessionMutexes` 无回收路径」——确认存在，判定为**卫生项而非缺陷**

**证据链**（程序化核实，非推测）：

| 项 | 结果 |
|---|---|
| `sessionMutexes` 全部出现 | 3 处：L63 声明、L166 `getOrPut`、L227 `getOrPut` |
| `sessionMutexes.remove` / `.compute` / `.clear` / `.keys` | **全部不存在** |
| `sessions` 回收路径 | ✅ `dropSession`(L234) + `closeSession`(L229) |
| `downUntil` 回收路径 | ✅ `closeSession`(L228) + 成功路径(L208) |
| `sessionMutexes` 回收路径 | ❌ 无 |

**为何不是缺陷**：键是 `server.id`，其取值空间受**用户配置的 MCP server 数量**
约束（个位数到几十），不是无界增长。所以没有"长会话越跑越胖"的泄漏，
最坏情况是删掉 server 后残留一个永不再用的 `Mutex` 对象。

**为何不立即修**：安全回收必须满足"仅在该键无人持有/无人等待时摘除"。
若直接在 `closeSession` 里 `remove`，并发的 `ensureSession` 会拿到**不同的 Mutex**，
互斥失效——这与 `SessionTreeStore` 里已经正确处理的 ABA 场景是同一个坑。
引入 ABA 竞态去消除一个内存卫生问题，会**降低**正确性。

**处置**：记入卫生待办。若将来要修，应照抄 `SessionTreeStore.withRetainedLaneLock`
的引用计数 + `compute` 归零移除范式（该实现已在 F-01 中确认为正确）。
**当前不动。**

### 与 F-01 的区别（为什么一个证伪一个确认）

两者表面都是"表项不回收"，但机制不同，必须分开判：

- `SessionTreeStore.laneLocks` 用 `compute`，回调返回 `null` ⇒ 键被移除。
  **回收已实现**，只是不在 `deleteSession` 里而在每次 `finally` 里。→ 证伪。
- `McpHttpTransport.sessionMutexes` 用 `getOrPut`，**没有**任何归零语义。
  **回收确实缺失**。→ 确认存在，但影响面受键空间约束，降级为卫生项。

教训：**"不回收"必须以"表用了哪个 API"为准来判定**。
`compute` / `computeIfPresent` / `getOrPut` 三者的键生命周期语义完全不同，
只看"某处没有 remove 调用"会误判。

---

## 复核批次四：未审模块（runtime / core / tools / app / feature，502 个非测试文件）

### F-07「`runBlocking` 阻塞线程」——证伪（两处均为历史修复留档）

第二轮扫描覆盖此前未审的 502 个非测试 Kotlin 文件，`runBlocking` 全局仅 **2** 处命中：

| 位置 | 实际内容 | 判定 |
|---|---|---|
| `ScreenshotRecorder.kt:152` | 注释「挂起等待压缩完成：**不再** runBlocking 阻塞 MCP 工作线程」 | 已在 `suspend fun writePngAsync` 中改为 `done.await()` |
| `DistroConfigurator.kt:16` | KDoc「**唯一行为变化**：[configureRootfs] 由同步 runBlocking 改为 suspend」 | 已是 `suspend fun configureRootfs` |

**两者都是描述"过去曾用 runBlocking、现已改掉"的说明性文字**，不是活跃调用。

### 给后续审计的警告：注释会污染正则审计

这是本轮第二次踩到同一类坑（第一次是 F-03/F-04 的行级作用域误判）。
**`runBlocking` / `GlobalScope` / `Thread.sleep` 这类关键字一旦被写进"我们不再用 X"的
注释里，任何基于文本匹配的审计都会把它当成违规重现。** 本次 2 处全部是这种情况。

结论：**正则审计只能用来缩小候选面，绝不能直接下结论。**
任何候选必须回到源码确认其**是否处于可执行路径**，再判定。
凡涉及并发/阻塞的候选，还需确认**所在函数是不是 suspend、被谁调用**。

### 本批次其余扫描面的信噪比结论

| 扫描面 | 命中 | 结论 |
|---|---|---|
| `runBlocking` | 2 | 全部证伪（见上） |
| `SHARED_MUTABLE_STATIC`（`val x = mutableListOf()`） | 101 | **信噪比过低**——Kotlin 里绝大多数是函数内局部变量，非共享状态。不予采信。 |
| `DISPOSABLE_NOT_CLOSED`（资源构造） | 90 | **信噪比过低**——`File(...)` 等非资源对象大量误匹配。不予采信。 |

后两项说明：**静态文本模式在 Kotlin 上对"共享可变状态"和"资源未关闭"几乎没有判别力**，
这两类问题必须靠**逐函数阅读 + 现有单测覆盖情况**来发现，不能靠 grep。
后续不再对这两个面做模式扫描，以免用低质量候选稀释台账。

---

## 复核批次五：`SettingsDataStore` 凭据路径（2026-10-07）

### F-08「`decodeProtectedValue` 丢弃明文兼容值」——**已撤回**（我的提取器出错）

**我曾误判为真实缺陷**，理由是我提取到的函数体是：

```kotlin
private fun decodeProtectedValue(value: String): String? =
    if (value.startsWith(PROTECTED_VALUE_PREFIX)) {
        secretManager.decrypt(value.removePrefix(PROTECTED_VALUE_PREFIX))
    }
    // ← 提取结果里 else 分支不见了
```

并据此断言「明文老数据会被 `orEmpty()` 变成空串」。

**实际源码**（括号感知提取器复核）：

```kotlin
private fun decodeProtectedValue(value: String): String? =
    if (value.startsWith(PROTECTED_VALUE_PREFIX)) {
        secretManager.decrypt(value.removePrefix(PROTECTED_VALUE_PREFIX))
    } else {
        value          // 明文直通，正是 KDoc 承诺的「兼容升级前的明文值」
    }
```

**实现完全正确**，KDoc 与实际行为一致。**缺陷不存在。**

### 根因：提取脚本在 `fun x() = if(...){...} else {...}` 上崩解

我用的提取逻辑是 `index('{')` + 花括号计数。对**表达式体**函数，
第一个 `{` 属于 `if` 而不属于函数体，配对在 `if` 块结束时就归零了，
**`else` 分支被整段截断**。于是"函数看起来少了 else"。

这类形状在本仓大量存在（`decodeProtectedValue`、`planBlock` 的多个分支、
`HarnessTool` 的穷尽 `when` 等），**每一次都可能被我误判**。

### 已固化的修复：`/tmp/extract.py` → 建议纳入 `scripts/`

新提取器做三件事：
1. 从 `fun` 关键字起，**先扫描到签名结束**，只在遇到顶层 `=`（表达式体）
   或 `{`（块体）时才决定提取策略；
2. 表达式体：按**缩进层级**消费续行，不靠花括号配对；
3. 块体：配对时**跳过字符串字面量与转义**（`instr` 状态机），
   避免 `"{"` 这类内容污染计数。

### 本轮方法论总账（三次同类错误的共同根源）

| 批次 | 误判 | 真正的根因 |
|---|---|---|
| 二 | `COMPRESS` 缺分支（计数 19/20） | 正则没跳过与成员**同行**的 `@SerialName` 注解 |
| 二 | `join()` 在非 suspend 函数中 | 正则看不到 `launch { }` 的**作用域嵌套** |
| 五 | `decodeProtectedValue` 丢 else | 花括号配对没识别**表达式体** |

**三次都不是代码的问题，是"用文本推断结构"的问题。**

因此今后在本仓的所有审计，**必须遵守**：
1. 判定"某分支是否存在"→ 用括号/字符串感知的提取（`extract.py`），
   或直接 `Read` 完整函数，**不得用 grep/正则的局部输出**；
2. 判定"某调用是否合法"→ 必须确认它**所在的 lambda/函数作用域**；
3. 判定"枚举是否穷尽"→ 用 Kotlin 编译器本身（给枚举注入探针成员，
   看 `must be exhaustive` 报错），**这比任何正则都权威**。

第 3 条已在 F-05 中验证：注入 `__PROBE__` 一次就精确列出了全部 4 处穷尽约束点。

---

## 复核批次六：ToolManager.installJobs 跨线程无同步共享表（2026-10-07）

### D-08 `ToolManager.installJobs` 用 `mutableMapOf` 承载跨线程共享状态

**现象**
安装/更新任务的登记表 `installJobs` 被两类线程同时访问，且只有一侧加锁。
单看代码不会报错，症状是低频、不可复现的：

- 两个工具（或同一工具连点两次安装）偶发**并发解包到同一目录**，文件互相覆盖，
  最终版本内容混杂；
- 极端情况下 UI 线程**卡死占满一个核**（CPU 100%，无堆栈可读）；
- `syncRegistry()` 偶发抛 `ConcurrentModificationException`，被上层 catch 吞掉后
  表现为"工具列表偶尔刷不出来"。

**触发条件**
| 访问点 | 线程 | 是否加锁 |
|---|---|---|
| `startInstall` / `startUpdate` 读表判活跃 | 主线程（ViewModel 直调） | **无** |
| `cancelInstall` 读表取 Job | 主线程 | **无** |
| `syncRegistry` 迭代 `installJobs.keys` | 主线程 / IO | **无** |
| `installInternal` 登记 `installJobs[id] = job` | IO（managerScope） | `installMutex` |
| `installInternal` finally 注销 | IO | `installMutex` |

写侧持有 `installMutex`，读侧完全无锁 —— **两侧不共享任何同步原语，
无法建立 happens-before**。

**根因（三个独立缺陷叠加在同一个字段上）**

1. **非原子「先读后写」**：`val existing = installJobs[toolId]; if (existing?.isActive == true) return existing`
   与随后的登记之间没有原子性。两个并发 `startInstall` 会同时读到空表、
   各自 `launch` 一路装配流，对同一工具并发解包。

2. **非原子「先查后删」**：`if (installJobs[toolId] === currentJob) installJobs.remove(toolId)`
   两条语句之间，新任务可能已把同一个 key 换成自己的 Job；旧任务收尾时
   会把这个**接任者**删掉，新任务随之成为表里查不到的孤儿，
   `cancelInstall` 再也找不到它（用户点取消无反应）。

3. **容器本身不安全**：`mutableMapOf()` 背后是 `LinkedHashMap`，
   跨线程无同步读写会踩两类不可恢复的坑 ——
   读取时若正逢扩容（resize），桶链表可能成环导致读线程**死循环**；
   迭代 `keys` 时并发写入抛 `ConcurrentModificationException`。

**修法**
- 容器换成 `ConcurrentHashMap`（消除第 3 条：可见性、迭代安全、无成环）；
- 登记改为 `putIfAbsent`，**登记与「复用已有任务」一步完成**（消除第 1 条）；
- 注销改为两参 `remove(key, value)`（消除第 2 条）；
- `startInstall`/`startUpdate` 合并为 `launchInstall`，用 `CoroutineStart.LAZY` +
  `putIfAbsent` 实现「**登记成功才启动**」：若表内已有活跃任务，把刚创建的这个
  LAZY Job 取消并返回既有 Job，保证任一时刻同一工具只有一个活跃装配。

**修法过程中自己引入又纠正的一处错误（记录以免重犯）**
把 `LinkedHashMap` 换成 `ConcurrentHashMap` 后，
`check(toolId !in installJobs)` 这一行**编译器报了 `contains` 语义警告**：
`ConcurrentHashMap` 覆写了 `contains` 使其等价于 `containsValue`（历史遗留），
于是 `in` 运算符会去比对**全部 Job 值**，永远为真 ——
「安装中禁止卸载」这道闸门会**静默失效**。
已改为显式 `containsKey`。
这条只有编译器的 `-Xmap-contains` 类检查能抓到，人工 review 极易放过。

**反向验证**
`tools/src/test/java/.../ToolManagerInstallJobsConcurrencyTest.kt`（4 项，全绿）：

| 用例 | 断言 |
|---|---|
| `legacy check-then-act registry fails under concurrency` | **旧协议在同一压测下必然出现违规（>0）** |
| `atomic registry never hands out two instances for one key` | 新协议违规数恒为 0 |
| `legacy check-then-remove can evict a successor` | 旧写法确实会误删接任者；两参 `remove` 不会 |
| `lazy start with putIfAbsent reuses the same job instance` | LAZY + putIfAbsent 复用同一实例 |

第一项是**测试有效性守卫**：若压测强度不足、复现不出窗口，它会失败，
从而防止"测试永远是绿的但其实什么都没测"。

**限度（必须如实标注）**
这是**协议级**回归测试，不是对 `ToolManager` 整体的行为测试。
生产类需要 17 个真实依赖才能构造，且 `tools` 模块只有 `junit` + `okhttp-mockwebserver`
（无 MockK/Mockito），故测试用与生产**同构**的协议复刻来锁定语义。
它能防止"有人把这三处改回非原子写法"，但**不能**替代对 `installInternal`
状态机本身的审查。真正的反向验证（改坏生产代码→测试转红）在此路径上不成立，
不应宣称做过。

**行数棘轮**
`ToolManager.kt` 在 `.architecture-baseline.json` 中额度 **1131**（只许下调）。
本次同时把四处重复的「回滚 + 落库 + 释放引用」善后逻辑抽成局部
`suspend fun settleFailure(cancelledByUser: Boolean)`，净减 9 行 → **1122**。
既满足棘轮，又消除了四处重复逻辑各自漂移的风险（原先三处传 `cancelled=true`
只有一处传 `false`，任何一处漏改都会造成状态落库不一致）。
