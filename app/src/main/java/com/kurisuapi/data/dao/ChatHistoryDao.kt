package com.kurisuapi.data.dao

import androidx.room.*
import com.kurisuapi.data.entity.ChatHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatHistoryDao {
    @Query("SELECT * FROM chat_history WHERE characterId = :characterId ORDER BY timestamp DESC")
    fun getByCharacter(characterId: Long): Flow<List<ChatHistoryEntity>>

    @Query("SELECT * FROM chat_history WHERE sessionId = :sessionId ORDER BY timestamp DESC")
    fun getBySession(sessionId: Long): Flow<List<ChatHistoryEntity>>

    @Query("SELECT * FROM chat_history WHERE characterId = :characterId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(characterId: Long, limit: Int): List<ChatHistoryEntity>

    @Query("SELECT * FROM chat_history WHERE sessionId = :sessionId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentBySession(sessionId: Long, limit: Int): List<ChatHistoryEntity>

    @Query("SELECT COUNT(*) FROM chat_history WHERE characterId = :characterId")
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
}
