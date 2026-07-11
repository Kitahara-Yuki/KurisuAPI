package com.kurisuapi.data.repository

import androidx.room.withTransaction
import com.kurisuapi.data.dao.ChatHistoryDao
import com.kurisuapi.data.dao.ConversationIndexDao
import com.kurisuapi.data.dao.ConversationSessionDao
import com.kurisuapi.data.dao.MemoryDao
import com.kurisuapi.data.entity.ConversationSessionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConversationSessionRepository @Inject constructor(
    private val db: com.kurisuapi.data.database.KurisuDatabase,
    private val sessionDao: ConversationSessionDao,
    private val chatHistoryDao: ChatHistoryDao,
    private val indexDao: ConversationIndexDao,
    private val memoryDao: MemoryDao
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
        sessionDao.getAllByCharacterOnce(characterId)

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

    /** 硬删除会话，同步清理关联数据（事务保护） */
    suspend fun deleteById(id: Long) = db.withTransaction {
        chatHistoryDao.deleteBySession(id)
        indexDao.deleteBySession(id)
        memoryDao.deleteBySessionPermanent(id)
        sessionDao.deleteById(id)
    }

    suspend fun getAllActiveIds(characterId: Long): List<Long> =
        sessionDao.getAllActiveIds(characterId)

    /** 软删除会话，同步软删除关联记忆（事务保护） */
    suspend fun softDelete(id: Long) = db.withTransaction {
        sessionDao.softDelete(id)
        memoryDao.softDeleteBySession(id)
    }

    /** 恢复会话，同步恢复关联记忆（事务保护） */
    suspend fun restore(id: Long) = db.withTransaction {
        sessionDao.restore(id)
        memoryDao.restoreBySession(id)
    }

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
        val sessions = sessionDao.getAllByCharacterOnce(characterId)
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
