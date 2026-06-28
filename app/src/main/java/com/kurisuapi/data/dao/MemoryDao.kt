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

    @Query("SELECT * FROM memories WHERE characterId = :characterId AND content LIKE '%' || :keyword || '%' AND isDeleted = 0 ORDER BY importance DESC, updatedAt DESC")
    suspend fun search(characterId: Long, keyword: String): List<MemoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(memory: MemoryEntity): Long

    @Update
    suspend fun update(memory: MemoryEntity)

    @Delete
    suspend fun delete(memory: MemoryEntity)

    @Query("DELETE FROM memories WHERE characterId = :characterId")
    suspend fun deleteByCharacter(characterId: Long)

    @Query("SELECT * FROM memories WHERE characterId = :characterId AND isDeleted = 0 ORDER BY updatedAt ASC")
    suspend fun getAllByCharacter(characterId: Long): List<MemoryEntity>

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

    /** 物理删除指定会话的所有记忆（仅已软删除的，防止误删正常记忆） */
    @Query("DELETE FROM memories WHERE sessionId = :sessionId AND isDeleted = 1")
    suspend fun deleteBySession(sessionId: Long)

    /** 更新记忆的会话归属 */
    @Query("UPDATE memories SET sessionId = :sessionId, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateSessionId(id: Long, sessionId: Long, updatedAt: Long = System.currentTimeMillis())

    /** 批量更新记忆的会话归属 */
    @Query("UPDATE memories SET sessionId = :sessionId, updatedAt = :updatedAt WHERE id IN (:ids)")
    suspend fun batchUpdateSessionId(ids: List<Long>, sessionId: Long, updatedAt: Long = System.currentTimeMillis())

    /** 物理删除多条记忆 */
    @Query("DELETE FROM memories WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    /** 所有关联了会话的活跃记忆（用于孤儿清理） */
    @Query("SELECT DISTINCT sessionId FROM memories WHERE isDeleted = 0 AND sessionId > 0")
    suspend fun getActiveMemorySessionIds(): List<Long>

    /** 批量软删除指定会话的记忆 */
    @Query("UPDATE memories SET isDeleted = 1, deletedAt = :deletedAt WHERE sessionId IN (:sessionIds)")
    suspend fun softDeleteBySessionIds(sessionIds: List<Long>, deletedAt: Long = System.currentTimeMillis())
}
