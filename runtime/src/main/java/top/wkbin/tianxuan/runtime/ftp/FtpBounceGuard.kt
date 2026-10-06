package top.wkbin.tianxuan.runtime.ftp

/**
 * FTP bounce 防护的地址比对。
 *
 * 单列出来有两个原因：
 * - 可单测：判定逻辑不依赖 socket，单测无需起真实服务端
 * - 与 [AndroidFtpServer] 的行数棘轮解耦，避免防护逻辑把主文件顶过上限
 *
 * 背景：FTP 的两种数据连接模式都由客户端指定地址。
 * - PASV：服务端监听一个端口，但该端口在时间窗内任何主机都能抢连，
 *   故服务端要校验「连接来自谁」。
 * - PORT / EPRT（主动模式）：目标地址完全由客户端给出，服务端不做任何校验
 *   就会去连它指定的任意地址。
 *
 * 后者是 RFC 2577 所说的 FTP bounce：一个已认证的客户端能让服务端
 * 连接内网地址或第三方主机，形成扫描内网、代发数据的跳板。
 */
internal object FtpBounceGuard {

    /**
     * 数据连接地址是否来自控制连接的同一来源主机。
     *
     * @param candidate 数据连接的对端地址（PORT/EPRT 的目标，或 PASV 连接的来源）
     * @param controlPeer 控制连接的来源地址
     */
    fun isSamePeer(candidate: String?, controlPeer: String?): Boolean {
        // 任一侧缺失都无法判定同源。此时保守拒绝：宁可让合法客户端重试，
        // 也不能因为拿不到地址就放行一个可能指向内网的地址。
        if (candidate.isNullOrBlank() || controlPeer.isNullOrBlank()) return false
        return normalize(candidate) == normalize(controlPeer)
    }

    /**
     * 归一化地址文本。
     *
     * 两种情形会让同一台主机呈现为不同字符串：
     * - 大小写与首尾空白（IPv6 十六进制大小写混用很常见）
     * - IPv6 的 zone id（`fe80::1%wlan0`）。网卡重命名或索引变化会改写它，
     *   但那仍是同一台主机，所以比对前必须剥掉。
     *
     * 只做文本级归一，不用 InetAddress 反解：后者会把「未知」之类的占位值
     * 变成解析异常，制造新的失败路径。
     */
    private fun normalize(address: String): String {
        val bare = address.trim()
        val zoneCut = bare.indexOf('%')
        val withoutZone = if (zoneCut >= 0) bare.substring(0, zoneCut) else bare
        return withoutZone.lowercase()
    }
}

/** 主动模式（PORT / EPRT）解析出的数据连接目标。 */
internal data class FtpActiveTarget(val ip: String, val port: Int)

/**
 * PORT / EPRT 参数解析。
 *
 * 与 [FtpBounceGuard] 同文件：两者是「主动模式数据连接」这一件事的两半，
 * 放一起便于对照——解析出地址后必然要过 bounce 校验。
 *
 * 解析失败一律返回 null，由调用方回 501。刻意不抛异常：命令行解析面对的
 * 是不可信输入，抛异常会把一次协议错误升级成连接异常。
 */
internal object ActiveTargetParser {

    /** PORT h1,h2,h3,h4,p1,p2 —— 高位在前。 */
    fun parsePort(arg: String): FtpActiveTarget? {
        val parts = arg.split(",").map { it.trim() }
        if (parts.size != 6) return null
        val octets = parts.take(4).map { it.toIntOrNull() ?: return null }
        // 主动模式只能指向合法单播地址：0/8 与 127/8 都是本机，
        // 交给 bounce 校验去比对clientIp 即可，此处只保证字节可拼装
        if (octets.any { it !in 0..255 }) return null
        val high = parts[4].toIntOrNull() ?: return null
        val low = parts[5].toIntOrNull() ?: return null
        if (high !in 0..255 || low !in 0..255) return null
        return FtpActiveTarget(octets.joinToString("."), high * 256 + low)
    }

    /**
     * EPRT |proto|address|port| —— 首字符是分隔符。
     *
     * 分隔符由客户端自选（RFC 2428 允许任意分隔符），不能硬编码为 '|'：
     * 硬编码会把用其它分隔符的合法客户端全部误判为语法错误。
     */
    fun parseEprt(arg: String): FtpActiveTarget? {
        if (arg.isEmpty()) return null
        val delimiter = arg[0]
        val parts = arg.split(delimiter).filter { it.isNotEmpty() }
        if (parts.size < 3) return null
        // parts[0] 是协议号（1=IPv4，2=IPv6）。只确认它存在，不据此改写地址，
        // 因为地址文本本身已足够定位，且解析协议号容易与实际地址脱节
        val ip = parts[1]
        val port = parts[2].toIntOrNull() ?: return null
        if (ip.isBlank() || port !in 1..65535) return null
        return FtpActiveTarget(ip, port)
    }
}