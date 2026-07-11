package com.kurisuapi.data.dao

import androidx.room.*
import com.kurisuapi.data.entity.ConversationSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationSessionDao {
    @Query("SELECT * FROM conversation_sessions WHERE characterId = :characterId AND isDeleted = 0 ORDER BY updatedAt DESC")
    fun observeAllByCharacter(characterId: Long): Flow<List<ConversationSessionEntity>>

    /** 一次性查询（替代 observeAllByCharacter().firstOrNull() 的 Flow 误用） */
    @Query("SELECT * FROM conversation_sessions WHERE characterId = :characterId AND isDeleted = 0 ORDER BY updatedAt DESC")
    suspend fun getAllByCharacterOnce(characterId: Long): List<ConversationSessionEntity>

    @Query("SELECT * FROM conversation_sessions WHERE folderId = :folderId AND isDeleted = 0 ORDER BY updatedAt DESC")
    fun getByFolder(folderId: Long): Flow<List<ConversationSessionEntity>>

    @Query("SELECT * FROM conversation_sessions WHERE id = :id")
    fun observeById(id: Long): Flow<ConversationSessionEntity?>

    @Query("SELECT * FROM conversation_sessions WHERE id = :id")
    suspend fun getById(id: Long): ConversationSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: ConversationSessionEntity): Long

    @Update
    suspend fun update(session: ConversationSessionEntity)

    @Query("UPDATE conversation_sessions SET folderId = :folderId, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateFolder(id: Long, folderId: Long?, updatedAt: Long = System.currentTimeMillis())

    /** 清空指定文件夹中所有会话的 folderId（删除文件夹时调用） */
    @Query("UPDATE conversation_sessions SET folderId = NULL, updatedAt = :updatedAt WHERE folderId = :folderId")
    suspend fun clearFolderId(folderId: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE conversation_sessions SET isArchived = 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun archive(id: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE conversation_sessions SET title = :title, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateTitle(id: Long, title: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE conversation_sessions SET summary = :summary WHERE id = :id")
    suspend fun updateSummary(id: Long, summary: String?)

    @Query("UPDATE conversation_sessions SET lastPromptTokens = :tokens WHERE id = :id")
    suspend fun updateLastPromptTokens(id: Long, tokens: Int)

    // === 软删除 ===
    @Query("SELECT id FROM conversation_sessions WHERE characterId = :characterId AND isDeleted = 0")
    suspend fun getAllActiveIds(characterId: Long): List<Long>

    @Query("UPDATE conversation_sessions SET isDeleted = 1, deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :id")
    suspend fun softDelete(id: Long, deletedAt: Long = System.currentTimeMillis())

    @Query("UPDATE conversation_sessions SET isDeleted = 0, deletedAt = 0, updatedAt = :updatedAt WHERE id = :id")
    suspend fun restore(id: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("SELECT * FROM conversation_sessions WHERE characterId = :characterId AND isDeleted = 1 ORDER BY deletedAt DESC")
    fun observeDeletedByCharacter(characterId: Long): Flow<List<ConversationSessionEntity>>

    @Query("DELETE FROM conversation_sessions WHERE isDeleted = 1 AND deletedAt > 0 AND deletedAt < :beforeTimestamp")
    suspend fun deleteExpired(beforeTimestamp: Long)

    // === 物理删除（保留供清理使用） ===
    @Query("DELETE FROM conversation_sessions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM conversation_sessions WHERE characterId = :characterId")
    suspend fun deleteByCharacter(characterId: Long)
}
