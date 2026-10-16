package top.wkbin.tianxuan.runtime.pty

import top.wkbin.tianxuan.runtime.shell.LinuxSession
import top.wkbin.tianxuan.runtime.shell.SessionConfig
import top.wkbin.tianxuan.runtime.shell.TerminalOutput
import top.wkbin.tianxuan.runtime.shell.TerminalStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 真正的 PTY 会话：JNI forkpty 打开 master/slave，命令直接 exec 在 slave 上
 * （setsid + 控制终端），App 通过 master fd 读写。与 Termux 的 PTY 语义一致：
 * Ctrl+C / 任务控制 / SIGWINCH 缩放 / raw mode 全部可用。
 */
class NativePtySession(
    command: List<String>,
    hostEnvironment: Map<String, String>,
    private val config: SessionConfig,
    private val cleanupCallback: suspend () -> Unit = {},
    /** 就绪追踪配置；null 使用默认值。 */
    readinessConfig: PtyReadinessTracker.ReadinessConfig = PtyReadinessTracker.ReadinessConfig(),
) : LinuxSession {

    private val pair: IntArray = NativePty.openAndExec(
        command.toTypedArray(),
        hostEnvironment.entries.map { "${it.key}=${it.value}" }.toTypedArray(),
        "/",
        config.columns,
        config.rows,
    )
    private val masterFd: Int = pair[0]
    private val childPid: Int = pair[1]

    // PTY readiness 协议（对齐 DSH terminal-bash）：流式净化 + 就绪追踪。
    // TerminalSanitizer 剥离 CSI/OSC 转义并检测 OSC 133;D prompt 标记；
    // PtyReadinessTracker 通过标记+提示语+前台进程组判定 shell 就绪状态。
    private val sanitizer = TerminalSanitizer()
    private val readinessTracker = PtyReadinessTracker(readinessConfig)

    private val outputChannel = Channel<TerminalOutput>(OUTPUT_BUFFER_CAPACITY)
    private val closed = AtomicBoolean(false)

    /** 子进程只 reap 一次：PID 复用后再 waitpid 可能误收无关进程。 */
    private val reaped = AtomicBoolean(false)
    private val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val utf8Decoder = IncrementalUtf8Decoder(8192)

    private val readerJob: Job = sessionScope.launch {
        val buffer = ByteArray(8192)
        try {
            while (!closed.get()) {
                val n = NativePty.readFd(masterFd, buffer)
                if (n < 0) break
                if (n > 0) {
                    val rawText = decodeUtf8(buffer, n)
                    if (rawText.isNotEmpty()) {
                        // 流式净化：剥离 CSI/OSC 转义，检测 prompt 标记
                        val sanitized = sanitizer.push(rawText)
                        readinessTracker.onData(sanitized)
                        if (sanitized.text.isNotEmpty()) {
                            outputChannel.send(TerminalOutput(TerminalStream.STDOUT, sanitized.text))
                        }
                    }
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (io: IOException) {
            // fd 在关闭时被另一线程 close，视为 EOF
        }
        decodeUtf8End().takeIf { it.isNotEmpty() }?.let {
            outputChannel.send(TerminalOutput(TerminalStream.STDOUT, it))
        }
        reapChild()
        outputChannel.close()
    }

    /** 流式 UTF-8 解码，避免多字节字符跨 read 被截断。 */
    private fun decodeUtf8(buffer: ByteArray, length: Int): String {
        return utf8Decoder.decode(buffer, length)
    }

    private fun decodeUtf8End(): String = utf8Decoder.finish()

    /** 幂等收割子进程（reader 自然结束与 close() 都会调用，避免重复 waitpid）。 */
    private fun reapChild() {
        if (reaped.compareAndSet(false, true)) NativePty.waitPid(childPid)
    }

    override val output: Flow<TerminalOutput> = outputChannel.receiveAsFlow()

    override val pid: Long? get() = childPid.toLong()

    override val isAlive: Boolean
        get() = !closed.get() && NativePty.killPid(childPid, 0) == 0

    override suspend fun write(data: ByteArray) = withContext(Dispatchers.IO) {
        if (!closed.get()) {
            var offset = 0
            while (offset < data.size) {
                val written = NativePty.writeFd(masterFd, data, offset, data.size - offset)
                if (written <= 0) break
                offset += written
            }
        }
    }

    override suspend fun resize(columns: Int, rows: Int) = withContext(Dispatchers.IO) {
        if (!closed.get()) {
            NativePty.resizeFd(masterFd, columns.coerceIn(20, 400), rows.coerceIn(5, 200))
        }
    }

    override suspend fun interrupt() = write(byteArrayOf(3))

    /**
     * 查询当前 shell 就绪状态（对齐 DSH terminal-bash readiness）。
     *
     * 供 Agent/工具调用方判断 shell 是否已回到提示符（可接收新命令）。
     * 返回值 null 表示尚未就绪或无法判定。
     */
    fun readinessState(): PtyReadinessTracker.WaitReason? {
        return readinessTracker.poll(
            foreground = null, // 前台进程组状态暂不支持（需要额外的 /proc 查询）
            scrollbackHasOutput = true,
            sessionStatus = if (closed.get()) PtyReadinessTracker.SessionStatus.EXITED else PtyReadinessTracker.SessionStatus.RUNNING,
        )
    }

    /**
     * 重置就绪追踪（开始新命令时调用）。
     */
    fun resetReadiness() {
        readinessTracker.reset()
    }

    override suspend fun close() = withContext(Dispatchers.IO) {
        if (closed.compareAndSet(false, true)) {
            // 先终止子进程：子进程退出会关闭 slave 端，使 reader 阻塞中的 readFd 返回 EOF
            // 而自然退出——这是在不改 JNI 阻塞读语义的前提下，解开「reader 仍持 masterFd 时
            // 就 closeFd」竞态的唯一手段（贸然先 closeFd 可能让该 fd 编号被复用到别处）。
            // 探活防止 PID 复用后误杀无关进程；SIGHUP + grace + SIGKILL 让 shell 优雅退出
            if (NativePty.killPid(childPid, 0) == 0) {
                NativePty.killPid(childPid, 1) // SIGHUP
                delay(500) // 给 SIGHUP 优雅退出留窗口
                if (NativePty.killPid(childPid, 0) == 0) {
                    NativePty.killPid(childPid, 9) // SIGKILL 兜底
                }
                reapChild()
            }
            // 有界等待 reader 退出，确保确认没有线程再使用 masterFd 之后才 closeFd。
            withTimeoutOrNull(READER_JOIN_TIMEOUT_MS) { readerJob.join() }
            NativePty.closeFd(masterFd)
            outputChannel.close()
            sessionScope.cancel()
            runCatching { cleanupCallback() }
        }
    }

    companion object {
        // 输出缓冲：~8KB/帧 × 1024 ≈ 8MB 上限，配合 send 背压可在高负载下不丢数据。
        const val OUTPUT_BUFFER_CAPACITY = 1024
        /** close() 等待 reader 退出的上限；子进程已死时读取应立即返回 EOF，正常为毫秒级。 */
        private const val READER_JOIN_TIMEOUT_MS = 1_000L
    }
}
