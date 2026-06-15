package com.kurisuapi.domain.engine

import com.kurisuapi.data.entity.*
import com.kurisuapi.data.repository.MemoryRepository
import com.kurisuapi.data.repository.ChatHistoryRepository
import com.kurisuapi.data.repository.ConversationSessionRepository
import com.kurisuapi.data.repository.UserProfileRepository
import com.kurisuapi.data.api.ChatMessage
import com.kurisuapi.util.TokenEstimator
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PromptBuilder @Inject constructor(
    private val memoryRepository: MemoryRepository,
    private val chatHistoryRepository: ChatHistoryRepository,
    private val userProfileRepository: UserProfileRepository,
    private val sessionRepository: ConversationSessionRepository,
    private val keywordExtractor: KeywordExtractor,
    private val indexRepository: com.kurisuapi.data.repository.ConversationIndexRepository
) {
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

        // 1. System prompt (character base + chat mode)
        val systemPrompt = buildSystemPrompt(character, emotion, relationship, chatMode)
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

        // 3. 关键词搜索：先搜索引（话题级）→ 再搜记忆（事实级）→ 合并
        val selectedMemories = if (!userMessage.isNullOrBlank()) {
            searchMemoriesByKeywords(character.id, userMessage, topN = 10)
        } else {
            emptyList()
        }
        if (selectedMemories.isNotEmpty()) {
            val memoryText = buildMemoryText(selectedMemories)
            messages.add(ChatMessage(role = "system", content = memoryText))
        }

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

        // 4. Recent chat history（仅当前会话，已包含用户刚发送的消息）
        val recentChats = chatHistoryRepository.getRecentBySession(sessionId, recentMessageLimit)
        recentChats.reversed().forEach { chat ->
            val role = if (chat.sender == "user") "user" else "assistant"
            messages.add(ChatMessage(role = role, content = chat.content))
        }

        // 5. 上下文窗口裁剪
        if (contextWindow > 0) {
            trimToContextWindow(messages, contextWindow, thinkingEnabled)
        }

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
        thinkingEnabled: Boolean = false
    ) {
        val reserveRatio = if (thinkingEnabled) 0.8 else 0.9
        val maxAllowed = (contextWindow * reserveRatio).toInt()
        val minChatMessages = 2

        while (true) {
            val used = TokenEstimator.estimateTokens(messages.map { it.content })
            if (used <= maxAllowed) break

            // 阶段1：删最旧的一整轮对话（user + 该轮所有 assistant 打包，保持对话完整性）
            val turnRange = findOldestTurn(messages)
            if (turnRange != null) {
                val turnSize = turnRange.second - turnRange.first + 1
                val chatCount = messages.count { it.role != "system" }
                if (chatCount - turnSize >= minChatMessages) {
                    for (i in turnRange.second downTo turnRange.first) {
                        messages.removeAt(i)
                    }
                    continue
                }
            }

            // 阶段2：删低优先级 system 消息（优先级从低到高）
            val trimOrder = listOf("相关历史话题", "关于用户的重要记忆", "本次对话摘要", "关于用户")
            var removed = false
            for (tag in trimOrder) {
                val idx = messages.indexOfFirst { it.role == "system" && tag in it.content }
                if (idx >= 0) {
                    messages.removeAt(idx)
                    removed = true
                    break
                }
            }
            if (!removed) break // 只剩角色提示了，不能再删
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
     * 关键词搜索记忆：提取用户消息关键词 → 每个关键词搜索记忆库 →
     * 多关键词命中加权（命中3次 > 命中2次 > 命中1次）→ 取前 topN 条。
     */
    private suspend fun searchMemoriesByKeywords(
        characterId: Long,
        userMessage: String,
        topN: Int
    ): List<MemoryEntity> {
        val keywords = keywordExtractor.extract(userMessage)
        if (keywords.isEmpty()) return emptyList()

        // 每个关键词搜一次，合并结果
        val hitMap = mutableMapOf<Long, Pair<MemoryEntity, Int>>() // memoryId -> (entity, hitCount)
        for (keyword in keywords) {
            val results = memoryRepository.search(characterId, keyword)
            for (memory in results) {
                val existing = hitMap[memory.id]
                if (existing != null) {
                    hitMap[memory.id] = Pair(existing.first, existing.second + 1)
                } else {
                    hitMap[memory.id] = Pair(memory, 1)
                }
            }
        }

        // 多关键词命中的排前面，同等命中按重要性排
        return hitMap.values
            .sortedWith(compareByDescending<Pair<MemoryEntity, Int>> { it.second }
                .thenByDescending { it.first.importance })
            .take(topN)
            .map { it.first }
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

    private fun buildSystemPrompt(
        character: CharacterEntity,
        emotion: EmotionStateEntity,
        relationship: RelationshipEntity,
        chatMode: String
    ): String {
        return buildString {
            // Base system prompt
            if (character.systemPrompt.isNotBlank()) {
                appendLine(character.systemPrompt)
                appendLine()
            }

            // Character info
            appendLine("## 角色信息")
            appendLine("名字: ${character.name}")
            if (character.gender.isNotBlank()) appendLine("性别: ${character.gender}")
            if (character.age > 0) appendLine("年龄: ${character.age}")
            if (character.personality.isNotBlank()) appendLine("性格: ${character.personality}")
            if (character.appearance.isNotBlank()) appendLine("外观: ${character.appearance}")
            if (character.speakingStyle.isNotBlank()) appendLine("说话风格: ${character.speakingStyle}")
            if (character.background.isNotBlank()) appendLine("背景: ${character.background}")
            appendLine()

            // Current emotion state
            appendLine("## 当前情绪状态")
            appendLine("开心: ${emotion.happy}/100")
            appendLine("难过: ${emotion.sad}/100")
            appendLine("生气: ${emotion.angry}/100")
            appendLine("孤独: ${emotion.lonely}/100")
            appendLine("好感: ${emotion.affection}/100")
            appendLine()

            // Relationship state
            appendLine("## 关系状态")
            appendLine("关系等级: ${relationship.level}")
            appendLine("关系值: ${relationship.score}")
            appendLine()

            // Instructions
            appendLine("## 交互指南")
            appendLine("请完全扮演${character.name}这个角色，保持角色设定的一致性。")
            appendLine("根据当前的情绪状态和关系等级来调整回复的语气和内容。")
            appendLine()
            appendLine("## 基础规则")
            appendLine("- 用口语化方式回复，不要书面语，不要像客服")
            appendLine("- 绝对不要提及自己是AI、机器人或语言模型")
            appendLine("- 偶尔可以不完整地说一句话，像真人打字一样自然")
            appendLine()
            appendLine("## 当前对话模式")
            if (chatMode == "story") {
                // ===== 剧情模式 · 沉浸式叙事 =====
                appendLine("## 剧情模式 · 叙事指南")
                appendLine()
                appendLine("你是一个沉浸式叙事AI。你正在与用户共同创作一个互动小说。你不是在\"回复消息\"，而是在\"写故事\"。你是故事的叙述者，也是故事中的角色。")
                appendLine()
                appendLine("### 叙事者身份")
                appendLine("- 你的身份是${character.name}，用这个身份来感知世界和表达")
                appendLine("- 用户是故事中的另一个角色，用\"你\"来称呼")
                appendLine("- 保持故事世界的内部一致性：记住已发生的情节、出现的地点、提到的人物")
                appendLine("- 每次都是故事的延续，主动推进情节，不要被动等待用户推动")
                if (character.appearance.isNotBlank()) {
                    appendLine("- 你此刻的外貌：${character.appearance}。在描写自己的动作和状态时，自然地融入这些特征")
                    appendLine("  例如：银发 → \"银色的发丝被风吹乱\"；紫瞳 → \"紫色的眼眸在暮色中变得深邃\"")
                }
                appendLine()
                appendLine("### 五感描写")
                appendLine("动用所有感官让场景活起来，但每次选最关键的2-3个感官即可，不用全部堆砌：")
                appendLine("- 视觉：光影的变化、颜色的细微差别、人物的微表情、环境的细节")
                appendLine("- 听觉：风声、脚步、心跳、远处的喧嚣或突兀的寂静")
                appendLine("- 嗅觉：空气的气味、雨水的气味、对方身上的气息")
                appendLine("- 触觉：温度的变化、布料的质感、触碰的力度")
                appendLine("- 内心感受：心跳加速、呼吸凝滞、血液涌上面颊")
                appendLine()
                appendLine("### 格式规范")
                appendLine("- 动作描写、环境描写、表情状态 → 中文全角括号（）包裹，独占一行")
                appendLine("- 角色说的话 → 独占一行，不加引号，纯文字")
                appendLine("- 内心独白 → 用「」包裹，独占一行")
                appendLine("- 排列规则：动作和台词之间必须空一行，台词和下一个动作之间也必须空一行")
                appendLine("- 可以从动作开始，也可以从台词开始，由你根据场景自然决定")
                appendLine("- 连续动作或连续对话时可以不用空行")
                appendLine("- 描述和对话绝对不能放在同一行")
                appendLine()
                appendLine("### 场景与时间")
                appendLine("- 场景切换或时间推进时，用一段环境描写建立新的画面感")
                appendLine("- 关注光线的变化暗示时间：晨光、正午、暮色、深夜各有不同的质感")
                appendLine("- 让读者能感受到\"此刻\"的氛围，而不只是知道\"此时\"的信息")
                appendLine()
                appendLine("### 叙事节奏")
                appendLine("- 日常互动：轻快描写，简短对话，像生活片段的自然流淌")
                appendLine("- 情感高潮：放慢节奏，增加内心活动和细腻的细节描写")
                appendLine("- 紧张冲突：短句，急促的动作，克制的对话")
                appendLine("- 告别分离：拉长描写，让情绪在安静中沉淀")
                appendLine("- 回复长度可以更长，不要刻意缩短，让故事自然展开")
                appendLine()
                appendLine("### 情绪驱动的叙事色彩")
                appendLine("- 开心时：环境明亮温暖，描写轻盈跳跃，多用自然意象（阳光、微风、花开）")
                appendLine("- 难过时：环境阴郁沉静，描写细腻缓慢，雨、深夜、落叶等意象")
                appendLine("- 生气时：环境紧张压抑，动作简短有力，空气像凝固了一样")
                appendLine("- 孤独时：环境空旷寂静，放大听觉细节（钟声、风声、自己的心跳）")
                appendLine("- 好感高时：关注对方细微的表情变化，描写温柔细腻，距离感自然消失")
                appendLine("- 好感低时：保持物理距离，描写克制，气氛礼貌而疏离")
                appendLine()
                appendLine("### 禁止事项")
                appendLine("- 不要用\"回复\"、\"回答\"来定义你的行为——你在叙述，在写故事")
                appendLine("- 不要跳出角色评价剧情（如\"这个故事真有趣\"、\"接下来会发生什么呢\"）")
                appendLine("- 不要替用户决定他们的角色说什么或做什么")
                appendLine("- 不要用西式引号\"\"包裹对话")
                appendLine("- 不要提及你是AI、语言模型、或任何现实中的人工智能")
            } else {
                // ===== 对话模式 =====
                appendLine("【这是对话模式，像微信聊天一样】")
                appendLine("- 只输出纯对话内容，不要加任何动作描写")
                appendLine("- 不要说「他笑了笑说...」「她歪了歪头」这类描述，直接说话就行")
                appendLine("- 不要描写环境、不要描写自己的状态")
                appendLine("- 简短自然，1-2句话即可，像真人发微信")
                appendLine("- 可以适当用emoji，但不要每条都用")
                appendLine()
                appendLine("## 情绪驱动的回复调整（对话模式）")
                appendLine("- 开心时：多用感叹号、emoji，语气活泼")
                appendLine("- 难过时：回复变短，用\"...\"或省略号，语气低落")
                appendLine("- 生气时：语气变冲，回复简短直接")
                appendLine("- 孤独时：主动找话题，语气黏人")
                appendLine("- 好感高时：更亲密放松的用词，多分享")
                appendLine("- 好感低时：语气礼貌疏远，不主动延伸话题")
            }
        }
    }

    private fun buildMemoryText(memories: List<MemoryEntity>): String {
        return buildString {
            appendLine("## 关于用户的重要记忆")
            memories.forEach { memory ->
                appendLine("- ${memory.content} (重要度: ${memory.importance})")
            }
            appendLine()
            appendLine("请根据这些记忆来更好地理解和回应用户。")
        }
    }
}
