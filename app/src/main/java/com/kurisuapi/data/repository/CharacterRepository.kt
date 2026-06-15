package com.kurisuapi.data.repository

import com.kurisuapi.data.dao.CharacterDao
import com.kurisuapi.data.entity.CharacterEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CharacterRepository @Inject constructor(
    private val characterDao: CharacterDao
) {
    fun getAll(): Flow<List<CharacterEntity>> = characterDao.getAll()

    suspend fun getAllOnce(): List<CharacterEntity> = characterDao.getAllOnce()

    suspend fun getById(id: Long): CharacterEntity? = characterDao.getById(id)

    fun observeById(id: Long): Flow<CharacterEntity?> = characterDao.observeById(id)

    suspend fun insert(character: CharacterEntity): Long = characterDao.insert(character)

    suspend fun update(character: CharacterEntity) = characterDao.update(character)

    suspend fun delete(character: CharacterEntity) = characterDao.delete(character)

    suspend fun deleteById(id: Long) = characterDao.deleteById(id)
}
