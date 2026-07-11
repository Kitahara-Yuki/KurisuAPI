package com.kurisuapi.domain.engine

import com.kurisuapi.data.repository.ChatHistoryRepository
import com.kurisuapi.data.repository.SettingsRepository
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 用户活跃度周期监测：追踪用户的聊天模式，根据参与度阶段调整主动消息策略。
 *
 * 基于 UMAP '26 纵向研究（1100 万用户）和 NetcoreCloud 2026 基准：
 * - 监测每日/每周活跃模式
 * - 检测参与度下降趋势（早期流失信号）
 * - 根据用户所处阶段调整消息频率和内容策略
 *
 * 四个参与度阶段：
 * - NASCENT (0-3 天): 新用户或刚恢复活跃 → 适度主动
 * - ENGAGED (4-14 天持续活跃): 稳定互动 → 标准频率
 * - FADING (活跃下降50%): 参与度下滑 → 增加关怀，降低打扰
 * - LAPSED (7+ 天无消息): 长期沉默 → 减少频率，提高质量
 */
@Singleton
class EngagementMonitor @Inject constructor(
    private val chatHistoryRepository: ChatHistoryRepository,
    private val settingsRepository: SettingsRepository
) {
    enum class Stage {
        NASCENT, ENGAGED, FADING, LAPSED
    }

    data class EngagementProfile(
        val stage: Stage,
        val activeDaysInWeek: Int,       // 过去7天有消息的天数
        val totalMessagesWeek: Int,      // 过去7天总消息数
        val avgResponseMinutes: Int,     // 平均回复间隔（分钟）
        val trendDirection: String,      // "rising", "stable", "declining"
        val recommendedIntervalMul: Double, // 建议的间隔乘数
        val shouldReduceFrequency: Boolean
    )

    /**
     * 分析用户活跃度并返回参与度画像。
     */
    suspend fun analyze(characterId: Long): EngagementProfile {
        val now = System.currentTimeMillis()
        val weekAgo = now - 7 * 24 * 3600_000L
        val twoWeeksAgo = now - 14 * 24 * 3600_000L

        // 取足够多的历史消息以确保覆盖两周数据（高活跃用户可能需要更多）
        val allRecent = chatHistoryRepository.getRecent(characterId, 1000)
            .filter { it.sender == "user" && it.timestamp >= twoWeeksAgo }

        // 本周数据
        val thisWeek = allRecent
            .filter { it.timestamp >= weekAgo }
            .sortedBy { it.timestamp }

        // 上周数据（用于趋势对比）
        val lastWeek = allRecent
            .filter { it.timestamp < weekAgo }
            .sortedBy { it.timestamp }

        val activeDaysThisWeek = thisWeek.map {
            Instant.ofEpochMilli(it.timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
        }.distinct().size

        val activeDaysLastWeek = lastWeek.map {
            Instant.ofEpochMilli(it.timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
        }.distinct().size

        val totalMessagesThisWeek = thisWeek.size
        val totalMessagesLastWeek = lastWeek.size

        // 平均回复间隔
        val avgResponseMin = if (thisWeek.size >= 2) {
            val intervals = thisWeek.zipWithNext { a, b -> (b.timestamp - a.timestamp) / 60_000 }
            intervals.average().toInt()
        } else 1440  // 默认 24 小时

        // 趋势判断
        val trend = when {
            totalMessagesThisWeek > totalMessagesLastWeek * 1.2 -> "rising"
            totalMessagesThisWeek < totalMessagesLastWeek * 0.5 -> "declining"
            else -> "stable"
        }

        // 阶段判断
        val daysSinceLast = if (thisWeek.isNotEmpty()) {
            ((now - thisWeek.last().timestamp) / 86_400_000).toInt()
        } else 14

        val stage = when {
            activeDaysThisWeek == 0 && daysSinceLast >= 7 -> Stage.LAPSED
            trend == "declining" || (activeDaysLastWeek >= 4 && activeDaysThisWeek <= 2) -> Stage.FADING
            activeDaysThisWeek <= 3 || totalMessagesThisWeek < 10 -> Stage.NASCENT
            else -> Stage.ENGAGED
        }

        // 建议的间隔乘数
        val intervalMul = when (stage) {
            Stage.NASCENT -> 1.0   // 正常频率，帮助建立习惯
            Stage.ENGAGED -> 0.8   // 稍微频繁，维持活跃
            Stage.FADING -> 1.5    // 减少频率，避免过度打扰
            Stage.LAPSED -> 2.5    // 大幅减少，只在最佳时机发送
        }

        return EngagementProfile(
            stage = stage,
            activeDaysInWeek = activeDaysThisWeek,
            totalMessagesWeek = totalMessagesThisWeek,
            avgResponseMinutes = avgResponseMin,
            trendDirection = trend,
            recommendedIntervalMul = intervalMul,
            shouldReduceFrequency = stage == Stage.FADING || stage == Stage.LAPSED
        )
    }

    /**
     * 根据用户参与度阶段生成 prompt 增强片段。
     * 不同阶段的主动消息风格不同。
     */
    fun buildStagePromptHint(profile: EngagementProfile): String? {
        return when (profile.stage) {
            Stage.NASCENT -> null  // 不需要特殊提示
            Stage.ENGAGED -> "<engagement_hint>对方最近很活跃，保持自然的聊天节奏，可以稍微主动一些。</engagement_hint>"
            Stage.FADING -> "<engagement_hint>对方最近聊天变少了。发一条温暖但不压力的消息，不要提及对方不常来。如果对方回复简短，不要追问。</engagement_hint>"
            Stage.LAPSED -> "<engagement_hint>对方已经超过一周没说话了。这一次的消息很重要——选最好的时机，发最有温度的内容。不要提\"好久不见\"，自然开场。如果对方没回复，不要再发第二条。</engagement_hint>"
        }
    }
}
