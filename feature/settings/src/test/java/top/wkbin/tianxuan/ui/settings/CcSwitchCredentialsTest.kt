package top.wkbin.tianxuan.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 凭据生成与弱口令判定。
 *
 * 守卫的意图：防止有人日后「为了方便」又把默认口令加回来。
 * 这类回退在code review 里很难被肉眼发现——它看起来只是「恢复默认值」，
 * 但实际等于把局域网可访问的控制台重新交给弱口令。
 */
class CcSwitchCredentialsTest {

    @Test
    fun `generated password has the expected length`() {
        assertEquals(CcSwitchCredentials.PASSWORD_LENGTH, CcSwitchCredentials.generatePassword().length)
    }

    @Test
    fun `generated passwords never repeat`() {
        // 连生成两次都不相同的概率可忽略。若这条挂了，说明随机源退化成固定种子。
        val samples = List(50) { CcSwitchCredentials.generatePassword() }
        assertEquals("50 次生成出现了重复，随机源可能已退化", 50, samples.toSet().size)
    }

    @Test
    fun `generated password only uses the unambiguous alphabet`() {
        val password = CcSwitchCredentials.generatePassword()
        // 字符集刻意剔除 0/O/1/l/I：口令要靠人眼从界面抄走，混淆字符会让人
        // 敲错后以为服务坏了。若此处失败，说明字符集被改宽了。
        for (ch in password) {
            assertTrue("出现了易混字符 '$ch'", ch in "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789")
        }
    }

    @Test
    fun `legacy weak passwords are still detected`() {
        // 存量用户（升级前就已装机）的口令文件里就是这些值，必须能识别出来并提示。
        for (weak in listOf("admin123", "admin", "123456", "password", "")) {
            assertTrue("未识别出弱口令 '$weak'", CcSwitchCredentials.isKnownWeakPassword(weak))
        }
    }

    @Test
    fun `generated strong password is not flagged as weak`() {
        assertFalse(CcSwitchCredentials.isKnownWeakPassword(CcSwitchCredentials.generatePassword()))
    }
}