package top.wkbin.tianxuan.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 社区坐标一致性测试。
 *
 * 群号曾以字面量散落在 6 个文件 13 处（含中英文资源与本地化映射键），
 * 改号必然漏改，且漏改不会报错、只会让用户加错群。现在群号只有
 * [Community.QQ_GROUP_ID] 一个来源，本测试锁住它的一致性。
 *
 * 注意：本测试只校验纯逻辑。资源文件里是否还残留硬编码群号由
 * `CommunityNoHardcodedGroupIdTest`（settings 模块）覆盖。
 */
class CommunityTest {

    @Test
    fun `群号是纯数字且非空`() {
        assertTrue("群号不应为空", Community.QQ_GROUP_ID.isNotBlank())
        assertTrue("群号应为纯数字，实际=${Community.QQ_GROUP_ID}",
            Community.QQ_GROUP_ID.all { it.isDigit() })
    }

    @Test
    fun `加群 scheme 内嵌的 uin 与群号一致`() {
        assertTrue(
            "QQ_GROUP_URI 必须携带 uin 参数，实际=${Community.QQ_GROUP_URI}",
            Community.QQ_GROUP_URI.contains("uin=${Community.QQ_GROUP_ID}"),
        )
    }

    @Test
    fun `加群 scheme 使用正确的卡片类型`() {
        // card_type=group 才是群卡片；写成 group_chat 会跳到群聊而非群资料页
        assertTrue(Community.QQ_GROUP_URI.contains("card_type=group"))
        assertTrue(Community.QQ_GROUP_URI.startsWith("mqqapi://card/show_pslcard"))
    }

    @Test
    fun `中英文文案的群号占位与常量一致`() {
        assertEquals(
            "群号展示文案应含群号",
            Community.QQ_GROUP_LABEL_ZH,
            "群号: ${Community.QQ_GROUP_ID} · 点击一键加群 / 复制群号",
        )
        assertEquals(
            "加群文案应含群号",
            Community.QQ_JOIN_LABEL_ZH,
            "加入 QQ 交流群 (${Community.QQ_GROUP_ID})",
        )
        assertTrue(
            "英文展示文案应含群号",
            Community.QQ_GROUP_LABEL_EN.contains(Community.QQ_GROUP_ID),
        )
        assertTrue(
            "英文加群文案应含群号",
            Community.QQ_JOIN_LABEL_EN.contains(Community.QQ_GROUP_ID),
        )
    }

    @Test
    fun `文案不写死第二个群号`() {
        // 文案里除本群号外不应出现另一串 6位以上数字（防止手写错群）
        listOf(
            Community.QQ_GROUP_LABEL_ZH,
            Community.QQ_GROUP_LABEL_EN,
            Community.QQ_JOIN_LABEL_ZH,
            Community.QQ_JOIN_LABEL_EN,
            Community.QQ_GROUP_URI,
        ).forEach { text ->
            Regex("""\d{6,}""").findAll(text).forEach { m ->
                assertEquals(
                    "文案中出现非当前群号的数字：${m.value}（全文=$text）",
                    Community.QQ_GROUP_ID,
                    m.value,
                )
            }
        }
    }
}