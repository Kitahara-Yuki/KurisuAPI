package com.kurisuapi.domain.engine

import com.kurisuapi.data.repository.ChatHistoryRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 时间守护者 — 让 AI 感知对话节奏和时间流逝。
 *
 * 方案来源：OGMA 项目 Temporal Guardian + Daily Journal。
 * 纯本地计算，不需要任何 API 调用。
 */
@Singleton
class TemporalGuardian @Inject constructor(
    private val chatHistoryRepository: ChatHistoryRepository
) {
    companion object {
        private const val MINUTE = 60_000L
        private const val HOUR = 60 * MINUTE
        private const val DAY = 24 * HOUR
    }

    /**
     * 获取指定角色最后一条消息的时间戳。
     * 遍历最近的聊天记录直到找到一条用户消息。
     */
    suspend fun getLastActiveTime(characterId: Long): Long {
        return try {
            // 扩大到 100 条，防止最近消息全是 AI 回复时丢失用户活跃时间
            val recent = chatHistoryRepository.getRecent(characterId, 100)
            // 找最后一条 user 消息的时间
            recent.firstOrNull { it.sender == "user" }?.timestamp ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * 获取最近对话的简短摘要（上一次的话题是什么）。
     * 取最后几条用户消息作为上下文。
     */
    suspend fun getRecentContext(characterId: Long, limit: Int = 3): String? {
        return try {
            val recent = chatHistoryRepository.getRecent(characterId, 50)
            val userMessages = recent.filter { it.sender == "user" }.take(limit)
            if (userMessages.isEmpty()) return null
            userMessages.joinToString("；") { it.content.take(30) }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 构建时间语境文本，用于注入系统提示词。
     * @param lastActiveMs 上次用户活跃时间戳
     * @param nowMs 当前时间戳
     * @param recentContext 最近对话上下文（可选）
     * @return 自然语言的时间语境描述
     */
    suspend fun buildTemporalContext(
        characterId: Long,
        nowMs: Long = System.currentTimeMillis()
    ): String {
        val lastActiveMs = getLastActiveTime(characterId)

        return if (lastActiveMs == 0L) {
            "这是你们的第一次对话。"
        } else {
            val diffMs = nowMs - lastActiveMs
            val minutes = diffMs / MINUTE
            val hours = minutes / 60
            val days = hours / 24

            val absenceText = when {
                minutes < 5 -> "你们刚刚还在聊天。"
                minutes < 60 -> "${minutes}分钟前聊过。"
                hours < 6 -> "${hours}小时前聊过。"
                hours < 24 -> "${hours}小时没见了。"
                days < 2 -> "昨天聊过，隔了一天了。"
                days < 7 -> "已经${days}天没见了。"
                days < 30 -> "已经${days / 7}周没见了。"
                else -> "很久没见了（${days / 30}个月）。"
            }

            // 超过 1 小时没聊，补充上次话题
            val contextText = if (hours >= 1) {
                val recent = getRecentContext(characterId)
                if (recent != null) {
                    val lastDate = formatShortDate(lastActiveMs)
                    "\n上次你们聊了：$recent（$lastDate）"
                } else null
            } else null

            absenceText + (contextText ?: "")
        }
    }

    /** 简短日期格式：6月15日 */
    private fun formatShortDate(timestampMs: Long): String {
        return try {
            val instant = java.time.Instant.ofEpochMilli(timestampMs)
            val local = instant.atZone(java.time.ZoneId.systemDefault())
            "${local.monthValue}月${local.dayOfMonth}日"
        } catch (e: Exception) {
            ""
        }
    }
}
