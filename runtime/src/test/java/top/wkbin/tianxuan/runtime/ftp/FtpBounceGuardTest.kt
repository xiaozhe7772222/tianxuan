package top.wkbin.tianxuan.runtime.ftp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FTP bounce 防护。
 *
 * 该守卫的价值全在「拒绝」这一侧，所以测试重心也放在反例：
 * 只要漏掉一类绕过，内网扫描与代发数据的风险就回来了。
 */
class FtpBounceGuardTest {

    @Test
    fun `identical addresses are same peer`() {
        assertTrue(FtpBounceGuard.isSamePeer("192.168.1.10", "192.168.1.10"))
    }

    @Test
    fun `case and surrounding whitespace do not matter`() {
        // socket 层的地址文本可能带 scope id 或大小写差异，同一主机不该被判成两台
        assertTrue(FtpBounceGuard.isSamePeer(" 192.168.1.10 ", "192.168.1.10"))
        assertTrue(FtpBounceGuard.isSamePeer("FE80::1", "fe80::1"))
    }

    @Test
    fun `a different host is rejected`() {
        // 这是 bounce 的核心用例：已认证的客户端让服务端去连内网另一台机器
        assertFalse(FtpBounceGuard.isSamePeer("192.168.1.11", "192.168.1.10"))
    }

    @Test
    fun `loopback target from a remote peer is rejected`() {
        assertFalse(FtpBounceGuard.isSamePeer("127.0.0.1", "192.168.1.10"))
    }

    @Test
    fun `external target is rejected`() {
        assertFalse(FtpBounceGuard.isSamePeer("203.0.113.5", "192.168.1.10"))
    }

    @Test
    fun `null candidate is rejected`() {
        // 拿不到对端地址时保守拒绝：放行等于把防护交给运气
        assertFalse(FtpBounceGuard.isSamePeer(null, "192.168.1.10"))
    }

    @Test
    fun `blank addresses are rejected on both sides`() {
        assertFalse(FtpBounceGuard.isSamePeer("", "192.168.1.10"))
        assertFalse(FtpBounceGuard.isSamePeer("   ", "192.168.1.10"))
        assertFalse(FtpBounceGuard.isSamePeer("192.168.1.10", ""))
        assertFalse(FtpBounceGuard.isSamePeer("192.168.1.10", null))
        assertFalse(FtpBounceGuard.isSamePeer(null, null))
    }

    @Test
    fun `ipv6 scope suffix is ignored`() {
        // 网卡重命名或索引变化会让 scope id 变，但那是同一台主机
        assertTrue(FtpBounceGuard.isSamePeer("fe80::1%wlan0", "fe80::1%eth0"))
    }

    @Test
    fun `lookalike addresses are not accepted`() {
        // 字符串前缀/后缀相似但并非同一主机，最容易被草率的 contains 判断放过
        assertFalse(FtpBounceGuard.isSamePeer("192.168.1.100", "192.168.1.10"))
        assertFalse(FtpBounceGuard.isSamePeer("1192.168.1.10", "192.168.1.10"))
        assertFalse(FtpBounceGuard.isSamePeer("192.168.1.10.evil.com", "192.168.1.10"))
    }
}