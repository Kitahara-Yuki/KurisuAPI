package com.kurisuapi.data.repository

import com.kurisuapi.data.dao.ModelDao
import com.kurisuapi.data.entity.ModelEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelRepository @Inject constructor(
    private val modelDao: ModelDao
) {
    fun getByProvider(providerId: Long): Flow<List<ModelEntity>> =
        modelDao.getByProvider(providerId)

    fun getAllByProvider(providerId: Long): Flow<List<ModelEntity>> =
        modelDao.getAllByProvider(providerId)

    suspend fun getById(id: Long): ModelEntity? = modelDao.getById(id)

    suspend fun getByModelId(providerId: Long, modelId: String): ModelEntity? =
        modelDao.getByModelId(providerId, modelId)

    suspend fun findAnyByModelId(modelId: String): ModelEntity? =
        modelDao.findAnyByModelId(modelId)

    suspend fun insert(model: ModelEntity): Long = modelDao.insert(model)

    suspend fun insertAll(models: List<ModelEntity>) = modelDao.insertAll(models)

    suspend fun update(model: ModelEntity) = modelDao.update(model)

    suspend fun delete(model: ModelEntity) = modelDao.delete(model)

    suspend fun deleteAllByProvider(providerId: Long) = modelDao.deleteAllByProvider(providerId)

    suspend fun replaceFetchedModels(providerId: Long, models: List<ModelEntity>) {
        // Bug 10 fix: delegates to ModelDao's @Transaction method for atomicity
        modelDao.replaceFetchedModels(providerId, models)
    }

    suspend fun countByProvider(providerId: Long): Int = modelDao.countByProvider(providerId)

    suspend fun updateCapabilities(providerId: Long, modelId: String, supportsReasoning: Boolean, supportsAudio: Boolean, supportsImageGen: Boolean, supportsVision: Boolean, supportsTools: Boolean) =
        modelDao.updateCapabilities(providerId, modelId, supportsReasoning, supportsAudio, supportsImageGen, supportsVision, supportsTools)
}
