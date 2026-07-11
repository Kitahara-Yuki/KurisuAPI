package com.kurisuapi.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kurisuapi.data.entity.CacheDailyStatsEntity

@Dao
interface CacheStatsDao {

    /** 更新或插入今日统计（原子累加，防止并发丢失） */
    @Query("""
        INSERT INTO cache_daily_stats (date, embedHits, embedMisses, chatL1L2Hits, chatL3Hits, chatMisses, updatedAt)
        VALUES (:date, :embedHits, :embedMisses, :chatL1L2Hits, :chatL3Hits, :chatMisses, :updatedAt)
        ON CONFLICT(date) DO UPDATE SET
            embedHits = embedHits + excluded.embedHits,
            embedMisses = embedMisses + excluded.embedMisses,
            chatL1L2Hits = chatL1L2Hits + excluded.chatL1L2Hits,
            chatL3Hits = chatL3Hits + excluded.chatL3Hits,
            chatMisses = chatMisses + excluded.chatMisses,
            updatedAt = excluded.updatedAt
    """)
    suspend fun upsertDaily(
        date: String,
        embedHits: Int,
        embedMisses: Int,
        chatL1L2Hits: Int,
        chatL3Hits: Int,
        chatMisses: Int,
        updatedAt: Long
    )

    /** 查询某一天的统计 */
    @Query("SELECT * FROM cache_daily_stats WHERE date = :date")
    suspend fun getByDate(date: String): CacheDailyStatsEntity?

    /** 查询所有有记录的日期（按日期倒序，最新的在前） */
    @Query("SELECT date FROM cache_daily_stats ORDER BY date DESC")
    suspend fun getAllDates(): List<String>

    /** 查询某个日期范围（倒序） */
    @Query("SELECT * FROM cache_daily_stats WHERE date BETWEEN :from AND :to ORDER BY date DESC")
    suspend fun getByDateRange(from: String, to: String): List<CacheDailyStatsEntity>

    /** 查询最近 N 天的统计 */
    @Query("SELECT * FROM cache_daily_stats ORDER BY date DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 30): List<CacheDailyStatsEntity>
}
