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

---

# 复核批次七：发布链路（release.sh / ops / .github/workflows）

发布链路是**唯一能把缺陷直接送到用户设备上**的通道，故单独成批逐文件精读。
本批覆盖 15 个文件，其中 4 个为自测脚本。总原则沿用「守卫查产物不查配置」：
配置会被人改错，产物不会说谎。

## 逐文件结论

| 文件 | 行数 | 结论 |
|---|---|---|
| `release.sh` | 352 | 无缺陷。三处 APK 命名对齐已交叉核对（见下） |
| `ops/apkmanifest.py` | 188 | 无缺陷。模糊测试 0/200 崩溃 + 60 次截断 0 崩溃 |
| `ops/apkmanifest_test.py` | 186 | 无缺陷。7 项用例覆盖 UTF-16/UTF-8 池、非 manifest 根、整型、坏输入 |
| `ops/gen-manifest.sh` | 125 | 无缺陷。版本排序实测 `0.21.10 > 0.21.3 > 0.21 > 0.20.0 > 0.9.0` |
| `ops/gen_manifest_test.py` | 257 | 无缺陷。6 组用例含幂等与软链去重 |
| `ops/tianxuan_dist.py` | 344 | 无缺陷。目录穿越 11 向量全部拦截 |
| `ops/tianxuan-fetch.sh` | 42 | 无缺陷。见「窄口子」一节 |
| `ops/sync-tianxuan-plugins.sh` | 34 | **D-09**（已修复，见下） |
| `ops/release_guard_test.py` | 171→414 | **D-10**（已修复，见下） |
| `ops/offline-packages.json` | 55 | 无缺陷。`total_bytes` 与逐项求和精确相等（6,911,578,380 = 6.44 GiB） |
| `ops/README.md` | 285→301 | 补充守卫自测的跑法 |
| `ops/DEPLOY.md` | 96 | 无缺陷。见「窄口子」一节 |
| `ops/.gitignore` | 6 | 无缺陷。相对目录解析的坑有注记 |
| 两个 `.service` | 41 / 22 | 无缺陷。`ProtectSystem=strict` + `ReadWritePaths` 已收口 |
| 三个 `.yml` | 267 / 76 / 39 | 代码无缺陷；失败均为环境条件（见下） |

## D-09：`sync-tianxuan-plugins.sh` 在悬空软链上以失败收场

`ops` 目录下 `plugins/` 存的是软链，指向离线包实体。离线包被清理或下载失败时
会有链接悬空。原脚本对**全部** `*.txplugin` 链接调 `os.path.getsize`，
悬空项抛 `FileNotFoundError`；脚本是 `set -euo pipefail`，于是
「补软链」这件**已经成功**的事会以 exit 1 收场，误导排障者去查一个不存在的错误。

修法两步：
1. `find` 加 `-xtype f`（跟随软链后仍是普通文件），排除悬空链接。
   `-type l` 会把悬空项也算成插件，报出的数量比实际可下载的多。
2. 统计字节数时用 `os.path.isfile` 过滤。这与 `tianxuan_dist.py` 的
   `_serve_plugins`（同样用 `os.path.isfile`）保持一致——**两处口径必须一致**，
   否则运维看到的数字与端点返回的数字对不上。

**反向验证**：同一输入下，旧写法 exit 1 且日志含 `FileNotFoundError`，
新写法 exit 0 且计数正确。三种场景（全悬空 / 混合 / 全正常）均验证。

**影响面**：面向运维的工具（`ops/README.md` 列为手动步骤），不在 systemd 路径上，
故不影响自动发布。

## D-10：`release_guard_test.py` 的 manifest 分支从未被执行

`release.sh` 的产物洁净度守卫要查两处：APK 的 manifest 与 dex。
而旧自测**只覆盖 dex 分支**——manifest 分支的代码写了，却从未跑过，
源码自己承认这点：第 142-146 行 `del dirty_manifest`，
注释写「真实包无法在单测内造」。

**缺口是可利用的**，已实测：把 `release.sh` 里守卫的 grep 交替
`LeakLauncherActivity` 改成 `LeakLauncherActvty`（少一个字母），
守卫就此漏掉这个组件，而旧自测**照样打印 OK 并 exit 0**。
原因有两层：
1. `assert_release_sh_still_has_guard` 做的是**子串检查**，
   而正确拼写同时出现在第 152 行注释与第 163-179 行的报错文案里，
   于是拼错模式之后，正则串本身成了唯一被改坏的地方，子串断言查不到。
2. manifest 分支压根没跑，模式坏没坏都看不出。

后果的严重性：v0.21.0 与 v0.21.1 两个内测包实测带了 LeakCanary，
桌面多出「Leaks」小鸟图标，且它在进程启动阶段自动初始化，是「打开即闪退」
的高概率来源。守卫若已静默失效，同类包会**再次被放行**。

**修法**（三条，缺一不可）：

1. **真造含组件的 APK**，让 manifest 分支真正被执行。
   此前认为做不到，是因为手写二进制 AXML 太脆；但这条路是通的：
   `aapt2 link` 能把一份纯文本 `AndroidManifest.xml` 编成**真·二进制 AXML**，
   放进 zip 就是一个 `aapt2 dump xmltree` 能读的真包。造出的包复刻了真实事故
   包的形态：`LeakLauncherActivity` 带 LAUNCHER intent-filter，
   `PlumberInstaller` / `MainProcessAppWatcherInstaller` / `LeakCanaryFileProvider`
   作为 ContentProvider。实测 dump 出 4 个组件，守卫如实拦下。
2. **抠模式而非搜关键词**。从 `release.sh` 里用正则取出那个**真正被 grep 执行**
   的模式串，逐分支核对。并且必须**先剥掉注释行**——整段守卫最省事的停用办法
   就是在关键行前加个 `#`，那一行的模式串照样躺在文件里；
   若不剥注释去搜「第一个 `grep -oE`」，被注释掉的那行会被当成「守卫还在」，
   自测又退化回子串断言。
3. **保留「干净包必须放行」的负向用例**。守卫若「一律拦下」，
   发布链路直接不可用，等于没有守卫。

**反向验证（6 组场景，全部符合预期）**

| 场景 | 期望 | 旧自测 | 新自测 |
|---|---|---|---|
| 基线（未改动） | 放行 | PASS | **PASS** |
| 模式拼错 `LeakLauncherActvty` | 拦住 | **PASS（漏报）** | **BLOCK** |
| 模式里删掉 `PlumberInstaller` | 拦住 | PASS（漏报） | **BLOCK** |
| 注释掉守卫的 `grep -oE` 行 | 拦住 | PASS（漏报） | **BLOCK** |
| 整段删除 `LEAK_COMPONENTS` | 拦住 | PASS（漏报） | **BLOCK** |
| 删掉 `leakcanary.` 兜底分支 | 拦住 | PASS（漏报） | **BLOCK** |

第 2 行是全批最硬的一条证据：**同一棵被改坏的树，旧自测 exit 0，新自测 exit 1**。

**限度（必须如实标注）**
- 新自测造的是**结构同构**的包，不是真实构建产物。它能证明「守卫认得这些组件名」，
  不能证明「真实 48 MB 包的 manifest 解析路径完全相同」——后者要靠
  `release.sh` 在真实发布时执行守卫来兜底。
- 无 aapt2 时（如未装 SDK 的机器）报 `SKIP` 并以 0 退出，不误报失败。
  代价是：那种环境下 manifest 分支未被覆盖，`SKIP` 行必须被看见而非被当作通过。

## release.sh 三处 APK 命名对齐（交叉核对）

命名不一致会导致「构建产出的文件名」与「release.sh 期望的文件名」对不上，
发布时才会以「文件不存在」暴露。三处已核对一致：

| 位置 | 表达式 | 值 |
|---|---|---|
| `app/build.gradle.kts` | `buildAppName = "tianxuan-v${appVersionName}-${variant.name}.apk"` | `tianxuan-v0.21.3-debug.apk` |
| `release.sh` L119 | `tianxuan-v${VERSION}-${BUILD_VARIANT}.apk` | 同上 |
| workflow 校验 | 从 `gradle.properties` 读 `versionName` 再拼 | 同上 |

## 窄口子（记录为上界，非缺陷）

审查中确认两处**理论存在、当前不触发**的窄口子，如实记录而不夸大为缺陷：

1. **`/tmp/tianxuan-manifest.tsv` 以 TAB 分列**，`tianxuan-fetch.sh` 用
   `IFS=$'\t' read -r size name url` 读取。若**文件名或 URL 含 TAB**，
   字段会错位：name 被截断、落盘文件名错误，且字节数校验失败，
   重试 5 次后 FAIL 并计入 `failures`。实测 `offline-packages.json` 里
   11 个包名**均不含 TAB 或空格**，且 QQ 闪传直链不含 TAB，故当前不触发。
   提防的是将来换用别的 CDN 或包名带 TAB——那时会以「下载失败」而非
   「解析失败」的形式暴露，排障方向会偏。留此记录以缩短下次定位时间。
2. **`tianxuan-fetch.sh` 的成功判据是「rc==0 且字节数相等」，不是校验和。**
   字节数相同而内容不同，理论上可被放行。这与 APK 侧形成不对称：
   `gen-manifest.sh` 为每个 APK 记录 `sha256`（L94），APK 下载链路有校验和；
   离线插件包这条链路只有字节数。影响面为零（插件包来自可信直链、走 HTTPS），
   且插件的 `sha256` 未记录在 `offline-packages.json` 中，无法在不重取链的情况下
   补上校验和。记录为已知的**非对称**，不擅自改协议。

## CI 失败：环境条件，非代码缺陷

如实归因，避免被误当成代码问题反复排查：

| 工作流 | 失败点 | 性质 |
|---|---|---|
| `release.yml` | `Restore Release Keystore`：`SIGNING_KEYSTORE_BASE64 is not configured` | 仓库 secret 未配置 |
| `pages.yml` | `Create Pages site failed: Resource not accessible by integration` | 仓库未启用 Pages |

`release.yml` 自 v0.20.0 起**每个 tag 推送都以同一原因失败**（v0.21.0/0.21.1/0.21.2/0.21.3 一致）。
其设计是**失败即停**（fail-safe）：keystore 缺失时不产出未签名包，这是正确行为。
但副作用值得记录：**每次推 tag 都会在 Actions 面板留下一条红色记录**，
长期会让人对红色告警脱敏。要么配置 secret，要么在 secret 缺失时改为
显式 `skip` 而非 fail，二者取一，不应放任红色堆积。

## 行数棘轮

`ops/release_guard_test.py` 171 → 414 行（+243）。`ops/` 不在
`architecture-policy.json` 的 31 个受管模块内（该策略管 Kotlin 模块），
故不触碰 400 行上限与 `.architecture-baseline.json`。
`ops/README.md` 285 → 301 行，同理。

---

# 复核批次八：runtime 模块（逐文件精读，起点 WorkspaceManager.kt）

## D-11：APK 解包的防膨胀闸完全缺失（已修复，已反向验证）

`WorkspaceManager.kt` 里有**两条**归档解包路径，口径不一致：

| 路径 | 输入来源 | 条目数闸 | 单文件闸 | 总体积闸 | 路径穿越闸 |
|---|---|---|---|---|---|
| `extractProjectArchive` | 用户导入的 **ZIP 项目包** | ✅ | ✅ | ✅ | ✅ |
| `unpackApk` | 用户导入的 **APK** | ❌ | ❌ | ❌ | ✅ |

`unpackApk` 只防了 zip-slip。而 APK 是用户从系统文件选择器挑的**任意文件**，
可以声明任意大的解压体积。实测构造：一个 **128 KiB** 的包声明 128 MiB 全零数据
（DEFLATE 膨胀 **1027×**）即可；把规模放大到 4 GiB 只需几 MB 的包。
后果是解包一路写到磁盘满或 OOM —— `unpackApk` 走的是
`importApkForReverse` → `createProject(APK_REVERSE)` 这条用户可达路径。

**修法不是给 unpackApk 再抄一遍三道闸**，而是把拷贝循环抽成
`copyCapped(input, output, entryName)` 由两条路径共用（含单文件闸、返回字节数），
体积额度由调用方累加——那是**整包**的额度，不能每文件各算。
这样两条路径不会再次漂移，而这正是缺陷的成因：
一个规则写两遍，改的时候只改了一处。

同时顺手消除同文件里另外两组逐字重复：
- `observeProjects` 与 `listProjects` 里相同的「剔除父目录」算法 → `dropParentDirectories`；
- 两处「关联目录不含空段/`.`/`..`」校验 → `requireValidRelativePath`
  （这两份已开始漂移：一处传 `base.canonicalFile`、一处传 `base`）。

**反向验证**：把 `unpackApk` 的三道闸临时停掉，新增用例
`apkUnpackRejectsZipBombByTotalBytes` **转红**（17 项中 1 项失败）；
恢复后全绿。同时保留负向对照 `apkUnpackAllowsOrdinaryApk`，
确保「一律拦下」这种假修复也会被抓到。

**测试自身的一个坑（已修，值得记下）**
首版用例的常量写成 `4 * 1024 * 1024 * 1024 + 64 * 1024 * 1024` 且声明为 `Int`，
Kotlin 按 Int 计算后**溢出成 −134217728**，构造循环一次都不执行，
"炸弹"是空包，用例以「未失败」告终——看起来像产品代码没设防，实则是测试自己错了。
已改为 `Long` 字面量，并加了一条**用例有效性守卫**：
`assertTrue("构造出的炸弹只有 N 字节…", apk.length() > 1024)`，
让这类错误在构造阶段暴露，而不是伪装成产品缺陷。

## 行数棘轮（本次为下调，非放宽）

`WorkspaceManager.kt` 原额度 **1015**。加入修复与合并后一度到 1058，
`architectureCheck` 如实报红：`增长到 1058 行，超过棘轮基线 1015（基线只许下调，请先缩减该文件）`。

处理方式是**缩减文件**而非抬基线：
把 `writeReverseReadme`（87 行的纯文档模板：jadx/apktool 命令、加固壳特征表、
脱壳方案表）抽到新文件 `ApkReverseReadme.kt`（102 行，未登记，合规 ≤400）。
该函数只用自身 4 个参数、不碰任何实例状态，属于天然可分离的职责。

结果：`WorkspaceManager.kt` **1015 → 970**（下调 45 行），
`.architecture-baseline.json` 中该条目同步下调为 970。
`architectureCheck: 31 个模块 · 依赖白名单/无环/import 黑名单/尺寸棘轮 全部通过`。

---

# 批次九：runtime 鉴权面精读（LinuxRuntimeImpl → gui 组）

本轮继续按「逐文件精读、不跳读不猜测」推进 runtime 模块。累计读完
`runtime/src/main/java` 下 **60 个 ≥60 行的源文件全部**（另加若干小文件）。
下面只记本轮**新发现**的缺陷与判断，其余文件的结论是「未发现缺陷」。

## 缺陷 #1（已修）：FTP 跨服务凭据复用

`FtpServiceManager.start()` 原为：

```kotlin
val ftpPassword  = preferences.readPassword(distroId)?.ifBlank { null }
val sshPassword  = sshPreferences.readPassword(distroId)?.ifBlank { null }
val password     = ftpPassword ?: sshPassword
```

即「FTP 没单独设密码时，自动拿 SSH 密码顶上」。三层危害：

1. **跨服务凭据复用**。本实现的 FTP 是明文协议（连 `AUTH`/TLS 都不支持），
   把 SSH 密码喂给它，等于让一处泄漏同时失守两个服务，而 FTP 的影响面更大。
2. **界面与行为相反**。`FtpSettingsScreen` 在未单独设密码时显示
   「未设置密码（免密登录，客户端密码留空或填任意内容即可）」，
   但用户只要配过 SSH 密码，FTP 实际要求的就是那把 SSH 密码——
   照界面操作会一直 530，且没有任何可排查线索。
3. **不可观测**。`FtpPreferences.passwordConfigured` 只看 FTP 键，
   所以 UI 永远不可能提示「正在使用 SSH 密码」。

**先证后改**：用沙箱内的 Kotlin 编译器搭了独立探针
（`/tmp/ftp-auth/AuthLogic.kt` + `Probe.kt`，逐字复刻凭据解析契约、
遍历真实取值组合），探针退出码 `1` 并打印
`UI 文案承诺「留空即可」但实际被拒 —— 界面与行为相反`。
缺陷在动产品代码之前已被独立复现。

**修法**：FTP 只认 FTP 自己的密码；免密有且只有一个显式入口 `anonymousEnabled`。
并**移除依赖**而非仅改逻辑——`SshPreferences` 从 `FtpServiceManager`、
两个 Koin 模块、`FtpSettingsViewModel` / `FtpSettingsUiState` 一并删除，
使回落无法静默回归。UI 重写为三个真实状态（已配置 / 匿名 / 未设置且不可用）。

涉及文件：`FtpServiceManager.kt`、`runtime/di/runtime/KoinModule.kt`、
`FtpSettingsScreen.kt`、`FtpSettingsViewModel.kt`、`feature/settings/di/.../KoinModule.kt`。

## 缺陷 #2（已修）：WebChat 配对码用 `==` 比对

`WebChatBridgeServer` 的 `isAuthenticated` 与 `SessionBootstrapHandler`
都以 `token == _status.value.pinCode` 校验 6 位十进制配对码（约 20 bit）。
Kotlin 字符串 `==` 逐字符比较并在**首个不同字符处提前返回**，
攻击者按响应耗时逐位收敛，平均 20 次量级即可猜中，而非 10^6 次。

**可达性已核实**（不是纯理论风险）：服务绑定通配地址 `InetSocketAddress(port)`、
响应头带 `Access-Control-Allow-Origin: *`、监听期间长期常驻、全程无频率限制；
两个调用点的 `token` 均由攻击者控制（query param / `Authorization: Bearer`，
以及 bootstrap 的 JSON body）。

**注意这是项目内部的不一致而非认知缺失**：同项目 `ShellCommandFactory.verifyPin`
早已用 `MessageDigest.isEqual` 处理同一问题。

**修法**：抽出 `PinVerifier`（`MessageDigest.isEqual` + 空候选/空期望拒绝），
`isAuthenticated` 与 `SessionBootstrapHandler` 共用同一入口。
同时把 MIME 表与「是否静态资源」判据合并为 `WebChatAssets.mimeTypeOrNull`
（原先是两份扩展名清单，新增类型时会错配：按资源取文件却回落 `index.html`）。

## 卫生改进 #3（已修）：HostBridge 的 Bearer 密钥比对

`HostBridge.checkAuth` 原为 `return token == bridgeKey`。

**定性必须与缺陷 #2 分开**：该密钥是 UUID 去横杠后的 **128 bit 随机值**，
逐字节计时旁路在数学上不可穷举；且服务只监听 `127.0.0.1`，
能连上的攻击者已经在设备内。**所以这不是可利用漏洞。**

仍然改掉的理由是**防止无声退化**：同一行代码今天面对 128 bit 是安全的，
明天若有人为「方便调试」把 `bridgeKey` 换成 6 位数字，
`==` 立刻变成可利用的计时旁路，而没有任何编译期或运行期提示。
改为 `MessageDigest.isEqual`，与 `ShellCommandFactory.verifyPin` / `PinVerifier`
保持同一写法，退化就不可能静默发生。

## 行数棘轮（本次均为下调，非放宽）

- `WebChatBridgeServer.kt` **672 → 665**（抽出 `PinVerifier` 44 行、`WebChatAssets` 37 行）。
  中间曾因在文件内压缩走错方向（673 → 674 → 679），
  用 `diff` 定位到原因是反向验证用的 `/tmp/wc.bak` 备份已含改动、不是 672 行基线。
- `HostBridge.kt` **451 → 450**。恒定时间比较带 5 行注释（净增），
  从同文件两处抵回：header 解析的局部变量合并、`writeResponse` 的 `when` 压行。
- `FtpSettingsScreen.kt` **532 → 527**（三个真实状态的文案比原两条分支更短）。
- 新增 `PinVerifier.kt`(44)、`WebChatAssets.kt`(37) 登记入 `files`，`fileCount` 113 → 115。
- `FtpServiceManager.kt` 327 → 336（加 14 行安全注释、删依赖），
  不在棘轮条目内，受 `maxFileLines=400` 约束，合规。

`architectureCheck: 31 个模块 · 依赖白名单/无环/import 黑名单/尺寸棘轮 全部通过`。

## 守卫与反向验证

新增 `ops/ftp_auth_guard_test.py`（6 项检查，与 `release_guard_test.py` 同约定）：
`assert_no_ssh_fallback` / `assert_no_ssh_dependency` / `assert_ui_matches_behaviour` /
`assert_session_still_rejects_blank_password` / `assert_webchat_pin_compared_in_constant_time` /
`assert_host_bridge_key_compared_in_constant_time`。

**守卫自身出过一次错，值得记下**：首版
`assert_session_still_rejects_blank_password` 只检查表达式里是否出现
`isNullOrBlank` 子串。反向验证 D1（把 `!config.password.isNullOrBlank() && …`
改成 `config.password.isNullOrBlank() || …`）**通过了守卫**——
即守卫抓不住它本就是为了防的那个漏洞。改为按**布尔结构**判定：
引入 `split_top_level(expr, op)` 按顶层 `||` / `&&` 切分，
拒绝任何「未取反地测试密码为空」的顶层析取项、拒绝 `pass.isEmpty()` 析取项、
要求存在 `pass == config.password`、要求存在取反形式的合取项。
重跑 D1/D2/D3 三种退化形态，均已正确转红。

另执行反向验证 E4：`HostBridge.checkAuth` 退回 `==` → 该项如实转红。

## 本轮判为「未发现缺陷」的文件（含判断依据）

- **`LinuxRuntimeImpl.kt`(909)**：八个变更入口全走 `initializeMutex.withLock`；
  `updateRootfs` 的取消/异常/健康检查失败三条路径都调 `rollbackPendingUpdate`；
  `storageMounts()` 对用户可编辑的 Room 表先 `validationError` 过滤再告警，
  注释明写「绝不能让非法绑定流入 ProotCommandBuilder」；
  `MIN_FREE_BYTES = 600L * 1024L * 1024L` 用 `L` 字面量避开 Int 溢出。
- **`SshServiceManager.kt`(576)**：凭据从不进命令行明文——
  authorized_keys 与 sshd_config 整体 Base64 后 `printf|base64 -d` 落盘，
  密码同样经 `base64 -d | chpasswd`；`normalizePassword` 拒绝 `:` 与 ISO 控制字符
  （不拒 `:` 会截断、不拒 `\n` 可注入额外用户行）；
  `cleanupStaleSshd` 用 `withTimeoutOrNull` 轮询端口而非无限等。
- **`DistroConfigurator.kt`(518)**：`configureDns` 用
  `Files.isSymbolicLink(...) || resolvConf.exists()` 双条件移除悬空链接
  （注释明写 OCI 镜像里 `/etc/resolv.conf` 常指向 `/run/systemd/resolve/...`，
  PRoot 无 systemd 导致链接悬空）；`statoverride` 判重用 `endsWith(" $path")`
  避免 `/usr/bin/su` 误匹配 `/usr/bin/sudo`。
- **`RuntimePathManager.kt`(278)**：`installedDistroIdsCache` 只缓存非空结果；
  `needsCopy` 用 `contentEquals` 比内容而非仅比长度
  （注释明写「equal file length alone does not prove that an old SONAME copy matches」）。
- **`LinuxEnvironmentManager.kt`(308)**：写 profile 用 `umask 077` + 临时文件 + `mv -f`
  原子替换；`normalizeKey` 白名单并禁 `TIANXUAN_`/`ANDROID_` 前缀
  （防用户覆盖桥接用的 `TIANXUAN_BRIDGE_URL`）；切换发行版时先清空三个 StateFlow。
- **`gui/HostGuiToolkit.kt`(371)**：`trySetFocusedText` 的降级纪律——
  `ACTION_SET_TEXT` 幂等可重试，而剪贴板/`input text` 是追加式，
  故「曾被接受但读不回」时主动返回成功而不降级，注释明写
  「否则会造成文本重复——对发消息这类场景，重复比失败更糟」。
- **`gui/HostGuiController.kt`(388)**：`selectForDisplay` 先保可交互控件再补文本节点
  （DFS 先序直接 take 会截掉屏幕底部/右侧的发送、悬浮按钮，
  「模型看不到就不会去点」）；`observeScreen` 刻意不保留 `rawXml`
  （注释明写曾致 target footprint OOM）。
- **`gui/WorkflowGuiHudBridge.kt`(173)**：`screenOpDepth` 引用计数且 `endScreenOp`
  用 `coerceAtLeast(0)` 防空（否则悬浮窗永不再隐藏）；
  `stopRequestedFor` 记录**具体 executionId** 而非布尔量，防上次的停止请求取消下次执行。
- **`gui/AndroidGuiXmlParser.kt`(113)**：`XmlPullParser` 流式解析；
  解析失败返回已解析部分；`resourceId.substringAfterLast('/')` 去前缀同时
  确认 `click_text` 命中判断仍按完整 `resourceId` 做 `contains`。
- **`di/runtime/KoinModule.kt`(318)**：FTP 改动已干净落地，
  `SshServiceManager` 保留自己的 `preferences`，两者不再共享依赖；依赖无环。

## 测试

`runtime` **131 → 143**（新增 `PinVerifierTest` 7 例 + `WebChatAssetsTest` 5 例），
全项目 `1450 tests, 0 failures, 0 errors`。

## 复核批次七：tools 模块 41 个 main 文件全量精读（2026-10-07）

精读范围：`tools/src/main` 下全部 **41** 个 `.kt`（`core/tools` 35 + `runtime/tools` 5 + `di/tools` 1），
逐文件读完，不跳读、不猜测。发现 **3 处真实缺陷**（均已修复并反向验证），
其余 38 个文件判为「未发现缺陷」。

### 缺陷 #4（已修）：`manifest.version` 未做字符白名单 → 本地插件任意路径写入

**位置**：`core/tools/ToolManifestValidator.kt` + `core/tools/ToolRegistry.kt:importLocal`

**成因**：`validateAll` 对 `manifest.version` 只做 `isNotBlank()`。而
`ToolRegistry.importLocal` 用它直接拼落盘路径：

```kotlin
val versionDir = File(localRoot, "${manifest.id}/${manifest.version}")
```

`id` 有 `idPattern = Regex("[a-z0-9][a-z0-9-]{1,63}")` 全锚定白名单，`version` 没有。
于是版本号填 `"../../../shared_prefs/x"` 时，`File` 归一化后写到了 `files/plugins` **之外**。

**放大条件**：`importLocal` 的「同版本覆盖」分支会先
`versionDir.renameTo(legacy)` 把已存在的目标整体搬走，写入后再 `legacy.deleteRecursively()`。
当 `versionDir` 逃逸到应用私有目录中的敏感路径时，这就从「越权写入」升级为
**「先搬走、再写入、最后删除原物」的任意覆盖/删除原语**。

**修复**（两层，白名单 + 纵深防御）：

1. `ToolManifestValidator` 新增 `versionPattern = Regex("[A-Za-z0-9][A-Za-z0-9._+-]{0,63}")`，
   对 `version` 与 `latestVersion` 做**全锚定**匹配。该白名单覆盖 `1.2.0`、`1.2.0-beta.1`、
   `v2`、`2025.10.07`、`1.0.0+build.42` 等常见写法，不误伤。
2. `importLocal` 落盘前再加一次规范化包含性检查：
   `versionDir.canonicalFile.toPath().startsWith(localRoot.canonicalFile.toPath())`，
   保证「校验层被绕过时仍有第二道闸」。

### 缺陷 #5（已修）：`maxByOrNull { it.name }` 用字典序挑版本 → 静默取旧 payload

**位置**：`core/tools/ToolRegistry.kt:loadLocalManifests`

**成因**：在 `plugins/<id>/<version>/` 的候选目录里用 `it.name` 的 `Comparable` 取最大，
而 `String` 是**字典序**：

```
max("1.9.0", "1.10.0") == "1.9.0"     // 错，应为 1.10.0
max("1.9.0", "1.11.0") == "1.9.0"     // 错，应为 1.11.0
```

**后果是静默的**：用户导入并成功安装了 1.10.0，`localPayloadRoot()` 仍指向 1.9.0 的
payload 目录，装出来的永远是旧内容，**不报任何错误**。这类「没报错所以以为没问题」
的缺陷只能靠版本比较本身的正反向断言兜住。

**修复**：改为读取每个候选目录的 `manifest.json` 声明版本，用新增的顶层函数
`compareToolVersions` 做**数值化优先**比较（`\d+(\.\d+){0,3}` 逐段比，段数不足补 0），
数字段全等即返回 0；仅当任一侧完全提取不到数字时才退化为字典序兜底。
提取为顶层 `internal` 函数是为了不构造 `ToolRegistry`（需真实 `Context`）即可做单元回归。

### 缺陷 #6（已修）：同包两条路径对「重复 manifest.json」判定不一致

**位置**：`core/tools/ToolRegistry.kt:importLocal`

**成因**：`inspectSeekableManifest` 有
`require(manifestEntry == null) { "插件包包含重复的 manifest.json" }`，
但真正落盘的 `importLocal` **没有**这道检查，而是
`if (name == "manifest.json") manifestText = target.readText()` ——静默采用**最后一个**。

**后果**：同一个 ZIP 在「预览」与「导入」两条路径给出不同结论。攻击者可以把
**通过校验的 manifest 放在前面**充当用户看到的预览，把**真正生效的放在后面**，
预览所见与最终导入的清单不是同一份。

**修复**：`importLocal` 增加同一判定——
`if (name == "manifest.json") { require(manifestText == null) { "插件包包含重复的 manifest.json" } }`，
两条路径必须同一结论。

### 反向验证（守卫自身必须能被证伪）

| 测试 | 回退修复后 | 恢复修复后 |
| --- | --- | --- |
| `ToolManifestVersionPatternTest`（4 例） | **3 红** | 4 绿 |
| `ToolVersionOrderingTest`（5 例） | **4 红** | 5 绿 |

回退方式：把 `versionPattern` 两处 `require` 删除、把 `compareToolVersions` 忠实改回
`left.compareTo(right)`、删掉 `canonicalLocalRoot` 检查。删掉版本白名单后
`rejectsPathTraversalVersions` / `rejectsAbsoluteAndSeparatorVersions` /
`rejectsUnsafeLatestVersion` 三条立即转红；纯字典序下
`numericSegmentsBeatLexicographicOrder` / `picksHighestVersionFromCandidateDirectories` /
`missingSegmentsCompareAsZero` / `prefixesAndSuffixesDoNotBreakNumericOrdering` 四条立即转红。

> 教训记录：第一次回退时只删了 `return 0`（保留了数值比较），结果 5 例只红 1 例。
> 说明「反向验证必须忠实还原修复前实现」，否则守卫的强度会被高估——
> 数值比较本身才是这次修复的核心，只回退「平局处理」自然证不伪。

### 行数棘轮（上调 ToolRegistry，附理由）

`ToolRegistry.kt` 445 → **504**（基线同步至 504）。
净增 59 行全部是新增防御与注释：`compareToolVersions` 顶层函数 + 版本目录选择的
数值化比较 + `canonicalLocalRoot` 纵深防御 + 重复 manifest 一致性检查 + 逐条解释成因的注释。
本次为**上调**而非下调，理由已在上文逐条说明；`ToolManifestValidator.kt` 92 行，
低于 400 上限，不进基线。

### 其余 38 个文件判为「未发现缺陷」（含判断依据）

- **`ToolManager.kt`(1125)**：`installJobs` 为 `ConcurrentHashMap` 且登记用
  `putIfAbsent`、注销用**两参原子 `remove(key, value)`**（注释明写「写成
  `if (installJobs[id] === job) remove(id)` 会在两条语句之间被新任务占据同一 key，
  把新任务从表里抹掉，取消安装将再也找不到它」）；`uninstall` 必须用
  `containsKey`（注释明写「ConcurrentHashMap 覆写了 contains，使其等价于
  containsValue，写成 `toolId !in installJobs` 会去比对全部 Job 值，永远为真，
  这道闸门等于不存在」）；`Completed` 分支**先 commit 再落库**，注释完整论证了
  反过来会在进程被杀窗口期内造成「磁盘回旧版却永不提示更新、无法自愈」。
- **`RuntimeManager.kt`(245)**：`installMutex` 串行化全部 apt 事务；装前先
  `rm -rf /var/lib/dpkg/updates/* /var/lib/dpkg/lock*` + `dpkg --configure -a` 自愈上次
  被强杀留下的「已解包未配置」状态；`versionSatisfies` 支持 `>=`/`>`/`=` 前缀。
- **`SkillPackageParser.kt`(339)**：ZIP 与目录两条路径**共享同一组上限**
  （注释明写「目录来源不受信任程度相同，无上限的 walkTopDown + readBytes 会在超大目录上
  直接 OOM」）；逐块读取时同时扣减单文件与总配额，到顶后再 `zip.read()` 探一次以区分
  「刚好到顶」与「超出」（防「恰好等于上限」的合法包被误杀）；`sanitizeSkillId` 拒绝
  全下划线结果；`sanitizeSkillId(fallbackId)` 失败时回退随机 id 而不阻断解析。
- **`SkillPackageInspector.kt`(418)**：5 维静态审计（结构 / ELF 与危险扩展名 /
  Prompt 注入与越狱 / 破坏性命令与反弹 Shell / 敏感文件嗅探），
  `reportMatches` 每模式限 3 条防刷屏；`OBFUSCATION_HINT_PATTERNS` 明确定性为
  WARNING 级人工复核（注释：「黑名单无法穷举」）。
- **`ClawHubClient.kt`(415)**：`lastCatalogUsedOfflineFallback` 用 `@Volatile`；
  下载用 `BoundedStreamCopy` + `OverflowPolicy.ABORT`；`contentLength()` 超限先拒；
  离线精选包由内置模板动态生成标准 ZIP。
- **`LocalPluginPayloadManager.kt`(118)**：`require(target.canonicalFile.toPath()
  .startsWith(canonicalRoot.toPath()))` 防工具 ID 逃逸；逐文件复制并保证
  `copiedBytes == totalBytes` 时必报一次进度；`normalizeShellScript` 去 BOM + CRLF→LF +
  补 shebang（注释：「Windows-created ZIPs commonly carry BOM/CRLF and no executable bit」）。
- **`RuntimeManager` / `ToolNotificationNotifier.kt`(264)**：`stageApkForInstall` 复制后
  `check(staged.length() == apk.length())` 校验完整性，并清理 24 小时前的旧暂存；
  `showBuildSuccess` 先 `cancel` 再 `notify`（注释：「Some Android/OEM notification
  managers keep the previous ongoing notification row when it is changed in-place to a
  non-ongoing one」）。
- **`di/tools/KoinModule.kt`(168)**：`SkillCompatibilityEvaluator(toolRegistry = getOrNull())`
  用 `getOrNull` 容忍 ToolRegistry 缺失；依赖图无环。

### 测试

`tools` **+5**（新增 `ToolVersionOrderingTest`）、`app` **+4**
（新增 `ToolManifestVersionPatternTest`）。全项目 `1089 tests, 0 failures, 0 errors`（`architectureCheck` 绿）。
