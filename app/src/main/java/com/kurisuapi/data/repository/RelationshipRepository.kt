package com.kurisuapi.data.repository

import com.kurisuapi.data.dao.RelationshipDao
import com.kurisuapi.data.entity.RelationshipEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RelationshipRepository @Inject constructor(
    private val relationshipDao: RelationshipDao
) {
    fun getByCharacter(characterId: Long): Flow<RelationshipEntity?> =
        relationshipDao.getByCharacter(characterId)

    suspend fun getByCharacterOnce(characterId: Long): RelationshipEntity? =
        relationshipDao.getByCharacterOnce(characterId)

    suspend fun insertOrUpdate(relationship: RelationshipEntity) =
        relationshipDao.insertOrUpdate(relationship)

    suspend fun deleteByCharacter(characterId: Long) =
        relationshipDao.deleteByCharacter(characterId)
}
