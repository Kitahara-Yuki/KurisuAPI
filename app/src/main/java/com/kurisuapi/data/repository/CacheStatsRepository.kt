package com.kurisuapi.data.repository

import com.kurisuapi.data.dao.CacheStatsDao
import com.kurisuapi.data.entity.CacheDailyStatsEntity
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CacheStatsRepository @Inject constructor(
    private val dao: CacheStatsDao
) {
    private val fmt: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    /** 今日日期字符串 */
    fun today(): String = LocalDate.now().format(fmt)

    /** 原子累加今日统计（ON CONFLICT DO UPDATE，不会丢数据） */
    suspend fun addToday(
        embedHits: Int = 0, embedMisses: Int = 0,
        chatL1L2Hits: Int = 0, chatL3Hits: Int = 0, chatMisses: Int = 0
    ) {
        dao.upsertDaily(
            date = today(),
            embedHits = embedHits,
            embedMisses = embedMisses,
            chatL1L2Hits = chatL1L2Hits,
            chatL3Hits = chatL3Hits,
            chatMisses = chatMisses,
            updatedAt = System.currentTimeMillis()
        )
    }

    /** 查某一天 */
    suspend fun get(date: String): CacheDailyStatsEntity? = dao.getByDate(date)

    /** 查今天 */
    suspend fun getToday(): CacheDailyStatsEntity? = dao.getByDate(today())

    /** 所有有记录的日期 */
    suspend fun allDates(): List<String> = dao.getAllDates()

    /** 最近 N 天 */
    suspend fun recent(limit: Int = 30): List<CacheDailyStatsEntity> = dao.getRecent(limit)
}
