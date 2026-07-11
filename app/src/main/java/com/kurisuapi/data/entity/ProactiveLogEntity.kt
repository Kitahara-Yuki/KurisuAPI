package com.kurisuapi.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 主动消息审计日志：记录每次触发决策（发送或跳过），
 * 用于调试、优化触发引擎参数、以及用户透明度。
 */
@Entity(
    tableName = "proactive_log",
    indices = [Index(value = ["characterId", "timestamp"])]
)
data class ProactiveLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val characterId: Long,
    val timestamp: Long,
    val decision: String,        // "sent" 或 "skipped"
    val triggerScore: Double,
    val reason: String,
    val silenceMinutes: Int,
    val emotionSnapshot: String, // "lonely:72,affection:55,happy:40"
    val timeOfDay: String,       // "morning", "afternoon", "evening", "night"
    val signalBreakdown: String  // JSON-like breakdown of signal scores
)
