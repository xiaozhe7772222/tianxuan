package top.wkbin.tianxuan.runtime.tools

import org.junit.Assert.assertEquals
import org.junit.Test

class ToolLayoutTest {
    @Test
    fun keepsProgramDataAndCommandEntrypointsSeparated() {
        assertEquals("/opt/tianxuan/tools/openclaw", ToolLayout.toolDirectory("openclaw"))
        assertEquals("/opt/tianxuan/data/openclaw", ToolLayout.toolDataDirectory("openclaw"))
        assertEquals("/opt/tianxuan/bin/openclaw", ToolLayout.commandPath("openclaw"))
    }
}
