package top.wkbin.tianxuan.harness.session

import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import top.wkbin.tianxuan.harness.HarnessTool
import top.wkbin.tianxuan.harness.ToolCall
import top.wkbin.tianxuan.harness.ToolCallMode
import top.wkbin.tianxuan.harness.SkillSuggestion
import top.wkbin.tianxuan.harness.ToolResult
import top.wkbin.tianxuan.harness.UserMessage

class ApiMessageProjectorTest {

    private fun messages() = listOf(
        UserMessage(id = "u1", createdAt = 1, text = "看看这张图"),
        ToolCall(id = "c1", createdAt = 2, tool = HarnessTool.READ, args = buildJsonObject {}),
        ToolResult(
            id = "r1",
            createdAt = 3,
            toolCallId = "c1",
            success = true,
            output = "已读取图片文件 chart.png",
            imageDataUrl = "data:image/png;base64,AAAA",
        ),
    )

    @Test
    fun `ui only skill suggestion is not projected to the provider`() {
        val projected = ApiMessageProjector.project(
            listOf(
                UserMessage(id = "u1", createdAt = 1L, text = "hi"),
                SkillSuggestion(
                    id = "s1",
                    createdAt = 2L,
                    action = "create",
                    skillName = "demo",
                    description = "demo skill",
                    systemPrompt = "demo prompt",
                ),
            ),
            toolCallMode = ToolCallMode.NATIVE,
            visionEnabled = true,
        )
        assertEquals(listOf("user"), projected.map { it.role })
    }

    @Test
    fun `vision bridge appends image message when vision enabled`() {
        val projected = ApiMessageProjector.project(messages(), ToolCallMode.NATIVE, visionEnabled = true)
        val imageMessage = projected.last()
        assertEquals("user", imageMessage.role)
        assertEquals(listOf("data:image/png;base64,AAAA"), imageMessage.imageUrls)
        assertTrue(projected.any { it.role == "tool" })
    }

    @Test
    fun `vision bridge is skipped when vision disabled`() {
        val projected = ApiMessageProjector.project(messages(), ToolCallMode.NATIVE, visionEnabled = false)
        assertTrue(projected.none { it.imageUrls.isNotEmpty() })
    }
}