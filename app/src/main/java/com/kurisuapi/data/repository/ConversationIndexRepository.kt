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

    suspend fun search(characterId: Long, keyword: String, limit: Int = 10): List<ConversationIndexEntity> =
        indexDao.search(characterId, keyword, limit)

    suspend fun deleteBySession(sessionId: Long) = indexDao.deleteBySession(sessionId)
}
