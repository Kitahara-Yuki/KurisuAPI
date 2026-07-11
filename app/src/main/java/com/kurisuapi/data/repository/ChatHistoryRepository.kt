package com.kurisuapi.data.repository

import com.kurisuapi.data.dao.ChatHistoryDao
import com.kurisuapi.data.entity.ChatHistoryEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatHistoryRepository @Inject constructor(
    private val chatHistoryDao: ChatHistoryDao
) {
    fun getByCharacter(characterId: Long): Flow<List<ChatHistoryEntity>> =
        chatHistoryDao.getByCharacter(characterId)

    fun getBySession(sessionId: Long): Flow<List<ChatHistoryEntity>> =
        chatHistoryDao.getBySession(sessionId)

    suspend fun getRecent(characterId: Long, limit: Int = 20): List<ChatHistoryEntity> =
        chatHistoryDao.getRecent(characterId, limit)

    suspend fun getRecentBySession(sessionId: Long, limit: Int = 20): List<ChatHistoryEntity> =
        chatHistoryDao.getRecentBySession(sessionId, limit)

    suspend fun countByCharacter(characterId: Long): Int =
        chatHistoryDao.countByCharacter(characterId)

    suspend fun countBySession(sessionId: Long): Int =
        chatHistoryDao.countBySession(sessionId)
    suspend fun getFirstUserMessageOfSession(sessionId: Long): ChatHistoryEntity? =
        chatHistoryDao.getFirstUserMessageOfSession(sessionId)

    suspend fun insert(message: ChatHistoryEntity): Long = chatHistoryDao.insert(message)

    suspend fun deleteByCharacter(characterId: Long) = chatHistoryDao.deleteByCharacter(characterId)

    suspend fun deleteBySession(sessionId: Long) = chatHistoryDao.deleteBySession(sessionId)

    suspend fun deleteById(id: Long) = chatHistoryDao.deleteById(id)

    /** 查询某角色在指定时间范围内的消息 */
    suspend fun getByCharacterAndDateRange(characterId: Long, startOfDay: Long, endOfDay: Long): List<ChatHistoryEntity> =
        chatHistoryDao.getByCharacterAndDateRange(characterId, startOfDay, endOfDay)
}
