package com.kurisuapi.data.repository

import com.kurisuapi.data.dao.MemoryDao
import com.kurisuapi.data.entity.MemoryEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryRepository @Inject constructor(
    private val memoryDao: MemoryDao
) {
    fun getByCharacter(characterId: Long): Flow<List<MemoryEntity>> =
        memoryDao.getByCharacter(characterId)

    suspend fun getTopImportant(characterId: Long, limit: Int = 10): List<MemoryEntity> =
        memoryDao.getTopImportant(characterId, limit)

    suspend fun search(characterId: Long, keyword: String): List<MemoryEntity> =
        memoryDao.search(characterId, keyword)

    suspend fun insert(memory: MemoryEntity): Long = memoryDao.insert(memory)

    suspend fun update(memory: MemoryEntity) = memoryDao.update(memory)

    suspend fun delete(memory: MemoryEntity) = memoryDao.delete(memory)

    suspend fun deleteByCharacter(characterId: Long) = memoryDao.deleteByCharacter(characterId)

    suspend fun getAllByCharacter(characterId: Long): List<MemoryEntity> =
        memoryDao.getAllByCharacter(characterId)

    suspend fun updateAll(memories: List<MemoryEntity>) = memoryDao.updateAll(memories)

    suspend fun softDeleteBySession(sessionId: Long) = memoryDao.softDeleteBySession(sessionId)

    suspend fun restoreBySession(sessionId: Long) = memoryDao.restoreBySession(sessionId)

    suspend fun deleteExpired(beforeTimestamp: Long) = memoryDao.deleteExpired(beforeTimestamp)

    /** 查询指定会话的已软删除记忆 */
    suspend fun getDeletedBySession(sessionId: Long): List<MemoryEntity> =
        memoryDao.getDeletedBySession(sessionId)

    /** 物理删除指定会话的已软删除记忆 */
    suspend fun deleteBySessionPhysically(sessionId: Long) = memoryDao.deleteBySession(sessionId)

    /** 为旧记忆分配会话归属 */
    suspend fun assignToSession(memoryId: Long, sessionId: Long) =
        memoryDao.updateSessionId(memoryId, sessionId)

    /** 批量分配记忆到会话 */
    suspend fun batchAssignToSession(memoryIds: List<Long>, sessionId: Long) =
        memoryDao.batchUpdateSessionId(memoryIds, sessionId)

    /** 批量物理删除记忆 */
    suspend fun deleteByIds(ids: List<Long>) = memoryDao.deleteByIds(ids)

    /** 清理孤儿记忆：标记所有 sessionId 指向不存在会话的记忆为已删除 */
    suspend fun purgeOrphanedMemories(allValidSessionIds: List<Long>) {
        val memorySessionIds = memoryDao.getActiveMemorySessionIds()
        val orphanIds = memorySessionIds.filter { it !in allValidSessionIds }
        if (orphanIds.isNotEmpty()) {
            memoryDao.softDeleteBySessionIds(orphanIds)
        }
    }
}
