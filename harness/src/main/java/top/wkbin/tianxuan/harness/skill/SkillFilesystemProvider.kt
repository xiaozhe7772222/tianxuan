package top.wkbin.tianxuan.harness.skill

import kotlinx.coroutines.flow.first
import top.wkbin.tianxuan.core.database.AgentSkillRepository
import top.wkbin.tianxuan.core.database.SkillScanRoot
import top.wkbin.tianxuan.core.model.AgentSkill

/**
 * 文件系统技能提供者（对齐 DSH skill-filesystem 包）。
 *
 * 把天玄已有的 [AgentSkillRepository] 包装为 [SkillProvider] 接口，
 * 使技能注册表 [SkillRegistry] 可以发现并加载来自文件系统的技能。
 *
 * AgentSkillRepository 已实现：
 * - 从 .agents/skills/、attachments/skills/、workspace/skills/ 发现技能
 * - 解析 SKILL.md frontmatter（name、description 等）
 * - 嵌套目录递归扫描
 *
 * 本提供者将这些能力适配到 SkillProvider 接口。
 */
class SkillFilesystemProvider(
    private val repository: AgentSkillRepository,
    private val scanRoots: List<SkillScanRoot> = emptyList(),
) : SkillProvider {

    override val name: String = "filesystem"

    override suspend fun list(options: SkillLookupOptions): List<SkillCandidate> {
        // 如果提供了扫描根，先同步目录发现新技能
        if (scanRoots.isNotEmpty()) {
            runCatching { repository.syncFromDirectories(scanRoots) }
        }
        // 从数据库读取所有已注册技能
        val skills = repository.allSkills.first()
        return skills.map { skill -> toCandidate(skill) }
    }

    override suspend fun get(candidate: SkillCandidate, options: SkillLookupOptions): SkillDefinition? {
        // locator 是原始 AgentSkill 的 id
        val skillId = candidate.locator as? String ?: return null
        val skills = repository.allSkills.first()
        val skill = skills.firstOrNull { it.id == skillId } ?: return null
        return toDefinition(skill)
    }

    private fun toCandidate(skill: AgentSkill): SkillCandidate {
        val summary = SkillSummary(
            name = skill.name.lowercase().replace(Regex("\\s+"), "-"),
            description = skill.description,
            invocation = SkillInvocationPolicy(
                modelInvocable = skill.isEnabled,
                userInvocable = true,
            ),
            source = if (skill.isBuiltin) SkillSource.BUNDLED.id else SkillSource.CUSTOM.id,
            provider = name,
            path = skill.resourcePath,
        )
        return SkillCandidate(
            summary = summary,
            rank = if (skill.isBuiltin) BUNDLED_SKILL_RANK else RUNTIME_RANK,
            locator = skill.id,
            metadata = null,
        )
    }

    private fun toDefinition(skill: AgentSkill): SkillDefinition {
        val summary = SkillSummary(
            name = skill.name.lowercase().replace(Regex("\\s+"), "-"),
            description = skill.description,
            invocation = SkillInvocationPolicy(
                modelInvocable = skill.isEnabled,
                userInvocable = true,
            ),
            source = if (skill.isBuiltin) SkillSource.BUNDLED.id else SkillSource.CUSTOM.id,
            provider = name,
            path = skill.resourcePath,
        )
        return SkillDefinition(
            summary = summary,
            content = skill.systemPrompt,
        )
    }
}
