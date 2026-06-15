package com.kurisuapi.domain.engine

import com.kurisuapi.data.entity.EmotionStateEntity
import com.kurisuapi.data.repository.EmotionRepository
import com.kurisuapi.util.containsAny
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

@Singleton
class EmotionEngine @Inject constructor(
    private val emotionRepository: EmotionRepository
) {
    private val emotionMutex = Mutex()

    suspend fun getEmotion(characterId: Long): EmotionStateEntity {
        return emotionRepository.getByCharacterOnce(characterId)
            ?: EmotionStateEntity(characterId = characterId).also {
                emotionRepository.insertOrUpdate(it)
            }
    }

    /**
     * 更新角色情绪。
     *
     * @param personality 角色性格描述文本（如"温柔体贴，带一点傲娇"）。
     *                    用于个性化情绪反应幅度。空字符串使用中性默认值。
     */
    suspend fun updateEmotion(characterId: Long, userMessage: String, personality: String = "") = emotionMutex.withLock {
        val current = getEmotion(characterId)

        // 第一层：关键词分析（纯文本 → 情绪变化量）
        val adjustments = analyzeMessage(userMessage)

        // 第二层：性格修正系数
        val mods = getPersonalityModifiers(personality)

        // 第三层：应用惯性 + 性格修正 + 跨情绪阻力
        val updated = current.copy(
            happy = computeNewEmotion(
                currentValue = current.happy, rawDelta = adjustments.happy,
                personalityMul = mods.happyMul, opposingA = current.sad, opposingB = current.angry
            ),
            sad = computeNewEmotion(
                currentValue = current.sad, rawDelta = adjustments.sad,
                personalityMul = mods.sadMul, opposingA = current.happy, opposingB = 0
            ),
            angry = computeNewEmotion(
                currentValue = current.angry, rawDelta = adjustments.angry,
                personalityMul = mods.angryMul, opposingA = current.happy, opposingB = current.affection
            ),
            lonely = computeNewEmotion(
                currentValue = current.lonely, rawDelta = adjustments.lonely,
                personalityMul = mods.lonelyMul, opposingA = current.affection, opposingB = 0
            ),
            affection = computeNewEmotion(
                currentValue = current.affection, rawDelta = adjustments.affection,
                personalityMul = mods.affectionMul, opposingA = current.angry, opposingB = 0
            ),
            updatedAt = System.currentTimeMillis()
        )

        // 自然衰减
        val decayed = applyDecay(updated)
        emotionRepository.insertOrUpdate(decayed)
    }

    /**
     * 计算单个情绪维度的最终新值。
     *
     * 应用三层修正：
     * 1. 性格修正系数 — 不同性格对同一刺激的反应幅度不同
     * 2. 跨情绪阻力 — 高对立情绪时正向变化受阻（如难过时不容易开心）
     * 3. 自惯性 — 情绪越极端越难继续同向变化，越容易反向回归
     *
     * @param currentValue 当前情绪值 (0-100)
     * @param rawDelta 关键词分析得出的原始变化量
     * @param personalityMul 性格修正系数（默认 1.0 = 不变）
     * @param opposingA 抑制该情绪的对立情绪当前值（如 sad 抑制 happy）
     * @param opposingB 第二抑制情绪当前值
     */
    private fun computeNewEmotion(
        currentValue: Int,
        rawDelta: Int,
        personalityMul: Float,
        opposingA: Int,
        opposingB: Int
    ): Int {
        if (rawDelta == 0) return currentValue

        var delta = rawDelta.toFloat() * personalityMul

        // 跨情绪阻力：仅对正向变化生效（升高受阻）
        // 例如：很难在难过(80)时说一句"哈哈"就让开心大幅上升
        if (delta > 0) {
            if (opposingA > 30) delta *= (1.0f - opposingA / 100.0f * 0.5f)
            if (opposingB > 30) delta *= (1.0f - opposingB / 100.0f * 0.5f)
        }

        // 自惯性（diminishing returns / emotional momentum）
        // 当前值越高 → 继续升高越难（阻力 70%）；降低越容易（阻力仅 30%）
        if (delta > 0) {
            val resistance = currentValue / 100.0f * 0.7f
            delta *= (1.0f - resistance)
        } else {
            // 负向变化（降低）：阻力更小，情绪自然倾向于回归中位
            val resistance = currentValue / 100.0f * 0.3f
            delta *= (1.0f - resistance)
        }

        return clamp(currentValue + delta.toInt())
    }

    /**
     * 根据角色性格描述解析情绪反应修正系数。
     *
     * 支持的标签及其效果：
     * - 傲娇/别扭/嘴硬: 好感表达受阻(×0.5)，更容易生气(×1.5)
     * - 温柔/体贴: 更容易共情难过(×1.3)，不易生气(×0.5)，好感表达更顺畅(×1.2)
     * - 活泼/开朗/元气: 开心更容易触发(×1.5)，难过更不易(×0.5)
     * - 冷淡/高冷/冰山: 所有情绪偏 muted，好感极难上升(×0.3)
     * - 敏感/细腻/多愁善感: 情绪放大，尤其难过(×1.5)
     * - 热情/主动/奔放: 好感和开心更容易上升
     * - 内向/害羞/怕生: 好感表达受阻(×0.6)，孤独更容易触发(×1.5)
     *
     * 多个标签同时命中时乘算叠加，但单个系数限制在 [0.25, 2.0]。
     */
    private fun getPersonalityModifiers(personality: String): PersonalityModifiers {
        if (personality.isBlank()) return PersonalityModifiers.DEFAULT

        val lower = personality.lowercase()
        var affMul = 1.0f
        var hapMul = 1.0f
        var sadMul = 1.0f
        var angMul = 1.0f
        var lonMul = 1.0f

        // 傲娇：嘴硬心软，好感不易表露但生气容易上头
        if (lower.containsAny("傲娇", "别扭", "嘴硬", "tsundere")) {
            affMul *= 0.5f
            angMul *= 1.5f
        }

        // 温柔/体贴：善解人意，容易共情
        if (lower.containsAny("温柔", "体贴")) {
            sadMul *= 1.3f
            angMul *= 0.5f
            affMul *= 1.2f
        }

        // 活泼/开朗/元气：乐天派
        if (lower.containsAny("活泼", "开朗", "乐观", "元气")) {
            hapMul *= 1.5f
            sadMul *= 0.5f
        }

        // 冷淡/高冷/冰山：情绪表达极少
        if (lower.containsAny("冷淡", "高冷", "冷漠", "冰山")) {
            affMul *= 0.3f
            hapMul *= 0.5f
            sadMul *= 0.7f
            angMul *= 0.8f
        }

        // 敏感/细腻：情绪放大器
        if (lower.containsAny("敏感", "细腻", "多愁善感")) {
            sadMul *= 1.5f
            hapMul *= 1.2f
            affMul *= 1.3f
        }

        // 热情/主动/奔放：情感外放
        if (lower.containsAny("热情", "主动", "奔放", "外向")) {
            affMul *= 1.4f
            hapMul *= 1.3f
        }

        // 内向/害羞/怕生：情感内敛，容易孤独
        if (lower.containsAny("内向", "害羞", "怕生", "社恐")) {
            affMul *= 0.6f
            lonMul *= 1.5f
        }

        // 毒舌/腹黑：生气容易，好感表达少
        if (lower.containsAny("毒舌", "腹黑")) {
            angMul *= 1.3f
            affMul *= 0.7f
        }

        // 三无/无口：极 muted
        if (lower.containsAny("三无", "无口", "无表情")) {
            affMul *= 0.3f
            hapMul *= 0.4f
            sadMul *= 0.5f
            angMul *= 0.5f
        }

        // 病娇：好感极度放大，生气也放大
        if (lower.containsAny("病娇")) {
            affMul *= 1.8f
            angMul *= 1.5f
            sadMul *= 1.3f
        }

        return PersonalityModifiers(
            affectionMul = affMul.coerceIn(0.25f, 2.0f),
            happyMul = hapMul.coerceIn(0.25f, 2.0f),
            sadMul = sadMul.coerceIn(0.25f, 2.0f),
            angryMul = angMul.coerceIn(0.25f, 2.0f),
            lonelyMul = lonMul.coerceIn(0.25f, 2.0f)
        )
    }

    private fun analyzeMessage(message: String): EmotionDelta {
        var happy = 0
        var sad = 0
        var angry = 0
        var lonely = 0
        var affection = 0

        val lower = message.lowercase()

        // Bug 32 fix: check negative keywords BEFORE positive ones to avoid substring false positives
        // e.g. "不开心" contains "开心" but should match sad, not happy
        val isNegative = lower.containsAny("不开心", "不高兴", "难过", "伤心", "哭", "郁闷", "孤独", "寂寞", "sad", "miss")
        if (isNegative) {
            sad += 5; lonely += 3
        }

        // Angry keywords
        if (lower.containsAny("生气", "愤怒", "讨厌", "烦", "恨", "angry", "hate")) {
            angry += 5
        }

        // Positive / happy keywords (only if not already matched as negative)
        if (!isNegative && lower.containsAny("开心", "高兴", "哈哈", "嘻嘻", "太好了", "喜欢", "爱", "棒", "赞", "nice", "happy", "love")) {
            happy += 5; affection += 3
        }

        // Affection keywords
        if (lower.containsAny("想你", "陪我", "在一起", "宝贝", "亲爱的", "最喜欢你")) {
            affection += 8; happy += 3; lonely -= 5
        }
        // Greeting
        if (lower.containsAny("你好", "早上好", "晚安", "hello", "hi", "good morning", "good night")) {
            affection += 2; lonely -= 3
        }

        // 不在此处截断 lonely：负增量（亲密/问候）需要传递下去以降低孤独感，
        // 0..100 边界已由 updateEmotion 的 clamp() 统一处理
        return EmotionDelta(happy, sad, angry, lonely, affection)
    }

    private fun applyDecay(emotion: EmotionStateEntity): EmotionStateEntity {
        fun decay(value: Int, target: Int, rate: Int = 2): Int {
            return if (value > target) max(target, value - rate)
            else if (value < target) min(target, value + rate)
            else value
        }
        return emotion.copy(
            happy = decay(emotion.happy, 50),
            sad = decay(emotion.sad, 0),
            angry = decay(emotion.angry, 0),
            lonely = decay(emotion.lonely, 20),
            affection = decay(emotion.affection, 50)
        )
    }

    private fun clamp(value: Int): Int = max(0, min(100, value))

    private data class EmotionDelta(
        val happy: Int = 0,
        val sad: Int = 0,
        val angry: Int = 0,
        val lonely: Int = 0,
        val affection: Int = 0
    )

    /**
     * 性格修正系数。所有系数默认 1.0（不改变原始行为）。
     * > 1.0 = 该情绪更容易触发，< 1.0 = 更难触发。
     */
    private data class PersonalityModifiers(
        val affectionMul: Float = 1.0f,
        val happyMul: Float = 1.0f,
        val sadMul: Float = 1.0f,
        val angryMul: Float = 1.0f,
        val lonelyMul: Float = 1.0f
    ) {
        companion object {
            val DEFAULT = PersonalityModifiers()
        }
    }
}
