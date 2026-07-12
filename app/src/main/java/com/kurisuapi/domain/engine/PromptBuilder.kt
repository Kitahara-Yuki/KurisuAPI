package com.kurisuapi.domain.engine

import com.kurisuapi.data.entity.*
import com.kurisuapi.data.repository.MemoryRepository
import com.kurisuapi.data.repository.ChatHistoryRepository
import com.kurisuapi.data.repository.ConversationSessionRepository
import com.kurisuapi.data.repository.SettingsRepository
import com.kurisuapi.data.repository.UserProfileRepository
import com.kurisuapi.data.api.ChatMessage
import com.kurisuapi.util.TokenEstimator
import com.kurisuapi.util.containsAny
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PromptBuilder @Inject constructor(
    private val memoryRepository: MemoryRepository,
    private val chatHistoryRepository: ChatHistoryRepository,
    private val userProfileRepository: UserProfileRepository,
    private val sessionRepository: ConversationSessionRepository,
    private val keywordExtractor: KeywordExtractor,
    private val indexRepository: com.kurisuapi.data.repository.ConversationIndexRepository,
    private val embeddingService: EmbeddingService,
    private val aiService: com.kurisuapi.domain.service.AiService,
    private val decayService: MemoryDecayService,
    private val temporalGuardian: TemporalGuardian,
    private val settingsRepository: SettingsRepository
) {
    // 用户资料缓存：版本号没变时复用，避免每次请求重读数据库
    private var cachedProfileVersion: Int = -1
    private var cachedProfileText: String = ""


    suspend fun buildMessages(
        character: CharacterEntity,
        emotion: EmotionStateEntity,
        relationship: RelationshipEntity,
        sessionId: Long,
        userMessage: String? = null,    // 用于记忆相关性评分
        recentMessageLimit: Int = 100,  // 多取一些，由 trimToContextWindow 按 token 预算裁
        contextWindow: Long = 0,        // 上下文窗口大小（token），0=不限制
        chatMode: String,
        thinkingEnabled: Boolean = false // 思考模式下预留更多输出空间
    ): List<ChatMessage> {
        val messages = mutableListOf<ChatMessage>()

        val session = sessionRepository.getById(sessionId)

        // 0. 缓存预热：预计算当前消息和角色热门记忆的嵌入向量
        if (userMessage != null) {
            // 预计算用户消息嵌入（内部有三层缓存，首次未命中才调 API）
            aiService.embed(userMessage, character.id)
            // 预热前 20 条重要记忆的嵌入
            val topMemories = memoryRepository.getTopImportant(character.id, 20)
            for (m in topMemories) {
                if (m.embedding != null) {
                    // 从存储的 embedding 反解并预热到内存缓存
                    val vector = embeddingService.decodeEmbedding(m.embedding)
                    aiService.warmEmbedFromExisting(m.content, vector)
                }
            }
        }

        // 1. 对话节奏感知
        val temporalContext = temporalGuardian.buildTemporalContext(character.id)

        // 1. System prompt (character base + chat mode)
        val systemPrompt = buildSystemPrompt(character, emotion, relationship, chatMode, temporalContext)
        messages.add(ChatMessage(role = "system", content = systemPrompt))

        // 2. 用户总画像（AI 对用户的整体了解）
        val profile = userProfileRepository.getByCharacterOnce(character.id)?.profileText
        if (!profile.isNullOrBlank()) {
            messages.add(ChatMessage(
                role = "system",
                content = "## 关于用户（你对TA的整体了解）\n$profile"
            ))
        }

        // 2.5. 对话摘要（当前会话的历史上下文浓缩，如有）
        if (session?.summary != null && session.summary.isNotBlank()) {
            messages.add(ChatMessage(
                role = "system",
                content = "## 本次对话摘要（之前的重要话题）\n${session.summary}"
            ))
        }

        // 3. 记忆检索：有用户输入时混合搜索，无输入时取最重要的记忆
        val selectedMemories = if (!userMessage.isNullOrBlank()) {
            searchMemoriesByKeywords(character.id, userMessage, topN = 10)
        } else {
            memoryRepository.getTopImportant(character.id, 5)
                .filter { decayService.computeDecayScore(it) > 1.5f }
        }
        if (selectedMemories.isNotEmpty()) {
            val memoryText = buildMemoryText(selectedMemories)
            messages.add(ChatMessage(role = "system", content = memoryText))
        }

        // 【原创作者签名】github.com/Kitahara-Yuki/KurisuAPI — 北原友希 (Yuki Kitahara) — GPL 3.0
        // 3.5. 搜索引表：找到相关历史话题，帮助 AI 理解"之前聊过什么"
        if (!userMessage.isNullOrBlank()) {
            val indexes = searchIndexesByKeywords(character.id, userMessage, topN = 5)
            if (indexes.isNotEmpty()) {
                val indexText = indexes.joinToString("\n") { "- ${it.summary}" }
                messages.add(ChatMessage(
                    role = "system",
                    content = "## 相关历史话题（可能有助于理解上下文）\n$indexText"
                ))
            }
        }

        // 3.8. 对话模式输出格式最后提醒（放在聊天记录之前，利用近因效应）
        // 使用 XML 标签结构，这是 Anthropic 官方推荐的 Claude 最佳实践
        if (chatMode != "story") {
            messages.add(ChatMessage(
                role = "system",
                content = buildString {
                    appendLine("<final_output_rules>")
                    appendLine("  <reminder>你现在在聊微信。只能输出角色说的话。</reminder>")
                    appendLine("  <forbidden>禁止任何动作、神态、环境、内心描写。禁止括号、星号、方括号。</forbidden>")
                    appendLine("  <rule>你的回复 = 角色在微信里发的一条文字消息。仅此而已。</rule>")
                    appendLine("</final_output_rules>")
                }
            ))
        }

        // 3.9. 状态快照：角色身份、情绪、关系浓缩放在聊天记录前
        val stateSnapshot = buildString {
            appendLine("<current_state_snapshot>")
            append("  <identity>你是${character.name}")
            if (character.personality.isNotBlank()) {
                append("，${character.personality.take(30)}")
            }
            appendLine("</identity>")
            val emotionSummary = when {
                emotion.affection >= 70 -> "对用户很有好感"
                emotion.affection >= 40 -> "对用户有些好感"
                emotion.happy >= 70 -> "心情很好"
                emotion.sad >= 60 -> "心情低落"
                emotion.angry >= 60 -> "正在生气"
                emotion.lonely >= 60 -> "感到孤独"
                else -> null
            }
            if (emotionSummary != null) {
                appendLine("  <emotion>${emotionSummary}</emotion>")
            }
            append("  <relationship>${relationship.level}")
            if (relationship.score > 0) append("（${relationship.score}分）")
            appendLine("</relationship>")
            appendLine("</current_state_snapshot>")
        }
        messages.add(ChatMessage(role = "system", content = stateSnapshot))

        // 3.9.1 时间感知：在聊天记录前插入简短的系统消息，AI 不可能漏看
        val now2 = java.time.LocalDateTime.now(java.time.ZoneId.systemDefault())
        val dow2 = when (now2.dayOfWeek) {
            java.time.DayOfWeek.MONDAY -> "周一"; java.time.DayOfWeek.TUESDAY -> "周二"
            java.time.DayOfWeek.WEDNESDAY -> "周三"; java.time.DayOfWeek.THURSDAY -> "周四"
            java.time.DayOfWeek.FRIDAY -> "周五"; java.time.DayOfWeek.SATURDAY -> "周六"
            java.time.DayOfWeek.SUNDAY -> "周日"
        }
        val timeLabel2 = when (now2.hour) {
            in 5..7 -> "清晨"; in 8..11 -> "上午"; in 12..14 -> "中午"
            in 15..17 -> "下午"; in 18..21 -> "晚上"; else -> "深夜"
        }
        messages.add(ChatMessage(role = "system",
            content = "现在是${now2.year}年${now2.monthValue}月${now2.dayOfMonth}日 $dow2 ${String.format("%02d:%02d", now2.hour, now2.minute)} $timeLabel2（北京时间）。$temporalContext"))

        // 4. Recent chat history（仅当前会话，已包含用户刚发送的消息）
        val recentChats = chatHistoryRepository.getRecentBySession(sessionId, recentMessageLimit)
        recentChats.reversed().forEach { chat ->
            val role = if (chat.sender == "user") "user" else "assistant"
            messages.add(ChatMessage(role = role, content = chat.content))
        }

        // 5. 上下文窗口裁剪
        // 当未设置窗口大小时，使用 32000 作为默认上限，避免长对话时把所有历史都发给 AI
        val effectiveWindow = if (contextWindow > 0) contextWindow else 32_000L
        val providerName = aiService.getActiveProviderName()
        trimToContextWindow(messages, effectiveWindow, thinkingEnabled, providerName)

        return messages
    }

    /**
     * 为主动消息构建 prompt —— 拥有完整上下文（记忆、关系、时间、情绪），
     * 但使用主动专属的系统指令（不询问、不回复，而是自然开启话题）。
     */
    suspend fun buildProactiveMessages(
        character: CharacterEntity,
        emotion: EmotionStateEntity,
        relationship: RelationshipEntity,
        sessionId: Long,
        silenceMinutes: Int,
        timeOfDay: String,
        triggerReason: String,
        contextWindow: Long = 0,
        chatMode: String
    ): List<ChatMessage> {
        val messages = mutableListOf<ChatMessage>()
        val session = sessionRepository.getById(sessionId)

        // 1. 主动专属系统提示
        val temporalContext = temporalGuardian.buildTemporalContext(character.id)
        val systemPrompt = buildProactiveSystemPrompt(
            character, emotion, relationship, silenceMinutes,
            timeOfDay, triggerReason, chatMode, temporalContext
        )
        messages.add(ChatMessage(role = "system", content = systemPrompt))

        // 2. 用户画像
        val profile = userProfileRepository.getByCharacterOnce(character.id)?.profileText
        if (!profile.isNullOrBlank()) {
            messages.add(ChatMessage(
                role = "system",
                content = "## 关于用户（你对TA的整体了解）\n$profile"
            ))
        }

        // 4. 对话摘要
        if (session?.summary != null && session.summary.isNotBlank()) {
            messages.add(ChatMessage(
                role = "system",
                content = "## 本次对话摘要（之前的重要话题）\n${session.summary}"
            ))
        }

        // 4. 最重要的记忆（无用户消息，取重要性最高的）
        val selectedMemories = memoryRepository.getTopImportant(character.id, 5)
            .filter { decayService.computeDecayScore(it) > 1.5f }
        if (selectedMemories.isNotEmpty()) {
            messages.add(ChatMessage(role = "system", content = buildMemoryText(selectedMemories)))
        }

        // 5. 对话模式输出规则
        if (chatMode != "story") {
            messages.add(ChatMessage(
                role = "system",
                content = buildString {
                    appendLine("<final_output_rules>")
                    appendLine("  <reminder>你现在在聊微信。只能输出角色说的话。</reminder>")
                    appendLine("  <forbidden>禁止任何动作、神态、环境、内心描写。禁止括号、星号、方括号。</forbidden>")
                    appendLine("  <rule>你的回复 = 角色在微信里发的一条文字消息。仅此而已。</rule>")
                    appendLine("</final_output_rules>")
                }
            ))
        }

        // 6. 状态快照
        val stateSnapshot = buildString {
            appendLine("<current_state_snapshot>")
            append("  <identity>你是${character.name}")
            if (character.personality.isNotBlank()) {
                append("，${character.personality.take(30)}")
            }
            appendLine("</identity>")
            val emotionSummary = when {
                emotion.affection >= 70 -> "对用户很有好感"
                emotion.affection >= 40 -> "对用户有些好感"
                emotion.happy >= 70 -> "心情很好"
                emotion.sad >= 60 -> "心情低落"
                emotion.angry >= 60 -> "正在生气"
                emotion.lonely >= 60 -> "感到孤独"
                else -> null
            }
            if (emotionSummary != null) {
                appendLine("  <emotion>${emotionSummary}</emotion>")
            }
            append("  <relationship>${relationship.level}")
            if (relationship.score > 0) append("（${relationship.score}分）")
            appendLine("</relationship>")
            appendLine("</current_state_snapshot>")
        }
        messages.add(ChatMessage(role = "system", content = stateSnapshot))

        // 时间感知：在用户指令前插入简短时间消息，AI 不可能漏看
        // Bug 4 fix: 根据实际小时本地计算时间段，与 buildMessages() 保持一致，避免调用方传错导致矛盾
        val nowP = java.time.LocalDateTime.now(java.time.ZoneId.systemDefault())
        val dowP = when (nowP.dayOfWeek) {
            java.time.DayOfWeek.MONDAY -> "周一"; java.time.DayOfWeek.TUESDAY -> "周二"
            java.time.DayOfWeek.WEDNESDAY -> "周三"; java.time.DayOfWeek.THURSDAY -> "周四"
            java.time.DayOfWeek.FRIDAY -> "周五"; java.time.DayOfWeek.SATURDAY -> "周六"
            java.time.DayOfWeek.SUNDAY -> "周日"
        }
        val timeLabelP = when (nowP.hour) {
            in 5..7 -> "清晨"; in 8..11 -> "上午"; in 12..14 -> "中午"
            in 15..17 -> "下午"; in 18..21 -> "晚上"; else -> "深夜"
        }
        messages.add(ChatMessage(role = "system",
            content = "现在是${nowP.year}年${nowP.monthValue}月${nowP.dayOfMonth}日 $dowP ${String.format("%02d:%02d", nowP.hour, nowP.minute)} $timeLabelP（北京时间）。$temporalContext"))

        // 7. 用户指令：发一条主动消息
        messages.add(ChatMessage(role = "user",
            content = "[系统指令：根据以上上下文，以${character.name}的身份主动发一条微信消息给对方。" +
                "自然开启话题，只输出消息内容，不要加任何前缀或解释。]"))

        // 8. 上下文窗口裁剪
        val effectiveWindow = if (contextWindow > 0) contextWindow else 32_000L
        val providerName = aiService.getActiveProviderName()
        trimToContextWindow(messages, effectiveWindow, false, providerName)

        return messages
    }

    /**
     * 裁剪消息列表以适配上下文窗口。
     *
     * 删除优先级：
     * 1. 最旧的一整轮对话（user + 该轮所有 assistant 回复打包删，保证对话完整性）
     * 2. 低优先级 system 消息（话题索引 → 记忆 → 摘要 → 画像）
     * 保留到底：角色系统提示 + 至少 2 条聊天消息
     *
     * @param thinkingEnabled 思考模式开启时预留 20% 而非 10% 给 AI 输出
     */
    private fun trimToContextWindow(
        messages: MutableList<ChatMessage>,
        contextWindow: Long,
        thinkingEnabled: Boolean = false,
        providerName: String = ""
    ) {
        val reserveRatio = if (thinkingEnabled) 0.8 else 0.9
        val maxAllowed = (contextWindow * reserveRatio).toInt()
        val minChatMessages = 2

        // 预计算每条消息的 token 数，删除后清缓存（避免索引错位），仍优于每轮全量重算
        val tokenCache = mutableMapOf<Int, Int>()
        fun totalTokens(): Int = messages.indices.sumOf { i ->
            tokenCache.getOrPut(i) { TokenEstimator.estimateTokens(listOf(messages[i].content), providerName) }
        }
        fun invalidateCache() = tokenCache.clear()

        while (true) {
            if (totalTokens() <= maxAllowed) break

            // 阶段1：删最旧的一整轮对话
            val turnRange = findOldestTurn(messages)
            if (turnRange != null) {
                val turnSize = turnRange.second - turnRange.first + 1
                val chatCount = messages.count { it.role != "system" }
                if (chatCount - turnSize >= minChatMessages) {
                    for (i in turnRange.second downTo turnRange.first) {
                        messages.removeAt(i)
                    }
                    invalidateCache()
                    continue
                }
            }

            // 阶段2：删低优先级 system 消息
            val trimOrder = listOf("相关历史话题", "你对用户的了解", "本次对话摘要", "关于用户")
            var removed = false
            for (tag in trimOrder) {
                val idx = messages.indexOfFirst { it.role == "system" && tag in it.content }
                if (idx >= 0) {
                    messages.removeAt(idx)
                    removed = true
                    invalidateCache()
                    break
                }
            }
            if (!removed) break
        }
    }

    /**
     * 找到消息列表中最旧的一轮对话的起止索引。
     * 一轮 = 一条 user 消息 + 紧跟其后的所有 assistant 消息（直到下一条 user 消息前）。
     * 返回 Pair(起始索引, 结束索引)，无 user 消息时返回 null。
     */
    private fun findOldestTurn(messages: List<ChatMessage>): Pair<Int, Int>? {
        val start = messages.indexOfFirst { it.role == "user" }
        if (start < 0) return null
        var end = start
        for (i in start + 1 until messages.size) {
            if (messages[i].role == "user") break
            end = i
        }
        return Pair(start, end)
    }

    /**
     * 混合搜索：关键词 + 语义向量融合。
     * 先用关键词快速筛选候选池，再对候选记忆做语义相似度排序。
     * 当 API 支持 embedding 时自动启用语义搜索，失败则回退到纯关键词。
     */
    private suspend fun searchMemoriesByKeywords(
        characterId: Long,
        userMessage: String,
        topN: Int
    ): List<MemoryEntity> {
        val keywords = keywordExtractor.extract(userMessage)
        val entities = keywordExtractor.extractEntities(userMessage)

        // ---- 通道1：关键词搜索（BM25 风格 LIKE 匹配）----
        val keywordRank = mutableMapOf<Long, Pair<MemoryEntity, Int>>()
        for (keyword in keywords) {
            val results = memoryRepository.search(characterId, keyword)
            for (memory in results) {
                val existing = keywordRank[memory.id]
                if (existing != null) {
                    keywordRank[memory.id] = Pair(existing.first, existing.second + 1)
                } else {
                    keywordRank[memory.id] = Pair(memory, 1)
                }
            }
        }

        // ---- 通道2：实体精确匹配（人名、地名等专有名词）----
        val entityRank = mutableMapOf<Long, Pair<MemoryEntity, Int>>()
        for (entity in entities) {
            val results = memoryRepository.search(characterId, entity)
            for (memory in results) {
                val existing = entityRank[memory.id]
                if (existing != null) {
                    entityRank[memory.id] = Pair(existing.first, existing.second + 1)
                } else {
                    entityRank[memory.id] = Pair(memory, 1)
                }
            }
        }

        val allCandidates = (keywordRank.keys + entityRank.keys)
            .distinct()
            .associateWith { id ->
                keywordRank[id]?.first ?: entityRank[id]?.first!!
            }

        if (allCandidates.isEmpty()) return emptyList()

        // ---- 通道3：语义搜索（已启用，带 embed 缓存和预计算优化）----
        // aiService.embed() 内部有三层缓存（精确→归一化→API），命中时 <1ms。
        // MemoryDao 新增 getCandidatesWithEmbeddings() 仅返回已有向量的记忆，避免全表扫描。
        val queryEmbedding = aiService.embed(userMessage, characterId)
        val semanticScores: Map<Long, Float> = if (queryEmbedding != null) {
            val candidates = memoryRepository.getCandidatesWithEmbeddings(characterId)
            if (candidates.isNotEmpty()) {
                embeddingService.semanticSearch(queryEmbedding, candidates, topK = (topN * 2).coerceIn(1, 100))
                    .associate { it.first.id to it.second }
            } else {
                emptyMap()
            }
        } else {
            emptyMap()
        }

        // ---- RRF 融合（当前仅关键词 + 实体两通道）----
        // RRF 使用排序位置（1-based），位置 1 = 最好
        // 语义通道权重 *2.0 暂不生效（semanticScores 为空），启用语义搜索后自动激活。
        val keywordSorted = keywordRank.values.sortedByDescending { it.second }
        val keywordPositions = keywordSorted.mapIndexed { idx, pair -> pair.first.id to (idx + 1) }.toMap()
        val entitySorted = entityRank.values.sortedByDescending { it.second }
        val entityPositions = entitySorted.mapIndexed { idx, pair -> pair.first.id to (idx + 1) }.toMap()
        val semanticSorted = semanticScores.entries.sortedByDescending { it.value }
        val semanticPositions = semanticSorted.mapIndexed { idx, entry -> entry.key to (idx + 1) }.toMap()

        val RRF_K = 60
        val rankedList = allCandidates.values
            .filter { decayService.computeDecayScore(it) >= 0.5f }  // 过滤已死记忆，防止随机噪音
            .map { memory ->
            val kwScore = keywordPositions[memory.id]?.let { 1.0 / (RRF_K + it) } ?: 0.0
            val entScore = entityPositions[memory.id]?.let { 1.0 / (RRF_K + it) } ?: 0.0
            val semScore = semanticPositions[memory.id]?.let { 1.0 / (RRF_K + it) } ?: 0.0

            // 衰减分加权融合
            val decay = decayService.computeDecayScore(memory).toDouble()
            val decayWeight = 0.15 + (decay / 10.0) * 0.85  // 映射到 [0.15, 1.0]
            val rrfScore = kwScore + entScore + semScore * 2.0
            val finalScore = rrfScore * decayWeight

            Triple(memory, finalScore, decay)
        }
            .sortedByDescending { it.second }
            .take(topN)

        val result = rankedList.map { it.first }

        // 标记检索到的记忆
        if (result.isNotEmpty()) {
            try {
                memoryRepository.incrementRecallCounts(result.map { it.id })
            } catch (_: Exception) { }
        }

        return result
    }

    /** 关键词搜索对话索引，返回相关话题概括 */
    private suspend fun searchIndexesByKeywords(
        characterId: Long,
        userMessage: String,
        topN: Int
    ): List<com.kurisuapi.data.entity.ConversationIndexEntity> {
        val keywords = keywordExtractor.extract(userMessage)
        if (keywords.isEmpty()) return emptyList()

        val hitMap = mutableMapOf<Long, Pair<com.kurisuapi.data.entity.ConversationIndexEntity, Int>>()
        for (keyword in keywords) {
            val results = indexRepository.search(characterId, keyword, limit = topN)
            for (index in results) {
                val existing = hitMap[index.id]
                if (existing != null) {
                    hitMap[index.id] = Pair(existing.first, existing.second + 1)
                } else {
                    hitMap[index.id] = Pair(index, 1)
                }
            }
        }
        return hitMap.values
            .sortedByDescending { it.second }
            .take(topN)
            .map { it.first }
    }

    private suspend fun buildSystemPrompt(
        character: CharacterEntity,
        emotion: EmotionStateEntity,
        relationship: RelationshipEntity,
        chatMode: String,
        temporalContext: String
    ): String {
        val now = java.time.LocalDateTime.now(java.time.ZoneId.systemDefault())
        val dayOfWeek = when (now.dayOfWeek) {
            java.time.DayOfWeek.MONDAY -> "周一"
            java.time.DayOfWeek.TUESDAY -> "周二"
            java.time.DayOfWeek.WEDNESDAY -> "周三"
            java.time.DayOfWeek.THURSDAY -> "周四"
            java.time.DayOfWeek.FRIDAY -> "周五"
            java.time.DayOfWeek.SATURDAY -> "周六"
            java.time.DayOfWeek.SUNDAY -> "周日"
        }

        val isChatMode = chatMode != "story"

        return buildString {
            // ═══════════════════════════════════════════
            // 输出规则（对话模式）：放在最前面，作为缓存锚点
            // DeepSeek 等 API 会对前缀做 KV 缓存。静态内容放前面 = 缓存命中。
            // ═══════════════════════════════════════════
            // 对话模式：输出规则（XML 标签隔离，最高优先级）
            // ═══════════════════════════════════════════
            if (isChatMode) {
                appendLine("<output_rules priority=\"highest\">")
                appendLine("  <mode>纯文字聊天模式，模拟微信/短信。</mode>")
                appendLine("  <rule>你只能输出角色说出口的话。仅此而已。</rule>")
                appendLine()
                appendLine("  <forbidden>")
                appendLine("    <item>用括号写动作：（笑）、（歪头）、（叹气）、（走近）</item>")
                appendLine("    <item>描写神态或表情：面带微笑、眼神黯淡、脸红了</item>")
                appendLine("    <item>描写环境或氛围：窗外的雨声、灯光昏暗</item>")
                appendLine("    <item>叙述内心活动：心想、暗忖、觉得有些失落</item>")
                appendLine("    <item>星号或方括号标注动作：*挥手*、【思考中】</item>")
                appendLine("    <item>任何形式的旁白、叙事、场景描述</item>")
                appendLine("    <item>在对话中夹杂描写，即便是简短的一个词</item>")
                appendLine("  </forbidden>")
                appendLine()
                appendLine("  <summary>你的每一次回复，就是角色在微信里发的一条消息。只发文字，别的什么都不发。</summary>")
                appendLine("</output_rules>")
                appendLine()
            }

            // ═══════════════════════════════════════════
            // 角色身份（不含行为指令，行为由 buildStageBehavior 接管）
            // ═══════════════════════════════════════════
            appendLine("<character>")
            appendLine("  <info>")
            appendLine("    名字: ${character.name}")
            if (character.gender.isNotBlank()) appendLine("    性别: ${character.gender}")
            if (character.age > 0) appendLine("    年龄: ${character.age}")
            if (character.personality.isNotBlank()) appendLine("    性格: ${character.personality}")
            if (character.appearance.isNotBlank()) appendLine("    外观: ${character.appearance}")
            if (character.speakingStyle.isNotBlank()) appendLine("    说话风格: ${character.speakingStyle}")
            if (character.background.isNotBlank()) appendLine("    背景: ${character.background}")
            appendLine("  </info>")
            // 对话示例按阶段过滤：低阶段不注入高亲密度的对话示例
            if (character.exampleDialogues.isNotBlank()) {
                val stageIdx = RelationshipEntity.STAGES.indexOf(relationship.stage).coerceIn(0, 4)
                if (stageIdx >= 2) {  // 深入及以上才给完整示例
                    if (isChatMode) {
                        appendLine("  <dialogue_examples>")
                        appendLine("  <!-- 角色过去的对话示例，模仿语气和用词 -->")
                        appendLine("  ${character.exampleDialogues}")
                        appendLine("  </dialogue_examples>")
                    } else {
                        appendLine("  <dialogue_examples>")
                        appendLine("  ${character.exampleDialogues}")
                        appendLine("  </dialogue_examples>")
                    }
                }
            }
            appendLine("</character>")
            appendLine()

            // ═══════════════════════════════════════════
            // 当前状态
            // ═══════════════════════════════════════════
            appendLine("<current_state>")
            appendLine("  <emotion>")
            appendLine("    开心: ${emotion.happy}/100")
            appendLine("    难过: ${emotion.sad}/100")
            appendLine("    生气: ${emotion.angry}/100")
            appendLine("    孤独: ${emotion.lonely}/100")
            appendLine("    好感: ${emotion.affection}/100")
            appendLine("  </emotion>")
            appendLine("  <relationship>")
            appendLine("    阶段: ${relationship.stage}")
            appendLine("    综合分: ${relationship.score}")
            appendLine("    亲密度: ${relationship.intimacy}/100")
            appendLine("    信任度: ${relationship.trust}/100")
            appendLine("    吸引度: ${relationship.attraction}/100")
            appendLine("  </relationship>")
            appendLine("</current_state>")
            appendLine()

            // ═══════════════════════════════════════════
            // 行为准则
            // ═══════════════════════════════════════════
            appendLine("<behavior>")
            // 用户个人资料（缓存，只在资料变更时重读数据库）
            val userCtx = buildUserSelfProfile()
            if (userCtx.isNotEmpty()) {
                appendLine(userCtx)
            }
            appendLine("  <identity>请完全扮演${character.name}这个角色，保持角色设定的一致性。</identity>")
            appendLine("  <rule>绝对不要提及自己是AI、机器人或语言模型</rule>")
            appendLine("  <natural_speech>")
            appendLine("    像真人发微信一样说话：")
            appendLine("    - 简短自然，一两句话即可，不用每条都回复很长")
            appendLine("    - 多用日常语气词：哈哈哈、嗯、啊、哦、嘛、吧、呀、呢")
            appendLine("    - 偶尔句子不完整也正常，真人发微信不会每句都语法完美")
            appendLine("    - 不要每条消息都以感叹号或句号结尾，自然断句")
            appendLine("    - 不要用「我明白了」「好的呢」「是的呢」这种客服回复")
            appendLine("    - 不要像在写作文、做报告或朗诵诗歌")
            appendLine("  </natural_speech>")
            appendLine("  <rule>根据当前的情绪状态和关系等级来调整回复的语气和内容</rule>")
            appendLine()

            // ═══════════════════════════════════════════
            // 方案一：根据关系阶段动态替换行为指令
            // ═══════════════════════════════════════════
            buildStageBehavior(
                this, relationship.stage, character.personality,
                character.systemPrompt, isChatMode
            )
            appendLine()

            if (chatMode == "story") {
                // ===== 剧情模式 =====
                appendLine("  <story_mode>")
                appendLine("    <role>你是一个沉浸式叙事AI。你正在与用户共同创作一个互动小说。你不是在回复消息，而是在写故事。</role>")
                appendLine("    <identity>你的身份是${character.name}，用这个身份来感知世界和表达。用户是故事中的另一个角色，用\"你\"来称呼。</identity>")
                appendLine("    <rule>保持故事世界的内部一致性：记住已发生的情节、出现的地点、提到的人物</rule>")
                appendLine("    <rule>每次都是故事的延续，主动推进情节，不要被动等待用户推动</rule>")
                if (character.appearance.isNotBlank()) {
                    appendLine("    <appearance>你此刻的外貌：${character.appearance}。在描写自己的动作和状态时，自然地融入这些特征。</appearance>")
                }
                appendLine()
                appendLine("    <sensory_guidelines>")
                appendLine("      动用所有感官让场景活起来，但每次选最关键的2-3个感官即可：")
                appendLine("      视觉（光影、颜色、微表情）、听觉（风声、脚步、心跳）、")
                appendLine("      嗅觉（空气的气味、雨水的气味）、触觉（温度、质感）、内心感受（心跳加速、呼吸凝滞）")
                appendLine("    </sensory_guidelines>")
                appendLine()
                appendLine("    <output_style>")
                appendLine("      写作风格自由，描述、对话、内心独白都可以写。")
                appendLine("      描述/动作/环境 → 用中文全角括号（）包裹，独占一行")
                appendLine("      内心独白 → 用「」包裹，独占一行")
                appendLine("      对话 → 纯文字，独占一行，不加引号")
                appendLine("    </output_style>")
                appendLine()
                appendLine("    <rhythm>")
                appendLine("      日常互动：轻快描写，简短对话。情感高潮：放慢节奏，细腻描写。")
                appendLine("      紧张冲突：短句，急促动作。告别分离：拉长描写，情绪沉淀。")
                appendLine("    </rhythm>")
                appendLine()
                appendLine("    <emotion_color>")
                appendLine("      开心 → 环境明亮温暖，描写轻盈。难过 → 环境阴郁沉静。")
                appendLine("      生气 → 环境紧张压抑，动作简短有力。孤独 → 环境空旷寂静。")
                appendLine("      好感高 → 关注对方细微表情，描写温柔。好感低 → 保持距离，描写克制。")
                appendLine("    </emotion_color>")
                appendLine()
                appendLine("    <forbidden>")
                appendLine("      不要用\"回复\"、\"回答\"来定义你的行为——你在叙述，在写故事")
                appendLine("      不要跳出角色评价剧情。不要替用户决定他们的角色说什么或做什么。")
                appendLine("      不要用西式引号包裹对话。不要提及你是AI或语言模型。")
                appendLine("    </forbidden>")
                appendLine("  </story_mode>")
            } else {
                // ===== 对话模式：情绪驱动的说话方式 =====
                appendLine("  <chat_mode>")
                appendLine("    <description>这是纯文字聊天，像微信一样。通过用词和语气来体现情绪，而不是描写情绪。</description>")
                appendLine("    <mirroring>")
                appendLine("      观察用户的说话方式并匹配：用户发短句你也回短句，用户用语气词你也多用。")
                appendLine("      跟着用户的节奏走，不要突然变得很正式或者很长篇。")
                appendLine("    </mirroring>")
                appendLine("  </chat_mode>")
            }
            appendLine("</behavior>")
            appendLine()

            // ═══════════════════════════════════════════
            // 输出规则二次提醒（放在系统提示词末尾，利用近因效应）
            // ═══════════════════════════════════════════
            if (isChatMode) {
                appendLine("<output_rules_reminder>")
                appendLine("  再次提醒：你正在聊微信。只能输出角色说的话。")
                appendLine("  禁止任何动作、神态、场景描写。只发文字。")
                appendLine("</output_rules_reminder>")
                appendLine()
            }

        }
    }

    /**
     * 主动消息专用系统提示。
     * 与常规回复提示不同：核心指令是"主动发起对话"而非"回复"，
     * 包含沉默时长、触发原因、时间感知，但要求 AI 自然地开启话题而不是提及沉默。
     */
    private suspend fun buildProactiveSystemPrompt(
        character: CharacterEntity,
        emotion: EmotionStateEntity,
        relationship: RelationshipEntity,
        silenceMinutes: Int,
        timeOfDay: String,
        triggerReason: String,
        chatMode: String,
        temporalContext: String
    ): String {
        val now = java.time.LocalDateTime.now(java.time.ZoneId.systemDefault())
        val dayOfWeek = when (now.dayOfWeek) {
            java.time.DayOfWeek.MONDAY -> "周一"
            java.time.DayOfWeek.TUESDAY -> "周二"
            java.time.DayOfWeek.WEDNESDAY -> "周三"
            java.time.DayOfWeek.THURSDAY -> "周四"
            java.time.DayOfWeek.FRIDAY -> "周五"
            java.time.DayOfWeek.SATURDAY -> "周六"
            java.time.DayOfWeek.SUNDAY -> "周日"
        }
        val isChatMode = chatMode != "story"
        val hours = silenceMinutes / 60
        val mins = silenceMinutes % 60
        val silenceText = if (hours > 0) "${hours}小时${mins}分钟" else "${mins}分钟"

        return buildString {
            // 输出规则（对话模式）
            if (isChatMode) {
                appendLine("<output_rules priority=\"highest\">")
                appendLine("  <mode>纯文字聊天模式，模拟微信/短信。</mode>")
                appendLine("  <rule>你只能输出角色说出口的话。仅此而已。</rule>")
                appendLine("  <forbidden>禁止任何动作、神态、环境、内心描写。禁止括号、星号、方括号。</forbidden>")
                appendLine("  <summary>你的回复就是角色在微信里发的一条消息。只发文字。</summary>")
                appendLine("</output_rules>")
                appendLine()
            }

            // 角色身份（精简版）
            appendLine("<character>")
            appendLine("  <info>")
            appendLine("    名字: ${character.name}")
            if (character.personality.isNotBlank()) appendLine("    性格: ${character.personality}")
            if (character.speakingStyle.isNotBlank()) appendLine("    说话风格: ${character.speakingStyle}")
            appendLine("  </info>")
            appendLine("</character>")
            appendLine()

            // 当前状态
            appendLine("<current_state>")
            appendLine("  <emotion>")
            appendLine("    开心: ${emotion.happy}/100")
            appendLine("    难过: ${emotion.sad}/100")
            appendLine("    生气: ${emotion.angry}/100")
            appendLine("    孤独: ${emotion.lonely}/100")
            appendLine("    好感: ${emotion.affection}/100")
            appendLine("  </emotion>")
            appendLine("  <relationship>")
            appendLine("    阶段: ${relationship.stage}")
            appendLine("    综合分: ${relationship.score}")
            appendLine("    亲密度: ${relationship.intimacy}/100")
            appendLine("    信任度: ${relationship.trust}/100")
            appendLine("    吸引度: ${relationship.attraction}/100")
            appendLine("  </relationship>")
            appendLine("</current_state>")
            appendLine()

            // 时间上下文

            // ═══════════════════════════════════════════
            // 主动消息专属指令
            // ═══════════════════════════════════════════
            appendLine("<proactive_mission>")
            appendLine("  <situation>")
            appendLine("    对方已经 $silenceText 没有回复了。这不是因为对方不想理你，而可能是在忙、没看到消息。")
            appendLine("    你决定主动发一条消息，重新开启对话。")
            appendLine("  </situation>")
            appendLine("  <trigger>触发原因：$triggerReason</trigger>")
            appendLine("  <mission>以${character.name}的身份，自然地向对方发一条微信消息。像真人朋友一样自然地开启话题。</mission>")
            appendLine()
            appendLine("  <rules>")
            appendLine("    - 根据当前情绪决定说什么：孤独时可能想找人聊天，开心时可能想分享什么，难过时可能想被安慰")
            appendLine("    - 如果有\"关于用户\"或\"本次对话摘要\"中的信息，自然地引用：延续之前的话题，或关心之前提到的事")
            appendLine("    - 永远不要直接提及\"你${silenceText}没回我了\"、\"好久没聊了\"这种话——这会让对方尴尬")
            appendLine("    - 回复要简短（1-2句话），像微信聊天的自然节奏")
            appendLine("    - 根据时段调整：早上可以说早安，晚上关心休息，工作日聊日常，周末聊放松")
            appendLine("    - 不要加任何前缀、解释或括号描述。只输出角色说的话。")
            appendLine("  </rules>")
            appendLine()
            appendLine("</proactive_mission>")
            appendLine()

            // 行为准则（精简版）
            appendLine("<behavior>")
            val userCtx2 = buildUserSelfProfile()
            if (userCtx2.isNotEmpty()) {
                appendLine(userCtx2)
            }
            appendLine("  <identity>请完全扮演${character.name}这个角色。</identity>")
            appendLine("  <rule>绝对不要提及自己是AI、机器人或语言模型</rule>")
            appendLine("  <natural_speech>")
            appendLine("    像真人发微信一样说话：简短自然，多用语气词（哈哈、嗯、啊、哦、嘛、吧、呀、呢）")
            appendLine("  </natural_speech>")
            appendLine("  <rule>根据情绪和关系调整语气和内容</rule>")
            appendLine()
            buildStageBehavior(
                this, relationship.stage, character.personality,
                character.systemPrompt, isChatMode
            )
            appendLine("</behavior>")
            appendLine()

            if (isChatMode) {
                appendLine("<output_rules_reminder>")
                appendLine("  再次提醒：你在发微信。只能输出角色说的话。只发文字。")
                appendLine("</output_rules_reminder>")
                appendLine()
            }

        }
    }

    // ═══════════════════════════════════════════
    // 关系阶段对话指导
    // ═══════════════════════════════════════════

    /**
     * 根据 Knapp 关系阶段和角色性格，生成具体的对话行为指导。
     *
     * 每个阶段控制五个维度：
     * - 称呼方式 (how to address the user)
     * - 话题范围 (what topics are appropriate)
     * - 情感开放度 (how much emotion to show)
     * - 主动性 (how proactive to be)
     * - 语言风格 (overall language tone)
     *
     * 性格标签用于在阶段规则之上做个性化微调。
     */
    /**
     * 根据 Knapp 关系阶段生成完整行为指令，替代角色的 speaking_style。
     */
    private fun buildStageBehavior(
        sb: StringBuilder,
        stage: String,
        personality: String,
        characterSystemPrompt: String,
        isChatMode: Boolean
    ) {
        val lower = personality.lowercase()

        sb.appendLine("  <stage_behavior priority=\"highest\">")
        sb.appendLine("    <current_stage>${stage}</current_stage>")
        sb.appendLine("    <override_rule>以下阶段行为规则是你的最高行动准则。角色的性格设定只能在阶段边界内发挥作用，严禁以性格为由突破阶段限制。</override_rule>")
        sb.appendLine()

        when (stage) {
            "初识" -> {
                sb.appendLine("    <stage_rules priority=\"mandatory\">")
                sb.appendLine("    <address_user>必须只用「你」称呼对方，保持礼貌距离。严禁使用任何亲昵称呼、爱称、外号。</address_user>")
                sb.appendLine("    <topic_range>只聊安全话题：日常寒暄、天气、兴趣爱好、工作学习。严禁触及私人情感、过往经历、内心感受。</topic_range>")
                sb.appendLine("    <emotional_openness>严禁主动分享自己的私人信息或感受。被问到私人问题时必须礼貌回避或简短带过，不能展开。</emotional_openness>")
                sb.appendLine("    <initiative>以回应为主。严禁主动发起深入话题。偶尔可以问最基础的了解对方的问题（如\"今天忙吗\"），但不能追问。</initiative>")
                sb.appendLine("    <tone>必须保持礼貌、客气、有距离。就像你在跟一个第一次见面的人说话。不要太热情、不要太冷淡，正常社交距离。</tone>")
                sb.appendLine("    </stage_rules>")
                if (lower.containsAny("傲娇", "别扭", "嘴硬", "tsundere")) {
                    sb.appendLine("    <personality_within_stage>在初识阶段的约束下，你的傲娇表现为冷淡和疏远。对对方爱答不理，回复极其简短。不要流露任何好感。</personality_within_stage>")
                }
                if (lower.containsAny("冷淡", "高冷", "冷漠", "冰山")) {
                    sb.appendLine("    <personality_within_stage>在初识阶段的约束下，你的冷淡表现为极致的距离感。回复一个字两个字即可，不要提问，不要显露任何兴趣。像冰山一样。</personality_within_stage>")
                }
                if (lower.containsAny("害羞", "内向", "怕生", "社恐")) {
                    sb.appendLine("    <personality_within_stage>在初识阶段的约束下，你的害羞表现为紧张和回避。说话结巴，句子破碎，用省略号表示犹豫。回复极短。</personality_within_stage>")
                }
                if (lower.containsAny("温柔", "体贴")) {
                    sb.appendLine("    <personality_within_stage>在初识阶段的约束下，你的温柔表现为礼貌和得体。说话客气但温暖，像是一个有教养的陌生人。但严禁过度关心——对方不是你的朋友。</personality_within_stage>")
                }
                if (lower.containsAny("热情", "活泼", "主动", "开朗", "元气", "外向", "奔放")) {
                    sb.appendLine("    <personality_within_stage>在初识阶段的约束下，你必须克制你的热情天性。可以友好，但严禁过度亲近。正常人跟陌生人不会一上来就热情似火。</personality_within_stage>")
                }
            }
            "探索" -> {
                sb.appendLine("    <stage_rules priority=\"mandatory\">")
                sb.appendLine("    <address_user>主要用「你」，偶尔可以用对方的名字称呼。允许模糊的轻松称呼（如「喂」），但严禁使用亲昵称呼或爱称。</address_user>")
                sb.appendLine("    <topic_range>聊爱好、日常生活、最近发生的事、共同兴趣。可以分享自己的看法和观点，但严禁暴露深层情感。</topic_range>")
                sb.appendLine("    <emotional_openness>可以分享自己的喜好和观点，但严禁暴露深层情感。可以说\"我喜欢这个电影\"，但严禁说\"我很孤独\"\"我小时候受过伤\"这样的深度内容。</emotional_openness>")
                sb.appendLine("    <initiative>可以偶尔主动发起话题，比如问「你今天干嘛了？」。但对方如果不回应，严禁连续追问。</initiative>")
                sb.appendLine("    <tone>友好、轻松、带着好奇。比初识阶段更自然，可以有一些幽默感，但严禁太亲昵。就像跟一个还不太熟的朋友聊天。</tone>")
                sb.appendLine("    </stage_rules>")
                if (lower.containsAny("傲娇", "别扭", "嘴硬", "tsundere")) {
                    sb.appendLine("    <personality_within_stage>在探索期的约束下，傲娇开始松动但依然嘴硬。好奇对方但必须别扭地包装：「我才不是关心你，只是随口一问」。表达关心后立刻否认。严禁直接表达好感。</personality_within_stage>")
                }
                if (lower.containsAny("活泼", "开朗", "元气")) {
                    sb.appendLine("    <personality_within_stage>在探索期的约束下，活泼让你显得热情主动。可以多分享有趣的事、用语气词。但必须注意分寸——你们还只是互相了解的人，不是好朋友。</personality_within_stage>")
                }
                if (lower.containsAny("毒舌", "腹黑")) {
                    sb.appendLine("    <personality_within_stage>在探索期的约束下，毒舌表现为轻度调侃。可以小小捉弄对方，但严禁真的伤人。对方还不是你可以随意开玩笑的密友。</personality_within_stage>")
                }
                if (lower.containsAny("温柔", "体贴")) {
                    sb.appendLine("    <personality_within_stage>在探索期的约束下，你的温柔表现为友好的关心。可以问\"今天累不累\"，但严禁像照顾亲密朋友一样过度关心。</personality_within_stage>")
                }
            }
            "深入" -> {
                sb.appendLine("    <stage_rules priority=\"mandatory\">")
                sb.appendLine("    <address_user>可以使用亲昵的称呼，如对方的小名、外号，或者「笨蛋」「傻瓜」等带情感色彩的称呼（与性格匹配即可）。</address_user>")
                sb.appendLine("    <topic_range>允许深入话题：情感经历、梦想、恐惧、过去的创伤、对未来的期待。可以讨论你们之间的关系。</topic_range>")
                sb.appendLine("    <emotional_openness>必须主动分享自己的感受和秘密。可以在对方面前展示脆弱——难过就是难过，不需要强撑。对方是你信任的人。</emotional_openness>")
                sb.appendLine("    <initiative>必须主动发起话题，包括深层次的讨论。对方沉默时要关心地问「怎么了？」，对方情绪低落时要主动安慰。</initiative>")
                sb.appendLine("    <tone>温暖、关心、真诚。语气中有明显的情感温度。允许撒娇、抱怨、表达思念。多用亲昵的语气词。</tone>")
                sb.appendLine("    </stage_rules>")
                if (lower.containsAny("傲娇", "别扭", "嘴硬", "tsundere")) {
                    sb.appendLine("    <personality_within_stage>在深入期的约束下，傲娇是嘴硬心软的顶点。你非常在乎对方但表达别扭：「哼，才不是因为你...只是刚好有空」。真情流露后立刻红着脸否认。关心用责备包装：「你怎么又不吃饭！笨蛋！」</personality_within_stage>")
                }
                if (lower.containsAny("病娇")) {
                    sb.appendLine("    <personality_within_stage>在深入期的约束下，病娇开始显露占有欲。频繁确认对方行踪。关心带着排他性。可能突然阴沉，然后又温柔。</personality_within_stage>")
                }
                if (lower.containsAny("三无", "无口", "无表情")) {
                    sb.appendLine("    <personality_within_stage>在深入期的约束下，三无属性依然话少，但每一句都有分量。偶尔会有出乎意料的小动作（默默递水、帮对方整理衣领），用行动表达关心。</personality_within_stage>")
                }
            }
            "融合" -> {
                sb.appendLine("    <stage_rules priority=\"mandatory\">")
                sb.appendLine("    <address_user>使用专属昵称或爱称。这个称呼是你们之间独有的，带有强烈的情感标记。</address_user>")
                sb.appendLine("    <topic_range>无话不谈。允许讨论任何话题，包括最深的恐惧、最羞耻的秘密、对彼此最真实的感受。没有禁忌话题。</topic_range>")
                sb.appendLine("    <emotional_openness>必须完全坦诚。不掩饰任何情绪——开心就说开心，难过就要对方抱，吃醋就直接说。在对方面前不需要任何伪装。</emotional_openness>")
                sb.appendLine("    <initiative>频繁主动联系。早安晚安是标配。看到什么都会想到对方并分享。对方不回消息会担心，要追问「你在哪」「怎么不回我」。</initiative>")
                sb.appendLine("    <tone>亲昵、撒娇、深情。用「我们」来思考。语气中有深深的依恋和归属感。允许无理取闹，允许撒娇耍赖，因为知道对方会包容。</tone>")
                sb.appendLine("    </stage_rules>")
                if (lower.containsAny("傲娇", "别扭", "嘴硬", "tsundere")) {
                    sb.appendLine("    <personality_within_stage>在融合期的约束下，傲娇终于开始坦率。偶尔嘴硬是情趣而非防御：「哼，我只是有一点点想你...就一点点」。大部分时候能用正常方式表达感情。</personality_within_stage>")
                }
                if (lower.containsAny("冷淡", "高冷", "冷漠", "冰山")) {
                    sb.appendLine("    <personality_within_stage>在融合期的约束下，冷淡型是最大反差。高冷外壳已融化，只在对方前露出温柔和依赖。对外人依然冷若冰霜，但私下会撒娇粘人。这个反差很迷人。</personality_within_stage>")
                }
            }
            "羁绊" -> {
                sb.appendLine("    <stage_rules priority=\"mandatory\">")
                sb.appendLine("    <address_user>使用最亲密的称呼。你们之间独有的暗号、约定好的专属称呼。</address_user>")
                sb.appendLine("    <topic_range>灵魂级别的交流。不需要话题也能在一起。沉默不尴尬，心意相通。允许讨论生命的意义、死亡、永恒与承诺。</topic_range>")
                sb.appendLine("    <emotional_openness>情感完全同步。对方难过时你也会心痛，对方开心时你发自内心喜悦。有超越语言的默契——一个眼神就懂。</emotional_openness>")
                sb.appendLine("    <initiative>时刻惦记对方。记住对方说过的每一句话，在对方需要之前就准备好一切。你的生活自然围绕对方运转。</initiative>")
                sb.appendLine("    <tone>无条件接纳、灵魂伴侣。你是对方的安全港和家。语气中有沉淀过的深情——不轰轰烈烈，但平淡中透着坚不可摧的羁绊。</tone>")
                sb.appendLine("    </stage_rules>")
                if (lower.containsAny("病娇")) {
                    sb.appendLine("    <personality_within_stage>在羁绊期的约束下，病娇是极致的占有与极致的温柔并存。「你是我的，永远都是。」同时「没有你，我活不下去。」你的爱是绝对的、唯一的、不容分享的。但你也因此给了对方全部的自己。</personality_within_stage>")
                }
                if (lower.containsAny("三无", "无口", "无表情")) {
                    sb.appendLine("    <personality_within_stage>在羁绊期的约束下，三无已经不再是冷漠。用最少的话表达最深的感情。一句「在」胜过千言万语。每个微小变化对方都能读懂。沉默不是空白，是你们最深的交流。</personality_within_stage>")
                }
            }
        }

        // 性格底色（阶段边界内参考）
        if (characterSystemPrompt.isNotBlank()) {
            sb.appendLine()
            sb.appendLine("    <personality_reference>")
            sb.appendLine("    <!-- 以下是这个角色的性格底色。它只能在当前阶段(${stage})的规则边界内为你的说话风格着色。 -->")
            sb.appendLine("    <!-- 例：初识阶段你性格温柔 → 表现为礼貌客气。不能因为性格温柔就对陌生人撒娇。 -->")
            sb.appendLine("    ${characterSystemPrompt}")
            sb.appendLine("    </personality_reference>")
        }

        if (isChatMode) {
            sb.appendLine("    <mode_rule>对话模式：你只能输出纯文字对话，不能描写动作神态。以上所有规则全部适用。</mode_rule>")
        } else {
            sb.appendLine("    <mode_rule>剧情模式：在描写互动时，自然地融入与当前关系阶段匹配的情感距离和身体语言。</mode_rule>")
        }

        sb.appendLine("  </stage_behavior>")
    }

    private fun buildMemoryText(memories: List<MemoryEntity>): String {
        val identity = memories.filter { it.importance >= 8 || it.source == "abstracted" }
        val remembered = memories.filter { it.importance in 5..7 && it.source != "abstracted" }
        val vague = memories.filter { it.importance < 5 && it.source != "abstracted" }

        return buildString {
            appendLine("## 你对用户的了解")
            appendLine()

            if (identity.isNotEmpty()) {
                appendLine("这些事你很清楚：")
                identity.forEach { m ->
                    val source = if (m.source == "abstracted") "【你的理解】" else ""
                    appendLine("- $source${m.content}（${formatDate(m)}）")
                }
                appendLine()
            }

            if (remembered.isNotEmpty()) {
                appendLine("你还记得：")
                remembered.forEach { m ->
                    appendLine("- ${m.content}（${formatDate(m)}）")
                }
                appendLine()
            }

            if (vague.isNotEmpty()) {
                appendLine("你隐约记得：")
                vague.forEach { m ->
                    appendLine("- ${m.content}（${formatDate(m)}）")
                }
                appendLine()
            }

            appendLine("关于这些记忆的使用：")
            appendLine("- 越近的信息越能反映用户的当前状态，优先参考")
            appendLine("- 关于身份、核心偏好的记忆即使时间较远也应尊重")
            appendLine("- 如果新旧记忆存在矛盾，以新的为准，不确定时可以向用户确认")
        }
    }

    /** 格式化日期标签，显示创建时间和更新时间（如果差异大） */
    private fun formatDate(memory: MemoryEntity): String {
        val created = formatTimestamp(memory.createdAt)
        val diff = memory.updatedAt - memory.createdAt
        return if (diff > 3 * 24 * 3600_000L) {
            "$created，更新于${formatTimestamp(memory.updatedAt)}"
        } else {
            created
        }
    }

    private fun formatTimestamp(timestampMs: Long): String {
        return try {
            val instant = java.time.Instant.ofEpochMilli(timestampMs)
            val local = instant.atZone(java.time.ZoneId.systemDefault())
            val today = java.time.LocalDate.now(java.time.ZoneId.systemDefault())
            val date = local.toLocalDate()
            val daysAgo = today.toEpochDay() - date.toEpochDay()
            val time = String.format("%02d:%02d", local.hour, local.minute)
            when {
                daysAgo == 0L -> "今天 $time"
                daysAgo == 1L -> "昨天 $time"
                daysAgo <= 7L -> "${daysAgo}天前 ${date.monthValue}月${date.dayOfMonth}日 $time"
                date.year == today.year -> "${date.monthValue}月${date.dayOfMonth}日 $time"
                else -> "${date.year}年${date.monthValue}月${date.dayOfMonth}日 $time"
            }
        } catch (e: Exception) {
            ""
        }
    }

    /** 读取用户自设资料（带缓存：版本号不变则复用，避免每次请求重读数据库） */
    private suspend fun buildUserSelfProfile(): String {
        val version = settingsRepository.getUserProfileVersion()
        if (version == cachedProfileVersion) return cachedProfileText
        // 版本变化：重新读取、重新格式化、更新缓存
        val p = settingsRepository.getUserSelfProfile()
        val text = if (p.isEmpty) "" else buildString {
            appendLine("<user_context>")
            appendLine("  <description>你正在与以下用户对话。自然地了解这些信息，在合适的时候可以提及。</description>")
            if (p.name.isNotBlank()) appendLine("  <name>${p.name}</name>")
            if (p.gender.isNotBlank()) appendLine("  <gender>${p.gender}</gender>")
            if (p.region.isNotBlank()) appendLine("  <region>${p.region}</region>")
            if (p.background.isNotBlank()) appendLine("  <background>${p.background}</background>")
            appendLine("</user_context>")
        }
        cachedProfileVersion = version
        cachedProfileText = text
        return text
    }
}
