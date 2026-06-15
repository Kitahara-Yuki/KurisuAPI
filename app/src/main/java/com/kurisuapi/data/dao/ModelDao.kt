package com.kurisuapi.data.dao

import androidx.room.*
import com.kurisuapi.data.entity.ModelEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ModelDao {
    @Query("SELECT * FROM models WHERE providerId = :providerId AND isEnabled = 1 ORDER BY displayName ASC")
    abstract fun getByProvider(providerId: Long): Flow<List<ModelEntity>>

    @Query("SELECT * FROM models WHERE providerId = :providerId ORDER BY displayName ASC")
    abstract fun getAllByProvider(providerId: Long): Flow<List<ModelEntity>>

    @Query("SELECT * FROM models WHERE id = :id")
    abstract suspend fun getById(id: Long): ModelEntity?

    @Query("SELECT * FROM models WHERE providerId = :providerId AND modelId = :modelId LIMIT 1")
    abstract suspend fun getByModelId(providerId: Long, modelId: String): ModelEntity?

    @Query("SELECT * FROM models WHERE modelId = :modelId AND contextWindow > 0 LIMIT 1")
    abstract suspend fun findAnyByModelId(modelId: String): ModelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(model: ModelEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAll(models: List<ModelEntity>)

    @Update
    abstract suspend fun update(model: ModelEntity)

    @Delete
    abstract suspend fun delete(model: ModelEntity)

    @Query("DELETE FROM models WHERE providerId = :providerId")
    abstract suspend fun deleteAllByProvider(providerId: Long)

    @Query("DELETE FROM models WHERE providerId = :providerId AND isCustom = 0")
    abstract suspend fun deleteFetchedByProvider(providerId: Long)

    @Query("SELECT COUNT(*) FROM models WHERE providerId = :providerId")
    abstract suspend fun countByProvider(providerId: Long): Int

    // Bug 1 fix: updateCapabilities now also persists supportsVision and supportsTools
    @Query("UPDATE models SET supportsReasoning = :supports, supportsAudio = :supportsAudio, supportsImageGeneration = :supportsImageGen, supportsVision = :supportsVision, supportsTools = :supportsTools WHERE providerId = :providerId AND modelId = :modelId")
    abstract suspend fun updateCapabilities(providerId: Long, modelId: String, supports: Boolean, supportsAudio: Boolean, supportsImageGen: Boolean, supportsVision: Boolean, supportsTools: Boolean)

    // Bug 10 fix: atomic delete + insert in a single transaction
    @Transaction
    open suspend fun replaceFetchedModels(providerId: Long, models: List<ModelEntity>) {
        deleteFetchedByProvider(providerId)
        insertAll(models)
    }
}
