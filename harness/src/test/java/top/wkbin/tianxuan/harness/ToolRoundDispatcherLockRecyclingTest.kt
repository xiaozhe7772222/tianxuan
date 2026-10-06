package top.wkbin.tianxuan.harness

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 作用域锁的引用计数回收。
 *
 * 两组关注点，必须分开验：
 * - 回收：调度器是进程级单例，键是工作区路径，不回收就是按工作区数量增长的泄漏
 * - 正确性：回收不能误伤并发。若计数算错，会出现「锁被删掉后两个持有者共用
 *   一把已解锁的 Mutex」，并发保护静默失效——那比泄漏严重得多，且更难察觉
 */
class ToolRoundDispatcherLockRecyclingTest {

    @Test
    fun `scope entry is removed after the last holder finishes`() = runBlocking {
        val dispatcher = ToolRoundDispatcher()

        dispatcher.withMutationLock("/workspace/a") { }
        assertEquals("锁用完后不该留在表里", 0, dispatcher.activeScopeCount())

        dispatcher.withMutationLock("/workspace/a") { }
        dispatcher.withMutationLock("/workspace/b") { }
        assertEquals("两个作用域先后用完都应被回收", 0, dispatcher.activeScopeCount())
    }

    @Test
    fun `many distinct workspaces do not accumulate`() = runBlocking {
        val dispatcher = ToolRoundDispatcher()

        // 模拟长期使用的设备：用户开过 500 个工作区
        repeat(500) { i ->
            dispatcher.withMutationLock("/workspace/project-$i") { }
        }
        assertEquals(
            "500 个不同工作区用完后应全部回收，实际残留 ${dispatcher.activeScopeCount()} 个作用域",
            0,
            dispatcher.activeScopeCount(),
        )
    }

    @Test
    fun `entry stays alive while a holder is still inside`() = runBlocking {
        val dispatcher = ToolRoundDispatcher()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        val holder = launch {
            dispatcher.withMutationLock("/workspace/live") {
                entered.complete(Unit)
                release.await()
            }
        }
        entered.await()
        // 持有者仍在临界区内，锁必须留着，否则等待者会拿到另一把锁而直接进入
        assertEquals(1, dispatcher.activeScopeCount())

        release.complete(Unit)
        holder.join()
        assertEquals(0, dispatcher.activeScopeCount())
    }

    @Test
    fun `mutual exclusion still holds with many sequential holders`() = runBlocking {
        val dispatcher = ToolRoundDispatcher()
        val concurrent = AtomicInteger(0)
        val violations = AtomicInteger(0)

        // 同一工作区连着 200 次串行进入，任何一次重叠都是漏保护
        repeat(200) {
            dispatcher.withMutationLock("/workspace/hot") {
                if (concurrent.incrementAndGet() != 1) violations.incrementAndGet()
                // 让出调度权，给潜在的并发者插入窗口
                kotlinx.coroutines.yield()
                concurrent.decrementAndGet()
            }
        }
        assertEquals("同一工作区出现了并发进入", 0, violations.get())
    }

    @Test
    fun `mutual exclusion holds when holders arrive from many threads`() = runBlocking {
        val dispatcher = ToolRoundDispatcher()
        val concurrent = AtomicInteger(0)
        val violations = AtomicInteger(0)

        // 从 Default 调度器并发发起，抢在「建锁 + 计数」两步之间：
        // 若实现不是原子的，这里就会暴露为计数错乱进而漏保护
        withTimeout(20_000) {
            (1..64).map {
                async(Dispatchers.Default) {
                    dispatcher.withMutationLock("/workspace/racy") {
                        if (concurrent.incrementAndGet() != 1) violations.incrementAndGet()
                        kotlinx.coroutines.yield()
                        concurrent.decrementAndGet()
                    }
                }
            }.awaitAll()
        }
        assertEquals("64 路并发下出现了临界区重叠", 0, violations.get())
        assertEquals("全部完成后应无残留作用域", 0, dispatcher.activeScopeCount())
    }

    @Test
    fun `distinct workspaces still run in parallel`() = runBlocking {
        val dispatcher = ToolRoundDispatcher()
        val arrived = AtomicInteger(0)
        // 第二个进入临界区的人负责放行。不同工作区必须能同时进来，
        // 否则说明互斥被错误地退化成了全局串行——那会让所有工作区互相阻塞。
        val bothInside = CompletableDeferred<Unit>()
        val rendezvous: suspend () -> Unit = {
            if (arrived.incrementAndGet() == 2) bothInside.complete(Unit)
            bothInside.await()
        }

        val a = launch { dispatcher.withMutationLock("/workspace/x") { rendezvous() } }
        val b = launch { dispatcher.withMutationLock("/workspace/y") { rendezvous() } }

        withTimeout(5000) { bothInside.await() }
        a.join()
        b.join()
        assertTrue("两个工作区未能同时进入临界区，互斥过度串行化", bothInside.isCompleted)
        assertEquals(0, dispatcher.activeScopeCount())
    }

    @Test
    fun `blank scope keys share one global lock`() = runBlocking {
        val dispatcher = ToolRoundDispatcher()

        dispatcher.withMutationLock("") { }
        dispatcher.withMutationLock("   ") { }
        dispatcher.withMutationLock("  /real/workspace  ") { }

        // 空串与空白串必须归一到同一把全局锁，且用完即回收；
        // 带空白的工作区路径则归一到去空白后的键
        assertEquals(0, dispatcher.activeScopeCount())
    }
}