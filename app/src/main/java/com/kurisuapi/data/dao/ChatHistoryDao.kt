package com.kurisuapi.data.dao

import androidx.room.*
import com.kurisuapi.data.entity.ChatHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatHistoryDao {
    /** 角色所有消息（上限 2000 条，超过部分在 UI 不可见，旧数据仍存 DB 供日志查看） */
    @Query("SELECT * FROM chat_history WHERE characterId = :characterId AND (sessionId = 0 OR sessionId NOT IN (SELECT id FROM conversation_sessions WHERE isDeleted = 1)) ORDER BY timestamp DESC LIMIT 2000")
    fun getByCharacter(characterId: Long): Flow<List<ChatHistoryEntity>>

    /** 会话消息（上限 1000 条，防止超长会话 OOM，完整记录仍可通过日志导出） */
    @Query("SELECT * FROM chat_history WHERE sessionId = :sessionId ORDER BY timestamp DESC LIMIT 1000")
    fun getBySession(sessionId: Long): Flow<List<ChatHistoryEntity>>

    @Query("SELECT * FROM chat_history WHERE characterId = :characterId AND (sessionId = 0 OR sessionId NOT IN (SELECT id FROM conversation_sessions WHERE isDeleted = 1)) ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(characterId: Long, limit: Int): List<ChatHistoryEntity>

    @Query("SELECT * FROM chat_history WHERE sessionId = :sessionId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentBySession(sessionId: Long, limit: Int): List<ChatHistoryEntity>

    @Query("SELECT COUNT(*) FROM chat_history WHERE characterId = :characterId AND (sessionId = 0 OR sessionId NOT IN (SELECT id FROM conversation_sessions WHERE isDeleted = 1))")
    suspend fun countByCharacter(characterId: Long): Int

    @Query("SELECT COUNT(*) FROM chat_history WHERE sessionId = :sessionId")
    suspend fun countBySession(sessionId: Long): Int

    @Query("SELECT * FROM chat_history WHERE sessionId = :sessionId AND sender = 'user' ORDER BY timestamp ASC LIMIT 1")
    suspend fun getFirstUserMessageOfSession(sessionId: Long): ChatHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: ChatHistoryEntity): Long

    @Query("DELETE FROM chat_history WHERE characterId = :characterId")
    suspend fun deleteByCharacter(characterId: Long)

    @Query("DELETE FROM chat_history WHERE sessionId = :sessionId")
    suspend fun deleteBySession(sessionId: Long)

    @Query("DELETE FROM chat_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** 查询某角色在指定时间范围内的消息（按时间正序） */
    @Query("SELECT * FROM chat_history WHERE characterId = :characterId AND (sessionId = 0 OR sessionId NOT IN (SELECT id FROM conversation_sessions WHERE isDeleted = 1)) AND timestamp >= :startOfDay AND timestamp < :endOfDay ORDER BY timestamp ASC")
    suspend fun getByCharacterAndDateRange(characterId: Long, startOfDay: Long, endOfDay: Long): List<ChatHistoryEntity>
}
