package top.wkbin.tianxuan.harness.mcp

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertTrue
import org.junit.Test
import top.wkbin.tianxuan.runtime.shell.LinuxSession
import top.wkbin.tianxuan.runtime.shell.TerminalOutput
import top.wkbin.tianxuan.runtime.shell.TerminalStream

class LinuxMcpStdioChannelTest {

    /** output 永不完成的会话：构造"泵挂起在 lines.send 上"的泄漏前提（消费方已离场）。 */
    private class EndlessSession : LinuxSession {
        override val isAlive = true
        override val output = flow {
            var i = 0
            while (true) {
                emit(TerminalOutput(TerminalStream.STDOUT, "{\"resp\":$i}\n"))
                delay(10)
            }
        }
        override suspend fun write(data: ByteArray) {}
        override suspend fun resize(columns: Int, rows: Int) {}
        override suspend fun interrupt() {}
        override suspend fun close() {}
    }

    /** 输出一行后正常完成的会话：锁定泵的正常收尾路径。 */
    private class CompletingSession : LinuxSession {
        override val isAlive = true
        override val output = flow {
            emit(TerminalOutput(TerminalStream.STDOUT, "{\"resp\":1}\n"))
        }
        override suspend fun write(data: ByteArray) {}
        override suspend fun resize(columns: Int, rows: Int) {}
        override suspend fun interrupt() {}
        override suspend fun close() {}
    }

    /** 连续吐出多条超长行后正常完成的会话：锁定"单帧熔断"行为。 */
    private class OversizedSpamSession(maxFrameChars: Int) : LinuxSession {
        override val isAlive = true
        override val output = flow {
            val oversizedLine = "x".repeat(maxFrameChars + 1024) + "\n"
            emit(TerminalOutput(TerminalStream.STDOUT, oversizedLine))
            emit(TerminalOutput(TerminalStream.STDOUT, oversizedLine))
            emit(TerminalOutput(TerminalStream.STDOUT, oversizedLine))
            emit(TerminalOutput(TerminalStream.STDOUT, "{\"ok\":true}\n"))
        }
        override suspend fun write(data: ByteArray) {}
        override suspend fun resize(columns: Int, rows: Int) {}
        override suspend fun interrupt() {}
        override suspend fun close() {}
    }

    @Test
    fun `consecutive oversized lines produce a single circuit-break error frame`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val maxFrameChars = 64
        val channel = LinuxMcpStdioChannel("srv", OversizedSpamSession(maxFrameChars), scope = scope, maxFrameChars = maxFrameChars)
        val frames = mutableListOf<String>()
        withTimeoutOrNull(2_000L) {
            for (frame in channel.incoming) {
                frames += frame
            }
        }
        scope.cancel()

        val errorFrames = frames.filter { it.contains("-32603") }
        assertTrue("连续超长行只应广播一条熔断帧，实际 ${errorFrames.size} 条: $frames", errorFrames.size == 1)
        assertTrue("正常帧应照常转发", frames.any { it.contains("\"ok\":true") })
    }

    @Test
    fun `close unwinds a pump stalled on a full send buffer`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        // 容量 1：泵发出第 2 行即挂起在 send 上，session.close() 无法 resume 它——
        // close() 若不取消 scope，泵与 Channel 永久泄漏（每次销毁忙连接漏一个）
        val channel = LinuxMcpStdioChannel("srv", EndlessSession(), scope = scope, bufferCapacity = 1)
        delay(200)
        channel.close()
        val drained = withTimeoutOrNull(2_000L) {
            while (!channel.incoming.receiveCatching().isClosed) {
                // 逐条排空缓冲，直到拿到关闭标记
            }
            true
        }
        assertTrue("close() must terminate the pump and close the lines channel", drained == true)
        scope.cancel()
    }

    @Test
    fun `pump closes the lines channel when the session output completes`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val channel = LinuxMcpStdioChannel("srv", CompletingSession(), scope = scope, bufferCapacity = 8)
        val drained = withTimeoutOrNull(2_000L) {
            while (!channel.incoming.receiveCatching().isClosed) {
                // 正常完成路径同样要拿到关闭标记
            }
            true
        }
        assertTrue("output completion must close the lines channel", drained == true)
        scope.cancel()
    }
}
