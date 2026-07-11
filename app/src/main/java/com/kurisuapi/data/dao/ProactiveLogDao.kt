package com.kurisuapi.data.dao

import androidx.room.*
import com.kurisuapi.data.entity.ProactiveLogEntity

@Dao
interface ProactiveLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: ProactiveLogEntity): Long

    @Query("SELECT * FROM proactive_log WHERE characterId = :characterId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(characterId: Long, limit: Int = 50): List<ProactiveLogEntity>

    @Query("DELETE FROM proactive_log WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)

    /** 保留每个角色最近 N 条记录，删除更早的 */
    @Query("DELETE FROM proactive_log WHERE characterId = :characterId AND id < (SELECT COALESCE(MIN(id), 0) FROM (SELECT id FROM proactive_log WHERE characterId = :characterId ORDER BY timestamp DESC LIMIT :keep))")
    suspend fun prune(characterId: Long, keep: Int = 200)

    /** 删除指定角色的所有主动消息日志 */
    @Query("DELETE FROM proactive_log WHERE characterId = :characterId")
    suspend fun deleteByCharacter(characterId: Long)
}
