package com.kurisuapi.data.repository

import com.kurisuapi.data.dao.ConversationSessionDao
import com.kurisuapi.data.entity.ConversationSessionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConversationSessionRepository @Inject constructor(
    private val sessionDao: ConversationSessionDao
) {
    fun observeAllByCharacter(characterId: Long): Flow<List<ConversationSessionEntity>> =
        sessionDao.observeAllByCharacter(characterId)

    fun getByFolder(folderId: Long): Flow<List<ConversationSessionEntity>> =
        sessionDao.getByFolder(folderId)

    fun observeById(id: Long): Flow<ConversationSessionEntity?> =
        sessionDao.observeById(id)

    suspend fun getById(id: Long): ConversationSessionEntity? =
        sessionDao.getById(id)

    suspend fun getAllOnce(characterId: Long): List<ConversationSessionEntity> =
        observeAllByCharacter(characterId).firstOrNull() ?: emptyList()

    suspend fun insert(session: ConversationSessionEntity): Long =
        sessionDao.insert(session)

    suspend fun update(session: ConversationSessionEntity) =
        sessionDao.update(session)

    suspend fun updateFolder(id: Long, folderId: Long?) =
        sessionDao.updateFolder(id, folderId)

    suspend fun archive(id: Long) =
        sessionDao.archive(id)

    suspend fun updateTitle(id: Long, title: String) =
        sessionDao.updateTitle(id, title)

    suspend fun updateSummary(id: Long, summary: String?) =
        sessionDao.updateSummary(id, summary)

    suspend fun updateLastPromptTokens(id: Long, tokens: Int) =
        sessionDao.updateLastPromptTokens(id, tokens)

    suspend fun deleteById(id: Long) =
        sessionDao.deleteById(id)

    suspend fun getAllActiveIds(characterId: Long): List<Long> =
        sessionDao.getAllActiveIds(characterId)

    suspend fun softDelete(id: Long) =
        sessionDao.softDelete(id)

    suspend fun restore(id: Long) =
        sessionDao.restore(id)

    fun observeDeletedByCharacter(characterId: Long): Flow<List<ConversationSessionEntity>> =
        sessionDao.observeDeletedByCharacter(characterId)

    suspend fun deleteExpired(beforeTimestamp: Long) =
        sessionDao.deleteExpired(beforeTimestamp)

    suspend fun deleteByCharacter(characterId: Long) =
        sessionDao.deleteByCharacter(characterId)

    private val sessionMutex = Mutex()

    /**
     * 获取当前活跃会话（最近更新的未归档会话），
     * 若不存在则创建一个新会话并返回其 ID。
     * 供微信自动回复等外部调用使用。
     */
    suspend fun getOrCreateActiveSession(characterId: Long): Long = sessionMutex.withLock {
        val sessions = observeAllByCharacter(characterId).firstOrNull() ?: emptyList()
        val activeSession = sessions.firstOrNull { !it.isArchived }
        return if (activeSession != null) {
            activeSession.id
        } else {
            val now = System.currentTimeMillis()
            insert(
                ConversationSessionEntity(
                    characterId = characterId,
                    title = "新对话",
                    createdAt = now,
                    updatedAt = now
                )
            )
        }
    }
}
