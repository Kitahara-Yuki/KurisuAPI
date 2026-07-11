package com.kurisuapi.data.dao

import androidx.room.*
import com.kurisuapi.data.entity.MemoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories WHERE characterId = :characterId AND isDeleted = 0 ORDER BY importance DESC, updatedAt DESC")
    fun getByCharacter(characterId: Long): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE characterId = :characterId AND isDeleted = 0 ORDER BY importance DESC LIMIT :limit")
    suspend fun getTopImportant(characterId: Long, limit: Int): List<MemoryEntity>

    @Query("SELECT * FROM memories WHERE characterId = :characterId AND content LIKE '%' || :keyword || '%' ESCAPE '\\' AND isDeleted = 0 ORDER BY importance DESC, updatedAt DESC")
    suspend fun search(characterId: Long, keyword: String): List<MemoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(memory: MemoryEntity): Long

    @Update
    suspend fun update(memory: MemoryEntity)

    @Delete
    suspend fun delete(memory: MemoryEntity)

    /** 软删除指定角色的所有记忆（标记删除而非物理删除，可恢复） */
    @Query("UPDATE memories SET isDeleted = 1, deletedAt = :deletedAt WHERE characterId = :characterId AND isDeleted = 0")
    suspend fun deleteByCharacter(characterId: Long, deletedAt: Long = System.currentTimeMillis())

    @Query("SELECT * FROM memories WHERE characterId = :characterId AND isDeleted = 0 ORDER BY updatedAt ASC LIMIT :limit")
    suspend fun getAllByCharacter(characterId: Long, limit: Int = 500): List<MemoryEntity>

    @Update
    suspend fun updateAll(memories: List<MemoryEntity>)

    // === 软删除 ===
    @Query("UPDATE memories SET isDeleted = 1, deletedAt = :deletedAt WHERE sessionId = :sessionId AND sessionId > 0")
    suspend fun softDeleteBySession(sessionId: Long, deletedAt: Long = System.currentTimeMillis())

    @Query("UPDATE memories SET isDeleted = 0, deletedAt = 0 WHERE sessionId = :sessionId AND sessionId > 0")
    suspend fun restoreBySession(sessionId: Long)

    @Query("DELETE FROM memories WHERE isDeleted = 1 AND deletedAt > 0 AND deletedAt < :beforeTimestamp")
    suspend fun deleteExpired(beforeTimestamp: Long)

    /** 查询指定会话的已软删除记忆 */
    @Query("SELECT * FROM memories WHERE sessionId = :sessionId AND isDeleted = 1 ORDER BY importance DESC")
    suspend fun getDeletedBySession(sessionId: Long): List<MemoryEntity>

    /** 查询指定角色所有已软删除的记忆 */
    @Query("SELECT * FROM memories WHERE characterId = :characterId AND isDeleted = 1 ORDER BY importance DESC")
    suspend fun getDeletedByCharacter(characterId: Long): List<MemoryEntity>

    /** 物理删除指定会话的所有记忆（仅已软删除的，防止误删正常记忆） */
    @Query("DELETE FROM memories WHERE sessionId = :sessionId AND isDeleted = 1")
    suspend fun deleteBySession(sessionId: Long)

    /** 无条件物理删除指定会话的所有记忆（级联硬删除专用，不管 isDeleted 状态） */
    @Query("DELETE FROM memories WHERE sessionId = :sessionId")
    suspend fun deleteBySessionPermanent(sessionId: Long)

    /** 无条件物理删除指定角色的所有记忆（级联硬删除专用，不管 isDeleted 状态） */
    @Query("DELETE FROM memories WHERE characterId = :characterId")
    suspend fun deleteByCharacterPermanent(characterId: Long)

    /** 更新记忆的会话归属 */
    @Query("UPDATE memories SET sessionId = :sessionId, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateSessionId(id: Long, sessionId: Long, updatedAt: Long = System.currentTimeMillis())

    /** 批量更新记忆的会话归属 */
    @Query("UPDATE memories SET sessionId = :sessionId, updatedAt = :updatedAt WHERE id IN (:ids)")
    suspend fun batchUpdateSessionId(ids: List<Long>, sessionId: Long, updatedAt: Long = System.currentTimeMillis())

    /** 物理删除多条记忆 */
    @Query("DELETE FROM memories WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    /** 软删除单条记忆 */
    @Query("UPDATE memories SET isDeleted = 1, deletedAt = :deletedAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDeleteById(id: Long, deletedAt: Long = System.currentTimeMillis(), updatedAt: Long = System.currentTimeMillis())

    /** 批量软删除记忆 */
    @Query("UPDATE memories SET isDeleted = 1, deletedAt = :deletedAt, updatedAt = :updatedAt WHERE id IN (:ids)")
    suspend fun softDeleteByIds(ids: List<Long>, deletedAt: Long = System.currentTimeMillis(), updatedAt: Long = System.currentTimeMillis())

    /** 所有关联了会话的活跃记忆（用于孤儿清理） */
    @Query("SELECT DISTINCT sessionId FROM memories WHERE characterId = :characterId AND isDeleted = 0 AND sessionId > 0")
    suspend fun getActiveMemorySessionIds(characterId: Long): List<Long>

    /** 批量软删除指定会话的记忆 */
    @Query("UPDATE memories SET isDeleted = 1, deletedAt = :deletedAt WHERE sessionId IN (:sessionIds)")
    suspend fun softDeleteBySessionIds(sessionIds: List<Long>, deletedAt: Long = System.currentTimeMillis())

    /** 恢复指定角色所有被软删除的记忆（用于误删恢复） */
    @Query("UPDATE memories SET isDeleted = 0, deletedAt = 0, updatedAt = :updatedAt WHERE characterId = :characterId AND isDeleted = 1")
    suspend fun restoreSoftDeletedByCharacter(characterId: Long, updatedAt: Long = System.currentTimeMillis())

    /** 增加记忆的检索计数（用于衰减计算中的 recall_count 因子） */
    @Query("UPDATE memories SET recallCount = recallCount + 1, lastRecalledAt = :now WHERE id IN (:ids)")
    suspend fun incrementRecallCounts(ids: List<Long>, now: Long = System.currentTimeMillis())

    /** 获取带嵌入向量的候选记忆（用于语义搜索） */
    @Query("SELECT * FROM memories WHERE characterId = :characterId AND isDeleted = 0 AND embedding IS NOT NULL ORDER BY importance DESC")
    suspend fun getCandidatesWithEmbeddings(characterId: Long): List<MemoryEntity>

    /** 更新单条记忆的嵌入向量 */
    @Query("UPDATE memories SET embedding = :embedding, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateEmbedding(id: Long, embedding: ByteArray, updatedAt: Long = System.currentTimeMillis())

    /** 批量更新嵌入向量（用于首次预计算或迁移） */
    @Query("UPDATE memories SET embedding = :embedding WHERE id = :id")
    suspend fun updateEmbeddingRaw(id: Long, embedding: ByteArray)

    /** 查询缺少嵌入向量的记忆（最多 limit 条，用于逐批预计算） */
    @Query("SELECT * FROM memories WHERE characterId = :characterId AND isDeleted = 0 AND embedding IS NULL ORDER BY importance DESC LIMIT :limit")
    suspend fun getWithoutEmbeddings(characterId: Long, limit: Int = 50): List<MemoryEntity>
}
