package com.kurisuapi.data.dao

import androidx.room.*
import com.kurisuapi.data.entity.ProviderEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ProviderDao {
    @Query("SELECT * FROM providers ORDER BY isDefault DESC, name ASC")
    abstract fun getAll(): Flow<List<ProviderEntity>>

    @Query("SELECT * FROM providers WHERE isEnabled = 1 ORDER BY isDefault DESC, name ASC")
    abstract fun getEnabled(): Flow<List<ProviderEntity>>

    @Query("SELECT * FROM providers WHERE isEnabled = 1 ORDER BY isDefault DESC, name ASC")
    abstract suspend fun getEnabledOnce(): List<ProviderEntity>

    @Query("SELECT * FROM providers WHERE id = :id")
    abstract suspend fun getById(id: Long): ProviderEntity?

    @Query("SELECT * FROM providers WHERE isDefault = 1 LIMIT 1")
    abstract suspend fun getDefault(): ProviderEntity?

    @Query("SELECT * FROM providers WHERE isDefault = 1 LIMIT 1")
    abstract fun observeDefault(): Flow<ProviderEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(provider: ProviderEntity): Long

    @Update
    abstract suspend fun update(provider: ProviderEntity)

    @Delete
    abstract suspend fun delete(provider: ProviderEntity)

    @Query("UPDATE providers SET isDefault = 0")
    abstract suspend fun clearDefault()

    @Query("UPDATE providers SET isDefault = 1 WHERE id = :id")
    abstract suspend fun setDefault(id: Long)

    @Query("SELECT COUNT(*) FROM providers")
    abstract suspend fun count(): Int

    // Bug 3 fix: atomic clear + set in a single transaction
    @Transaction
    open suspend fun setDefaultAtomic(id: Long) {
        clearDefault()
        setDefault(id)
    }

    // Bug 16 fix: atomic insert of all default providers
    @Transaction
    open suspend fun insertAll(providers: List<ProviderEntity>) {
        providers.forEach { insert(it) }
    }
}
