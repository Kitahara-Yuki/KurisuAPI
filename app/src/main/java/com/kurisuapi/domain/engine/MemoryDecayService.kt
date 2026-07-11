package com.kurisuapi.domain.engine

import com.kurisuapi.data.entity.MemoryEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.exp

/**
 * 艾宾浩斯记忆衰减服务。
 *
 * 核心公式（莱特纳间隔重复模型）：
 *   effective_lambda = base_lambda / (1 + recall_count × 0.5)
 *   decay_score = importance × e^(-effective_lambda × days_since_recall)
 *
 * 每次检索会减慢该记忆的衰减速度（模拟人类的"复习巩固"效应）。
 * 身份/高重要度记忆有衰减底线，永不彻底遗忘。
 */
@Singleton
class MemoryDecayService @Inject constructor() {

    companion object {
        // 半衰期校准为伴侣关系级别（年尺度，非实验级周尺度）
        // λ = ln(2) / halfLifeDays
        private const val LAMBDA_TRIVIAL = 0.035     // 琐事：半衰期 ~20天
        private const val LAMBDA_NORMAL = 0.007      // 一般：半衰期 ~100天
        private const val LAMBDA_PREFERENCE = 0.0019 // 偏好：半衰期 ~365天（1年）
        private const val LAMBDA_IDENTITY = 0.0007   // 身份：半衰期 ~1000天（~3年）

        // 每次检索对衰减速度的减缓系数（莱特纳模型）
        private const val RECALL_DECAY_SLOW = 0.5

        // 身份/高重要度记忆的衰减底线（永不彻底遗忘）
        private const val IDENTITY_FLOOR = 2.0f

        // 偏好/身份相关关键词
        private val PREFERENCE_KEYWORDS = listOf(
            "喜欢", "爱", "讨厌", "不喜欢", "习惯", "经常", "总是",
            "最爱", "偏好", "擅长", "不擅长", "害怕", "梦想", "想做",
            "来自", "住在", "名字", "年龄", "生日", "工作", "职业",
            "过敏", "恶心", "毕业", "学历", "专业", "公司",
            "结婚了", "女朋友", "男朋友", "家人", "父母", "孩子"
        )
    }

    /**
     * 计算记忆的当前衰减分数。分数越高越重要。
     * 重要度 8+ 的记忆有衰减底线，无论多久都不会跌到 2.0 以下。
     */
    fun computeDecayScore(memory: MemoryEntity, nowMs: Long = System.currentTimeMillis()): Float {
        val daysSinceRecall = if (memory.lastRecalledAt > 0) {
            ((nowMs - memory.lastRecalledAt) / (24.0 * 3600_000)).coerceAtLeast(0.0)
        } else {
            ((nowMs - memory.createdAt) / (24.0 * 3600_000)).coerceAtLeast(0.0)
        }

        val baseLambda = getDecayRate(memory)
        // 莱特纳模型：每次检索减慢衰减速度
        val effectiveLambda = baseLambda / (1.0 + memory.recallCount * RECALL_DECAY_SLOW)

        val timeDecay = exp(-effectiveLambda * daysSinceRecall)
        val raw = memory.importance * timeDecay

        // 身份级记忆的底线保护
        val floor = if (memory.importance >= 8 || getDecayRate(memory) == LAMBDA_IDENTITY) {
            IDENTITY_FLOOR
        } else {
            0f
        }

        return maxOf(raw.toFloat(), floor).coerceIn(0f, 10f)
    }

    /**
     * 根据记忆内容和重要度返回衰减率。
     * 高重要度（8+）和偏好关键词匹配的记忆用慢衰减。
     */
    fun getDecayRate(memory: MemoryEntity): Double {
        // 最高重要度 = 身份级衰减（半衰期约 3 年，永不彻底遗忘）
        if (memory.importance >= 9) return LAMBDA_IDENTITY

        // 高重要度 = 慢衰减，无论关键词
        if (memory.importance >= 8) return LAMBDA_PREFERENCE

        val content = memory.content
        for (keyword in PREFERENCE_KEYWORDS) {
            if (keyword in content) return LAMBDA_PREFERENCE
        }
        // 短内容（< 10字）往往是偏好陈述，用慢衰减
        if (content.length < 10) return LAMBDA_PREFERENCE
        return LAMBDA_NORMAL
    }
}
