package com.kurisuapi.data.dao

import androidx.room.*
import com.kurisuapi.data.entity.SettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingsDao {
    @Query("SELECT value FROM settings WHERE `key` = :key")
    suspend fun getValue(key: String): String?

    @Query("SELECT value FROM settings WHERE `key` = :key")
    fun observeValue(key: String): Flow<String?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(setting: SettingsEntity)

    @Query("DELETE FROM settings WHERE `key` = :key")
    suspend fun delete(key: String)
}
