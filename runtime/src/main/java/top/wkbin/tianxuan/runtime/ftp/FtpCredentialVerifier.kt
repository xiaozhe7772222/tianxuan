package top.wkbin.tianxuan.runtime.ftp

import java.security.MessageDigest

/**
 * FTP 登录口令的比对。
 *
 * 独立成文件有两个原因（与 [top.wkbin.tianxuan.runtime.webchat.PinVerifier] 的拆分理由同源）：
 * 可单测（比对逻辑不依赖 socket 与 FTP 协议栈），以及不与 [AndroidFtpServer] 的行数棘轮相冲突
 * ——防护逻辑不该把主文件顶过上限（同 [FtpBounceGuard] 的拆分理由）。
 *
 * ## 为什么不能用 `==`
 *
 * Kotlin 的字符串 `==` 逐字符比较并在首个不同字符处**提前返回**，比较耗时因此与
 * 「前导正确字符数」正相关。攻击者按响应耗时逐位收敛，无需枚举整个口令空间。
 *
 * ## 本场景为什么成立
 *
 * FTP 服务绑定通配地址，同网段任意主机可访问；服务在监听期间长期常驻；
 * [AndroidFtpServer] 对认证请求不做任何频率限制与失败锁定——与
 * [top.wkbin.tianxuan.runtime.webchat.PinVerifier] 记录的构成要件逐条一致，
 * 因此同样构成稳定的远程计时旁路信道。
 *
 * 注意这**不是**在补救协议本身：FTP 是明文协议，口令在网络上直接可嗅探。
 * 恒定时间比较消除的是主动攻击者逐字节猜解口令的通道，属于纵深防御的一层，
 * 不能替代「不要把 FTP 暴露到不可信网络」这一根本边界。
 */
internal object FtpCredentialVerifier {

    /**
     * 比对候选口令与期望口令。
     *
     * 期望口令为空（null/blank）时一律拒绝：此前 [AndroidFtpServer] 的判定是
     * `isNullOrBlank() || pass == password`，未设密码时放行任意口令，
     * 等于向局域网开放无密码的 rootfs 读写。免密访问有且只有一个显式入口：
     * `anonymousEnabled`。该语义在此显式保留，防止回退。
     */
    fun passwordMatches(candidate: String, expected: String?): Boolean {
        if (expected.isNullOrBlank()) return false
        return MessageDigest.isEqual(
            candidate.toByteArray(Charsets.UTF_8),
            expected.toByteArray(Charsets.UTF_8),
        )
    }
}
