package com.kurisuapi.domain.engine

import android.util.Log
import com.kurisuapi.data.api.ChatMessage
import com.kurisuapi.data.repository.ChatHistoryRepository
import com.kurisuapi.data.repository.ConversationSessionRepository
import com.kurisuapi.data.repository.SettingsRepository
import com.kurisuapi.domain.service.AiService
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 对话摘要引擎：当会话消息数超过阈值时，使用 LLM 生成增量摘要。
 *
 * - 首次摘要：会话消息 >= 30 条且尚无摘要
 * - 增量更新：之后每累计 30 条新消息，将旧摘要 + 新消息喂给 LLM 生成更完整的摘要
 * - 摘要存入 ConversationSessionEntity.summary，在 PromptBuilder 中注入 Prompt
 * - 所有 LLM 调用通过 Mutex 串行化，失败静默不阻塞聊天主流程
 */
@Singleton
class ConversationSummarizer @Inject constructor(
    private val aiService: AiService,
    private val chatHistoryRepository: ChatHistoryRepository,
    private val sessionRepository: ConversationSessionRepository,
    private val settingsRepository: SettingsRepository
) {
    companion object {
        private const val TAG = "ConversationSummarizer"
        private const val SUMMARY_THRESHOLD = 30   // 触发摘要的最小消息数
        private const val RECENT_FOR_SUMMARY = 40  // 喂给 LLM 的最近消息条数
        private const val SUMMARY_MAX_CHARS = 200  // 摘要最大长度（写入前裁剪），与 prompt 中的 200 字一致
    }

    private val mutex = Mutex()

    /**
     * 如果满足条件（消息数达到阈值且是首次或每 N 条更新），则生成/更新摘要。
     * 调用方应在后台协程中调用，不应阻塞主流程等待返回值。
     */
    suspend fun summarizeIfNeeded(sessionId: Long) {
        if (sessionId <= 0) return
        mutex.withLock {
            doSummarizeIfNeeded(sessionId)
        }
    }

    private suspend fun doSummarizeIfNeeded(sessionId: Long) {
        val count = try {
            chatHistoryRepository.countBySession(sessionId)
        } catch (e: Exception) {
            Log.e(TAG, "查询会话消息数失败", e)
            return
        }

        // 首次触发条件：消息数 >= 阈值且摘要为空
        val session = try {
            sessionRepository.getById(sessionId)
        } catch (e: Exception) {
            Log.e(TAG, "查询会话失败", e)
            return
        } ?: return

        val hasSummary = !session.summary.isNullOrBlank()
        if (!hasSummary && count < SUMMARY_THRESHOLD) return
        // 后续更新：距上次成功摘要后累计 SUMMARY_THRESHOLD 条新消息
        val lastCount = settingsRepository.getSummaryLastCount(sessionId)
        if (hasSummary && count - lastCount < SUMMARY_THRESHOLD) return

        // 获取最近消息（正序）
        val recent = try {
            chatHistoryRepository.getRecentBySession(sessionId, RECENT_FOR_SUMMARY).reversed()
        } catch (e: Exception) {
            Log.e(TAG, "读取聊天记录失败", e)
            return
        }
        if (recent.isEmpty()) return

        val conversation = recent.joinToString("\n") { chat ->
            val who = if (chat.sender == "user") "用户" else "AI"
            "$who: ${chat.content}"
        }

        val previousSummary = session.summary

        val systemPrompt = """
            你是一个对话摘要助手。请将以下对话内容压缩为一段简洁的摘要。
            要求：
            - 第三人称描述对话的要点、用户表达的重要信息、以及对话的情感基调
            - 如果有"已有摘要"，请将其与新对话内容融合，保留仍然有效的旧信息
            - 控制在一段话以内，不超过 200 字
            - 只输出摘要正文，不要任何前缀、标题或解释
        """.trimIndent()

        val userPrompt = buildString {
            if (!previousSummary.isNullOrBlank()) {
                append("已有摘要：\n")
                append(previousSummary)
                append("\n\n")
            }
            append("新对话内容：\n")
            append(conversation)
        }

        val response = try {
            val bgModel = settingsRepository.getBackgroundModel()
            aiService.chat(listOf(
                ChatMessage(role = "system", content = systemPrompt),
                ChatMessage(role = "user", content = userPrompt)
            ), modelOverride = bgModel.ifBlank { null })
        } catch (e: Exception) {
            Log.e(TAG, "摘要 LLM 调用失败", e)
            return
        }

        if (!response.success || response.content.isBlank()) return
        val summary = response.content.trim().take(SUMMARY_MAX_CHARS)
        if (summary.isBlank()) return

        try {
            sessionRepository.updateSummary(sessionId, summary)
            settingsRepository.setSummaryLastCount(sessionId, count)
        } catch (e: Exception) {
            Log.e(TAG, "摘要写入失败", e)
        }
    }
}
