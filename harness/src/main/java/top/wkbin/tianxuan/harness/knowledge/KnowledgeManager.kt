package top.wkbin.tianxuan.harness.knowledge

import java.security.MessageDigest
import java.util.UUID
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import top.wkbin.tianxuan.core.database.KbChunkEntity
import top.wkbin.tianxuan.core.database.KbDocumentEntity
import top.wkbin.tianxuan.core.database.KnowledgeRepository
import top.wkbin.tianxuan.core.database.toEmbedding
import top.wkbin.tianxuan.core.database.toEmbeddingBytes
import top.wkbin.tianxuan.core.tools.ProviderRepository

/**
 * RAG 知识库管理器：文档增删、分块嵌入、余弦相似度检索。
 *
 * 嵌入向量在 DAO 层以 BLOB 存储（LE float32），检索时全量读入内存计算余弦。
 * 知识库规模在数千 chunk 内时性能完全够用；如未来扩展到百万级需引入 ANN 索引。
 */
class KnowledgeManager(
    private val repository: KnowledgeRepository,
    private val embeddingClient: EmbeddingClient,
    private val providerRepository: ProviderRepository,
    private val aiModelRepository: top.wkbin.tianxuan.core.database.AiModelRepository,
    private val settingsDataStore: top.wkbin.tianxuan.core.datastore.SettingsDataStore,
) {

    val documents get() = repository.observeDocuments()

    /** 添加文档：分块 → 写入 → 嵌入 → 回填向量。 */
    suspend fun addDocument(name: String, content: String, source: String = "text"): String {
        val docId = UUID.randomUUID().toString()
        val contentHash = sha256(content)
        repository.upsertDocument(
            KbDocumentEntity(
                id = docId,
                name = name.trim(),
                source = source,
                contentHash = contentHash,
                status = "embedding",
                chunkCount = 0,
            ),
        )

        val chunks = TextChunker.chunk(content)
        if (chunks.isEmpty()) {
            repository.updateDocumentStatus(docId, "error", 0, "文档内容为空", System.currentTimeMillis())
            return docId
        }

        val chunkEntities = chunks.mapIndexed { index, text ->
            KbChunkEntity(
                id = "${docId}_$index",
                docId = docId,
                chunkIndex = index,
                text = text,
                tokenCount = text.length / 4,
            )
        }
        repository.upsertChunks(chunkEntities)

        try {
            val cfg = providerConfig()
            val embeddings = embeddingClient.embed(chunks, cfg.baseUrl, cfg.apiKey, cfg.model, cfg.endpointSuffix)
            val withEmbedding = chunkEntities.mapIndexed { index, entity ->
                val vector = embeddings.getOrNull(index)
                if (vector != null) entity.copy(embedding = vector.toEmbeddingBytes()) else entity
            }
            val embeddedCount = withEmbedding.count { it.embedding != null }
            if (embeddedCount == 0) {
                repository.updateDocumentStatus(docId, "error", 0, "嵌入全部失败，请检查 API 配置", System.currentTimeMillis())
            } else {
                repository.upsertChunks(withEmbedding)
                repository.updateDocumentStatus(
                    docId,
                    if (embeddedCount < chunks.size) "partial" else "embedded",
                    embeddedCount,
                    if (embeddedCount < chunks.size) "${chunks.size - embeddedCount} 块嵌入失败" else null,
                    System.currentTimeMillis(),
                )
            }
        } catch (e: Exception) {
            repository.updateDocumentStatus(docId, "error", 0, e.message, System.currentTimeMillis())
        }
        return docId
    }

    suspend fun removeDocument(docId: String) {
        repository.deleteDocument(docId)
    }

    suspend fun reEmbed(docId: String) {
        val doc = repository.documentById(docId) ?: return
        repository.updateDocumentStatus(docId, "embedding", 0, null, System.currentTimeMillis())
        val chunks = repository.chunksOf(docId)
        if (chunks.isEmpty()) return
        try {
            val cfg = providerConfig()
            val embeddings = embeddingClient.embed(chunks.map { it.text }, cfg.baseUrl, cfg.apiKey, cfg.model, cfg.endpointSuffix)
            val updated = chunks.mapIndexed { index, entity ->
                val vector = embeddings.getOrNull(index)
                if (vector != null) entity.copy(embedding = vector.toEmbeddingBytes()) else entity
            }
            repository.upsertChunks(updated)
            val embeddedCount = updated.count { it.embedding != null }
            repository.updateDocumentStatus(
                docId,
                if (embeddedCount < chunks.size) "partial" else "embedded",
                embeddedCount,
                if (embeddedCount < chunks.size) "${chunks.size - embeddedCount} 块嵌入失败" else null,
                System.currentTimeMillis(),
            )
        } catch (e: Exception) {
            repository.updateDocumentStatus(docId, "error", chunks.size, e.message, System.currentTimeMillis())
        }
    }

    /** 检索 top-k 相关 chunk（含文档名）。向量嵌入失败时回退 BM25 关键词检索。 */
    suspend fun search(query: String, topK: Int = 5): List<KnowledgeHit> {
        val allChunks = repository.allEmbeddedChunks()
        // 向量库为空时回退到全部 chunk 的关键词检索（而非空返）
        val chunksToSearch = if (allChunks.isNotEmpty()) allChunks else repository.allDocuments().flatMap { repository.chunksOf(it.id) }
        if (chunksToSearch.isEmpty()) return emptyList()

        val docNameById = repository.allDocuments().associate { it.id to it.name }
        val cfg = providerConfig()
        val queryVector = try {
            embeddingClient.embed(listOf(query), cfg.baseUrl, cfg.apiKey, cfg.model, cfg.endpointSuffix).firstOrNull()
        } catch (e: Exception) {
            null // 嵌入失败 → BM25 降级（不阻断检索）
        }

        return withContext(Dispatchers.Default) {
            if (queryVector != null) {
                // 向量余弦相似度
                allChunks.asSequence()
                    .mapNotNull { chunk ->
                        val vector = chunk.embedding ?: return@mapNotNull null
                        val score = cosineSimilarity(queryVector, vector.toEmbedding())
                        KnowledgeHit(
                            docId = chunk.docId,
                            docName = docNameById[chunk.docId] ?: "未知",
                            text = chunk.text,
                            score = score,
                        )
                    }
                    .sortedByDescending { it.score }
                    .take(topK)
                    .toList()
            } else {
                // BM25 降级：按查询词在 chunk 文本中的出现频次/占比打分
                val queryTokens = query.lowercase().split(Regex("[\\s,，。、;；:：!?！？]+")).filter { it.isNotBlank() }
                if (queryTokens.isEmpty()) return@withContext emptyList()
                chunksToSearch.map { chunk ->
                    val lowerText = chunk.text.lowercase()
                    val hits = queryTokens.count { token -> lowerText.contains(token) }
                    val ratio = if (chunk.text.isNotEmpty()) hits.toDouble() / queryTokens.size.coerceAtLeast(1) else 0.0
                    KnowledgeHit(
                        docId = chunk.docId,
                        docName = docNameById[chunk.docId] ?: "未知",
                        text = chunk.text,
                        score = (ratio * 0.5).toFloat() + (hits.coerceAtLeast(0) * 0.1f),
                    )
                }.filter { it.score > 0f }
                    .sortedByDescending { it.score }
                    .take(topK)
            }
        }
    }

    /** 自动注入用：返回拼接好的检索片段文本。 */
    suspend fun contextForPrompt(query: String, topK: Int = 3): String? {
        val hits = search(query, topK)
        if (hits.isEmpty()) return null
        return buildString {
            appendLine("以下是知识库中与当前问题相关的内容：")
            for ((index, hit) in hits.withIndex()) {
                appendLine("--- 片段 ${index + 1}（来源: ${hit.docName}, 相似度: ${"%.2f".format(hit.score)}）---")
                appendLine(hit.text)
                appendLine()
            }
        }
    }

    /**
     * 嵌入配置：优先用当前激活模型档案的 baseUrl + key（用户在模型编辑器里填的那套），
     * 回退到全局 ProviderPreferences。模型档案缺失或字段为空时回退默认值。
     * 避免"用户配置了模型档案但嵌入走全局默认 OpenAI"导致 Socket closed。
     */
    /**
     * 嵌入配置：优先用当前激活模型档案的 baseUrl + key（用户在模型编辑器里填的那套），
     * 嵌入模型名与端点后缀取知识库页自定义设置（覆盖 OpenAI 默认 text-embedding-3-small）。
     * 避免"用户配置了模型档案但嵌入走全局默认 OpenAI"导致 Socket closed 或 404。
     */
    private suspend fun providerConfig(): EmbeddingConfig {
        val active = aiModelRepository.activeModel()
        val baseUrl = active?.baseUrl?.takeIf { it.isNotBlank() }
            ?: providerRepository.baseUrl.first().ifBlank { "https://api.openai.com/v1" }
        val apiKey = active?.secretRef?.takeIf { it.isNotBlank() }?.let {
            providerRepository.readModelApiKeys(it).firstOrNull()
        } ?: providerRepository.readApiKey()
        val model = settingsDataStore.embeddingModel.first()
            .takeIf { it.isNotBlank() } ?: EmbeddingClient.DEFAULT_MODEL
        val endpointSuffix = settingsDataStore.embeddingEndpointSuffix.first()
            .takeIf { it.isNotBlank() } ?: EmbeddingClient.DEFAULT_ENDPOINT_SUFFIX
        return EmbeddingConfig(baseUrl, apiKey, model, endpointSuffix)
    }

    private data class EmbeddingConfig(
        val baseUrl: String,
        val apiKey: String?,
        val model: String,
        val endpointSuffix: String,
    )

    companion object {
        fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
            if (a.size != b.size || a.isEmpty()) return 0f
            var dot = 0.0
            var normA = 0.0
            var normB = 0.0
            for (i in a.indices) {
                dot += a[i] * b[i]
                normA += a[i].toDouble() * a[i]
                normB += b[i].toDouble() * b[i]
            }
            val denom = sqrt(normA) * sqrt(normB)
            return if (denom == 0.0) 0f else (dot / denom).toFloat()
        }

        private fun sha256(text: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            return digest.digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
        }
    }
}

data class KnowledgeHit(
    val docId: String,
    val docName: String,
    val text: String,
    val score: Float,
)
