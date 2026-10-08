package top.wkbin.tianxuan.core.database

import kotlinx.coroutines.flow.Flow

/** RAG 知识库仓储接口：业务层通过此接口操作知识库，禁止直接注入 KnowledgeDao。 */
interface KnowledgeRepository {
    fun observeDocuments(): Flow<List<KbDocumentEntity>>
    suspend fun allDocuments(): List<KbDocumentEntity>
    suspend fun documentById(docId: String): KbDocumentEntity?
    suspend fun documentByName(name: String): KbDocumentEntity?
    suspend fun upsertDocument(doc: KbDocumentEntity)
    suspend fun updateDocumentStatus(
        docId: String,
        status: String,
        chunkCount: Int,
        errorMessage: String?,
        updatedAt: Long,
    )

    suspend fun deleteDocument(docId: String)
    suspend fun chunksOf(docId: String): List<KbChunkEntity>
    suspend fun allEmbeddedChunks(): List<KbChunkEntity>
    suspend fun upsertChunks(chunks: List<KbChunkEntity>)
    suspend fun replaceChunks(docId: String, chunks: List<KbChunkEntity>)
}

class RoomKnowledgeRepository(
    private val dao: KnowledgeDao,
) : KnowledgeRepository {
    override fun observeDocuments(): Flow<List<KbDocumentEntity>> = dao.observeDocuments()
    override suspend fun allDocuments(): List<KbDocumentEntity> = dao.allDocuments()
    override suspend fun documentById(docId: String): KbDocumentEntity? = dao.documentById(docId)
    override suspend fun documentByName(name: String): KbDocumentEntity? = dao.documentByName(name)
    override suspend fun upsertDocument(doc: KbDocumentEntity) = dao.upsertDocument(doc)
    override suspend fun updateDocumentStatus(
        docId: String,
        status: String,
        chunkCount: Int,
        errorMessage: String?,
        updatedAt: Long,
    ) = dao.updateDocumentStatus(docId, status, chunkCount, errorMessage, updatedAt)

    override suspend fun deleteDocument(docId: String) = dao.deleteDocument(docId)
    override suspend fun chunksOf(docId: String): List<KbChunkEntity> = dao.chunksOf(docId)
    override suspend fun allEmbeddedChunks(): List<KbChunkEntity> = dao.allEmbeddedChunks()
    override suspend fun upsertChunks(chunks: List<KbChunkEntity>) = dao.upsertChunks(chunks)
    override suspend fun replaceChunks(docId: String, chunks: List<KbChunkEntity>) = dao.replaceChunks(docId, chunks)
}
