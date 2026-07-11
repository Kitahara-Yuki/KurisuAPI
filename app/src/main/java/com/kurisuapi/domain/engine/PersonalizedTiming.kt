package com.kurisuapi.domain.engine

import com.kurisuapi.data.repository.ChatHistoryRepository
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 个性化最佳时机学习：从用户历史消息时间戳中学习其真实活跃时段。
 *
 * 基于 NetcoreCloud 2026 "Send-Time Optimization" 和 Momentum AI 的研究：
 * - ML 学习每个用户的最佳互动窗口（精确到小时）
 * - 替代通用时段评分，提高消息被阅读和回复的概率
 * - 持续从用户实际回复行为中学习和调整
 */
@Singleton
class PersonalizedTiming @Inject constructor(
    private val chatHistoryRepository: ChatHistoryRepository
) {
    /**
     * 分析用户历史消息，计算出每个小时的活跃权重。
     * @return 24 个元素的 DoubleArray，每个元素为该小时的活跃度 (0.0-1.0)
     */
    suspend fun computeHourlyActivity(characterId: Long): DoubleArray {
        val weights = DoubleArray(24) { 0.1 } // 默认低基线（避免完全为0）

        val recent = chatHistoryRepository.getRecent(characterId, 500)
        val userMessages = recent.filter { it.sender == "user" }
        if (userMessages.size < 5) return weights // 数据不足，使用默认

        // 按小时统计用户消息数
        val hourCounts = IntArray(24)
        for (msg in userMessages) {
            val hour = Instant.ofEpochMilli(msg.timestamp).atZone(ZoneId.systemDefault()).hour
            hourCounts[hour]++
        }

        // 归一化到 0.0-1.0
        val maxCount = hourCounts.maxOrNull() ?: 1
        for (i in 0..23) {
            weights[i] = if (maxCount > 0) {
                (hourCounts[i].toDouble() / maxCount).coerceIn(0.05, 1.0)
            } else 0.1
        }

        return weights
    }

    /**
     * 获取当前小时的个性化活跃权重 (0.0-1.0)。
     */
    suspend fun getCurrentHourWeight(characterId: Long): Double {
        val weights = computeHourlyActivity(characterId)
        val hour = java.time.LocalTime.now().hour
        return weights[hour]
    }

    /**
     * 根据个性化数据计算时段评分。
     * @return 0.0-1.0，越接近 1.0 表示当前是用户最活跃的时段
     */
    suspend fun computeScore(characterId: Long, nowMs: Long = System.currentTimeMillis()): Double {
        val weights = computeHourlyActivity(characterId)
        val hour = Instant.ofEpochMilli(nowMs).atZone(ZoneId.systemDefault()).hour

        // 基础权重
        val baseScore = weights[hour]

        // 相邻小时平滑（用户可能提前或延后一点）
        val prevHour = if (hour > 0) weights[hour - 1] else weights[23]
        val nextHour = if (hour < 23) weights[hour + 1] else weights[0]
        val smoothedScore = (prevHour * 0.2 + baseScore * 0.6 + nextHour * 0.2)

        return smoothedScore.coerceIn(0.0, 1.0)
    }

    /**
     * 获取用户活跃时段的友好描述（用于日志和提示）。
     */
    suspend fun getPeakHoursDescription(characterId: Long): String {
        val weights = computeHourlyActivity(characterId)
        val peakHours = weights.withIndex()
            .filter { it.value >= 0.5 }
            .sortedByDescending { it.value }
            .take(3)
            .map { "${it.index}:00" }
        return if (peakHours.isNotEmpty()) peakHours.joinToString(", ") else "数据不足"
    }
}
