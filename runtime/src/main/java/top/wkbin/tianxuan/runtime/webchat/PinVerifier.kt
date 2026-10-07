package top.wkbin.tianxuan.runtime.webchat

import java.security.MessageDigest

/**
 * 局域网页配对码的比对。
 *
 * 独立成文件有两个原因：可单测（比对逻辑不依赖 socket 与 HTTP 栈），
 * 以及不与 [WebChatBridgeServer] 的行数棘轮相冲突——防护逻辑不该把主文件顶过上限
 * （同 [top.wkbin.tianxuan.runtime.ftp.FtpBounceGuard] 的拆分理由）。
 *
 * ## 为什么不能用 `==`
 *
 * 配对码是 6 位十进制（100000..999999，约 20 bit）。Kotlin 的字符串 `==` 语义为
 * 逐字符比较并在首个不同字符处**提前返回**，比较耗时因此与「前导正确字符数」正相关。
 * 攻击者按响应耗时逐位收敛，平均 20 次量级即可猜中，而不是 10^6 次。
 *
 * 这不是纯理论风险：服务绑定通配地址（`InetSocketAddress(port)`），同网段任意主机
 * 可访问；响应头带 `Access-Control-Allow-Origin: *`；服务在监听期间长期常驻，
 * 且 [WebChatBridgeServer] 全程不做请求频率限制——构成稳定的远程计时旁路信道。
 *
 * 与 `ShellCommandFactory.verifyPin` 同因同解，故两处采用同一种比较方式
 * （`MessageDigest.isEqual` 按内容定长逐字节比较，无提前返回），避免同一问题
 * 出现两种写法。
 */
internal object PinVerifier {

    /**
     * 比对候选配对码与期望配对码。
     *
     * @param candidate 请求携带的 token（查询参数或 Authorization: Bearer）
     * @param expected 当前有效的配对码
     *
     * 候选缺失、期望为空（尚未生成配对码）时一律拒绝：若放行，
     * 未设置 pin 的服务会被空 token 直接通过。
     */
    fun matches(candidate: String?, expected: String): Boolean {
        if (candidate == null || expected.isEmpty()) return false
        return MessageDigest.isEqual(
            candidate.toByteArray(Charsets.UTF_8),
            expected.toByteArray(Charsets.UTF_8),
        )
    }
}
