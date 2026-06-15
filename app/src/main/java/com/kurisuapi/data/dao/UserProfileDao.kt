package com.kurisuapi.data.dao

import androidx.room.*
import com.kurisuapi.data.entity.UserProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profiles WHERE characterId = :characterId LIMIT 1")
    fun getByCharacter(characterId: Long): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profiles WHERE characterId = :characterId LIMIT 1")
    suspend fun getByCharacterOnce(characterId: Long): UserProfileEntity?

    /**
     * 如果 characterId 已有记录则更新（保留原 id），否则插入新记录。
     * 与 EmotionStateDao 一致：不用 @Insert(REPLACE)，避免 autoGenerate PK(0) 产生重复行。
     */
    @Transaction
    suspend fun insertOrUpdate(profile: UserProfileEntity) {
        val existing = getByCharacterOnce(profile.characterId)
        if (existing != null) {
            update(profile.copy(id = existing.id))
        } else {
            insert(profile)
        }
    }

    @Insert
    suspend fun insert(profile: UserProfileEntity): Long

    @Update
    suspend fun update(profile: UserProfileEntity)

    @Query("DELETE FROM user_profiles WHERE characterId = :characterId")
    suspend fun deleteByCharacter(characterId: Long)
}
