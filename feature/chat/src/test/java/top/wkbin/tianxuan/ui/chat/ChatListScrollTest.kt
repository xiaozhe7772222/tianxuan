package top.wkbin.tianxuan.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatListScrollTest {

    @Test
    fun `empty or unmeasured list has no scroll target`() {
        assertNull(lastLazyItemIndex(0))
        assertNull(lastLazyItemIndex(-1))
    }

    @Test
    fun `non-empty list scrolls to the last laid-out item`() {
        assertEquals(0, lastLazyItemIndex(1))
        assertEquals(11, lastLazyItemIndex(12))
    }
}
