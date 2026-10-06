package top.wkbin.tianxuan.harness

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import top.wkbin.tianxuan.core.model.AgentSkill
import top.wkbin.tianxuan.harness.prompt.renderSkillCatalog

/**
 * 借鉴 OpenMinis 技能模型的目录渲染契约：元数据常驻、正文按需（load_skill）。
 */
class SkillCatalogTest {

    private fun skill(id: String, name: String, desc: String = "用途描述", enabled: Boolean = true) =
        AgentSkill(
            id = id,
            name = name,
            description = desc,
            systemPrompt = "skill body",
            triggerCommand = null,
            iconName = "",
            isEnabled = enabled,
            isBuiltin = false,
            category = "自定义",
        )

    @Test
    fun `catalog lists enabled skills with guidance`() {
        val text = renderSkillCatalog(
            listOf(skill("s1", "pdf-export", "把文档导出为 PDF")),
            excludeIds = emptySet(),
        )
        assertTrue(text.contains("## 可用技能"))
        assertTrue(text.contains("load_skill"))
        assertTrue(text.contains("pdf-export"))
        assertTrue(text.contains("把文档导出为 PDF"))
    }

    @Test
    fun `excluded and disabled skills are omitted`() {
        val text = renderSkillCatalog(
            listOf(
                skill("s1", "active"),
                skill("s2", "disabled", enabled = false),
            ),
            excludeIds = setOf("s1"),
        )
        assertFalse(text.contains("active"))
        assertFalse(text.contains("disabled"))
        assertFalse(text.contains("## 可用技能"))
    }

    @Test
    fun `long descriptions are bounded`() {
        val text = renderSkillCatalog(
            listOf(skill("s1", "long", desc = "很长的描述".repeat(200))),
            excludeIds = emptySet(),
        )
        val line = text.lineSequence().first { it.startsWith("- ") }
        assertTrue(line.length < 200)
        assertTrue(line.endsWith("…"))
    }

    @Test
    fun `catalog is capped at 24 entries`() {
        val skills = (1..30).map { skill("s$it", "skill-$it") }
        val text = renderSkillCatalog(skills, excludeIds = emptySet())
        assertEquals(24, text.lineSequence().count { it.startsWith("- ") })
    }
}
