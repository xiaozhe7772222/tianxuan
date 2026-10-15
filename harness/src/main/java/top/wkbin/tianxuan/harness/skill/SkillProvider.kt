package top.wkbin.tianxuan.harness.skill

import kotlinx.serialization.Serializable

/**
 * 技能调用策略（对齐 DSH skill/src/index.ts 的 SkillInvocationPolicy）。
 *
 * 控制技能在模型可见目录和用户可见命令中的可调用性。
 */
@Serializable
data class SkillInvocationPolicy(
    /** 是否在模型可见目录和加载器中包含此技能。 */
    val modelInvocable: Boolean = true,
    /** 是否在用户可见命令目录和加载器中包含此技能。 */
    val userInvocable: Boolean = true,
)

/**
 * 技能来源桶（对齐 DSH SkillSource）。值是提示可见元数据，不是优先级本身。
 */
enum class SkillSource(val id: String) {
    PROJECT_DSH("project-dsh"),
    PROJECT_AGENTS("project-agents"),
    RUNTIME("runtime"),
    USER_DSH("user-dsh"),
    USER_AGENTS("user-agents"),
    CUSTOM("custom"),
    BUNDLED("bundled"),
}

/** 打包技能提供者的标准优先级（对齐 DSH BUNDLED_SKILL_RANK）。 */
const val BUNDLED_SKILL_RANK = 600

/** 运行时技能提供者的保留名。 */
const val RUNTIME_PROVIDER = "runtime"

/** 运行时技能的优先级（对齐 DSH RUNTIME_RANK）。 */
const val RUNTIME_RANK = 250

/** Kebab-case 技能名校验正则。 */
private val SKILL_NAME = Regex("^[a-z0-9]+(?:-[a-z0-9]+)*$")

/**
 * 返回字符串是否为合法的 kebab-case 技能名。
 */
fun isSkillName(name: String): Boolean = SKILL_NAME.matches(name)

/**
 * 技能摘要（对齐 DSH SkillSummary）——调用中立，供目录消费者使用。
 */
@Serializable
data class SkillSummary(
    /** Kebab-case 标识符，用于寻址技能。 */
    val name: String,
    /** 供目录消费者展示的路由描述。 */
    val description: String,
    /** 可选的额外路由指导。 */
    val whenToUse: String? = null,
    /** 解析后的模型和用户调用控制。 */
    val invocation: SkillInvocationPolicy = SkillInvocationPolicy(),
    /** 产生此技能的发现来源。 */
    val source: String = SkillSource.RUNTIME.id,
    /** 拥有此技能体的提供者名。 */
    val provider: String = RUNTIME_PROVIDER,
    /** 技能的绝对指令文件路径；虚拟技能可为 null。 */
    val path: String? = null,
    /** 提供者特定的资源基准，供加载的技能体解析相对资源。 */
    val resourceBase: SkillResourceBase? = null,
)

/**
 * 提供者目录条目（对齐 DSH SkillCandidate），注册表用于合并和后续加载。
 */
data class SkillCandidate(
    val summary: SkillSummary,
    /** 同名技能中低 rank 先赢；rank 相同按提供者注册序。 */
    val rank: Int,
    /** 不透明提供者句柄，回传给 provider.get()。 */
    val locator: Any,
    /** 提供者特定 frontmatter 元数据对象。 */
    val metadata: Map<String, Any>? = null,
)

/**
 * 完整解析的技能定义（对齐 DSH SkillDefinition），含 provider.get() 加载的正文。
 */
data class SkillDefinition(
    val summary: SkillSummary,
    /** Markdown 指令正文（去除提供者特定元数据后）。 */
    val content: String,
    /** Frontmatter 解析的元数据对象。 */
    val metadata: Map<String, Any>? = null,
)

/**
 * 运行时技能注册输入（对齐 DSH SkillRegistration）。
 */
data class SkillRegistration(
    val name: String,
    val description: String,
    val content: String,
    val whenToUse: String? = null,
    val invocation: SkillInvocationPolicy = SkillInvocationPolicy(),
    val source: String = SkillSource.RUNTIME.id,
    val provider: String? = null,
    val path: String? = null,
    val resourceBase: SkillResourceBase? = null,
    val metadata: Map<String, Any>? = null,
)

/** 提供者特定的资源基准，供加载的技能体解析相对资源。 */
@Serializable
sealed class SkillResourceBase {
    /** 目录基准。 */
    @Serializable
    data class Directory(val path: String) : SkillResourceBase()

    /** URL 基准。 */
    @Serializable
    data class Url(val url: String) : SkillResourceBase()

    /** 不透明基准。 */
    @Serializable
    data class Opaque(val description: String) : SkillResourceBase()
}

/** 技能查找选项。 */
data class SkillLookupOptions(
    /** 当前查找的工作区选择器。 */
    val cwd: String? = null,
)

/**
 * 技能提供者接口（对齐 DSH SkillProvider）——一个技能来源。
 */
interface SkillProvider {
    /** 注册表中唯一的提供者名。 */
    val name: String

    /**
     * 列出当前查找上下文的可用技能候选。提供者插件在 apply() 中同步注册；
     * 远程初始化、认证和发现在此方法内等待。实现应在 signal 中止时迅速 settle。
     */
    suspend fun list(options: SkillLookupOptions): List<SkillCandidate>

    /**
     * 为之前列出的候选加载完整技能体。
     * @param candidate 此提供者原始返回的获胜候选。
     * @return 完整技能体；不再可加载时返回 null。
     */
    suspend fun get(candidate: SkillCandidate, options: SkillLookupOptions): SkillDefinition?
}

/**
 * 目录观察结果（对齐 DSH SkillCatalogSnapshot）。
 */
data class SkillCatalogSnapshot(
    /** 本次观察收集的排序后调用中立摘要。 */
    val skills: List<SkillSummary>,
    /** 是否所有注册提供者在稳定目录版本内完成（可缓存）。 */
    val complete: Boolean,
)

/** 提供者候选及其发现权威性。 */
data class SkillProviderObservation(
    /** 当前提供者发现的候选。 */
    val candidates: List<SkillCandidate>,
    /** 发现完成且候选可缓存。 */
    val complete: Boolean,
)

/**
 * 渲染一个已加载的技能为模型可见格式（对齐 DSH renderSkillContent）。
 *
 * 输出由 `skill` 工具结果和用户显式调用注入共享，模型在两条路径上
 * 看到同一个 `<skill_content>` 形状。
 */
fun renderSkillContent(skill: SkillDefinition): String {
    val resourceHint = renderResourceHint(skill.summary)
    return buildString {
        append("""<skill_content name="${escapeAttr(skill.summary.name)}">""")
        append("\n<skill_resources>")
        append("\n")
        append(resourceHint)
        append("\n</skill_resources>")
        append("\n\n<skill_instructions>")
        append(skill.content)
        append("\n</skill_instructions>")
        append("\n</skill_content>")
    }
}

private fun renderResourceHint(summary: SkillSummary): String = when (val base = summary.resourceBase) {
    null -> """
        Resources for this skill are managed by provider "${escapeText(summary.provider)}".
        Load referenced resources only as needed.
    """.trimIndent()
    is SkillResourceBase.Directory -> """
        Base directory for this skill: ${escapeText(base.path)}
        Resolve relative paths mentioned by this skill against the base directory before using them.
        Load referenced resources only as needed.
    """.trimIndent()
    is SkillResourceBase.Url -> """
        Base URL for this skill: ${escapeText(base.url)}
        Resolve relative URLs mentioned by this skill against the base URL before using them.
        Load referenced resources only as needed.
    """.trimIndent()
    is SkillResourceBase.Opaque -> """
        Resources for this skill: ${escapeText(base.description)}
        Load referenced resources only as needed.
    """.trimIndent()
}

private fun escapeAttr(value: String): String =
    value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;")

private fun escapeText(value: String): String =
    value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
