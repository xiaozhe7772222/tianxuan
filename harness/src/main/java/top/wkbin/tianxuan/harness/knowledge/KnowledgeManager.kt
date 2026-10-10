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
            val (baseUrl, apiKey, model) = providerConfig()
            val embeddings = embeddingClient.embed(chunks, baseUrl, apiKey, model)
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
            val (baseUrl, apiKey, model) = providerConfig()
            val embeddings = embeddingClient.embed(chunks.map { it.text }, baseUrl, apiKey, model)
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

    /** 检索 top-k 相关 chunk（含文档名）。 */
    suspend fun search(query: String, topK: Int = 5): List<KnowledgeHit> {
        val allChunks = repository.allEmbeddedChunks()
        if (allChunks.isEmpty()) return emptyList()
        val (baseUrl, apiKey, model) = providerConfig()
        val queryVector = try {
            embeddingClient.embed(listOf(query), baseUrl, apiKey, model).firstOrNull()
        } catch (e: Exception) {
            return emptyList()
        } ?: return emptyList()

        // 预载文档名缓存：避免在序列 lambda 内调用挂起函数
        val docNameById = repository.allDocuments().associate { it.id to it.name }
        return withContext(Dispatchers.Default) {
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
    private suspend fun providerConfig(): Triple<String, String?, String> {
        val active = aiModelRepository.activeModel()
        val baseUrl = active?.baseUrl?.takeIf { it.isNotBlank() }
            ?: providerRepository.baseUrl.first().ifBlank { "https://api.openai.com/v1" }
        val apiKey = active?.secretRef?.takeIf { it.isNotBlank() }?.let {
            providerRepository.readModelApiKeys(it).firstOrNull()
        } ?: providerRepository.readApiKey()
        val model = EmbeddingClient.DEFAULT_MODEL
        return Triple(baseUrl, apiKey, model)
    }

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
