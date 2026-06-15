package com.kurisuapi.data.dao

import androidx.room.*
import com.kurisuapi.data.entity.ConversationFolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationFolderDao {
    @Query("SELECT * FROM conversation_folders WHERE characterId = :characterId ORDER BY createdAt ASC")
    fun observeByCharacter(characterId: Long): Flow<List<ConversationFolderEntity>>

    @Query("SELECT * FROM conversation_folders WHERE id = :id")
    suspend fun getById(id: Long): ConversationFolderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(folder: ConversationFolderEntity): Long

    @Update
    suspend fun update(folder: ConversationFolderEntity)

    @Query("DELETE FROM conversation_folders WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM conversation_folders WHERE characterId = :characterId")
    suspend fun deleteByCharacter(characterId: Long)
}
