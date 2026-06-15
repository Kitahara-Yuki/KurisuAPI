package com.kurisuapi.domain.engine

import com.kurisuapi.data.entity.RelationshipEntity
import com.kurisuapi.data.repository.RelationshipRepository
import com.kurisuapi.util.containsAny
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RelationshipEngine @Inject constructor(
    private val relationshipRepository: RelationshipRepository
) {
    companion object {
        val LEVELS = listOf(
            "陌生人" to 0,
            "朋友" to 20,
            "好友" to 50,
            "知己" to 80,
            "恋人" to 100
        )
    }

    private val relationshipMutex = Mutex()

    suspend fun getRelationship(characterId: Long): RelationshipEntity {
        return relationshipRepository.getByCharacterOnce(characterId)
            ?: RelationshipEntity(characterId = characterId).also {
                relationshipRepository.insertOrUpdate(it)
            }
    }

    /**
     * 更新角色关系值。
     *
     * @param personality 角色性格描述文本。用于个性化关系升温/降温速度。
     *                    空字符串使用中性默认值（系数 1.0）。
     */
    suspend fun updateRelationship(characterId: Long, userMessage: String, personality: String = "") = relationshipMutex.withLock {
        val current = getRelationship(characterId)
        val rawChange = calculateScoreChange(userMessage, current.score)

        // 性格修正：不同性格的关系变化速度不同
        val personalityMul = getRelationshipPersonalityMultiplier(personality)
        val adjustedChange = if (rawChange >= 0) {
            maxOf(0, (rawChange * personalityMul).toInt())
        } else {
            // 负向变化（争吵等）不受性格减速影响，但受加速影响
            minOf(0, (rawChange * if (personalityMul < 1.0f) 1.0f else personalityMul).toInt())
        }

        val newScore = maxOf(0, minOf(100, current.score + adjustedChange))
        val newLevel = calculateLevel(newScore)

        relationshipRepository.insertOrUpdate(
            current.copy(
                score = newScore,
                level = newLevel,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    /**
     * 根据角色性格计算关系变化速度系数。
     *
     * - 傲娇/内向/害羞/冷淡: 关系升温慢（难以敞开心扉）
     * - 热情/主动/温柔: 关系升温快（容易建立连接）
     * - 病娇: 关系升温极快（但也容易因负面事件急剧下降）
     *
     * 多个标签命中时乘算叠加，限制在 [0.3, 2.0]。
     */
    private fun getRelationshipPersonalityMultiplier(personality: String): Float {
        if (personality.isBlank()) return 1.0f

        val lower = personality.lowercase()
        var mul = 1.0f

        // 慢热型
        if (lower.containsAny("傲娇", "别扭", "嘴硬", "tsundere")) {
            mul *= 0.7f
        }
        if (lower.containsAny("内向", "害羞", "怕生", "社恐")) {
            mul *= 0.6f
        }
        if (lower.containsAny("冷淡", "高冷", "冷漠", "冰山")) {
            mul *= 0.4f
        }
        if (lower.containsAny("三无", "无口", "无表情")) {
            mul *= 0.3f
        }
        if (lower.containsAny("毒舌", "腹黑")) {
            mul *= 0.8f
        }

        // 快热型
        if (lower.containsAny("热情", "主动", "奔放", "外向")) {
            mul *= 1.5f
        }
        if (lower.containsAny("温柔", "体贴")) {
            mul *= 1.2f
        }
        if (lower.containsAny("活泼", "开朗", "元气")) {
            mul *= 1.3f
        }
        if (lower.containsAny("敏感", "细腻", "多愁善感")) {
            mul *= 1.1f
        }

        // 病娇：极快升温，但负向事件也不减速（已在 calculateScoreChange 中通过 minOf 处理）
        if (lower.containsAny("病娇")) {
            mul *= 1.8f
        }

        return mul.coerceIn(0.3f, 2.0f)
    }

    private fun calculateScoreChange(message: String, currentScore: Int): Int {
        val lower = message.lowercase()

        // 敌意/负向消息应降低关系值，而不是仍然 +1
        if (lower.containsAny("讨厌", "恨你", "滚", "分手", "再也不想")) {
            return -3
        }

        var change = 1 // Base score for each interaction

        // Longer messages show more engagement
        if (message.length > 50) change += 1
        if (message.length > 100) change += 1

        // Positive interactions
        if (lower.containsAny("喜欢", "爱", "谢谢", "开心", "陪伴", "想你")) {
            change += 2
        }

        // Deep conversations
        if (lower.containsAny("觉得", "认为", "想法", "感受", "心里", "秘密")) {
            change += 2
        }

        // Bug fix: diminishing returns — each threshold divides the PREVIOUS result,
        // not the original value. Previously: >50 then >80 both divided `change`,
        // so a score of 90 only got one reduction instead of two.
        if (currentScore > 80) change = maxOf(1, change / 2)
        if (currentScore > 50) change = maxOf(1, change / 2)

        return change
    }

    private fun calculateLevel(score: Int): String {
        return LEVELS.lastOrNull { score >= it.second }?.first ?: "陌生人"
    }
}
