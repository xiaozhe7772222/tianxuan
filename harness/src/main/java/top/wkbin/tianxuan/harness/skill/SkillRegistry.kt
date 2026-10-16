package top.wkbin.tianxuan.harness.skill

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * 分层技能提供者注册表（对齐 DSH skill/src/index.ts 的 SkillRegistry 核心语义）。
 *
 * 汇聚多个提供者的目录、解析同名技能的获胜者、向消费者暴露获胜摘要与定义。
 *
 * 关键语义：
 * - **分层**：全局层 + 每个 scope（会话/代理预设）一层；调用方的 scope 链
 *   决定可见分层，最近的层以同名条目遮蔽远端层（对齐 DSH ScopedLayers）。
 * - **rank 决胜**：同名条目中 rank 低者获胜；rank 相同按提供者注册序。
 * - **收集缓存**：完整发现（complete=true）按 (cwd, scopeChain, revision) 缓存；
 *   任何注册/注入变化递增 revision 清除缓存。
 * - **目录摘要按名称排序**，稳定输出。
 * - **调用策略**：modelInvocable / userInvocable 决定技能暴露的面。
 *
 * @param collectCacheMaxEntries 完成的 cwd/提供者目录的缓存上限。
 */
class SkillRegistry(
    private val collectCacheMaxEntries: Int = DEFAULT_COLLECT_CACHE_ENTRIES,
) {
    private class IndexedCandidate(
        val candidate: SkillCandidate,
        val provider: SkillProvider,
        val providerOrder: Int,
        val localOrder: Int,
        val layer: SkillLayer,
    )

    /** 每个 scope 一个层，保存提供者与运行时技能。 */
    private class SkillLayer {
        val providers = LinkedHashMap<String, RegisteredProvider>()
        val runtime = LinkedHashMap<String, SkillDefinition>()

        fun isEmpty(): Boolean = providers.isEmpty() && runtime.isEmpty()
    }

    private class RegisteredProvider(
        val provider: SkillProvider,
        val order: Int,
    )

    private val globalLayer = SkillLayer()
    private val scopedLayers = ConcurrentHashMap<String, SkillLayer>()

    private val collectCache = ConcurrentHashMap<String, Map<String, IndexedCandidate>>()
    private val revision = AtomicLong(0)
    private val nextProviderOrder = AtomicInteger(0)

    private fun layerFor(scope: String?): SkillLayer =
        if (scope == null) globalLayer else scopedLayers.computeIfAbsent(scope) { SkillLayer() }

    private fun <T> withCleanup(name: String, dispose: () -> Unit = {}, block: () -> T): T {
        return try {
            block()
        } catch (e: Throwable) {
            dispose()
            throw e
        }
    }

    fun registerProvider(scope: String? = null, create: (SkillProviderControl) -> SkillProvider): () -> Unit {
        val lifecycle = SkillProviderControl { invalidateCache() }
        val provider = create(lifecycle)
        val name = provider.name
        if (name == RUNTIME_PROVIDER) {
            throw IllegalArgumentException("\"$RUNTIME_PROVIDER\" is reserved for runtime skill registrations")
        }
        val order = nextProviderOrder.getAndIncrement()
        val layer = layerFor(scope)
        layer.providers[name] = RegisteredProvider(provider, order)
        invalidateCache()
        return {
            layer.providers.remove(name)
            invalidateCache()
        }
    }

    /**
     * 注册一个借用的只读运行时技能到指定 scope 的层。
     *
     * Project 条目 rank 高于 runtime 条目，runtime 条目高于 user 条目（由
     * 调用方选择适当 rank）。同 scope 内同名 runtime 条目先到先得。
     *
     * @param skill 技能定义输入；省略的 invocation/provider 字段取默认值。
     * @param scope 注册 scope；null = 全局层。
     * @return 效果销毁器：注销技能并失效缓存。
     */
    fun register(skill: SkillRegistration, scope: String? = null): () -> Unit {
        validateRuntimeSkill(skill)
        val effective = SkillDefinition(
            summary = SkillSummary(
                name = skill.name,
                description = skill.description,
                whenToUse = skill.whenToUse,
                invocation = skill.invocation,
                source = skill.source,
                provider = skill.provider ?: RUNTIME_PROVIDER,
                path = skill.path,
                resourceBase = skill.resourceBase,
            ),
            content = skill.content,
            metadata = skill.metadata,
        )
        val layer = layerFor(scope)
        val existing = layer.runtime[skill.name]
        if (existing != null) {
            // 同 scope 同名 runtime 先到先得，重复警告并返回无操作销毁器
            return {}
        }
        layer.runtime[skill.name] = effective
        invalidateCache()
        return { layer.runtime.remove(skill.name); invalidateCache() }
    }

    /**
     * 列出工作区的调用中立技能摘要。消费者在操作边界应用模型或用户调用策略。
     */
    suspend fun list(options: SkillViewOptions = SkillViewOptions()): List<SkillSummary> =
        snapshot(options).skills

    /**
     * 观察当前调用中立目录与发现完成度。
     * 不完整观察永不缓存，允许消费者保留最后良好状态并在下一请求边界重试。
     */
    suspend fun snapshot(options: SkillViewOptions = SkillViewOptions()): SkillCatalogSnapshot {
        val collected = collect(options)
        return SkillCatalogSnapshot(
            skills = collected.entries.values
                .map { toSummary(it.candidate) }
                .sortedBy { it.name },
            complete = collected.cacheable,
        )
    }

    /**
     * 加载并验证获胜候选，将其不透明发现 locator 传回提供者。
     * @return 完整技能（含正文）；未找到或不再可加载时返回 null。
     */
    suspend fun get(name: String, options: SkillViewOptions = SkillViewOptions()): SkillDefinition? {
        if (!isSkillName(name)) return null
        val collected = collect(options)
        val match = collected.entries[name] ?: return null
        val definition = runCatching {
            match.provider.get(match.candidate, SkillLookupOptions(cwd = options.cwd))
        }.getOrNull() ?: return null
        validateDefinition(definition)
        if (definition.summary.name != match.candidate.summary.name) {
            invalidateEntry(match)
            return null
        }
        return definition
    }

    /**
     * 返回模型可调用的技能摘要列表（modelInvocable 过滤）。
     */
    suspend fun modelInvocableSkills(options: SkillViewOptions = SkillViewOptions()): List<SkillSummary> =
        list(options).filter { it.invocation.modelInvocable }

    /**
     * 返回用户可调用的技能摘要列表（userInvocable 过滤）。
     */
    suspend fun userInvocableSkills(options: SkillViewOptions = SkillViewOptions()): List<SkillSummary> =
        list(options).filter { it.invocation.userInvocable }

    private suspend fun collect(options: SkillViewOptions): CollectResult {
        val key = collectCacheKey(options.cwd, options.scope, revision.get())
        collectCache[key]?.let {
            // 每次尝试最多 2 次（对齐 DSH MAX_COLLECT_ATTEMPTS）：修订号在两次读取之间
            // 可能被改写，需按最新修订号重查一次缓存。原先 attempt 从不自增、两个分支
            // 都无条件 return，重试形同虚设。
            var attempt = 1
            while (attempt <= MAX_COLLECT_ATTEMPTS) {
                val currentRevision = revision.get()
                val current = collectCache[collectCacheKey(options.cwd, options.scope, currentRevision)]
                if (current != null) {
                    return CollectResult(current, cacheable = true)
                }
                attempt += 1
            }
            return collectFresh(options)
        }
        return collectFresh(options)
    }

    private suspend fun collectFresh(options: SkillViewOptions): CollectResult {
        // 全局层第一，然后 scope 链（最近层最后覆盖 → 遮蔽）
        val layers = listOf(globalLayer) + (options.scope?.let { listOfNotNull(scopedLayers[it]) } ?: emptyList())
        val merged = LinkedHashMap<String, IndexedCandidate>()
        var cacheable = true
        for (layer in layers) {
            val collected = collectLayer(layer, options)
            if (!collected.cacheable) cacheable = false
            for (entry in collected.entries) merged[entry.candidate.summary.name] = entry
        }
        val currentRevision = revision.get()
        if (cacheable) {
            collectCache[collectCacheKey(options.cwd, options.scope, currentRevision)] = merged
            if (collectCache.size > collectCacheMaxEntries) {
                val oldestKey = collectCache.keys.firstOrNull()
                if (oldestKey != null) collectCache.remove(oldestKey)
            }
        }
        return CollectResult(merged, cacheable)
    }

    private suspend fun collectLayer(layer: SkillLayer, options: SkillViewOptions): LayerCollectResult {
        val collected = listLayerCandidates(layer, options)
        val sorted = collected.entries.sortedWith { a, b -> compareIndexedCandidates(a, b) }
        val seen = mutableSetOf<String>()
        val result = mutableListOf<IndexedCandidate>()
        for (entry in sorted) {
            val skill = entry.candidate
            if (seen.contains(skill.summary.name)) continue
            seen.add(skill.summary.name)
            result.add(entry)
        }
        return LayerCollectResult(result, collected.cacheable)
    }

    private suspend fun listLayerCandidates(layer: SkillLayer, options: SkillViewOptions): LayerCollectResult {
        val candidates = mutableListOf<IndexedCandidate>()
        var cacheable = true
        var runtimeOrder = 0
        for (skill in layer.runtime.values.sortedBy { it.summary.name }) {
            candidates.add(
                IndexedCandidate(
                    candidate = runtimeCandidate(skill),
                    provider = RUNTIME_SKILL_PROVIDER,
                    providerOrder = -1,
                    localOrder = runtimeOrder,
                    layer = layer,
                )
            )
            runtimeOrder++
        }
        for ((providerName, registered) in layer.providers) {
            var localOrder = 0
            val output = runCatching { registered.provider.list(SkillLookupOptions(cwd = options.cwd)) }
            if (output.isFailure) {
                cacheable = false
                // 提供者失败：跳过，不丢弃其他候选
                continue
            }
            val candidates2 = output.getOrNull() ?: emptyList()
            if (!cacheable) cacheable = false
            for (candidate in candidates2) {
                runCatching { validateCandidate(candidate, providerName) }
                    .onFailure { continue }
                candidates.add(
                    IndexedCandidate(
                        candidate = candidate,
                        provider = registered.provider,
                        providerOrder = registered.order,
                        localOrder = localOrder,
                        layer = layer,
                    )
                )
                localOrder++
            }
        }
        return LayerCollectResult(candidates, cacheable)
    }

    private fun invalidateCache() {
        revision.incrementAndGet()
        collectCache.clear()
    }

    /** 仅在产生该条目的确切注册仍存活时使失效。 */
    private fun invalidateEntry(entry: IndexedCandidate) {
        val stored = entry.layer.providers[entry.provider.name]
        if (stored != null && stored.provider === entry.provider) invalidateCache()
    }

    private fun collectCacheKey(cwd: String?, scope: String?, revision: Long): String =
        "$cwd|$scope|$revision"

    private data class CollectResult(
        val entries: Map<String, IndexedCandidate>,
        val cacheable: Boolean,
    )

    private data class LayerCollectResult(
        val entries: List<IndexedCandidate>,
        val cacheable: Boolean,
    )

    private fun validateRuntimeSkill(skill: SkillRegistration) {
        require(isSkillName(skill.name)) { "invalid skill name \"${skill.name}\"" }
        require(skill.description.isNotEmpty()) { "skill \"${skill.name}\" requires a description" }
        require(skill.content.isNotEmpty()) { "skill \"${skill.name}\" requires content" }
    }

    private fun validateCandidate(candidate: SkillCandidate, providerName: String) {
        val summary = candidate.summary
        require(isSkillName(summary.name)) {
            "skill provider \"$providerName\" returned invalid skill name \"${summary.name}\""
        }
        require(summary.description.isNotEmpty()) {
            "skill provider \"$providerName\" returned skill \"${summary.name}\" without a description"
        }
        require(summary.provider == providerName) {
            "skill provider \"$providerName\" returned skill \"${summary.name}\" for provider \"${summary.provider}\""
        }
    }

    private fun validateDefinition(skill: SkillDefinition) {
        val summary = skill.summary
        require(isSkillName(summary.name)) { "loaded skill has invalid name \"${summary.name}\"" }
        require(summary.description.isNotEmpty()) { "loaded skill \"${summary.name}\" requires a description" }
        require(skill.content.isNotEmpty()) { "loaded skill \"${summary.name}\" requires content" }
    }

    private fun runtimeCandidate(skill: SkillDefinition): SkillCandidate = SkillCandidate(
        summary = SkillSummary(
            name = skill.summary.name,
            description = skill.summary.description,
            whenToUse = skill.summary.whenToUse,
            invocation = skill.summary.invocation,
            source = skill.summary.source,
            provider = skill.summary.provider,
            path = skill.summary.path,
            resourceBase = skill.summary.resourceBase,
        ),
        rank = RUNTIME_RANK,
        locator = skill,
        metadata = skill.metadata,
    )

    private fun toSummary(candidate: SkillCandidate): SkillSummary = candidate.summary

    private fun compareIndexedCandidates(a: IndexedCandidate, b: IndexedCandidate): Int =
        a.candidate.rank.compareTo(b.candidate.rank)
            .let { if (it != 0) it else a.providerOrder.compareTo(b.providerOrder) }
            .let { if (it != 0) it else a.localOrder.compareTo(b.localOrder) }

    companion object {
        private const val DEFAULT_COLLECT_CACHE_ENTRIES = 128
        private const val MAX_COLLECT_ATTEMPTS = 2

        private val RUNTIME_SKILL_PROVIDER = object : SkillProvider {
            override val name: String get() = RUNTIME_PROVIDER

            override suspend fun list(options: SkillLookupOptions): List<SkillCandidate> = emptyList()

            override suspend fun get(candidate: SkillCandidate, options: SkillLookupOptions): SkillDefinition? =
                candidate.locator as? SkillDefinition
        }
    }
}

/** 注册范围内的生命周期与失效能力，借给一个提供者。 */
class SkillProviderControl(
    private val onInvalidate: () -> Unit,
) {
    /** 会话【读完】后由注册表调用。 */
    fun invalidate() {
        onInvalidate()
    }
}

/** 技能视图选项：调用者对当前作用域的观测选项。 */
data class SkillViewOptions(
    /** 工作区选择器。 */
    val cwd: String? = null,
    /** 观测作用域（调用代理）；省略时仅读全局层。 */
    val scope: String? = null,
)