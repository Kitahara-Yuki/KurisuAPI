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

    suspend fun search(characterId: Long, keyword: String): List<MemoryEntity> {
        // 转义 LIKE 通配符，防止 % 和 _ 被当作 SQL 通配符处理
        val escaped = keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        return memoryDao.search(characterId, escaped)
    }

    suspend fun insert(memory: MemoryEntity): Long = memoryDao.insert(memory)

    suspend fun update(memory: MemoryEntity) = memoryDao.update(memory)

    suspend fun delete(memory: MemoryEntity) = memoryDao.delete(memory)

    suspend fun deleteByCharacter(characterId: Long) = memoryDao.deleteByCharacter(characterId)

    suspend fun getAllByCharacter(characterId: Long, limit: Int = 500): List<MemoryEntity> =
        memoryDao.getAllByCharacter(characterId, limit)

    suspend fun updateAll(memories: List<MemoryEntity>) = memoryDao.updateAll(memories)

    suspend fun softDeleteBySession(sessionId: Long) = memoryDao.softDeleteBySession(sessionId)

    suspend fun restoreBySession(sessionId: Long) = memoryDao.restoreBySession(sessionId)

    suspend fun deleteExpired(beforeTimestamp: Long) = memoryDao.deleteExpired(beforeTimestamp)

    /** 查询指定会话的已软删除记忆 */
    suspend fun getDeletedBySession(sessionId: Long): List<MemoryEntity> =
        memoryDao.getDeletedBySession(sessionId)

    /** 物理删除指定会话的已软删除记忆 */
    suspend fun deleteBySessionPhysically(sessionId: Long) = memoryDao.deleteBySession(sessionId)

    /** 无条件物理删除指定会话的所有记忆（级联硬删除专用） */
    suspend fun deleteBySessionPermanent(sessionId: Long) = memoryDao.deleteBySessionPermanent(sessionId)

    /** 无条件物理删除指定角色的所有记忆（级联硬删除专用） */
    suspend fun deleteByCharacterPermanent(characterId: Long) = memoryDao.deleteByCharacterPermanent(characterId)

    /** 恢复指定角色所有被软删除的记忆（用于误删恢复），返回恢复条数 */
    suspend fun restoreSoftDeletedByCharacter(characterId: Long): Int {
        val before = memoryDao.getDeletedByCharacter(characterId).size
        if (before > 0) {
            memoryDao.restoreSoftDeletedByCharacter(characterId)
        }
        return before
    }

    /** 为旧记忆分配会话归属 */
    suspend fun assignToSession(memoryId: Long, sessionId: Long) =
        memoryDao.updateSessionId(memoryId, sessionId)

    /** 批量分配记忆到会话 */
    suspend fun batchAssignToSession(memoryIds: List<Long>, sessionId: Long) =
        memoryDao.batchUpdateSessionId(memoryIds, sessionId)

    /** 批量物理删除记忆 */
    suspend fun deleteByIds(ids: List<Long>) = memoryDao.deleteByIds(ids)

    /** 软删除单条记忆（手动删除，可恢复） */
    suspend fun softDeleteById(id: Long) = memoryDao.softDeleteById(id)

    /** 批量软删除记忆 */
    suspend fun softDeleteByIds(ids: List<Long>) = memoryDao.softDeleteByIds(ids)

    /** 清理孤儿记忆：标记所有 sessionId 指向不存在会话的记忆为已删除 */
    suspend fun purgeOrphanedMemories(characterId: Long, allValidSessionIds: List<Long>) {
        // Bug fix: 当角色没有有效会话时，不应该清理任何记忆（空列表 = 无参考依据）
        if (allValidSessionIds.isEmpty()) return
        val memorySessionIds = memoryDao.getActiveMemorySessionIds(characterId)
        val orphanIds = memorySessionIds.filter { it !in allValidSessionIds }
        if (orphanIds.isNotEmpty()) {
            memoryDao.softDeleteBySessionIds(orphanIds)
        }
    }

    /** 增加记忆的检索计数（用于衰减计算） */
    suspend fun incrementRecallCounts(ids: List<Long>) {
        if (ids.isEmpty()) return
        memoryDao.incrementRecallCounts(ids)
    }

    /** 获取带嵌入向量的候选记忆（用于语义搜索） */
    suspend fun getCandidatesWithEmbeddings(characterId: Long): List<MemoryEntity> =
        memoryDao.getCandidatesWithEmbeddings(characterId)

    /** 更新单条记忆的嵌入向量 */
    suspend fun updateEmbedding(id: Long, embedding: ByteArray) =
        memoryDao.updateEmbedding(id, embedding)

    /** 查询缺少嵌入向量的记忆 */
    suspend fun getWithoutEmbeddings(characterId: Long, limit: Int = 50): List<MemoryEntity> =
        memoryDao.getWithoutEmbeddings(characterId, limit)
}
