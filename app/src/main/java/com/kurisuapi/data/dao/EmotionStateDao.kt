package com.kurisuapi.data.dao

import androidx.room.*
import com.kurisuapi.data.entity.EmotionStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EmotionStateDao {
    @Query("SELECT * FROM emotion_states WHERE characterId = :characterId LIMIT 1")
    fun getByCharacter(characterId: Long): Flow<EmotionStateEntity?>

    @Query("SELECT * FROM emotion_states WHERE characterId = :characterId LIMIT 1")
    suspend fun getByCharacterOnce(characterId: Long): EmotionStateEntity?

    /**
     * 如果 characterId 已有记录则更新（保留原 id），否则插入新记录。
     * 不使用 @Insert(REPLACE)，因为 autoGenerate 的 PrimaryKey(0)
     * 不会与已有行冲突，导致产生重复行。
     */
    @Transaction
    suspend fun insertOrUpdate(emotion: EmotionStateEntity) {
        val existing = getByCharacterOnce(emotion.characterId)
        if (existing != null) {
            update(emotion.copy(id = existing.id))
        } else {
            insert(emotion)
        }
    }

    @Insert
    suspend fun insert(emotion: EmotionStateEntity): Long

    @Update
    suspend fun update(emotion: EmotionStateEntity)

    @Query("DELETE FROM emotion_states WHERE characterId = :characterId")
    suspend fun deleteByCharacter(characterId: Long)
}
