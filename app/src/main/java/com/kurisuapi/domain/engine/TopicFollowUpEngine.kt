package com.kurisuapi.domain.engine

import com.kurisuapi.data.repository.ChatHistoryRepository
import com.kurisuapi.data.repository.ConversationIndexRepository
import com.kurisuapi.data.repository.ConversationSessionRepository
import com.kurisuapi.data.repository.MemoryRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 智能话题跟进引擎：检测未完成的话题，在合适的时机自然跟进。
 *
 * 基于 MapDia (CoNLL 2025) 和 MemCog (2026) 的研究：
 * - 对话结束时如果用户提出了问题或表达了需求，记录下来
 * - 下次主动消息时检查是否有"未完成的事项"
 * - 如果有高重要性的未完成话题，提升触发优先级
 *
 * 检测维度：
 * 1. 用户最后一条消息是否包含疑问（"？"、"吗"、"呢"等）
 * 2. 对话索引中是否有标记为重要的待跟进话题
 * 3. 近期记忆中是否有需要确认/提醒的事项
 */
@Singleton
class TopicFollowUpEngine @Inject constructor(
    private val chatHistoryRepository: ChatHistoryRepository,
    private val sessionRepository: ConversationSessionRepository,
    private val indexRepository: ConversationIndexRepository,
    private val memoryRepository: MemoryRepository
) {
    data class FollowUpResult(
        val hasUnresolved: Boolean,
        val score: Double,             // 0.0-1.0，未完成话题的紧急程度
        val topicSummary: String?,     // 需要跟进的话题简述
        val daysSinceLastMessage: Int   // 距离上次用户消息的天数
    )

    /**
     * 评估当前是否有需要跟进的话题。
     * @return 跟进评估结果，score 越高越应该跟进
     */
    suspend fun evaluate(characterId: Long): FollowUpResult {
        val sessionId = getActiveSessionId(characterId) ?: return FollowUpResult(false, 0.0, null, 0)

        // 1. 获取最后几条用户消息，检测未完成的话题
        val recentMessages = chatHistoryRepository.getRecentBySession(sessionId, 20)
        val userMessages = recentMessages.filter { it.sender == "user" }

        if (userMessages.isEmpty()) return FollowUpResult(false, 0.0, null, 0)

        val lastUserMsg = userMessages.last()
        val daysSinceLast = ((System.currentTimeMillis() - lastUserMsg.timestamp) / 86_400_000).toInt()

        var score = 0.0
        val reasons = mutableListOf<String>()

        // 2. 检测疑问句（用户可能在等答案）
        val lastContent = lastUserMsg.content
        val questionMarks = lastContent.count { it == '？' || it == '?' }
        val hasQuestionWords = listOf("吗", "呢", "吧", "嘛", "怎么", "什么", "为啥", "如何", "能不能")
            .any { it in lastContent }
        if (questionMarks > 0 || hasQuestionWords) {
            score += 0.35
            reasons.add("用户有未回答的问题")
        }

        // 3. 检测情绪/需求关键词（用户表达了需要关注的状态）
        val emotionKeywords = listOf(
            "难过", "不开心", "烦", "累", "困", "压力", "焦虑", "担心", "害怕",
            "不舒服", "生病", "忙", "加班", "失眠", "睡不着"
        )
        val matchedEmotions = emotionKeywords.filter { it in lastContent }
        if (matchedEmotions.isNotEmpty()) {
            score += 0.25
            reasons.add("用户表达了负面状态: ${matchedEmotions.take(2).joinToString("、")}")
        }

        // 4. 检测承诺/计划关键词（用户提过要做的事）
        val commitmentKeywords = listOf("明天", "下次", "改天", "周末", "之后", "回头", "有空", "等")
        if (commitmentKeywords.any { it in lastContent }) {
            score += 0.15
            reasons.add("用户有未完成的约定")
        }

        // 5. 检查对话索引中是否有近期重要话题（仅在有关键词时搜索，避免空字符串匹配所有记录）
        if (lastContent.isNotBlank()) {
            val recentIndexes = indexRepository.search(
                characterId = characterId,
                keyword = lastContent.take(20),
                limit = 3
            )
            val importantTopics = recentIndexes.filter { it.keywords.isNotBlank() }
            if (importantTopics.isNotEmpty()) {
                score += 0.15
                reasons.add("有相关历史话题: ${importantTopics.first().summary.take(30)}")
            }
        }

        // 6. 天数加权：时间越长越应该跟进（但不要超过上限）
        val dayWeight = (daysSinceLast / 3.0).coerceIn(0.0, 0.3)
        score += dayWeight

        val clampedScore = score.coerceIn(0.0, 1.0)
        val summary = if (reasons.isNotEmpty()) reasons.joinToString("；") else null

        return FollowUpResult(
            hasUnresolved = clampedScore >= 0.3,
            score = clampedScore,
            topicSummary = summary,
            daysSinceLastMessage = daysSinceLast
        )
    }

    /**
     * 为跟进场景生成增强的 prompt 片段。
     * 当检测到未完成话题时，在主动消息 prompt 中注入此内容。
     */
    fun buildFollowUpPrompt(followUp: FollowUpResult): String? {
        if (!followUp.hasUnresolved || followUp.topicSummary == null) return null
        return buildString {
            appendLine("<topic_follow_up>")
            appendLine("  <note>你上次和对方聊天时有一些未完成的话题：${followUp.topicSummary}</note>")
            appendLine("  <instruction>在开启新话题之前，自然地问一下对方之前提到的事情。")
            appendLine("    不要生硬地说\"你上次说过...\"，而是自然地关心。")
            appendLine("    如果已经过去了${followUp.daysSinceLastMessage}天，可以用\"之前你说...现在怎么样了\"的方式。</instruction>")
            appendLine("</topic_follow_up>")
        }
    }

    private suspend fun getActiveSessionId(characterId: Long): Long? {
        val sessions = sessionRepository.getAllOnce(characterId)
        return sessions.firstOrNull { !it.isArchived && !it.isDeleted }?.id
    }
}
