package com.kurisuapi.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 每日缓存统计（精确、持久化、按天聚合）。
 * 每个自然日一行，计数器在当天内累加。
 */
@Entity(tableName = "cache_daily_stats")
data class CacheDailyStatsEntity(
    @PrimaryKey
    val date: String,              // "2026-06-20" 格式，天然唯一
    // 嵌入缓存
    val embedHits: Int = 0,
    val embedMisses: Int = 0,
    // 聊天缓存：L1+L2 精确/归一化命中
    val chatL1L2Hits: Int = 0,
    // 聊天缓存：L3 语义命中
    val chatL3Hits: Int = 0,
    // 聊天缓存：全部未命中（实际调了 API）
    val chatMisses: Int = 0,
    val updatedAt: Long = System.currentTimeMillis()
)
