package top.wkbin.tianxuan.runtime.ftp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PORT / EPRT 参数解析（[ActiveTargetParser]）。
 *
 * 重点在非法输入：这两个命令的参数直接来自不可信输入，解析器必须在任何
 * 畸形输入下返回 null，而不是抛异常或产出越界端口——后者会让服务端去
 * 连接攻击者指定的任意端点。
 */
class ActiveTargetParserTest {

    @Test
    fun `port command parses dotted quad with high byte first`() {
        val t = ActiveTargetParser.parsePort("192,168,1,10,4,210")!!
        assertEquals("192.168.1.10", t.ip)
        // 4*256+210 = 1234，必须按高位在前还原
        assertEquals(1234, t.port)
    }

    @Test
    fun `port command accepts zero port`() {
        assertEquals(0, ActiveTargetParser.parsePort("10,0,0,1,0,0")!!.port)
    }

    @Test
    fun `port command rejects wrong field count`() {
        assertNull(ActiveTargetParser.parsePort("192,168,1,10,4"))
        assertNull(ActiveTargetParser.parsePort("192,168,1,10,4,210,9"))
        assertNull(ActiveTargetParser.parsePort(""))
    }

    @Test
    fun `port command rejects non numeric or out of range octets`() {
        assertNull(ActiveTargetParser.parsePort("192,168,1,x,4,210"))
        assertNull(ActiveTargetParser.parsePort("192,168,1,300,4,210"))
        assertNull(ActiveTargetParser.parsePort("192,168,1,10,4,256"))
        assertNull(ActiveTargetParser.parsePort("192,168,1,-1,4,210"))
    }

    @Test
    fun `eprt parses ipv4 form`() {
        val t = ActiveTargetParser.parseEprt("|1|192.168.1.10|1234|")!!
        assertEquals("192.168.1.10", t.ip)
        assertEquals(1234, t.port)
    }

    @Test
    fun `eprt honours a non pipe delimiter`() {
        // RFC 2428 允许客户端自选分隔符，硬编码 '|' 会把这类合法客户端
        // 全部误判为语法错误
        val t = ActiveTargetParser.parseEprt("!1!192.168.1.10!1234!")!!
        assertEquals("192.168.1.10", t.ip)
        assertEquals(1234, t.port)
    }

    @Test
    fun `eprt accepts ipv6 address form`() {
        val t = ActiveTargetParser.parseEprt("|2|fe80::1|1234|")!!
        assertEquals("fe80::1", t.ip)
        assertEquals(1234, t.port)
    }

    @Test
    fun `eprt rejects malformed input`() {
        assertNull(ActiveTargetParser.parseEprt(""))
        assertNull(ActiveTargetParser.parseEprt("|1|"))
        assertNull(ActiveTargetParser.parseEprt("|1|192.168.1.10|"))
        // 端口非数字或越界：越界端口会让后续 Socket.connect 抛 IllegalArgumentException
        assertNull(ActiveTargetParser.parseEprt("|1|192.168.1.10|abc|"))
        assertNull(ActiveTargetParser.parseEprt("|1|192.168.1.10|70000|"))
        assertNull(ActiveTargetParser.parseEprt("|1|192.168.1.10|0|"))
        // 空地址会构造出无主机的 InetSocketAddress
        assertNull(ActiveTargetParser.parseEprt("|1||1234|"))
    }

    @Test
    fun `eprt never throws on hostile input`() {
        // 面对不可信输入，解析器必须始终返回结果或 null，不能抛异常
        val hostile = listOf(
            "|", "||", "|||", "|||||", "|a|b|c|", "\u0000\u0001\u0002",
            "|1|192.168.1.10|1234|extra|fields", "9999999999999999999999|1|h|1|",
        )
        for (arg in hostile) {
            val r = runCatching { ActiveTargetParser.parseEprt(arg) }
            assertTrue("解析 '$arg' 时抛异常了", r.isSuccess)
        }
        for (arg in hostile) {
            val r = runCatching { ActiveTargetParser.parsePort(arg) }
            assertTrue("解析 '$arg' 时抛异常了", r.isSuccess)
        }
    }
}