package com.kurisuapi.data.dao

import android.database.sqlite.SQLiteConstraintException
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
     * 并发安全：insert 失败时捕获 UNIQUE 约束冲突并重试查询→更新。
     */
    @Transaction
    suspend fun insertOrUpdate(emotion: EmotionStateEntity) {
        val existing = getByCharacterOnce(emotion.characterId)
        if (existing != null) {
            update(emotion.copy(id = existing.id))
        } else {
            try {
                insert(emotion)
            } catch (_: SQLiteConstraintException) {
                // 并发时另一协程已插入，重新查询后更新
                val race = getByCharacterOnce(emotion.characterId)
                if (race != null) {
                    update(emotion.copy(id = race.id))
                }
            }
        }
    }

    @Insert
    suspend fun insert(emotion: EmotionStateEntity): Long

    @Update
    suspend fun update(emotion: EmotionStateEntity)

    @Query("DELETE FROM emotion_states WHERE characterId = :characterId")
    suspend fun deleteByCharacter(characterId: Long)
}
