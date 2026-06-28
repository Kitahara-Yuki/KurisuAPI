package com.kurisuapi.data.dao

import android.database.sqlite.SQLiteConstraintException
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
     * 并发安全：insert 失败时捕获 UNIQUE 约束冲突并重试查询→更新。
     */
    @Transaction
    suspend fun insertOrUpdate(profile: UserProfileEntity) {
        val existing = getByCharacterOnce(profile.characterId)
        if (existing != null) {
            update(profile.copy(id = existing.id))
        } else {
            try {
                insert(profile)
            } catch (_: SQLiteConstraintException) {
                // 并发时另一协程已插入，重新查询后更新
                val race = getByCharacterOnce(profile.characterId)
                if (race != null) {
                    update(profile.copy(id = race.id))
                }
            }
        }
    }

    @Insert
    suspend fun insert(profile: UserProfileEntity): Long

    @Update
    suspend fun update(profile: UserProfileEntity)

    @Query("DELETE FROM user_profiles WHERE characterId = :characterId")
    suspend fun deleteByCharacter(characterId: Long)
}
