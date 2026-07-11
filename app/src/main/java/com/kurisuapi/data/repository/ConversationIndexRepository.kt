package com.kurisuapi.data.repository

import com.kurisuapi.data.dao.ConversationIndexDao
import com.kurisuapi.data.entity.ConversationIndexEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConversationIndexRepository @Inject constructor(
    private val indexDao: ConversationIndexDao
) {
    suspend fun insert(index: ConversationIndexEntity): Long = indexDao.insert(index)

    suspend fun search(characterId: Long, keyword: String, limit: Int = 10): List<ConversationIndexEntity> {
        // 转义 LIKE 通配符，与 MemoryDao/MemoryRepository 保持一致
        val escaped = keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        return indexDao.search(characterId, escaped, limit)
    }

    suspend fun deleteBySession(sessionId: Long) = indexDao.deleteBySession(sessionId)

    suspend fun deleteByCharacter(characterId: Long) = indexDao.deleteByCharacter(characterId)
}
