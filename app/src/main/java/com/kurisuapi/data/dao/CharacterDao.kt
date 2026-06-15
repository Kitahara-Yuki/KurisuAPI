package com.kurisuapi.data.dao

import androidx.room.*
import com.kurisuapi.data.entity.CharacterEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CharacterDao {
    @Query("SELECT * FROM characters ORDER BY updatedAt DESC")
    fun getAll(): Flow<List<CharacterEntity>>

    @Query("SELECT * FROM characters ORDER BY updatedAt DESC")
    suspend fun getAllOnce(): List<CharacterEntity>

    @Query("SELECT * FROM characters WHERE id = :id")
    suspend fun getById(id: Long): CharacterEntity?

    @Query("SELECT * FROM characters WHERE id = :id")
    fun observeById(id: Long): Flow<CharacterEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(character: CharacterEntity): Long

    @Update
    suspend fun update(character: CharacterEntity)

    @Delete
    suspend fun delete(character: CharacterEntity)

    @Query("DELETE FROM characters WHERE id = :id")
    suspend fun deleteById(id: Long)
}
