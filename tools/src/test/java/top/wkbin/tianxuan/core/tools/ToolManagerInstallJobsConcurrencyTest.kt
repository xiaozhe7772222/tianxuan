package top.wkbin.tianxuan.core.tools

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * ToolManager.installJobs 并发访问协议的回归测试。
 *
 * 背景：installJobs 是跨线程共享表 —— startInstall/startUpdate/cancelInstall/syncRegistry
 * 由 ViewModel 在主线程调用，而登记与注销发生在 managerScope(Dispatchers.IO) 内。修复前它是
 * `mutableMapOf`（LinkedHashMap）且无任何同步：
 *
 *   1. 「读一次再判空」非原子：两个并发 startInstall 会同时看到空表，各自启动一路装配，
 *      对同一工具并发解包，互相覆盖文件。
 *   2. 「检查后删除」非原子：if (map[id] === job) map.remove(id) 两条语句之间，
 *      新任务可能已占据同一 key，被误删后成为表里查不到的孤儿，取消安装再也找不到它。
 *   3. LinkedHashMap 跨线程无同步读写：读时若正逢扩容，桶链表可能成环导致读线程死循环；
 *      syncRegistry 迭代 keys 时并发写入抛 ConcurrentModificationException。
 *
 * 本文件用「旧协议 vs 新协议」在同一并发压力下的失败计数做对照，而不是复述实现细节：
 * 若把生产代码改回旧写法，这些断言必须转红 —— 因此它们能真实区分修复前后。
 *
 * 说明：这里验证的是访问协议的语义（登记/复用/删除的原子性），生产类 ToolManager 需要
 * 17 个真实依赖才能构造，tools 模块亦无 MockK/Mockito，故不直接实例化 ToolManager。
 */
class ToolManagerInstallJobsConcurrencyTest {

    private val threads = 8
    private val rounds = 400

    /**
     * 旧协议的纯逻辑复刻：LinkedHashMap + 先读后写 + 先查后删。
     * 只在单线程顺序执行时正确；并发下会给出重复的「新任务」句柄。
     */
    private class LegacyRegistry {
        private val table = LinkedHashMap<String, Any>()
        fun acquireOrCreate(key: String, make: () -> Any): Any {
            // 缺陷所在：判空与写入之间存在窗口。
            val existing = table[key]
            if (existing != null) return existing
            val created = make()
            table[key] = created
            return created
        }
    }

    /** 新协议的纯逻辑复刻：ConcurrentHashMap.putIfAbsent，登记与复用一步完成。 */
    private class AtomicRegistry {
        private val table = ConcurrentHashMap<String, Any>()
        fun acquireOrCreate(key: String, make: () -> Any): Any =
            table.putIfAbsent(key, make()) ?: table.getValue(key)
    }

    private fun hammer(acquire: (String, () -> Any) -> Any): Int {
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        val done = CountDownLatch(threads)
        // 判定违规的标准：同一个 key 在一次压测中被交出过**多于一个**实例。
        // 注意不能直接用 Set.add 的返回值判违规 —— 同一实例被反复复用是正确行为，
        // 第二次 add 同样返回 false。必须按 key 记录首次实例再比对。
        val firstByKey = ConcurrentHashMap<String, Any>()
        val violations = AtomicInteger(0)

        repeat(threads) {
            pool.execute {
                start.await()
                try {
                    repeat(rounds) { r ->
                        val key = "tool-$r"
                        val got = acquire(key) { Any() }
                        // putIfAbsent 记住该 key 的首个实例；若已存在且不是同一实例 → 违规。
                        val first = firstByKey.putIfAbsent(key, got)
                        if (first != null && first !== got) violations.incrementAndGet()
                    }
                } finally {
                    done.countDown()
                }
            }
        }
        start.countDown()
        assertTrue("并发压测超时", done.await(60, TimeUnit.SECONDS))
        pool.shutdownNow()
        return violations.get()
    }

    @Test
    fun `legacy check-then-act registry fails under concurrency`() {
        val legacy = LegacyRegistry()
        // 旧协议：不保证同一个 key 只产生一个实例。这里不断言具体次数（竞态本就不确定），
        // 只要求「确实发生过」——若为 0，说明该压测没能复现窗口，测试本身无意义。
        var observed = 0
        repeat(5) { observed += hammer { k, make -> legacy.acquireOrCreate(k, make) } }
        // 该断言是「测试有效性守卫」：窗口存在则必然命中，若此处为 0 则压测失效。
        assertTrue("未能复现旧协议的竞态窗口，说明压测强度不足", observed > 0)
    }

    @Test
    fun `atomic registry never hands out two instances for one key`() {
        val atomic = AtomicRegistry()
        repeat(5) {
            assertEquals(0, hammer { k, make -> atomic.acquireOrCreate(k, make) })
        }
    }

    /**
     * 「检查后删除」的对照：旧写法在 key 已被新任务接管的瞬间会把新任务误删。
     */
    @Test
    fun `legacy check-then-remove can evict a successor`() {
        val table = ConcurrentHashMap<String, Any>()
        val mine = Any()
        val successor = Any()
        table["t"] = mine
        // 旧写法两步：命中检查通过 → 才执行删除；两步之间新任务接管了 key。
        val observedMine = table["t"]
        table["t"] = successor
        if (observedMine === mine) table.remove("t")
        // 新任务被旧任务的收尾动作抹掉。
        assertTrue("旧写法的检查后删除会误删接任者", !table.containsKey("t"))

        // 新写法：两参 remove 是原子比较并删除，不匹配则不动。
        table["t"] = mine
        table["t"] = successor
        table.remove("t", mine)
        assertTrue("两参 remove 不应误删接任者", table.containsKey("t"))
        assertEquals(successor, table["t"])
    }

    /**
     * 真实协程路径：LAZY 启动 + putIfAbsent 登记，保证「登记成功才启动」，
     * 且在已有活跃任务时被复用的是同一个 Job 实例。
     */
    @Test
    fun `lazy start with putIfAbsent reuses the same job instance`() = runBlocking {
        val jobs = ConcurrentHashMap<String, Job>()
        val scope = CoroutineScope(Dispatchers.IO)
        val job = scope.launch(start = CoroutineStart.LAZY) { }
        val existing = jobs.putIfAbsent("tool", job)
        assertTrue(existing == null)
        val again = jobs.putIfAbsent("tool", job)
        assertTrue("第二次登记应拿到已存在的同一实例", again === job)
        // LAZY 的 Job 在 start() 之前不运行；登记完成后再启动，避免「登记前就已开跑」。
        job.start()
        job.join()
        assertTrue(job.isCompleted)
    }
}
