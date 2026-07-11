package com.kurisuapi.service

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.kurisuapi.data.api.ChatMessage
import com.kurisuapi.data.entity.CharacterEntity
import com.kurisuapi.data.entity.DiaryEntryEntity
import com.kurisuapi.data.repository.*
import com.kurisuapi.domain.service.AiService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * 每天早上 7 点生成昨天的对话日记。
 * 以角色第一人称视角，总结昨天的互动。
 */
@HiltWorker
class DiaryWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val settingsRepository: SettingsRepository,
    private val characterRepository: CharacterRepository,
    private val chatHistoryRepository: ChatHistoryRepository,
    private val diaryRepository: DiaryRepository,
    private val aiService: AiService
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "DiaryWorker"
        private const val WORK_NAME = "daily_diary"

        /** 注册每日日记任务（幂等，重复调用不创建重复任务） */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()

            // 计算距离明天早上7点的延迟
            val now = LocalTime.now(ZoneId.of("Asia/Shanghai"))
            val target = LocalTime.of(7, 0)
            val nowInstant = Instant.now()
            val todayTarget = LocalDateTime.of(LocalDate.now(ZoneId.of("Asia/Shanghai")), target)
                .atZone(ZoneId.of("Asia/Shanghai")).toInstant()

            var delayMs = todayTarget.toEpochMilli() - nowInstant.toEpochMilli()
            if (delayMs <= 0) {
                // 今天7点已过，等明天7点
                delayMs += TimeUnit.DAYS.toMillis(1)
            }

            val request = OneTimeWorkRequestBuilder<DiaryWorker>()
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .setConstraints(constraints)
                .addTag(WORK_NAME)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    request
                )

            Log.i(TAG, "日记任务已注册，将在 ${delayMs / 1000 / 60} 分钟后执行")
        }
    }

    override suspend fun doWork(): Result {
        Log.i(TAG, "开始生成每日日记...")

        try {
            // 1. 获取活跃角色
            val activeCharacterId = settingsRepository.getActiveCharacterId()
            if (activeCharacterId == null || activeCharacterId <= 0) {
                Log.w(TAG, "没有活跃角色，跳过日记生成")
                return Result.success()
            }

            val character = characterRepository.getById(activeCharacterId)
            if (character == null) {
                Log.w(TAG, "角色不存在 id=$activeCharacterId")
                return Result.success()
            }

            // 2. 获取昨天的日期
            val yesterday = LocalDate.now(ZoneId.of("Asia/Shanghai")).minusDays(1)
            val dateStr = yesterday.format(DateTimeFormatter.ISO_LOCAL_DATE)

            // 检查是否已经生成过
            val existing = diaryRepository.getByDate(activeCharacterId, dateStr)
            if (existing != null) {
                Log.i(TAG, "日记已存在 date=$dateStr，跳过")
                return Result.success()
            }

            // 3. 获取昨天的聊天记录
            val startOfDay = yesterday.atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli()
            val endOfDay = yesterday.plusDays(1).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli()

            val messages = chatHistoryRepository.getByCharacterAndDateRange(
                activeCharacterId, startOfDay, endOfDay
            )

            if (messages.isEmpty()) {
                Log.i(TAG, "昨天没有对话，跳过日记生成")
                return Result.success()
            }

            // 4. 构建对话摘要文本
            val conversationText = buildString {
                for (msg in messages) {
                    val role = if (msg.sender == "user") "用户" else character.name
                    appendLine("$role: ${msg.content}")
                    appendLine()
                }
            }

            // 5. 调用 AI 生成日记
            val systemPrompt = buildSystemPrompt(character)
            val userPrompt = buildString {
                appendLine("以下是昨天（${dateStr}）我和用户的对话记录：")
                appendLine()
                appendLine("---")
                appendLine(conversationText)
                appendLine("---")
                appendLine()
                appendLine("请以第一人称写一篇关于昨天的日记。")
            }

            val response = aiService.chat(
                messages = listOf(
                    ChatMessage(role = "system", content = systemPrompt),
                    ChatMessage(role = "user", content = userPrompt)
                ),
                characterId = activeCharacterId
            )

            if (!response.success || response.content.isBlank()) {
                Log.e(TAG, "AI 生成日记失败: ${response.errorMessage}")
                return Result.retry()
            }

            // 6. 保存日记
            diaryRepository.save(
                DiaryEntryEntity(
                    characterId = activeCharacterId,
                    date = dateStr,
                    content = response.content.trim()
                )
            )

            // 7. 重新排程明天的日记
            schedule(applicationContext)

            Log.i(TAG, "日记生成成功 date=$dateStr, length=${response.content.length}")
            return Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "日记生成异常: ${e.message}", e)
            return if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private fun buildSystemPrompt(character: CharacterEntity): String = buildString {
        appendLine("你是${character.name}，${character.personality}")
        appendLine()
        appendLine("请以第一人称视角，写一篇关于昨天的私人日记。")
        appendLine()
        appendLine("要求：")
        appendLine("- 用「我」来称呼自己")
        appendLine("- 自然地记录你的感受、印象深刻的事、对这段关系的想法")
        appendLine("- 风格私密、自然，像真正的日记一样")
        appendLine("- 200-400字左右")
        appendLine("- 不要出现「用户说」、「对方说」等第三人称表达，把对话对象当成你熟悉的人来称呼")
        appendLine("- 只输出日记正文，不要加标题或日期")
        if (character.speakingStyle.isNotBlank()) {
            appendLine("- 保持你的说话风格：${character.speakingStyle}")
        }
    }
}
