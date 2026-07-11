package com.kurisuapi.data.repository

import com.kurisuapi.data.dao.DiaryEntryDao
import com.kurisuapi.data.entity.DiaryEntryEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DiaryRepository @Inject constructor(
    private val dao: DiaryEntryDao
) {
    fun getByCharacter(characterId: Long): Flow<List<DiaryEntryEntity>> =
        dao.getByCharacter(characterId)

    suspend fun getByDate(characterId: Long, date: String): DiaryEntryEntity? =
        dao.getByCharacterAndDate(characterId, date)

    suspend fun getLatest(characterId: Long): DiaryEntryEntity? =
        dao.getLatest(characterId)

    suspend fun save(entity: DiaryEntryEntity): Long =
        dao.insert(entity)

    suspend fun delete(id: Long) =
        dao.deleteById(id)
}
