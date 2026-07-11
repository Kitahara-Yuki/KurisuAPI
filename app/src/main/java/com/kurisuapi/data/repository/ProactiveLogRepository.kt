package com.kurisuapi.data.repository

import com.kurisuapi.data.dao.ProactiveLogDao
import com.kurisuapi.data.entity.ProactiveLogEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProactiveLogRepository @Inject constructor(
    private val dao: ProactiveLogDao
) {
    suspend fun insert(log: ProactiveLogEntity): Long = dao.insert(log)

    suspend fun getRecent(characterId: Long, limit: Int = 50): List<ProactiveLogEntity> =
        dao.getRecent(characterId, limit)

    /** 删除 N 天前的旧日志 */
    suspend fun cleanOldLogs(daysToKeep: Int = 30) {
        val cutoff = System.currentTimeMillis() - daysToKeep * 24 * 3600_000L
        dao.deleteOlderThan(cutoff)
    }

    /** 保留每个角色最近 200 条 */
    suspend fun prune(characterId: Long) = dao.prune(characterId, keep = 200)
}
