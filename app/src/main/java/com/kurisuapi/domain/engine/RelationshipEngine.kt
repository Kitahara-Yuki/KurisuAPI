package com.kurisuapi.domain.engine

import com.kurisuapi.data.entity.RelationshipEntity
import com.kurisuapi.data.repository.RelationshipRepository
import com.kurisuapi.util.containsAny
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 关系引擎 —— 三轴模型 + Knapp 五阶段 + OU 时间衰减。
 *
 * ## 设计来源
 * - Knapp 关系发展模型 (1978): 初识→探索→深入→融合→羁绊
 * - Sternberg 三角理论: 亲密(intimacy) × 激情(attraction) × 承诺(trust)
 * - Ornstein-Uhlenbeck 过程: 均值回归衰减 (affective-longing 项目)
 * - Stardew Valley / Persona: 每日上限、封锁机制
 * - FeelOra: 每阶段对应不同对话行为参数
 *
 * ## 与项目其他系统的交织
 * - **EmotionEngine**: 共享 personality 标签解析逻辑（保持一致的标签体系）
 * - **PromptBuilder**: 注入阶段对话指导（调用 RelationshipEntity 的三轴 + 阶段数据）
 * - **MemoryExtractor**: 记忆精炼时微调关系值（通过 score/composite 计算属性交互）
 * - **ProactiveTriggerEngine**: 羁绊阶段的主动消息频率更高、内容更亲密
 */
@Singleton
class RelationshipEngine @Inject constructor(
    private val relationshipRepository: RelationshipRepository
) {
    companion object {
        /** @deprecated 使用 [RelationshipEntity.STAGES] 代替 */
        val LEVELS = listOf(
            "陌生人" to 0,
            "朋友" to 20,
            "好友" to 50,
            "知己" to 80,
            "恋人" to 100
        )

        // ═══════════════════════════════════════════
        // OU 衰减参数
        // ═══════════════════════════════════════════

        /** 衰减速率 θ：每天向基线回归的比例 (0-1) */
        const val DECAY_THETA = 0.01f

        /** 底线保护系数：已建立的最高分不会跌到这个比例以下 */
        const val FLOOR_RATIO = 0.3f

        // ═══════════════════════════════════════════
        // 每日上限
        // ═══════════════════════════════════════════

        /** 单轴每日最大增益（在性格修正之前） */
        const val DAILY_CAP_PER_AXIS = 8

        // ═══════════════════════════════════════════
        // 封锁机制阈值
        // ═══════════════════════════════════════════

        /** 从探索→深入需要至少几次深入交流事件 */
        const val LOCK_DEEP_TALKS_FOR_INTENSIFY = 3

        /** 从深入→融合需要至少几次危机陪伴事件 */
        const val LOCK_CRISIS_EVENTS_FOR_INTEGRATE = 1

        // ═══════════════════════════════════════════
        // 事件加分表
        // ═══════════════════════════════════════════
        data class EventGains(
            val intimacy: Float = 0f,
            val trust: Float = 0f,
            val attraction: Float = 0f
        )

        /** 基础交互（每条消息都有） */
        val EVENT_BASE = EventGains(0.5f, 0.3f, 0.2f)
        /** 长消息 (>50字) */
        val EVENT_LONG_MSG = EventGains(1.0f, 0.5f, 0.3f)
        /** 很长消息 (>100字) */
        val EVENT_VERY_LONG_MSG = EventGains(1.5f, 0.5f, 0.3f)
        /** 积极互动 (喜欢/想你/谢谢) */
        val EVENT_POSITIVE = EventGains(1.0f, 0.5f, 1.5f)
        /** 深入交流 (觉得/感受/秘密/心里) */
        val EVENT_DEEP_TALK = EventGains(2.0f, 1.5f, 1.0f)
        /** 快速回复 (1分钟内) */
        val EVENT_QUICK_REPLY = EventGains(0.5f, 0.5f, 0.3f)
        /** 共享笑声 (哈哈/笑死/😂) */
        val EVENT_SHARED_LAUGH = EventGains(0.8f, 0.5f, 0.5f)
        /** 情绪流露 (难过/哭/累/压力) */
        val EVENT_EMOTIONAL_DISCLOSURE = EventGains(2.0f, 1.5f, 0.5f)
        /** 争吵/敌意 */
        val EVENT_HOSTILE = EventGains(-1.0f, -3.0f, -2.0f)
        /** 道歉 */
        val EVENT_APOLOGY = EventGains(1.0f, 2.0f, 0.5f)
        /** 沉默1天 */
        val EVENT_SILENCE_1DAY = EventGains(-1.0f, -1.5f, -0.5f)
        /** 沉默3天+ */
        val EVENT_SILENCE_3DAY = EventGains(-3.0f, -4.0f, -2.0f)
    }

    private val relationshipMutex = Mutex()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    // ═══════════════════════════════════════════
    // 公开 API（保持旧签名兼容）
    // ═══════════════════════════════════════════

    suspend fun getRelationship(characterId: Long): RelationshipEntity {
        return relationshipRepository.getByCharacterOnce(characterId)
            ?: RelationshipEntity(characterId = characterId).also {
                relationshipRepository.insertOrUpdate(it)
            }
    }

    /**
     * 更新角色关系值 —— 核心入口。
     *
     * @param characterId 角色 ID
     * @param userMessage 用户消息内容
     * @param personality 角色性格描述文本，用于个性化加权
     */
    suspend fun updateRelationship(
        characterId: Long,
        userMessage: String,
        personality: String = ""
    ) = relationshipMutex.withLock {
        val current = getRelationship(characterId)
        val now = System.currentTimeMillis()

        // ── 第 1 步：应用 OU 时间衰减（不互动时自然回落）──
        val afterDecay = applyDecay(current, now)

        // ── 第 2 步：检测事件并计算三轴增益 ──
        val eventGains = detectEvents(userMessage, current.lastInteractionTime, now)

        // ── 第 3 步：性格加权 ──
        val weights = getPersonalityWeights(personality)
        val weightedIntimacy = eventGains.intimacy * weights.intimacyMul
        val weightedTrust = eventGains.trust * weights.trustMul
        val weightedAttraction = eventGains.attraction * weights.attractionMul

        // ── 第 4 步：每日上限检查 ──
        val today = dateFormat.format(Date(now))
        var dailyTracker = DailyGainsTracker.parse(afterDecay.dailyGains, today)
        val (tracker1, cappedIntimacy) = dailyTracker.applyCap("intimacy", weightedIntimacy, weights.intimacyMul)
        dailyTracker = tracker1
        val (tracker2, cappedTrust) = dailyTracker.applyCap("trust", weightedTrust, weights.trustMul)
        dailyTracker = tracker2
        val (tracker3, cappedAttraction) = dailyTracker.applyCap("attraction", weightedAttraction, weights.attractionMul)
        dailyTracker = tracker3

        // ── 第 5 步：应用增益（含负向不减半）──
        fun applyGain(currentVal: Int, gain: Float): Int {
            if (gain >= 0) {
                return maxOf(0, minOf(100, currentVal + gain.toInt()))
            } else {
                // 负向变化：性格不减速（该降就降），但不低于 0
                // 注意：负向不减半已在 capped 值中为原始值
                return maxOf(0, minOf(100, (currentVal + gain).toInt()))
            }
        }

        val newIntimacy = applyGain(afterDecay.intimacy, cappedIntimacy)
        val newTrust = applyGain(afterDecay.trust, cappedTrust)
        val newAttraction = applyGain(afterDecay.attraction, cappedAttraction)

        // ── 第 6 步：封锁检查 ──
        val rawComposite = newIntimacy + newTrust + newAttraction
        val weightedComposite = calculateWeightedComposite(
            newIntimacy, newTrust, newAttraction, weights
        )
        val candidateStage = RelationshipEntity.stageForComposite(weightedComposite)
        val finalStage = applyLockMechanism(
            currentStage = afterDecay.stage,
            candidateStage = candidateStage,
            currentComposite = current.rawComposite,
            newComposite = rawComposite,
            hasDeepTalk = eventGains.intimacy >= EVENT_DEEP_TALK.intimacy,  // 本次有深入交流
            hasEmotionalDisclosure = eventGains.intimacy >= EVENT_EMOTIONAL_DISCLOSURE.intimacy  // 本次有情绪流露
        )

        // ── 第 7 步：同步向后兼容字段 ──
        val compatScore = minOf(100, (newIntimacy + newTrust + newAttraction) / 3)

        // ── 第 8 步：持久化 ──
        relationshipRepository.insertOrUpdate(
            afterDecay.copy(
                intimacy = newIntimacy,
                trust = newTrust,
                attraction = newAttraction,
                stage = finalStage,
                score = compatScore,
                level = finalStage,
                lastInteractionTime = now,
                updatedAt = now,
                dailyGains = dailyTracker.toJson()
            )
        )
    }

    // ═══════════════════════════════════════════
    // OU 时间衰减
    // ═══════════════════════════════════════════

    /**
     * Ornstein-Uhlenbeck 均值回归衰减。
     *
     * 公式: 衰减量 = θ × (当前值 - 基线值) × 天数
     * 底线: 不低于已建立最高分的 FLOOR_RATIO（30%）
     *
     * 基线值取决于性格：每轴有独立的自然基线（温柔型基线高，冷淡型基线低）。
     * 这里使用通用基线（各轴均为 20），在后续可以按性格调整。
     */
    private fun applyDecay(current: RelationshipEntity, now: Long): RelationshipEntity {
        if (current.lastInteractionTime <= 0) {
            return current.copy(lastInteractionTime = now)
        }

        val elapsedDays = maxOf(0f, (now - current.lastInteractionTime).toFloat() / (24 * 60 * 60 * 1000))
        if (elapsedDays < 0.5f) return current  // 不到半天不衰减

        fun decayAxis(value: Int, baseline: Int = 20): Int {
            if (value <= baseline) return value
            val target = baseline.toFloat()
            // OU: dX = -θ × (X - μ) × dt
            val newVal = value - DECAY_THETA * (value - target) * elapsedDays
            // 底线保护
            val floor = maxOf(baseline.toFloat(), value * FLOOR_RATIO)
            return maxOf(baseline, maxOf(floor, newVal).toInt())
        }

        val newIntimacy = decayAxis(current.intimacy)
        val newTrust = decayAxis(current.trust)
        val newAttraction = decayAxis(current.attraction)

        if (newIntimacy == current.intimacy && newTrust == current.trust && newAttraction == current.attraction) {
            return current
        }

        // 衰减后重新判定阶段
        val composite = newIntimacy + newTrust + newAttraction
        val newStage = if (composite < current.rawComposite) {
            // 分数降低了，重新判定（不会回到初识以下）
            val candidate = RelationshipEntity.stageForComposite(composite)
            val currentIdx = RelationshipEntity.STAGES.indexOf(current.stage)
            val candidateIdx = RelationshipEntity.STAGES.indexOf(candidate)
            if (candidateIdx >= currentIdx - 1) candidate else RelationshipEntity.STAGES[maxOf(0, currentIdx - 1)]
        } else {
            current.stage
        }

        return current.copy(
            intimacy = newIntimacy,
            trust = newTrust,
            attraction = newAttraction,
            stage = newStage
        )
    }

    // ═══════════════════════════════════════════
    // 事件检测
    // ═══════════════════════════════════════════

    /**
     * 从用户消息中检测关系事件，返回三轴增益值。
     *
     * 检测逻辑优先匹配高层次事件（如情绪流露 > 积极互动 > 基础），
     * 长消息作为叠加 bonus。
     */
    private fun detectEvents(
        message: String,
        lastInteractionTime: Long,
        now: Long
    ): EventGains {
        var intimacy = 0f
        var trust = 0f
        var attraction = 0f

        val lower = message.lowercase()

        // ── 负向事件（最优先检测，一旦命中直接返回负值）──
        val hasHostile = lower.containsAny("讨厌", "恨你", "滚", "分手", "再也不想", "离我远点")
        val isNegated = lower.containsAny(
            "不讨厌", "别讨厌", "没讨厌", "不要讨厌",
            "不恨你", "别恨你", "不想分手", "不是分手", "不要分手", "没分手",
            "不滚", "别滚"
        )
        if (hasHostile && !isNegated) {
            return EVENT_HOSTILE
        }

        // ── 道歉事件 ──
        if (lower.containsAny("对不起", "抱歉", "我错了", "原谅", "原谅我")) {
            intimacy += EVENT_APOLOGY.intimacy
            trust += EVENT_APOLOGY.trust
            attraction += EVENT_APOLOGY.attraction
        }

        // ── 情绪流露 (危机/脆弱时刻) ──
        if (lower.containsAny("难过", "想哭", "哭了", "好累", "压力好大", "撑不住", "崩溃")) {
            intimacy += EVENT_EMOTIONAL_DISCLOSURE.intimacy
            trust += EVENT_EMOTIONAL_DISCLOSURE.trust
            attraction += EVENT_EMOTIONAL_DISCLOSURE.attraction
        }

        // ── 深入交流 ──
        if (lower.containsAny("觉得", "认为", "想法", "感受", "心里", "秘密", "告诉你一件事")) {
            intimacy += EVENT_DEEP_TALK.intimacy
            trust += EVENT_DEEP_TALK.trust
            attraction += EVENT_DEEP_TALK.attraction
        }

        // ── 积极互动 ──
        if (lower.containsAny("喜欢", "爱", "谢谢", "开心", "陪伴", "想你", "在乎", "珍惜")) {
            intimacy += EVENT_POSITIVE.intimacy
            trust += EVENT_POSITIVE.trust
            attraction += EVENT_POSITIVE.attraction
        }

        // ── 共享笑声 ──
        if (lower.containsAny("哈哈", "笑死", "😂", "🤣", "www", "hhhh", "笑死我了")) {
            intimacy += EVENT_SHARED_LAUGH.intimacy
            trust += EVENT_SHARED_LAUGH.trust
            attraction += EVENT_SHARED_LAUGH.attraction
        }

        // ── 消息长度 bonus（叠加到已有增益）──
        if (message.length > 50) {
            intimacy += EVENT_LONG_MSG.intimacy
            trust += EVENT_LONG_MSG.trust
            attraction += EVENT_LONG_MSG.attraction
        }
        if (message.length > 100) {
            intimacy += EVENT_VERY_LONG_MSG.intimacy
            trust += EVENT_VERY_LONG_MSG.trust
            attraction += EVENT_VERY_LONG_MSG.attraction
        }

        // ── 回复速度 ──
        if (lastInteractionTime > 0) {
            val gapMinutes = (now - lastInteractionTime) / (60 * 1000)
            if (gapMinutes in 1..60) {
                // 1小时内回复，给予快速回复 bonus
                intimacy += EVENT_QUICK_REPLY.intimacy
                trust += EVENT_QUICK_REPLY.trust
                attraction += EVENT_QUICK_REPLY.attraction
            } else if (gapMinutes > 24 * 60) {
                // 超过1天未互动，衰减（由 applyDecay 处理）
            }
        }

        // ── 基础分（每条消息至少有的微小加分）──
        intimacy += EVENT_BASE.intimacy
        trust += EVENT_BASE.trust
        attraction += EVENT_BASE.attraction

        return EventGains(intimacy, trust, attraction)
    }

    // ═══════════════════════════════════════════
    // 性格权重矩阵
    // ═══════════════════════════════════════════

    /**
     * 性格权重 —— 决定不同性格角色在各轴上的增长速率。
     *
     * 矩阵设计逻辑：
     * - 傲娇: 嘴硬心软，吸引度涨得快（易动心），亲密度涨得慢（不坦率）
     * - 冷淡: 所有轴都慢，但信任度相对"正常"（一旦信任了反而稳）
     * - 病娇: 吸引度极快，亲密度快，但信任度脆弱（占有欲 ≠ 信任）
     * - 三无: 极度慢热，一旦突破阈值后会加速（由封锁机制解锁后体现）
     */
    data class PersonalityWeights(
        val intimacyMul: Float = 1.0f,
        val trustMul: Float = 1.0f,
        val attractionMul: Float = 1.0f,
        /** 综合分计算时的三轴权重 */
        val intimacyWeight: Float = 1.0f,
        val trustWeight: Float = 1.0f,
        val attractionWeight: Float = 1.0f
    )

    private fun getPersonalityWeights(personality: String): PersonalityWeights {
        if (personality.isBlank()) return PersonalityWeights()

        val lower = personality.lowercase()
        var iMul = 1.0f
        var tMul = 1.0f
        var aMul = 1.0f
        var iWeight = 1.0f
        var tWeight = 1.0f
        var aWeight = 1.0f

        // ── 慢热型 ──
        if (lower.containsAny("傲娇", "别扭", "嘴硬", "tsundere")) {
            iMul *= 0.7f; tMul *= 0.9f; aMul *= 1.3f
            iWeight = 1.0f; tWeight = 0.9f; aWeight = 1.2f
        }
        if (lower.containsAny("内向", "害羞", "怕生", "社恐")) {
            iMul *= 0.5f; tMul *= 0.7f; aMul *= 0.5f
            iWeight = 0.8f; tWeight = 1.2f; aWeight = 0.7f
        }
        if (lower.containsAny("冷淡", "高冷", "冷漠", "冰山")) {
            iMul *= 0.4f; tMul *= 0.5f; aMul *= 0.3f
            iWeight = 0.6f; tWeight = 1.3f; aWeight = 0.5f
        }
        if (lower.containsAny("三无", "无口", "无表情")) {
            iMul *= 0.3f; tMul *= 0.4f; aMul *= 0.2f
            iWeight = 0.5f; tWeight = 1.0f; aWeight = 0.4f
        }
        if (lower.containsAny("毒舌", "腹黑")) {
            iMul *= 0.6f; tMul *= 0.7f; aMul *= 1.0f
            iWeight = 0.9f; tWeight = 1.0f; aWeight = 1.1f
        }

        // ── 快热型 ──
        if (lower.containsAny("热情", "主动", "奔放", "外向")) {
            iMul *= 1.3f; tMul *= 1.0f; aMul *= 1.2f
            iWeight = 1.2f; tWeight = 0.9f; aWeight = 1.0f
        }
        if (lower.containsAny("温柔", "体贴")) {
            iMul *= 1.2f; tMul *= 1.1f; aMul *= 0.9f
            iWeight = 1.1f; tWeight = 1.1f; aWeight = 0.9f
        }
        if (lower.containsAny("活泼", "开朗", "元气")) {
            iMul *= 1.1f; tMul *= 0.9f; aMul *= 1.1f
            iWeight = 1.1f; tWeight = 0.9f; aWeight = 1.1f
        }
        if (lower.containsAny("敏感", "细腻", "多愁善感")) {
            iMul *= 1.3f; tMul *= 0.8f; aMul *= 1.0f
            iWeight = 1.3f; tWeight = 0.7f; aWeight = 1.0f
        }

        // ── 病娇（特殊：极速升温但信任脆弱）──
        if (lower.containsAny("病娇")) {
            iMul *= 1.5f; tMul *= 0.8f; aMul *= 1.8f
            iWeight = 1.0f; tWeight = 0.6f; aWeight = 1.5f
        }

        return PersonalityWeights(
            intimacyMul = iMul.coerceIn(0.2f, 2.0f),
            trustMul = tMul.coerceIn(0.2f, 2.0f),
            attractionMul = aMul.coerceIn(0.2f, 2.0f),
            intimacyWeight = iWeight,
            trustWeight = tWeight,
            attractionWeight = aWeight
        )
    }

    /**
     * 计算加权综合分（使用性格权重）。
     */
    private fun calculateWeightedComposite(
        intimacy: Int,
        trust: Int,
        attraction: Int,
        weights: PersonalityWeights
    ): Int {
        return (intimacy * weights.intimacyWeight +
                trust * weights.trustWeight +
                attraction * weights.attractionWeight).toInt()
    }

    // ═══════════════════════════════════════════
    // 封锁机制
    // ═══════════════════════════════════════════

    /**
     * 封锁机制：某些阶段升级需要满足特殊条件。
     *
     * - 探索→深入：需要有足够的深入交流（deepTalkCount）
     * - 深入→融合：需要有情绪脆弱的相互暴露时刻（crisisCount）
     * - 融合→羁绊：需要综合分达标 + 确认关系的"仪式感"互动
     *
     * 当前实现：简化版，依赖本次对话的事件检测结果。
     * 后续可通过 RelationshipEntity 的元数据字段追踪累计事件计数。
     */
    private fun applyLockMechanism(
        currentStage: String,
        candidateStage: String,
        currentComposite: Int,
        newComposite: Int,
        hasDeepTalk: Boolean,
        hasEmotionalDisclosure: Boolean
    ): String {
        val currentIdx = RelationshipEntity.STAGES.indexOf(currentStage)
        val candidateIdx = RelationshipEntity.STAGES.indexOf(candidateStage)

        // 不允许降级（阶段只升不降，除非衰减）
        if (candidateIdx <= currentIdx) return currentStage

        // 只允许逐级升级
        if (candidateIdx > currentIdx + 1) {
            // 跳级了，只升一级
            val nextStage = RelationshipEntity.STAGES[currentIdx + 1]
            return applyLockMechanism(
                currentStage, nextStage, currentComposite, newComposite,
                hasDeepTalk, hasEmotionalDisclosure
            )
        }

        // 探索(1) → 深入(2): 需要深入交流事件
        if (currentStage == "探索" && candidateStage == "深入") {
            if (!hasDeepTalk && !hasEmotionalDisclosure) return currentStage
        }

        // 深入(2) → 融合(3): 需要情绪脆弱暴露
        if (currentStage == "深入" && candidateStage == "融合") {
            if (!hasEmotionalDisclosure) return currentStage
        }

        // 融合(3) → 羁绊(4): 需要综合分在加权后依然达标
        if (currentStage == "融合" && candidateStage == "羁绊") {
            val threshold = RelationshipEntity.STAGE_THRESHOLDS["羁绊"] ?: 240
            if (newComposite < threshold) return currentStage
        }

        return candidateStage
    }

    // ═══════════════════════════════════════════
    // 公开工具方法
    // ═══════════════════════════════════════════

    /**
     * 获取性格权重（供 PromptBuilder 等外部使用）。
     */
    fun getWeightsForPersonality(personality: String): PersonalityWeights {
        return getPersonalityWeights(personality)
    }

    /**
     * 根据阶段名获取阶段序号 (0-4)。
     */
    fun stageIndex(stage: String): Int {
        return RelationshipEntity.STAGES.indexOf(stage).coerceIn(0, 4)
    }
}

// ═══════════════════════════════════════════
// 每日增益追踪器（内部工具类）
// ═══════════════════════════════════════════

/**
 * 追踪每日增益，实现每日上限。
 *
 * JSON 格式: {"date":"2026-06-23","intimacy":3.5,"trust":2.0,"attraction":1.0}
 *
 * 不可变设计：每次 `applyCap` 返回 (新 tracker, 允许的增益值) 对。
 */
private class DailyGainsTracker(
    val date: String,
    val intimacy: Float = 0f,
    val trust: Float = 0f,
    val attraction: Float = 0f
) {
    companion object {
        fun parse(json: String, today: String): DailyGainsTracker {
            if (json.isBlank()) return DailyGainsTracker(today)
            return try {
                val obj = JSONObject(json)
                val savedDate = obj.optString("date", "")
                if (savedDate != today) {
                    DailyGainsTracker(today)
                } else {
                    DailyGainsTracker(
                        date = today,
                        intimacy = obj.optDouble("intimacy", 0.0).toFloat(),
                        trust = obj.optDouble("trust", 0.0).toFloat(),
                        attraction = obj.optDouble("attraction", 0.0).toFloat()
                    )
                }
            } catch (_: Exception) {
                DailyGainsTracker(today)
            }
        }
    }

    /**
     * 对指定轴应用每日上限，返回 (新 tracker, 实际增益)。
     */
    fun applyCap(axis: String, gain: Float, personalityMul: Float): Pair<DailyGainsTracker, Float> {
        if (gain <= 0) return Pair(this, gain)  // 负向不限

        val current = getAxis(axis)
        val cap = RelationshipEngine.DAILY_CAP_PER_AXIS * personalityMul.coerceIn(0.5f, 1.5f)
        val available = maxOf(0f, cap - current)

        val actual = if (gain <= available) gain else available
        val newTracker = withAxis(axis, current + actual)
        return Pair(newTracker, actual)
    }

    private fun getAxis(axis: String): Float = when (axis) {
        "intimacy" -> intimacy
        "trust" -> trust
        "attraction" -> attraction
        else -> 0f
    }

    private fun withAxis(axis: String, value: Float): DailyGainsTracker = when (axis) {
        "intimacy" -> DailyGainsTracker(date, value, trust, attraction)
        "trust" -> DailyGainsTracker(date, intimacy, value, attraction)
        "attraction" -> DailyGainsTracker(date, intimacy, trust, value)
        else -> this
    }

    fun toJson(): String {
        val obj = JSONObject()
        obj.put("date", date)
        obj.put("intimacy", intimacy.toDouble())
        obj.put("trust", trust.toDouble())
        obj.put("attraction", attraction.toDouble())
        return obj.toString()
    }
}
