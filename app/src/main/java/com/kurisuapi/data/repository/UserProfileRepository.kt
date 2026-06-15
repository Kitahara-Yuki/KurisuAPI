package com.kurisuapi.data.repository

import com.kurisuapi.data.dao.UserProfileDao
import com.kurisuapi.data.entity.UserProfileEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserProfileRepository @Inject constructor(
    private val userProfileDao: UserProfileDao
) {
    fun getByCharacter(characterId: Long): Flow<UserProfileEntity?> =
        userProfileDao.getByCharacter(characterId)

    suspend fun getByCharacterOnce(characterId: Long): UserProfileEntity? =
        userProfileDao.getByCharacterOnce(characterId)

    suspend fun insertOrUpdate(profile: UserProfileEntity) =
        userProfileDao.insertOrUpdate(profile)

    suspend fun deleteByCharacter(characterId: Long) =
        userProfileDao.deleteByCharacter(characterId)
}
