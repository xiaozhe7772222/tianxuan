package top.wkbin.tianxuan.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * RAG 知识库文档。内容切块后写入 [KbChunkEntity]，嵌入完成后 [status] 置 EMBEDDED。
 */
@Entity(tableName = "kb_documents")
data class KbDocumentEntity(
    @PrimaryKey val id: String,
    /** 知识库文档名，用于手动 @kb:名称 引用。 */
    val name: String,
    /** 来源说明：text / workspace / import */
    val source: String = "text",
    /** 内容 SHA-256，用于去重与变更检测。 */
    val contentHash: String = "",
    /** pending / embedding / embedded / error */
    val status: String = "pending",
    val chunkCount: Int = 0,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

/**
 * 知识库文档分块，[embedding] 为 FloatArray 的序列化字节（LE float32，见 [embeddingBytes]）。
 */
@Entity(
    tableName = "kb_chunks",
    foreignKeys = [
        ForeignKey(
            entity = KbDocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["docId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("docId")],
)
data class KbChunkEntity(
    @PrimaryKey val id: String,
    val docId: String,
    val chunkIndex: Int,
    val text: String,
    /** FloatArray 序列化的 LE float32 字节；未嵌入时为 null。 */
    val embedding: ByteArray? = null,
    val tokenCount: Int = 0,
)

/** FloatArray → LE float32 字节，用于 [KbChunkEntity.embedding] 持久化。 */
fun FloatArray.toEmbeddingBytes(): ByteArray {
    val bytes = ByteArray(size * 4)
    var i = 0
    for (value in this) {
        val bits = java.lang.Float.floatToIntBits(value)
        bytes[i] = (bits and 0xFF).toByte()
        bytes[i + 1] = ((bits shr 8) and 0xFF).toByte()
        bytes[i + 2] = ((bits shr 16) and 0xFF).toByte()
        bytes[i + 3] = ((bits shr 24) and 0xFF).toByte()
        i += 4
    }
    return bytes
}

/** LE float32 字节 → FloatArray，从 [KbChunkEntity.embedding] 还原向量。 */
fun ByteArray.toEmbedding(): FloatArray {
    val floats = FloatArray(size / 4)
    var i = 0
    for (j in floats.indices) {
        val b0 = this[i].toInt() and 0xFF
        val b1 = this[i + 1].toInt() and 0xFF
        val b2 = this[i + 2].toInt() and 0xFF
        val b3 = this[i + 3].toInt() and 0xFF
        floats[j] = java.lang.Float.intBitsToFloat(b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24))
        i += 4
    }
    return floats
}