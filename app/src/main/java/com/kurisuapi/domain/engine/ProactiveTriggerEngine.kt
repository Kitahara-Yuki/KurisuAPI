package com.kurisuapi.domain.engine

import com.kurisuapi.data.repository.SettingsRepository
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 主动消息触发引擎 v3：简化为计时器模型的辅助检测。
 *
 * 保留两类检测：
 * - 情绪突变（孤独 ≥ 75）：绕过安静时段和每日上限，立即触发
 * - 安静时段检查：在设定时段内抑制触发，情绪突变除外
 *
 * 触发决策由 WeChatBridge 的计时器主逻辑处理。
 */
@Singleton
class ProactiveTriggerEngine @Inject constructor(
    private val emotionEngine: EmotionEngine,
    private val settingsRepository: SettingsRepository
) {
    enum class Urgency { URGENT, NORMAL, NONE }

    data class TriggerDecision(
        val shouldTrigger: Boolean,
        val urgency: Urgency,
        val reason: String
    )

    data class TriggerContext(
        val emotion: com.kurisuapi.data.entity.EmotionStateEntity
    )

    /**
     * 检测情绪突变（孤独 ≥ 75）。
     * 返回 URGENT 决策表示必须立即触发，不受安静时段和每日上限限制。
     * 返回 null 表示无情绪突变，按正常计时器逻辑处理。
     */
    suspend fun checkEmotionSpike(characterId: Long): TriggerDecision? {
        val emotion = emotionEngine.getEmotion(characterId)
        val nowMs = System.currentTimeMillis()
        val elapsedMs = (nowMs - emotion.updatedAt).coerceAtLeast(0)
        val freshEmotion = emotionEngine.computePassiveDecay(emotion, elapsedMs)

        if (freshEmotion.lonely >= 75) {
            return TriggerDecision(
                shouldTrigger = true,
                urgency = Urgency.URGENT,
                reason = "情绪突变: 孤独感临界(${freshEmotion.lonely}/100)"
            )
        }
        return null
    }

    /** 检查当前是否处于安静时段 */
    suspend fun isInQuietHours(nowMs: Long = System.currentTimeMillis()): Boolean {
        val start = settingsRepository.getProactiveQuietStart()
        val end = settingsRepository.getProactiveQuietEnd()
        val hour = Instant.ofEpochMilli(nowMs).atZone(ZoneId.systemDefault()).hour
        return if (start <= end) hour in start..end
        else hour >= start || hour < end
    }
}
