package top.wkbin.tianxuan.harness.checkpoint

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckpointStoreTest {

    private val store = CheckpointStore()

    @Test
    fun `capture without an active turn is ignored`() {
        assertFalse(store.capture("s", "a.txt", "x"))
    }

    @Test
    fun `capture dedups per path per turn and keeps first-touch content`() {
        store.beginTurn("s", "p0")
        assertTrue(store.capture("s", "a.txt", "turn0-start"))
        // 同轮再次触碰不覆盖：仍保留轮初内容
        assertFalse(store.capture("s", "a.txt", "turn0-after"))
        store.beginTurn("s", "p1")

        val checkpoints = store.checkpoints("s")
        assertEquals(1, checkpoints.size)
        assertEquals(listOf("a.txt"), checkpoints[0].changedFiles)
    }

    @Test
    fun `empty turn closes without creating a checkpoint`() {
        store.beginTurn("s", "p0")
        store.beginTurn("s", "p1")
        assertTrue(store.checkpoints("s").isEmpty())
    }

    @Test
    fun `turn numbers stay monotonic across read-only turns`() {
        // 只读轮（无 write/edit → 无 checkpoint）不得让后续轮号回退/复用：
        // 轮号是 UI 定位 checkpoint 后回传给 planCodeRewind 的边界，撞号会让撤回漏掉整轮改动。
        store.beginTurn("s", "p0")
        store.capture("s", "a.txt", null)
        store.beginTurn("s", "p1") // 只读轮：无 capture
        store.beginTurn("s", "p2")
        store.capture("s", "b.txt", "b2")
        store.beginTurn("s", "p3") // 只读轮：无 capture
        store.beginTurn("s", "p4")
        store.capture("s", "c.txt", "c4")
        store.beginTurn("s", "p5") // 关闭 turn4

        val turns = store.checkpoints("s").map { it.turn }
        assertEquals(listOf(0, 2, 4), turns)
    }

    @Test
    fun `rewind after read-only turns still reverts the intended turn`() {
        store.beginTurn("s", "p0")
        store.capture("s", "a.txt", "a0")
        store.beginTurn("s", "p1") // 只读轮
        store.beginTurn("s", "p2")
        store.capture("s", "a.txt", "a2")
        store.capture("s", "b.txt", "b2")
        store.beginTurn("s", "p3") // 只读轮

        // 轮号 2 才是写 a.txt/b.txt 的那一轮；撤回它必须把两者都还原到该轮轮初内容。
        val plan = store.planCodeRewind("s", 2).associate { it.path to it.content }
        assertEquals("a2", plan["a.txt"])
        assertEquals("b2", plan["b.txt"])

        // 只读轮不产生 checkpoint，按它的轮号撤回时命中的是"第一个不早于该轮号"的有写入轮。
        val fromReadOnlyTurn = store.planCodeRewind("s", 3).associate { it.path to it.content }
        assertTrue("只读轮之后无任何写入，不应产生回滚项", fromReadOnlyTurn.isEmpty())
    }

    @Test
    fun `anchor lookup works for read-only turns`() {
        store.beginTurn("s", "p0", anchorMessageId = "m0")
        store.capture("s", "a.txt", null)
        store.beginTurn("s", "p1", anchorMessageId = "m1") // 只读轮
        store.beginTurn("s", "p2", anchorMessageId = "m2")
        store.capture("s", "b.txt", "b2")
        store.beginTurn("s", "p3", anchorMessageId = "m3") // 关闭 turn2

        // 只有产生 checkpoint 的轮才有锚点可查（无写入轮不落 checkpoint，属既有约定）。
        assertEquals("m0", store.anchorMessageIdOf("s", 0))
        assertEquals("m2", store.anchorMessageIdOf("s", 2))
        assertEquals(null, store.anchorMessageIdOf("s", 1))
    }

    @Test
    fun `code rewind takes earliest snapshot per path starting from the target turn`() {
        // turn0: a.txt 不存在 → b.txt = "b0"
        store.beginTurn("s", "p0")
        store.capture("s", "a.txt", null)
        store.capture("s", "b.txt", "b0")
        // turn1: a.txt = "a1", c.txt = "c1"
        store.beginTurn("s", "p1")
        store.capture("s", "a.txt", "a1")
        store.capture("s", "c.txt", "c1")
        // turn2: a.txt = "a2"
        store.beginTurn("s", "p2")
        store.capture("s", "a.txt", "a2")

        // 撤回到 turn0：a.txt 恢复为"当时不存在 → null"、b.txt="b0"、c.txt="c1"
        val to0 = store.planCodeRewind("s", 0).associate { it.path to it.content }
        assertEquals(null, to0["a.txt"])
        assertEquals("b0", to0["b.txt"])
        assertEquals("c1", to0["c.txt"])

        // 撤回到 turn1：a.txt 恢复为 "a1"
        val to1 = store.planCodeRewind("s", 1).associate { it.path to it.content }
        assertEquals("a1", to1["a.txt"])
    }

    @Test
    fun `rewind plan is empty for out-of-range turns`() {
        store.beginTurn("s", "p0")
        store.capture("s", "a.txt", null)
        store.beginTurn("s", "p1")
        assertTrue(store.planCodeRewind("s", 5).isEmpty())
        assertTrue(store.planCodeRewind("s", -1).isEmpty())
    }

    @Test
    fun `retention keeps only the newest MAX_KEPT turns`() {
        repeat(CheckpointStore.MAX_KEPT + 10) { index ->
            store.beginTurn("s", "p$index")
            store.capture("s", "f$index.txt", "x")
        }
        assertEquals(CheckpointStore.MAX_KEPT, store.checkpoints("s").size)
    }

    @Test
    fun `failed disk restore is retried on next access`() {
        var reads = 0
        store.persistence = object : CheckpointStore.Persistence {
            override fun write(sessionId: String, checkpoint: Checkpoint) = Unit
            override fun delete(sessionId: String) = Unit
            override fun readAll(sessionId: String): List<Checkpoint> {
                reads++
                if (reads == 1) error("temporary disk failure")
                return listOf(Checkpoint(0, 1L, "saved", listOf(FileSnap("a.txt", "before"))))
            }
        }
        assertTrue(store.checkpoints("s").isEmpty())
        assertEquals(1, store.checkpoints("s").size)
        assertEquals(2, reads)
    }
}
