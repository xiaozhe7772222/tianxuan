package top.wkbin.tianxuan.lifecycle

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class RuntimeLifecycleSupervisorTest {

    @Test
    fun leaseLifecycle_singleLease() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val supervisor = RuntimeLifecycleSupervisor(app)

        assertFalse(supervisor.isActive.value)
        assertEquals(0, supervisor.holderCount())

        val lease = supervisor.acquireLease("test-holder")
        assertTrue(supervisor.isActive.value)
        assertEquals(1, supervisor.holderCount())
        assertEquals("test-holder", lease.holderId)

        lease.close()
        assertFalse(supervisor.isActive.value)
        assertEquals(0, supervisor.holderCount())

        // 幂等关闭测试
        lease.close()
        assertFalse(supervisor.isActive.value)
        assertEquals(0, supervisor.holderCount())
    }

    @Test
    fun leaseLifecycle_multipleHolders() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val supervisor = RuntimeLifecycleSupervisor(app)

        val lease1 = supervisor.acquireLease("holder-1")
        val lease2 = supervisor.acquireLease("holder-2")

        assertTrue(supervisor.isActive.value)
        assertEquals(2, supervisor.holderCount())
        assertEquals(setOf("holder-1", "holder-2"), supervisor.holderSnapshot())

        lease1.close()
        assertTrue(supervisor.isActive.value)
        assertEquals(1, supervisor.holderCount())
        assertEquals(setOf("holder-2"), supervisor.holderSnapshot())

        lease2.close()
        assertFalse(supervisor.isActive.value)
        assertEquals(0, supervisor.holderCount())
    }

    @Test
    fun leaseLifecycle_concurrentAcquireAndRelease() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val supervisor = RuntimeLifecycleSupervisor(app)

        val threadCount = 8
        val iterationsPerThread = 50
        val executor = Executors.newFixedThreadPool(threadCount)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threadCount)

        for (i in 0 until threadCount) {
            executor.execute {
                try {
                    startLatch.await()
                    for (j in 0 until iterationsPerThread) {
                        val lease = supervisor.acquireLease("thread-$i-$j")
                        assertTrue(supervisor.holderCount() >= 1)
                        lease.close()
                    }
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        startLatch.countDown()
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS))
        executor.shutdown()

        assertEquals(0, supervisor.holderCount())
        assertFalse(supervisor.isActive.value)
    }

    /**
     * 同名持有者必须各自独立计数。
     *
     * 回归背景：此前 `holders` 是 `MutableSet<String>`，直接装调用方传来的
     * holderId。于是同一个 holderId 的两次 acquire 只对应集合里的一个元素，
     * 但返回的是两个句柄——第二个句柄 `close()` 时就把第一个仍在使用中的持有
     * 一起抹掉了：`holders` 变空 → 立即 `releaseLocks()` → WakeLock/WifiLock
     * 被释放 → 息屏后 CPU 冻结，Agent 推理与 PRoot 构建中断。
     *
     * 这在 Agent 前台服务上是真实路径：[acquireLease] 既在 onStartCommand 里调用，
     * 也在运行态轮询里调用，而 `powerLease` 字段只记得住最后一个句柄。
     */
    @Test
    fun leaseLifecycle_sameHolderIdDoesNotCollide() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val supervisor = RuntimeLifecycleSupervisor(app)

        val first = supervisor.acquireLease("agent")
        val second = supervisor.acquireLease("agent")
        val third = supervisor.acquireLease("agent")

        assertEquals("三次同名申请应登记三次持有", 3, supervisor.holderCount())
        assertTrue(supervisor.isActive.value)

        // 关掉最早那个句柄，前两者之外的持有必须原封不动
        second.close()
        assertEquals(2, supervisor.holderCount())
        assertTrue("仍有持有者时不得释放保活锁", supervisor.isActive.value)

        first.close()
        assertEquals(1, supervisor.holderCount())
        assertTrue("最后一个持有者仍在，保活锁必须保持", supervisor.isActive.value)

        third.close()
        assertEquals(0, supervisor.holderCount())
        assertFalse(supervisor.isActive.value)
    }

    /** 同名持有者重复与异名持有者混用时，计数仍逐一对应。 */
    @Test
    fun leaseLifecycle_mixedSameAndDistinctHolders() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val supervisor = RuntimeLifecycleSupervisor(app)

        val agent1 = supervisor.acquireLease("agent")
        val agent2 = supervisor.acquireLease("agent")
        val runtime = supervisor.acquireLease("runtime-foreground-service")

        assertEquals(3, supervisor.holderCount())
        assertEquals(
            setOf("agent", "runtime-foreground-service"),
            supervisor.holderSnapshot(),
        )

        agent1.close()
        agent2.close()
        assertEquals("Agent 两个句柄关完，Runtime 的持有必须还在", 1, supervisor.holderCount())
        assertTrue(supervisor.isActive.value)

        runtime.close()
        assertEquals(0, supervisor.holderCount())
        assertFalse(supervisor.isActive.value)
    }

    /** 句柄重复 close 幂等，且不得连带释放他人的持有。 */
    @Test
    fun leaseLifecycle_repeatedCloseOfOneHandleIsIdempotent() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val supervisor = RuntimeLifecycleSupervisor(app)

        val kept = supervisor.acquireLease("workflow")
        val dropped = supervisor.acquireLease("workflow")

        repeat(5) { dropped.close() }

        assertEquals("重复关闭同一句柄不应多扣持有", 1, supervisor.holderCount())
        assertTrue(supervisor.isActive.value)

        // 关掉曾经被误伤过的那个句柄之后，计数必须归零——说明它一直有效
        kept.close()
        assertEquals(0, supervisor.holderCount())
        assertFalse(supervisor.isActive.value)
    }
}
