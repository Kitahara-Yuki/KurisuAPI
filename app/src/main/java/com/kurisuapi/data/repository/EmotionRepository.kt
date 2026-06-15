package com.kurisuapi.data.repository

import com.kurisuapi.data.dao.EmotionStateDao
import com.kurisuapi.data.entity.EmotionStateEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EmotionRepository @Inject constructor(
    private val emotionStateDao: EmotionStateDao
) {
    fun getByCharacter(characterId: Long): Flow<EmotionStateEntity?> =
        emotionStateDao.getByCharacter(characterId)

    suspend fun getByCharacterOnce(characterId: Long): EmotionStateEntity? =
        emotionStateDao.getByCharacterOnce(characterId)

    suspend fun insertOrUpdate(emotion: EmotionStateEntity) =
        emotionStateDao.insertOrUpdate(emotion)

    suspend fun deleteByCharacter(characterId: Long) =
        emotionStateDao.deleteByCharacter(characterId)
}
