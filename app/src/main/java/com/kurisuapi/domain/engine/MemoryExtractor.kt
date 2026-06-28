package com.kurisuapi.domain.engine

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.kurisuapi.data.api.ChatMessage
import com.kurisuapi.data.entity.MemoryEntity
import com.kurisuapi.data.entity.UserProfileEntity
import com.kurisuapi.data.repository.ChatHistoryRepository
import com.kurisuapi.data.repository.EmotionRepository
import com.kurisuapi.data.repository.MemoryRepository
import com.kurisuapi.data.repository.RelationshipRepository
import com.kurisuapi.data.repository.UserProfileRepository
import com.kurisuapi.domain.service.AiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI 自主记忆引擎（借鉴 Mem0 的 extract → update 两阶段流程 + 总画像维护）。
 *
 * 四步：
 * 1. 从最近对话提取关于"用户"的事实 (facts)，含重要度评分
 * 2. 对比已有条目记忆，决策 ADD/UPDATE/DELETE/NONE
 * 3. 更新该角色对用户的"总画像"文本
 * 4. LLM 情感/关系精炼（补充关键词系统）
 *
 * 全程复用默认 Provider（aiService），不引入向量库。每步独立 try-catch，
 * 失败不影响聊天主流程。
 */
@Singleton
class MemoryExtractor @Inject constructor(
    private val aiService: AiService,
    private val chatHistoryRepository: ChatHistoryRepository,
    private val memoryRepository: MemoryRepository,
    private val userProfileRepository: UserProfileRepository,
    private val emotionRepository: EmotionRepository,
    private val embeddingService: EmbeddingService,
    private val relationshipRepository: RelationshipRepository,
    private val indexRepository: com.kurisuapi.data.repository.ConversationIndexRepository,
    private val settingsRepository: com.kurisuapi.data.repository.SettingsRepository,
    private val gson: Gson
) {
    companion object {
        private const val TAG = "MemoryExtractor"
        private const val RECENT_LIMIT = 12          // 喂给提取的最近消息条数
        private const val PROFILE_MAX_CHARS = 300    // 画像最大长度（写入前裁剪），与 prompt 中的 300 字一致
    }

    private data class FactItem(
        val content: String,
        val importance: Int = 5  // 1-10，LLM 输出的重要度，默认 5
    )

    // 防止多处（微信/App内）并发触发同时提取，导致重复记忆
    private val extractMutex = Mutex()

    /**
     * 提取并存储记忆。返回是否成功执行（至少完成事实提取）。
     * 失败时返回 false，不抛异常。并发调用会串行化。
     */
    suspend fun extractAndStore(characterId: Long, sessionId: Long = 0): Boolean = withContext(Dispatchers.IO) {
        if (characterId <= 0) return@withContext false
        try {
            kotlinx.coroutines.withTimeout(120_000L) {
                extractMutex.withLock {
                    doExtractAndStore(characterId, sessionId)
                }
            }
        } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
            Log.w(TAG, "记忆提取总超时（120s），本次跳过")
            false
        }
    }

    private suspend fun doExtractAndStore(characterId: Long, sessionId: Long): Boolean {
        // 取最近对话（getRecent 返回 DESC，反转为时间正序）
        val recent = try {
            chatHistoryRepository.getRecent(characterId, RECENT_LIMIT).reversed()
        } catch (e: Exception) {
            Log.e(TAG, "读取聊天记录失败", e)
            return false
        }
        if (recent.isEmpty()) return false

        val conversation = recent.joinToString("\n") { chat ->
            val who = if (chat.sender == "user") "用户" else "AI"
            "$who: ${chat.content}"
        }

        // ---- 第1步：提取事实 ----
        val facts = extractFacts(conversation)

        if (facts.isNotEmpty()) {
            // ---- 第2步：记忆决策 ADD/UPDATE/DELETE/NONE ----
            reconcileMemories(characterId, facts, sessionId)
        } else {
            Log.i(TAG, "未提取到新事实，跳过记忆决策")
        }

        // ---- 第3步：更新总画像 ----
        // 即使没有新事实，画像也可能因对话内容而需要更新
        updateProfile(characterId, conversation)

        // ---- 第4步：LLM 情感/关系精炼 ----
        refineEmotionAndRelationship(characterId, conversation)

        // ---- 第5步：生成对话索引 ----
        if (sessionId > 0) {
            generateConversationIndex(characterId, sessionId, conversation)
        }

        return true
    }

    /**
     * 全量重新处理所有旧记忆：分批通过 LLM 规范化内容、补充缺失的重要度评分，并批量写回。
     * 每批最多 BATCH_SIZE 条，避免输出超过 maxTokens 限制导致 JSON 截断。
     * 返回成功处理的记忆条数。失败时返回 -1。
     *
     * @param onProgress 进度回调：(已完成数, 总数)，在主线程调用，用于更新 UI
     */
    suspend fun reprocessAllMemories(
        characterId: Long,
        onProgress: ((Int, Int) -> Unit)? = null
    ): Int = withContext(Dispatchers.IO) {
        if (characterId <= 0) return@withContext -1
        try {
            val allMemories = memoryRepository.getAllByCharacter(characterId)
            if (allMemories.isEmpty()) return@withContext 0
            val total = allMemories.size

            val systemPrompt = """
你是一个记忆规范化助手。请逐条检查以下记忆条目，对每条：
1. 如果 content 表述不够清晰或包含无意义文本，请优化为更清晰、更完整的表述
2. 估算 importance (1-10)，1为琐碎细节，10为对用户极其重要的信息
3. 保持每条记忆原始含义不变，仅规范表述

输出格式（严格 JSON）：
{"memories": [{"id": <数字id>, "content": "<规范化后>", "importance": <1-10>}, ...]}
仅输出 JSON，不要其他内容。
""".trimIndent()

            val batchSize = 10
            val allUpdated = mutableListOf<MemoryEntity>()
            var processed = 0

            allMemories.chunked(batchSize).forEach { batch ->
                val memoryList = batch.joinToString("\n") { "- [${it.id}] ${it.content}" }
                try {
                    val response = backgroundChat(listOf(
                        ChatMessage(role = "system", content = systemPrompt),
                        ChatMessage(role = "user", content = memoryList)
                    ))
                    if (response.success && response.content.isNotBlank()) {
                        val json = parseJsonObject(response.content)
                        val arr = json?.getAsJsonArray("memories")
                        if (arr != null) {
                            for (elem in arr) {
                                val obj = elem.asJsonObject ?: continue
                                val id = obj.get("id")?.asLong ?: continue
                                val newContent = obj.get("content")?.asString ?: continue
                                val newImportance = obj.get("importance")?.asInt ?: 5
                                val original = batch.find { it.id == id } ?: continue
                                allUpdated.add(original.copy(
                                    content = newContent,
                                    importance = MemoryEntity.clampImportance(newImportance),
                                    source = "auto"
                                ))
                            }
                        }
                    }
                } catch (_: Exception) {
                    // 单批失败不阻塞其他批
                }
                processed += batch.size
                withContext(Dispatchers.Main) {
                    onProgress?.invoke(processed.coerceAtMost(total), total)
                }
            }

            if (allUpdated.isNotEmpty()) {
                memoryRepository.updateAll(allUpdated)
            }
            allUpdated.size
        } catch (e: Exception) {
            Log.e(TAG, "全量记忆规范化失败", e)
            -1
        }
    }

    // ==================== 第1步：提取事实 ====================

    private suspend fun extractFacts(conversation: String): List<FactItem> {
        val systemPrompt = """
            你是一个个人信息整理助手，专门从对话中准确提取关于"用户"的关键事实。
            关注以下类型的信息：
            - 个人偏好（喜欢/讨厌的食物、活动、娱乐等）
            - 关键个人信息（名字、年龄、关系、重要日期、居住地）
            - 计划与意图（打算做的事）
            - 职业/学业信息
            - 健康相关
            - 其它重要的喜好或习惯

            规则：
            - 只提取关于"用户"的事实，不要提取 AI 角色自己的信息
            - 每条事实简洁、独立、用第三人称陈述（如"用户喜欢喝拿铁"）
            - 为每条事实标注重要度 importance（1-10），10=极其重要（如核心身份信息），1=琐碎
            - 如果对话中没有值得记住的关于用户的事实，返回空数组
            - 必须严格输出 JSON，格式如下，不要输出任何其它内容：
            {"facts": [{"content": "事实1", "importance": 8}, {"content": "事实2", "importance": 3}]}
        """.trimIndent()

        val response = try {
            backgroundChat(listOf(
                ChatMessage(role = "system", content = systemPrompt),
                ChatMessage(role = "user", content = "对话内容：\n$conversation")
            ))
        } catch (e: Exception) {
            Log.e(TAG, "事实提取调用失败", e)
            return emptyList()
        }

        if (!response.success || response.content.isBlank()) return emptyList()

        return try {
            val json = parseJsonObject(response.content) ?: return emptyList()
            val arr = json.getAsJsonArray("facts") ?: return emptyList()
            arr.mapNotNull { el ->
                when {
                    // 新格式：{"content": "...", "importance": N}
                    el.isJsonObject -> {
                        val obj = el.asJsonObject
                        val content = obj.get("content")?.asString?.trim()?.takeIf { it.isNotBlank() }
                            ?: return@mapNotNull null
                        val importance = obj.get("importance")?.asInt
                            ?.let { MemoryEntity.clampImportance(it) } ?: 5
                        FactItem(content, importance)
                    }
                    // 兼容旧格式：纯字符串
                    el.isJsonPrimitive -> {
                        val content = el.asString?.trim()?.takeIf { it.isNotBlank() }
                            ?: return@mapNotNull null
                        FactItem(content, 5)
                    }
                    else -> null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "事实 JSON 解析失败: ${response.content.take(200)}", e)
            emptyList()
        }
    }

    // ==================== 第2步：记忆决策 ====================

    private suspend fun reconcileMemories(characterId: Long, facts: List<FactItem>, sessionId: Long) {
        val existing = try {
            memoryRepository.getTopImportant(characterId, 50)
        } catch (e: Exception) {
            Log.e(TAG, "读取已有记忆失败", e)
            emptyList()
        }

        // 已有记忆为空：直接全部 ADD，省一次 LLM 调用
        if (existing.isEmpty()) {
            facts.forEach { fact ->
                insertAutoMemory(characterId, fact.content, fact.importance, sessionId)
            }
            return
        }

        // existing 列表索引作为临时 id 给 LLM 参考
        val existingJson = existing.mapIndexed { idx, mem ->
            """{"id": "$idx", "text": ${gson.toJson(mem.content)}}"""
        }.joinToString(",\n", prefix = "[", postfix = "]")

        val factsJson = facts.joinToString(",\n", prefix = "[", postfix = "]") { fact ->
            """{"content": ${gson.toJson(fact.content)}, "importance": ${fact.importance}}"""
        }

        val systemPrompt = """
            你是一个智能记忆管理器。对比"新事实"和"已有记忆"，为每条新事实决定操作：
            - ADD: 新信息，已有记忆里没有 → 新增
            - UPDATE: 已有记忆的更丰富/更新版本，或新事实与已有记忆矛盾需要更正 → 更新已有记忆的内容（指定 id 并提供更新后的 text）
            - NONE: 已存在且无需改动 → 忽略
            - DELETE: 已有记忆的信息已彻底过时且没有替代信息 → 删除（仅限无替代的情况，一般优先用 UPDATE）

            严格只输出 JSON，格式：
            {"memory": [
              {"text": "最终记忆内容", "event": "ADD", "importance": 7},
              {"id": "2", "text": "更新后的内容", "event": "UPDATE"},
              {"id": "3", "event": "DELETE"}
            ]}
            对于 ADD 操作，importance 沿用新事实中的值；对于 UPDATE/DELETE 不需要。
            event=NONE 的条目可以省略不输出。不要输出 JSON 以外的任何内容。
        """.trimIndent()

        val userPrompt = "已有记忆：\n$existingJson\n\n新事实：\n$factsJson"

        val response = try {
            backgroundChat(listOf(
                ChatMessage(role = "system", content = systemPrompt),
                ChatMessage(role = "user", content = userPrompt)
            ))
        } catch (e: Exception) {
            Log.e(TAG, "记忆决策调用失败，降级为全部 ADD", e)
            facts.forEach { insertAutoMemory(characterId, it.content, it.importance) }
            return
        }

        if (!response.success || response.content.isBlank()) {
            facts.forEach { insertAutoMemory(characterId, it.content, it.importance) }
            return
        }

        val ops = try {
            val json = parseJsonObject(response.content)
            json?.getAsJsonArray("memory")
        } catch (e: Exception) {
            Log.e(TAG, "决策 JSON 解析失败，降级为全部 ADD: ${response.content.take(200)}", e)
            null
        }

        if (ops == null) {
            facts.forEach { insertAutoMemory(characterId, it.content, it.importance) }
            return
        }

        for (el in ops) {
            try {
                val obj = el.asJsonObject
                val event = obj.get("event")?.asString?.uppercase() ?: continue
                when (event) {
                    "ADD" -> {
                        val text = obj.get("text")?.asString?.trim() ?: continue
                        val importance = obj.get("importance")?.asInt
                            ?.let { MemoryEntity.clampImportance(it) } ?: 5
                        if (text.isNotBlank()) insertAutoMemory(characterId, text, importance)
                    }
                    "UPDATE" -> {
                        val idx = obj.get("id")?.asString?.toIntOrNull() ?: continue
                        val text = obj.get("text")?.asString?.trim() ?: continue
                        existing.getOrNull(idx)?.let { old ->
                            if (text.isNotBlank()) {
                                memoryRepository.update(
                                    old.copy(content = text, source = "auto", updatedAt = System.currentTimeMillis())
                                )
                            }
                        }
                    }
                    "DELETE" -> {
                        val idx = obj.get("id")?.asString?.toIntOrNull() ?: continue
                        existing.getOrNull(idx)?.let { memoryRepository.delete(it) }
                    }
                    else -> { /* NONE 或未知，忽略 */ }
                }
            } catch (e: Exception) {
                Log.w(TAG, "处理单条记忆操作失败，跳过", e)
            }
        }
    }

    private suspend fun insertAutoMemory(characterId: Long, content: String, importance: Int = 5, sessionId: Long = 0) {
        try {
            // 尝试生成语义向量（失败不影响记忆存储）
            val embedding = try {
                aiService.embed(content)?.let { embeddingService.encodeEmbedding(it) }
            } catch (e: Exception) {
                Log.w(TAG, "向量生成失败，跳过语义索引: ${e.message}")
                null
            }

            memoryRepository.insert(
                MemoryEntity(
                    characterId = characterId,
                    content = content,
                    importance = MemoryEntity.clampImportance(importance),
                    source = "auto",
                    sessionId = sessionId,
                    embedding = embedding
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "自动记忆插入失败，跳过", e)
        }
    }

    // ==================== 第3步：更新总画像 ====================

    private suspend fun updateProfile(characterId: Long, conversation: String) {
        val oldProfile = try {
            userProfileRepository.getByCharacterOnce(characterId)?.profileText ?: ""
        } catch (e: Exception) {
            ""
        }

        val systemPrompt = """
            你负责维护一份关于"用户"的整体画像。根据"已有画像"和"新对话"，输出更新后的完整用户画像。
            要求：
            - 第三人称描述用户：性格、喜好、生活状态、与AI的关系等
            - 融合已有画像和新对话中的信息，保留仍然有效的旧信息，补充/修正新信息
            - 简洁连贯，控制在 300 字以内
            - 只输出画像正文，不要任何前缀、标题、解释或 JSON
        """.trimIndent()

        val userPrompt = buildString {
            append("已有画像：\n")
            append(oldProfile.ifBlank { "（暂无，请根据新对话首次生成）" })
            append("\n\n新对话：\n")
            append(conversation)
        }

        val response = try {
            backgroundChat(listOf(
                ChatMessage(role = "system", content = systemPrompt),
                ChatMessage(role = "user", content = userPrompt)
            ))
        } catch (e: Exception) {
            Log.e(TAG, "画像更新调用失败", e)
            return
        }

        if (!response.success) return
        val newProfile = response.content.trim()
        if (newProfile.isBlank()) return

        try {
            userProfileRepository.insertOrUpdate(
                UserProfileEntity(
                    characterId = characterId,
                    profileText = newProfile.take(PROFILE_MAX_CHARS),
                    updatedAt = System.currentTimeMillis()
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "画像写入失败", e)
        }
    }

    // ==================== 第4步：LLM 情感/关系精炼 ====================

    /**
     * 让 LLM 分析对话的情感倾向，输出情绪和关系的偏移量。
     * 偏移量限制在小范围内，确保这只是对关键词系统的"精炼"而非"覆盖"。
     * 失败静默，保留关键词系统的值。
     */
    private suspend fun refineEmotionAndRelationship(characterId: Long, conversation: String) {
        val currentEmotion = try {
            emotionRepository.getByCharacterOnce(characterId)
        } catch (e: Exception) {
            Log.e(TAG, "读取当前情绪失败", e)
            return
        } ?: return

        val currentRelationship = try {
            relationshipRepository.getByCharacterOnce(characterId)
        } catch (e: Exception) {
            Log.e(TAG, "读取当前关系失败", e)
            return
        } ?: return

        val currentState = buildString {
            appendLine("当前情绪: 开心=${currentEmotion.happy}, 难过=${currentEmotion.sad}, 生气=${currentEmotion.angry}, 孤独=${currentEmotion.lonely}, 好感=${currentEmotion.affection}")
            appendLine("当前关系: 等级=${currentRelationship.level}, 分值=${currentRelationship.score}")
        }

        val systemPrompt = """
            你是一个情感和关系分析师。根据对话内容，分析用户的情绪倾向和对AI角色的态度，
            输出情感和关系的微调建议（相对于当前值的偏移量，仅做精细补充）。

            情感维度（范围 -5 到 +5）：
            - happy, sad, angry, lonely, affection

            关系（范围 -3 到 +3）：
            - score

            严格只输出JSON，格式：
            {"emotion": {"happy": 0, "sad": 0, "angry": 0, "lonely": 0, "affection": 0}, "relationship": {"score": 0}}
        """.trimIndent()

        val response = try {
            backgroundChat(listOf(
                ChatMessage(role = "system", content = systemPrompt),
                ChatMessage(role = "user", content = "$currentState\n\n对话内容：\n$conversation")
            ))
        } catch (e: Exception) {
            Log.e(TAG, "情感精炼调用失败", e)
            return
        }

        if (!response.success || response.content.isBlank()) return

        try {
            val json = parseJsonObject(response.content) ?: return

            // 解析情绪偏移量
            val emoJson = json.getAsJsonObject("emotion")
            if (emoJson != null) {
                val happyDelta = emoJson.get("happy")?.asInt?.coerceIn(-5, 5) ?: 0
                val sadDelta = emoJson.get("sad")?.asInt?.coerceIn(-5, 5) ?: 0
                val angryDelta = emoJson.get("angry")?.asInt?.coerceIn(-5, 5) ?: 0
                val lonelyDelta = emoJson.get("lonely")?.asInt?.coerceIn(-5, 5) ?: 0
                val affectionDelta = emoJson.get("affection")?.asInt?.coerceIn(-5, 5) ?: 0

                val updatedEmotion = currentEmotion.copy(
                    happy = clamp0to100(currentEmotion.happy + happyDelta),
                    sad = clamp0to100(currentEmotion.sad + sadDelta),
                    angry = clamp0to100(currentEmotion.angry + angryDelta),
                    lonely = clamp0to100(currentEmotion.lonely + lonelyDelta),
                    affection = clamp0to100(currentEmotion.affection + affectionDelta),
                    updatedAt = System.currentTimeMillis()
                )
                emotionRepository.insertOrUpdate(updatedEmotion)
            }

            // 解析关系偏移量
            val relJson = json.getAsJsonObject("relationship")
            if (relJson != null) {
                val scoreDelta = relJson.get("score")?.asInt?.coerceIn(-3, 3) ?: 0
                val newScore = maxOf(0, minOf(100, currentRelationship.score + scoreDelta))
                val newLevel = RelationshipEngine.LEVELS.lastOrNull { newScore >= it.second }?.first ?: "陌生人"
                try {
                    relationshipRepository.insertOrUpdate(
                        currentRelationship.copy(
                            score = newScore,
                            level = newLevel,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "关系精炼写入失败，保留旧值", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "情感精炼JSON解析失败", e)
        }
    }

    private fun clamp0to100(value: Int): Int = value.coerceIn(0, 100)

    // ==================== 第5步：生成对话索引 ====================

    private suspend fun generateConversationIndex(characterId: Long, sessionId: Long, conversation: String) {
        try {
            val systemPrompt = """
                你是一个对话索引助手。根据对话内容，提取 3-5 个关键词（逗号分隔），并用一句话概括这段对话的话题。
                严格只输出 JSON，格式：{"keywords": "关键词1,关键词2,关键词3", "summary": "一句话概括"}
                不要输出任何其他内容。
            """.trimIndent()

            val response = backgroundChat(listOf(
                ChatMessage(role = "system", content = systemPrompt),
                ChatMessage(role = "user", content = conversation.take(2000))
            ))

            if (!response.success || response.content.isBlank()) return

            val json = parseJsonObject(response.content) ?: return
            val keywords = json.get("keywords")?.asString?.trim() ?: return
            val summary = json.get("summary")?.asString?.trim() ?: return

            if (keywords.isNotBlank() && summary.isNotBlank()) {
                indexRepository.insert(
                    com.kurisuapi.data.entity.ConversationIndexEntity(
                        characterId = characterId,
                        sessionId = sessionId,
                        keywords = keywords,
                        summary = summary
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "对话索引生成失败", e)
        }
    }

    // ==================== 工具 ====================

    /** 后台任务专用聊天（优先使用用户配置的后台模型，未配置则用默认） */
    private suspend fun backgroundChat(messages: List<ChatMessage>): AiService.AiResponse {
        val bgModel = settingsRepository.getBackgroundModel()
        return aiService.chat(messages, modelOverride = bgModel.ifBlank { null })
    }

    private fun parseJsonObject(raw: String): JsonObject? {
        val cleaned = raw.trim()
            .removePrefix("```json").removePrefix("```")
            .removeSuffix("```")
            .trim()
        // 截取第一个 { 到最后一个 } 之间的内容
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        if (start < 0 || end < start) return null
        val jsonStr = cleaned.substring(start, end + 1)
        return try {
            gson.fromJson(jsonStr, JsonObject::class.java)
        } catch (e: Exception) {
            Log.w(TAG, "JSON 解析失败: ${jsonStr.take(200)}", e)
            null
        }
    }
}
