package top.wkbin.tianxuan.core.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SKILL.md frontmatter 解析的规范对齐测试（借鉴 OpenMinis / Claude Code 技能生态）。
 */
class AgentSkillFrontmatterTest {

    @Test
    fun `parses simple single line frontmatter`() {
        val md = """
            ---
            name: pdf-export
            description: Export documents to PDF
            ---

            # Body
        """.trimIndent()
        val meta = AgentSkillRepository.parseFrontmatter(md)
        assertEquals("pdf-export", meta["name"])
        assertEquals("Export documents to PDF", meta["description"])
    }

    @Test
    fun `tolerates utf8 bom and crlf line endings`() {
        val md = "﻿---\r\nname: notes\r\ndescription: Obsidian vault skills\r\n---\r\nbody"
        val meta = AgentSkillRepository.parseFrontmatter(md)
        assertEquals("notes", meta["name"])
        assertEquals("Obsidian vault skills", meta["description"])
    }

    @Test
    fun `parses folded multiline description`() {
        val md = """
            ---
            name: web-research
            description: >-
              Research any topic across search
              engines and summarize findings.
            allowed-tools: [search, read]
            ---

            body
        """.trimIndent()
        val meta = AgentSkillRepository.parseFrontmatter(md)
        assertEquals(
            "Research any topic across search engines and summarize findings.",
            meta["description"],
        )
        // 未知字段（allowed-tools / license 等）保留在映射中供上游选用，
        // 技能导入只消费 name / description，其余自动忽略
        assertEquals("[search, read]", meta["allowed-tools"])
    }

    @Test
    fun `strips unquoted inline comments and quoted hashes survive`() {
        val md = """
            ---
            name: runner # main skill
            description: "runs #1 benchmarks"
            ---
        """.trimIndent()
        val meta = AgentSkillRepository.parseFrontmatter(md)
        assertEquals("runner", meta["name"])
        assertEquals("runs #1 benchmarks", meta["description"])
    }

    @Test
    fun `keys are case insensitive and body without frontmatter yields empty`() {
        val meta1 = AgentSkillRepository.parseFrontmatter("---\nName: a\n---\n")
        assertEquals("a", meta1["name"])
        assertTrue(AgentSkillRepository.parseFrontmatter("no frontmatter here").isEmpty())
        assertNull(AgentSkillRepository.extractSkillMetadata("plain text", "name"))
    }

    @Test
    fun `literal block and terminator ellipsis are handled`() {
        val md = "---\ndescription: |\n  line one\n  line two\n...\n"
        val meta = AgentSkillRepository.parseFrontmatter(md)
        assertEquals("line one line two", meta["description"])
    }
}
