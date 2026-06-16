package com.kurisuapi.domain.engine

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.kurisuapi.data.entity.MemoryEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * 负责记忆的语义向量生成和搜索。
 *
 * 当前阶段：使用纯 Kotlin 余弦相似度做向量检索（适合几千条记忆的场景）。
 * 后续可升级为 SQLite-Vector 扩展加速（十万级以上），接口不改变。
 *
 * 嵌入模型：通过应用的 AI API 提供商生成（OpenAI-compatible embedding API）。
 * 维度：取决于提供商，通常 1536（OpenAI）或 768（本地模型）。
 */
@Singleton
class EmbeddingService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val EMBEDDING_DIM = 1536  // OpenAI text-embedding-3-small 默认维度
    }

    /**
     * 从 ByteArray 解码为 FloatArray
     */
    fun decodeEmbedding(bytes: ByteArray): FloatArray {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val floats = FloatArray(bytes.size / 4)
        for (i in floats.indices) {
            floats[i] = buffer.float
        }
        return floats
    }

    /**
     * 将 FloatArray 编码为 ByteArray
     */
    fun encodeEmbedding(vector: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(vector.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (v in vector) {
            buffer.putFloat(v)
        }
        return buffer.array()
    }

    /**
     * 余弦相似度：两个向量之间的语义距离。
     * 返回 0~1，越接近 1 表示语义越相近。
     */
    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denom = sqrt(normA * normB)
        return if (denom > 0f) dot / denom else 0f
    }

    /**
     * 在记忆列表中按语义相似度搜索，返回 top-K 结果。
     * queryEmbedding: 用户消息的向量
     * candidates: 候选记忆列表（已按 characterId 过滤）
     * topK: 返回条数
     */
    fun semanticSearch(
        queryEmbedding: FloatArray,
        candidates: List<MemoryEntity>,
        topK: Int = 10
    ): List<Pair<MemoryEntity, Float>> {
        return candidates
            .filter { it.embedding != null }
            .map { memory ->
                val sim = cosineSimilarity(queryEmbedding, decodeEmbedding(memory.embedding!!))
                Pair(memory, sim)
            }
            .filter { it.second > 0.3f }  // 相似度阈值，过滤不相关结果
            .sortedByDescending { it.second }
            .take(topK)
    }

    /**
     * 加载 SQLite-Vector 扩展到指定的 SQLiteDatabase 连接。
     * 用于在 Room 之外的独立连接中执行向量操作。
     */
    fun loadVectorExtension(databasePath: String): SQLiteDatabase? {
        return try {
            val db = SQLiteDatabase.openDatabase(
                databasePath,
                null,
                SQLiteDatabase.OPEN_READONLY
            )
            // SQLite-Vector 扩展通过 sqlite3_load_extension 加载
            // 在 Android 上需要使用 System.loadLibrary 预加载
            System.loadLibrary("sqlite-vector")
            db
        } catch (e: Exception) {
            null
        }
    }
}
