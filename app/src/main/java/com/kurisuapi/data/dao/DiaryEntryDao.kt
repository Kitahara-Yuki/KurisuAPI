package com.kurisuapi.data.dao

import androidx.room.*
import com.kurisuapi.data.entity.DiaryEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DiaryEntryDao {

    @Query("SELECT * FROM diary_entries WHERE characterId = :characterId AND date = :date LIMIT 1")
    suspend fun getByCharacterAndDate(characterId: Long, date: String): DiaryEntryEntity?

    @Query("SELECT * FROM diary_entries WHERE characterId = :characterId ORDER BY date DESC")
    fun getByCharacter(characterId: Long): Flow<List<DiaryEntryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DiaryEntryEntity): Long

    @Update
    suspend fun update(entity: DiaryEntryEntity)

    @Query("DELETE FROM diary_entries WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM diary_entries WHERE characterId = :characterId ORDER BY date DESC LIMIT 1")
    suspend fun getLatest(characterId: Long): DiaryEntryEntity?

    /** 删除指定角色的所有日记 */
    @Query("DELETE FROM diary_entries WHERE characterId = :characterId")
    suspend fun deleteByCharacter(characterId: Long)
}
