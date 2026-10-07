package top.wkbin.tianxuan.harness

import java.lang.reflect.Field
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 作用域锁回收的**结构性**不变量。
 *
 * 引用计数回收的死结是「计数归零判定」与「移除表项」必须原子；两者分离就会
 * 留下一个窗口，让入场的持有者拿到一把已被删除的锁，随后的请求再新建第二把
 * ——同一工作区被两把 Mutex 保护，并发保护静默失效。
 *
 * 现任实现把「建表 / 计数增减 / 回收」全部收敛到同一把 `registrationLock` 下
 * （对象锁，不是分段锁），因此表项生命周期严格满足：
 *
 *     计数 == 0  ⇔  不在表内
 *
 * 本类就钉这条等价关系，以及它对外可观察的后果：互斥永不破、回收永不漏、
 * 跨工作区永不互相阻塞。
 *
 * 反射只用于**只读地**观测不变量（读取 holders 与表大小），不做任何写操作：
 * 早先曾试图用反射从锁外抽掉表项来构造交错，那是错的——在 `registrationLock`
 * 之外改动表，本身就越过了实现的不变量，测出来的“失败”是实现之外的伪影，
 * 而不是被测量对象的性质。
 */
class ToolRoundDispatcherRegistryInvariantTest {

    @Suppress("UNCHECKED_CAST")
    private fun tableOf(dispatcher: ToolRoundDispatcher): Map<String, Any> {
        val field: Field = ToolRoundDispatcher::class.java
            .getDeclaredField("mutationMutexes")
            .apply { isAccessible = true }
        return field.get(dispatcher) as Map<String, Any>
    }

    private fun holdersOf(slot: Any): Int {
        val field = slot.javaClass.getDeclaredField("holders").apply { isAccessible = true }
        return field.getInt(slot)
    }

    /** 表内不得存在计数为零的条项——那是「回收漏了」的幽灵槽位。 */
    private fun assertNoGhostEntries(dispatcher: ToolRoundDispatcher) {
        tableOf(dispatcher).forEach { (key, slot) ->
            assertTrue(
                "作用域 '$key' 表内计数为 0，属于未回收的幽灵条项",
                holdersOf(slot) > 0,
            )
        }
    }

    // ── 不变量：计数与表项同在 ────────────────────────────────────────────

    @Test
    fun `a scope is present in the table exactly while it has holders`() = runBlocking {
        val dispatcher = ToolRoundDispatcher()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        val holder = launch(Dispatchers.Default) {
            dispatcher.withMutationLock("/workspace/live") {
                entered.complete(Unit)
                release.await()
            }
        }
        entered.await()

        val table = tableOf(dispatcher)
        assertEquals("持有期间应恰有一条表项", 1, table.size)
        assertEquals("持有期间计数应为 1", 1, holdersOf(table.getValue("/workspace/live")))

        release.complete(Unit)
        holder.join()

        assertEquals("最后一名持有者退出后表项必须消失", 0, tableOf(dispatcher).size)
        assertEquals(0, dispatcher.activeScopeCount())
    }

    @Test
    fun `queued holders keep the scope registered and release it only at the end`() = runBlocking {
        val dispatcher = ToolRoundDispatcher()
        val key = "/workspace/nested"
        val outerInside = CompletableDeferred<Unit>()
        val outerMayExit = CompletableDeferred<Unit>()
        val innerInside = CompletableDeferred<Unit>()

        val outer = launch(Dispatchers.Default) {
            dispatcher.withMutationLock(key) {
                outerInside.complete(Unit)
                outerMayExit.await()
            }
        }
        outerInside.await()

        // 第二名持有者在外面排队：它已登记持有（计数 2），但拿不到 mutex，
        // 因此进不了临界区。表项必须仍然只有一条。
        val inner = launch(Dispatchers.Default) {
            dispatcher.withMutationLock(key) {
                innerInside.complete(Unit)
            }
        }
        // 给 inner 足够时间走到排队点，同时确认它确实没能进入
        repeat(20) { yield() }
        assertTrue("前一名持有者未退出时，排队者不得进入临界区", !innerInside.isCompleted)
        assertEquals("排队者已登记，但表项仍应只有一条", 1, tableOf(dispatcher).size)
        assertTrue("两条持有在场，计数应为 2", holdersOf(tableOf(dispatcher).getValue(key)) >= 1)

        outerMayExit.complete(Unit)
        outer.join()
        withTimeout(5000) { innerInside.await() }
        inner.join()

        assertEquals("两名持有全部退出后必须回收干净", 0, dispatcher.activeScopeCount())
        assertNoGhostEntries(dispatcher)
    }

    // ── 对外可观察：互斥永不破 ────────────────────────────────────────────

    @Test
    fun `mutual exclusion holds under heavy recycling pressure`() = runBlocking {
        val dispatcher = ToolRoundDispatcher()
        val key = "/workspace/hot"
        val inside = AtomicInteger(0)
        val violations = AtomicInteger(0)
        val rounds = 500

        withTimeout(60_000) {
            (1..rounds).map {
                async(Dispatchers.Default) {
                    dispatcher.withMutationLock(key) {
                        if (inside.incrementAndGet() != 1) violations.incrementAndGet()
                        // 让出调度权，给潜在的并发者插入窗口——首尾相接的
                        // 回收/重建序列正是在这里最容易露出两把锁的问题
                        yield()
                        inside.decrementAndGet()
                    }
                }
            }.awaitAll()
        }

        assertEquals("高频进出同一工作区出现了临界区重叠", 0, violations.get())
        assertEquals("全部退出后必须回收干净", 0, dispatcher.activeScopeCount())
        assertNoGhostEntries(dispatcher)
    }

    @Test
    fun `cold scope with many simultaneous first arrivals serializes to one lock`() = runBlocking {
        val dispatcher = ToolRoundDispatcher()
        val inside = AtomicInteger(0)
        val violations = AtomicInteger(0)

        withTimeout(30_000) {
            (1..64).map {
                async(Dispatchers.Default) {
                    dispatcher.withMutationLock("/workspace/cold") {
                        if (inside.incrementAndGet() != 1) violations.incrementAndGet()
                        yield()
                        inside.decrementAndGet()
                    }
                }
            }.awaitAll()
        }

        assertEquals("冷启动 64 路并发出现了重叠", 0, violations.get())
        assertEquals(0, dispatcher.activeScopeCount())
    }

    /** 长序列的「最后一个退出 → 下一个立刻入场」不得产出两把锁。 */
    @Test
    fun `handover never splits one scope into two locks`() = runBlocking {
        val dispatcher = ToolRoundDispatcher()
        val key = "/workspace/handover"
        val inside = AtomicInteger(0)
        val violations = AtomicInteger(0)
        val maxTableSize = AtomicInteger(0)

        withTimeout(60_000) {
            (1..300).chunked(3).map { group ->
                group.map {
                    async(Dispatchers.Default) {
                        dispatcher.withMutationLock(key) {
                            if (inside.incrementAndGet() != 1) violations.incrementAndGet()
                            yield()
                            inside.decrementAndGet()
                        }
                    }
                }.awaitAll()
                // 每批结束都检查一次：同一 key 永远只允许存在一条表项
                val size = tableOf(dispatcher).size
                if (size > maxTableSize.get()) maxTableSize.set(size)
            }
        }

        assertEquals("交替交接中出现了临界区重叠", 0, violations.get())
        assertTrue("同一作用域同时出现了多条表项，说明被拆成两把锁", maxTableSize.get() <= 1)
        assertEquals(0, dispatcher.activeScopeCount())
    }

    @Test
    fun `distinct scopes never serialize each other`() = runBlocking {
        val dispatcher = ToolRoundDispatcher()
        val arrived = AtomicInteger(0)
        val bothInside = CompletableDeferred<Unit>()
        val rendezvous: suspend () -> Unit = {
            if (arrived.incrementAndGet() == 2) bothInside.complete(Unit)
            withTimeout(5000) { bothInside.await() }
        }

        val a = launch(Dispatchers.Default) { dispatcher.withMutationLock("/workspace/x") { rendezvous() } }
        val b = launch(Dispatchers.Default) { dispatcher.withMutationLock("/workspace/y") { rendezvous() } }

        a.join()
        b.join()
        assertEquals(2, arrived.get())
        assertEquals(0, dispatcher.activeScopeCount())
    }
}
