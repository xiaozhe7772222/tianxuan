package top.wkbin.tianxuan.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface KnowledgeDao {

    @Query("SELECT * FROM kb_documents ORDER BY createdAt DESC")
    fun observeDocuments(): Flow<List<KbDocumentEntity>>

    @Query("SELECT * FROM kb_documents ORDER BY createdAt DESC")
    suspend fun allDocuments(): List<KbDocumentEntity>

    @Query("SELECT * FROM kb_documents WHERE id = :docId")
    suspend fun documentById(docId: String): KbDocumentEntity?

    @Query("SELECT * FROM kb_documents WHERE name = :name")
    suspend fun documentByName(name: String): KbDocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDocument(doc: KbDocumentEntity)

    @Query("UPDATE kb_documents SET status = :status, chunkCount = :chunkCount, errorMessage = :errorMessage, updatedAt = :updatedAt WHERE id = :docId")
    suspend fun updateDocumentStatus(
        docId: String,
        status: String,
        chunkCount: Int,
        errorMessage: String?,
        updatedAt: Long,
    )

    @Query("DELETE FROM kb_documents WHERE id = :docId")
    suspend fun deleteDocument(docId: String)

    @Query("SELECT * FROM kb_chunks WHERE docId = :docId ORDER BY chunkIndex ASC")
    suspend fun chunksOf(docId: String): List<KbChunkEntity>

    /** 全部已嵌入分块（检索时内存余弦计算）。 */
    @Query("SELECT * FROM kb_chunks WHERE embedding IS NOT NULL")
    suspend fun allEmbeddedChunks(): List<KbChunkEntity>

    @Query("SELECT COUNT(*) FROM kb_chunks")
    suspend fun chunkCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChunks(chunks: List<KbChunkEntity>)

    @Query("DELETE FROM kb_chunks WHERE docId = :docId")
    suspend fun deleteChunksOf(docId: String)

    @Transaction
    suspend fun replaceChunks(docId: String, chunks: List<KbChunkEntity>) {
        deleteChunksOf(docId)
        upsertChunks(chunks)
    }
}