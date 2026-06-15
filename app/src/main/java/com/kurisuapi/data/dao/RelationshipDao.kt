package com.kurisuapi.data.dao

import androidx.room.*
import com.kurisuapi.data.entity.RelationshipEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RelationshipDao {
    @Query("SELECT * FROM relationships WHERE characterId = :characterId LIMIT 1")
    fun getByCharacter(characterId: Long): Flow<RelationshipEntity?>

    @Query("SELECT * FROM relationships WHERE characterId = :characterId LIMIT 1")
    suspend fun getByCharacterOnce(characterId: Long): RelationshipEntity?

    /**
     * 如果 characterId 已有记录则更新（保留原 id），否则插入新记录。
     */
    @Transaction
    suspend fun insertOrUpdate(relationship: RelationshipEntity) {
        val existing = getByCharacterOnce(relationship.characterId)
        if (existing != null) {
            update(relationship.copy(id = existing.id))
        } else {
            insert(relationship)
        }
    }

    @Insert
    suspend fun insert(relationship: RelationshipEntity): Long

    @Update
    suspend fun update(relationship: RelationshipEntity)

    @Query("DELETE FROM relationships WHERE characterId = :characterId")
    suspend fun deleteByCharacter(characterId: Long)
}
