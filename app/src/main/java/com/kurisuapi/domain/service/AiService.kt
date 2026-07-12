package com.kurisuapi.domain.service

import android.util.Log
import com.kurisuapi.data.api.ChatMessage
import kotlinx.coroutines.CancellationException

// 【原创作者签名】github.com/Kitahara-Yuki/KurisuAPI — 北原友希 (Yuki Kitahara) — GPL 3.0
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import com.kurisuapi.data.repository.CacheStatsRepository
import com.kurisuapi.data.repository.ModelRepository
import com.kurisuapi.data.repository.ProviderRepository
import com.kurisuapi.data.repository.SettingsRepository
import com.kurisuapi.domain.provider.*
import com.kurisuapi.domain.provider.StreamToken
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiService @Inject constructor(
    private val providerRepository: ProviderRepository,
    private val modelRepository: ModelRepository,
    private val settingsRepository: SettingsRepository,
    private val providerFactory: ProviderFactory,
    private val cacheStatsRepository: CacheStatsRepository
) {
    data class AiResponse(
        val content: String,
        val reasoningContent: String = "",
        val promptTokens: Int = 0,
        val completionTokens: Int = 0,
        val success: Boolean = true,
        val errorMessage: String? = null
    )

    // === 聊天响应缓存（L1 精确 + L2 归一化 + L3 语义，按角色隔离） ===
    private data class ChatCacheKey(
        val characterId: Long,
        val contextHash: Int,
        val messageText: String
    )
    private data class ChatCacheEntry(
        val response: AiResponse,
        val messageEmbedding: FloatArray?,   // 用户消息的嵌入向量（L3 语义匹配用）
        val contextEmbedding: FloatArray?,   // 上下文嵌入向量（L3 上下文感知，取最近消息的均值向量）
        val hitCount: Int = 1,               // 累计命中次数（用于自适应阈值）
        val createdAt: Long = System.currentTimeMillis()
    ) {
        /** 是否已过期（默认 30 分钟 TTL，高命中条目自动延长） */
        fun isExpired(now: Long = System.currentTimeMillis()): Boolean {
            val baseTtl = 30 * 60 * 1000L  // 30 分钟
            val extendedTtl = baseTtl + (hitCount * 5 * 60 * 1000L).coerceAtMost(60 * 60 * 1000L)  // 每次命中 +5min，上限 +60min
            return now - createdAt > extendedTtl
        }
    }
    private val chatResponseCache = object : LinkedHashMap<ChatCacheKey, ChatCacheEntry>(100, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<ChatCacheKey, ChatCacheEntry>?): Boolean {
            // 双重淘汰：超过上限 OR 条目过期
            if (size > 100) return true
            return eldest?.value?.isExpired() == true
        }
    }
    private val chatCacheLock = Any()
    private val statsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    var chatCacheHits = 0   // L1+L2 命中
        private set
    var chatCacheL3Hits = 0 // L3 语义命中
        private set
    var chatCacheMisses = 0 // 全部未命中
        private set

    /** 清理所有过期缓存条目 */
    fun evictExpiredChatCache() {
        synchronized(chatCacheLock) {
            chatResponseCache.entries.removeAll { it.value.isExpired() }
        }
    }

    /**
     * 缓存预热：为指定角色预加载高频嵌入向量到内存。
     * 应在角色切换或 App 启动时异步调用，不阻塞 UI。
     * @param texts 需要预热的文本列表（如角色的高频记忆内容）
     */
    suspend fun warmupEmbedCache(texts: List<String>) {
        for (text in texts) {
            embed(text)
        }
    }

    /** 将已有的嵌入向量直接注入缓存（零开销，从 Room 加载时使用） */
    fun warmEmbedFromExisting(text: String, embedding: FloatArray) {
        val normalized = normalize(text)
        synchronized(embedCacheLock) {
            embedCache[text] = embedding
            if (normalized != text.lowercase(Locale.ROOT).trim()) {
                embedCache[normalized] = embedding
            }
        }
    }

    // === 每角色缓存统计 ===
    private val perCharacterStats = mutableMapOf<Long, CharacterCacheStats>()
    data class CharacterCacheStats(
        var embedHits: Int = 0,
        var embedMisses: Int = 0,
        var chatHits: Int = 0,
        var chatMisses: Int = 0
    )
    fun getCharacterStats(characterId: Long): CharacterCacheStats {
        return perCharacterStats.getOrPut(characterId) { CharacterCacheStats() }
    }

    // === 数据库持久化：增量追踪 ===
    // 每次计数器变动时累加到这里，flushStatsToDatabase() 时一次性写入 DB 并清零
    private val pendingEmbedHits = AtomicInteger(0)
    private val pendingEmbedMisses = AtomicInteger(0)
    private val pendingChatL1L2Hits = AtomicInteger(0)
    private val pendingChatL3Hits = AtomicInteger(0)
    private val pendingChatMisses = AtomicInteger(0)

    /** 将累积的统计增量写入数据库（异步、不阻塞、失败不影响主流程） */
    fun flushStatsToDatabase() {
        val eh = pendingEmbedHits.getAndSet(0)
        val em = pendingEmbedMisses.getAndSet(0)
        val cl1 = pendingChatL1L2Hits.getAndSet(0)
        val cl3 = pendingChatL3Hits.getAndSet(0)
        val cm = pendingChatMisses.getAndSet(0)
        if (eh == 0 && em == 0 && cl1 == 0 && cl3 == 0 && cm == 0) return
        statsScope.launch {
            try {
                cacheStatsRepository.addToday(
                    embedHits = eh, embedMisses = em,
                    chatL1L2Hits = cl1, chatL3Hits = cl3, chatMisses = cm
                )
            } catch (_: Exception) {
                // 静默失败：统计落库失败不影响聊天功能
                Log.w("AiService", "缓存统计写入数据库失败，跳过")
            }
        }
    }

    companion object {
        // === vCache 风格自适应阈值 ===
        // 每条缓存条目独立计算相似度门槛：
        //   命中 1 次（未经验证）→ 阈值 0.95（严格）
        //   命中 3 次（初步可靠）→ 阈值 0.934
        //   命中 5 次（已验证）   → 阈值 0.91
        //   命中 8+ 次（高度可靠）→ 阈值 0.88（宽松）
        // 公式：threshold = 0.95 - (hitCount * 0.008)，下限 0.88

        /** 自适应阈值：基于命中次数的动态相似度门槛 */
        private fun adaptiveThreshold(hitCount: Int): Float {
            return (0.95f - (hitCount * 0.008f)).coerceAtLeast(0.88f)
        }

        /** 缓存条目最低命中次数，低于此次数的条目不参与 L3 匹配 */
        private const val L3_MIN_HITS_FOR_MATCH = 2
    }

    /** 两个向量的余弦相似度（辅助 L3 语义匹配，与 EmbeddingService 逻辑一致） */
    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        var dot = 0f; var normA = 0f; var normB = 0f
        val len = minOf(a.size, b.size)
        for (i in 0 until len) { dot += a[i] * b[i]; normA += a[i] * a[i]; normB += b[i] * b[i] }
        val denom = kotlin.math.sqrt(normA * normB)
        return if (denom > 0f) dot / denom else 0f
    }

    /** 从消息列表提取上下文指纹（system prompt + 最近 3 条对话消息的哈希） */
    private fun contextHash(messages: List<ChatMessage>): Int {
        val systemFingerprint = messages.firstOrNull { it.role == "system" }?.content?.take(200) ?: ""
        val recentMessages = messages
            .filter { it.role != "system" }
            .takeLast(3)
            .joinToString("|") { "${it.role}:${it.content.takeLast(100)}" }
        return (systemFingerprint + recentMessages).hashCode()
    }

    /** 从最近的非 system 消息构建近似上下文嵌入向量（取已有嵌入的均值） */
    private fun buildContextEmbedding(messages: List<ChatMessage>): FloatArray? {
        val recentTexts = messages
            .filter { it.role != "system" }
            .takeLast(4)
            .map { it.content }
        if (recentTexts.isEmpty()) return null

        val embeddings = mutableListOf<FloatArray>()
        for (text in recentTexts) {
            synchronized(embedCacheLock) { embedCache[text]?.let { embeddings.add(it) } }
        }
        if (embeddings.isEmpty()) return null

        // 多个嵌入取均值
        val dim = embeddings.first().size
        val avg = FloatArray(dim)
        for (emb in embeddings) {
            for (i in 0 until dim) avg[i] += emb[i]
        }
        for (i in 0 until dim) avg[i] /= embeddings.size.toFloat()
        return avg
    }

    // Bug fix: removed redundant withContext(Dispatchers.IO) — all provider calls
    // already use withContext(Dispatchers.IO) internally, and suspend functions can be
    // called directly without an extra context switch.
    suspend fun chat(messages: List<ChatMessage>, modelOverride: String? = null, characterId: Long = 0): AiResponse {
        // 提取最后一条用户消息用于缓存匹配
        val lastUserMessage = messages.lastOrNull { it.role == "user" }?.content ?: ""
        if (lastUserMessage.isNotBlank()) {
            val ch = contextHash(messages)

            // L1：精确匹配
            val exactKey = ChatCacheKey(characterId, ch, lastUserMessage)
            synchronized(chatCacheLock) {
                chatResponseCache[exactKey]?.let { entry ->
                    if (entry.isExpired()) {
                        chatResponseCache.remove(exactKey)
                    } else {
                        chatCacheHits++
                        pendingChatL1L2Hits.incrementAndGet()
                        chatResponseCache[exactKey] = entry.copy(hitCount = entry.hitCount + 1)
                        perCharacterStats.getOrPut(characterId) { CharacterCacheStats() }.chatHits++
                        flushStatsToDatabase()
                        return entry.response
                    }
                }
            }

            // L2：归一化匹配
            val normalized = normalize(lastUserMessage)
            if (normalized != lastUserMessage.lowercase(Locale.ROOT).trim()) {
                val normKey = ChatCacheKey(characterId, ch, normalized)
                synchronized(chatCacheLock) {
                    chatResponseCache[normKey]?.let { entry ->
                        if (!entry.isExpired()) {
                            chatCacheHits++
                            pendingChatL1L2Hits.incrementAndGet()
                            val bumped = entry.copy(hitCount = entry.hitCount + 1)
                            chatResponseCache[exactKey] = bumped
                            chatResponseCache[normKey] = bumped
                            perCharacterStats.getOrPut(characterId) { CharacterCacheStats() }.chatHits++
                            flushStatsToDatabase()
                            return entry.response
                        }
                    }
                }
            }

            // L3：语义匹配（vCache 自适应阈值 + ContextCache 上下文感知）
            val msgEmbedding = embed(lastUserMessage, characterId)
            if (msgEmbedding != null) {
                // 构建当前消息的近似上下文嵌入（取最近用户消息的嵌入均值）
                val ctxEmbedding = buildContextEmbedding(messages)
                synchronized(chatCacheLock) {
                    val best = chatResponseCache.entries
                        .filter { it.key.characterId == characterId }
                        .filter { it.value.messageEmbedding != null }
                        .filter { it.value.hitCount >= L3_MIN_HITS_FOR_MATCH }
                        .map { entry ->
                            val msgSim = cosineSimilarity(msgEmbedding, entry.value.messageEmbedding!!)
                            // 上下文相似度（若双方都有上下文嵌入则计算，否则退化为仅消息相似度）
                            val ctxSim = if (ctxEmbedding != null && entry.value.contextEmbedding != null) {
                                cosineSimilarity(ctxEmbedding, entry.value.contextEmbedding!!)
                            } else 1.0f
                            // 加权融合：消息相似度 70% + 上下文相似度 30%
                            val fusedSim = msgSim * 0.7f + ctxSim * 0.3f
                            Pair(entry, fusedSim)
                        }
                        .filter { (entry, sim) -> sim >= adaptiveThreshold(entry.value.hitCount) }
                        .maxByOrNull { it.second }

                    if (best != null) {
                        chatCacheL3Hits++
                        pendingChatL3Hits.incrementAndGet()
                        val entry = best.first.value
                        chatResponseCache[best.first.key] = entry.copy(hitCount = entry.hitCount + 1)
                        chatResponseCache[exactKey] = entry.copy(hitCount = entry.hitCount + 1)
                        perCharacterStats[characterId]?.chatHits =
                            (perCharacterStats[characterId]?.chatHits ?: 0) + 1
                        flushStatsToDatabase()
                        return entry.response
                    }
                }
            }

            perCharacterStats[characterId]?.chatMisses =
                (perCharacterStats[characterId]?.chatMisses ?: 0) + 1

            chatCacheMisses++
            pendingChatMisses.incrementAndGet()
        }
        try {
            // Get the default provider
            val provider = providerRepository.getDefault()
                ?: run {
                    flushStatsToDatabase()
                    return AiResponse(
                        content = "",
                        success = false,
                        errorMessage = "未配置 AI Provider，请在设置中添加"
                    )
                }

            // Get API key: provider-specific first, then fallback to legacy settings
            val apiKey = provider.apiKey.ifBlank { settingsRepository.getApiKey() }
            if (apiKey.isBlank()) {
                flushStatsToDatabase()
                return AiResponse(
                    content = "",
                    success = false,
                    errorMessage = "还没有设置 API Key，请先在「Provider 管理」中填入 \"${provider.name}\" 的密钥"
                )
            }

            // Get model settings: modelOverride > provider-specific > legacy settings
            val model = modelOverride?.takeIf { it.isNotBlank() }
                ?: provider.model.ifBlank { settingsRepository.getModel() }
            // Bug 1 fix: 非流式 chat() 参数回退与 chatStream() 保持一致
            // 仅当 Provider 未显式设置参数（值为 0）时才回退到全局设置
            val temperature = if (provider.temperature != 0.0) provider.temperature else settingsRepository.getTemperature()
            val rawMaxTokens = if (provider.maxTokens > 0) provider.maxTokens else settingsRepository.getMaxTokens()
            val frequencyPenalty = if (provider.frequencyPenalty != 0.0) provider.frequencyPenalty else settingsRepository.getFrequencyPenalty()
            val presencePenalty = if (provider.presencePenalty != 0.0) provider.presencePenalty else settingsRepository.getPresencePenalty()
            val thinkingEnabled = provider.thinkingEnabled
            val reasoningEffort = provider.reasoningEffort
            val thinkingBudgetTokens = provider.thinkingBudgetTokens

            // 上下文窗口限制：确保 maxTokens 不超过上下文窗口
            val ctxWindow = getActiveContextWindow()
            val maxTokens = if (ctxWindow > 0 && rawMaxTokens > ctxWindow) {
                ctxWindow.toInt()
            } else {
                rawMaxTokens
            }

            Log.d("AiService", "Provider thinkingEnabled=$thinkingEnabled, reasoningEffort=$reasoningEffort, thinkingBudgetTokens=$thinkingBudgetTokens, type=${provider.type}, model=$model, maxTokens=$maxTokens, contextWindow=$ctxWindow")

            // Create the appropriate provider implementation
            val aiProvider = providerFactory.create(provider)

            // Call the provider
            val response = when (aiProvider) {
                is OpenAiCompatibleProvider -> {
                    aiProvider.chatWithConfig(
                        messages = messages,
                        model = model,
                        temperature = temperature,
                        maxTokens = maxTokens,
                        apiKey = apiKey,
                        baseUrl = provider.baseUrl,
                        thinkingEnabled = thinkingEnabled,
                        reasoningEffort = reasoningEffort,
                        frequencyPenalty = frequencyPenalty,
                        presencePenalty = presencePenalty
                    ).toAiResponse()
                }
                is AnthropicProvider -> {
                    aiProvider.chatWithConfig(
                        messages = messages,
                        model = model,
                        temperature = temperature,
                        maxTokens = maxTokens,
                        apiKey = apiKey,
                        baseUrl = provider.baseUrl,
                        thinkingEnabled = thinkingEnabled,
                        thinkingBudgetTokens = thinkingBudgetTokens
                    ).toAiResponse()
                }
                is GeminiProvider -> {
                    aiProvider.chatWithConfig(
                        messages = messages,
                        model = model,
                        temperature = temperature,
                        maxTokens = maxTokens,
                        apiKey = apiKey,
                        baseUrl = provider.baseUrl,
                        thinkingEnabled = thinkingEnabled,
                        thinkingBudgetTokens = thinkingBudgetTokens
                    ).toAiResponse()
                }
                else -> {
                    // Unreachable: ProviderFactory only creates the three types above
                    AiResponse(
                        content = "",
                        success = false,
                        errorMessage = "不支持的 Provider 类型: ${provider.type}"
                    )
                }
            }

            // 缓存成功的响应（精确 key + 归一化 key，附带嵌入向量供 L3 语义匹配）
            if (response.success && lastUserMessage.isNotBlank()) {
                val ch = contextHash(messages)
                val exactKey = ChatCacheKey(characterId, ch, lastUserMessage)
                val cachedMsgEmbedding = synchronized(embedCacheLock) { embedCache[lastUserMessage] }
                val cachedCtxEmbedding = buildContextEmbedding(messages)
                val entry = ChatCacheEntry(
                    response = response,
                    messageEmbedding = cachedMsgEmbedding,
                    contextEmbedding = cachedCtxEmbedding
                )
                synchronized(chatCacheLock) {
                    // 存储前淘汰过期条目（摊销成本）
                    chatResponseCache.entries.removeAll { it.value.isExpired() }
                    chatResponseCache[exactKey] = entry
                    val norm = normalize(lastUserMessage)
                    if (norm != lastUserMessage.lowercase(Locale.ROOT).trim()) {
                        chatResponseCache[ChatCacheKey(characterId, ch, norm)] = entry
                    }
                }
            }

            flushStatsToDatabase()
            return response
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            flushStatsToDatabase()
            return AiResponse(
                content = "",
                success = false,
                errorMessage = "发送消息失败，请检查网络连接后重试：${e.localizedMessage ?: "未知错误"}"
            )
        }
    }

    /**
     * 获取当前活跃的上下文窗口大小（token 数）。
     * 优先级：Provider.contextWindow > Model DB contextWindow > 0（不限制）
     */
    suspend fun getActiveContextWindow(): Long {
        val provider = providerRepository.getDefault() ?: return 0
        // Provider 设置优先
        if (provider.contextWindow > 0) return provider.contextWindow
        // Model 数据库兜底
        val modelId = provider.model.ifBlank { settingsRepository.getModel() }
        if (modelId.isBlank()) return 0
        return modelRepository.getByModelId(provider.id, modelId)?.contextWindow ?: 0
    }

    /**
     * 获取当前活跃模型和 Provider 的显示名称，如 "DeepSeek / deepseek-v4-flash"
     */
    suspend fun getActiveModelDisplay(): String {
        val provider = providerRepository.getDefault() ?: return ""
        val model = provider.model.ifBlank { settingsRepository.getModel() }
        return if (model.isNotBlank()) "${provider.name} / $model" else provider.name
    }

    /** 获取当前活跃 Provider 名称，用于 Token 权重等模型差异化逻辑 */
    suspend fun getActiveProviderName(): String {
        return providerRepository.getDefault()?.name ?: ""
    }

    /** 当前 Provider 是否启用了深度思考模式 */
    suspend fun isActiveProviderThinkingEnabled(): Boolean {
        return providerRepository.getDefault()?.thinkingEnabled == true
    }

    private fun ProviderResult.toAiResponse(): AiResponse {
        return AiResponse(
            content = content,
            reasoningContent = reasoningContent,
            promptTokens = promptTokens,
            completionTokens = completionTokens,
            success = success,
            errorMessage = errorMessage
        )
    }

    /**
     * 流式聊天。与 chat() 使用相同的 pre-call 逻辑，但调用 Provider 的 chatStream()
     * 返回 StreamToken 流，包含逐字内容和思考过程。
     */
    suspend fun chatStream(messages: List<ChatMessage>, modelOverride: String? = null, characterId: Long = 0): Flow<StreamToken> {
        // === 缓存查找（与 chat() 相同的 L1→L2→L3 逻辑） ===
        val lastUserMessage = messages.lastOrNull { it.role == "user" }?.content ?: ""
        if (lastUserMessage.isNotBlank()) {
            val ch = contextHash(messages)

            // L1：精确匹配
            val exactKey = ChatCacheKey(characterId, ch, lastUserMessage)
            synchronized(chatCacheLock) {
                chatResponseCache[exactKey]?.let { entry ->
                    if (entry.isExpired()) {
                        chatResponseCache.remove(exactKey)
                    } else {
                        chatCacheHits++
                        pendingChatL1L2Hits.incrementAndGet()
                        chatResponseCache[exactKey] = entry.copy(hitCount = entry.hitCount + 1)
                        perCharacterStats.getOrPut(characterId) { CharacterCacheStats() }.chatHits++
                        flushStatsToDatabase()
                        return flow {
                            emit(StreamToken(content = entry.response.content, reasoningContent = entry.response.reasoningContent))
                        }
                    }
                }
            }

            // L2：归一化匹配
            val normalized = normalize(lastUserMessage)
            if (normalized != lastUserMessage.lowercase(Locale.ROOT).trim()) {
                val normKey = ChatCacheKey(characterId, ch, normalized)
                synchronized(chatCacheLock) {
                    chatResponseCache[normKey]?.let { entry ->
                        if (!entry.isExpired()) {
                            chatCacheHits++
                            pendingChatL1L2Hits.incrementAndGet()
                            val bumped = entry.copy(hitCount = entry.hitCount + 1)
                            chatResponseCache[exactKey] = bumped
                            chatResponseCache[normKey] = bumped
                            perCharacterStats.getOrPut(characterId) { CharacterCacheStats() }.chatHits++
                            flushStatsToDatabase()
                            return flow {
                                emit(StreamToken(content = entry.response.content, reasoningContent = entry.response.reasoningContent))
                            }
                        }
                    }
                }
            }

            // L3：语义匹配（vCache 自适应阈值 + ContextCache 上下文感知）
            val msgEmbedding = embed(lastUserMessage, characterId)
            if (msgEmbedding != null) {
                val ctxEmbedding = buildContextEmbedding(messages)
                synchronized(chatCacheLock) {
                    val best = chatResponseCache.entries
                        .filter { it.key.characterId == characterId }
                        .filter { it.value.messageEmbedding != null }
                        .filter { it.value.hitCount >= L3_MIN_HITS_FOR_MATCH }
                        .map { entry ->
                            val msgSim = cosineSimilarity(msgEmbedding, entry.value.messageEmbedding!!)
                            val ctxSim = if (ctxEmbedding != null && entry.value.contextEmbedding != null) {
                                cosineSimilarity(ctxEmbedding, entry.value.contextEmbedding!!)
                            } else 1.0f
                            val fusedSim = msgSim * 0.7f + ctxSim * 0.3f
                            Pair(entry, fusedSim)
                        }
                        .filter { (entry, sim) -> sim >= adaptiveThreshold(entry.value.hitCount) }
                        .maxByOrNull { it.second }

                    if (best != null) {
                        chatCacheL3Hits++
                        pendingChatL3Hits.incrementAndGet()
                        val entry = best.first.value
                        chatResponseCache[best.first.key] = entry.copy(hitCount = entry.hitCount + 1)
                        chatResponseCache[exactKey] = entry.copy(hitCount = entry.hitCount + 1)
                        perCharacterStats[characterId]?.chatHits =
                            (perCharacterStats[characterId]?.chatHits ?: 0) + 1
                        flushStatsToDatabase()
                        return flow {
                            emit(StreamToken(content = entry.response.content, reasoningContent = entry.response.reasoningContent))
                        }
                    }
                }
            }

            perCharacterStats[characterId]?.chatMisses =
                (perCharacterStats[characterId]?.chatMisses ?: 0) + 1
            chatCacheMisses++
            pendingChatMisses.incrementAndGet()
        }

        // === 未命中缓存，调用 AI API（流式） ===
        // Bug 6 fix: 优雅处理配置缺失，返回空 flow 让 ViewModel 降级到非流式 chat()
        // 而非直接抛异常，避免不必要的崩溃日志
        val provider = providerRepository.getDefault()
        if (provider == null) {
            flushStatsToDatabase()
            return flow { }  // 空流 → ViewModel 检测无内容，降级到 chat() 获取错误提示
        }

        val apiKey = provider.apiKey.ifBlank { settingsRepository.getApiKey() }
        if (apiKey.isBlank()) {
            flushStatsToDatabase()
            return flow { }  // 空流 → ViewModel 降级到 chat()，展示完整错误信息
        }

        val model = modelOverride?.takeIf { it.isNotBlank() }
            ?: provider.model.ifBlank { settingsRepository.getModel() }
        // Bug 2 fix: 使用 != 0.0 而非 > 0，避免用户将 temperature/penalty 显式设为 0 时被误判为"未设置"
        val temperature = if (provider.temperature != 0.0) provider.temperature else settingsRepository.getTemperature()
        val rawMaxTokens = if (provider.maxTokens > 0) provider.maxTokens else settingsRepository.getMaxTokens()
        val frequencyPenalty = if (provider.frequencyPenalty != 0.0) provider.frequencyPenalty else settingsRepository.getFrequencyPenalty()
        val presencePenalty = if (provider.presencePenalty != 0.0) provider.presencePenalty else settingsRepository.getPresencePenalty()
        val thinkingEnabled = provider.thinkingEnabled
        val reasoningEffort = provider.reasoningEffort
        val thinkingBudgetTokens = provider.thinkingBudgetTokens

        val ctxWindow = getActiveContextWindow()
        val maxTokens = if (ctxWindow > 0 && rawMaxTokens > ctxWindow) ctxWindow.toInt() else rawMaxTokens
        // 流式读取超时：每次 readUtf8Line() 的最长等待时间，防止服务器卡住不发送数据
        val streamTimeoutMs = if (thinkingEnabled) 40_000L else 30_000L

        Log.d("AiService", "chatStream thinkingEnabled=$thinkingEnabled, reasoningEffort=$reasoningEffort, thinkingBudgetTokens=$thinkingBudgetTokens, type=${provider.type}, model=$model, maxTokens=$maxTokens")

        val aiProvider = providerFactory.create(provider)

        // 构建上游流（按 Provider 类型分发）
        val upstream = when (aiProvider) {
            is OpenAiCompatibleProvider -> {
                aiProvider.chatStreamWithConfig(
                    messages = messages,
                    model = model,
                    temperature = temperature,
                    maxTokens = maxTokens,
                    apiKey = apiKey,
                    baseUrl = provider.baseUrl,
                    thinkingEnabled = thinkingEnabled,
                    reasoningEffort = reasoningEffort,
                    streamTimeoutMs = streamTimeoutMs,
                    frequencyPenalty = frequencyPenalty,
                    presencePenalty = presencePenalty
                )
            }
            is AnthropicProvider -> {
                aiProvider.chatStreamWithConfig(
                    messages = messages,
                    model = model,
                    temperature = temperature,
                    maxTokens = maxTokens,
                    apiKey = apiKey,
                    baseUrl = provider.baseUrl,
                    thinkingEnabled = thinkingEnabled,
                    thinkingBudgetTokens = thinkingBudgetTokens,
                    streamTimeoutMs = streamTimeoutMs
                )
            }
            is GeminiProvider -> {
                aiProvider.chatStreamWithConfig(
                    messages = messages,
                    model = model,
                    temperature = temperature,
                    maxTokens = maxTokens,
                    apiKey = apiKey,
                    baseUrl = provider.baseUrl,
                    thinkingEnabled = thinkingEnabled,
                    thinkingBudgetTokens = thinkingBudgetTokens,
                    streamTimeoutMs = streamTimeoutMs
                )
            }
            else -> {
                flow { throw IllegalStateException("不支持的 Provider 类型: ${provider.type}") }
            }
        }

        // 包装流：收集完整回复，流结束后写入缓存
        return flow {
            var contentBuilder = StringBuilder()
            var reasoningBuilder = StringBuilder()
            var lastToken: StreamToken? = null

            try {
                upstream.collect { token ->
                    if (token.content.isNotEmpty()) contentBuilder.append(token.content)
                    if (token.reasoningContent.isNotEmpty()) reasoningBuilder.append(token.reasoningContent)
                    lastToken = token
                    emit(token)
                }
            } finally {
                // 流结束后写入缓存（失败不影响已发送的回复）
                val fullContent = contentBuilder.toString()
                if (fullContent.isNotBlank() && lastUserMessage.isNotBlank()) {
                    try {
                        val response = AiResponse(
                            content = fullContent,
                            reasoningContent = reasoningBuilder.toString(),
                            promptTokens = lastToken?.promptTokens ?: 0,
                            completionTokens = lastToken?.completionTokens ?: 0,
                            success = true
                        )
                        val ch = contextHash(messages)
                        val exactKey2 = ChatCacheKey(characterId, ch, lastUserMessage)
                        val cachedMsgEmbedding = synchronized(embedCacheLock) { embedCache[lastUserMessage] }
                        val cachedCtxEmbedding = buildContextEmbedding(messages)
                        val entry = ChatCacheEntry(
                            response = response,
                            messageEmbedding = cachedMsgEmbedding,
                            contextEmbedding = cachedCtxEmbedding
                        )
                        synchronized(chatCacheLock) {
                            chatResponseCache.entries.removeAll { it.value.isExpired() }
                            chatResponseCache[exactKey2] = entry
                            val norm = normalize(lastUserMessage)
                            if (norm != lastUserMessage.lowercase(Locale.ROOT).trim()) {
                                chatResponseCache[ChatCacheKey(characterId, ch, norm)] = entry
                            }
                        }
                    } catch (_: Exception) {
                        // 缓存写入失败不影响主流程
                    }
                }
                flushStatsToDatabase()
            }
        }
    }

    // === 嵌入缓存（三层缓存中的 L1+L2：精确匹配 + 归一化匹配） ===
    // 用 LinkedHashMap 实现 LRU：容量 200 条，超出时淘汰最旧条目
    private val embedCache = object : LinkedHashMap<String, FloatArray>(200, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, FloatArray>?): Boolean {
            return size > 200
        }
    }
    private val embedCacheLock = Any()

    /** 正在进行的 embed 请求（合并并发相同请求，仅发一次 API 调用） */
    private val pendingEmbedRequests = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.CompletableDeferred<FloatArray?>>()

    /** 缓存命中计数（调试用） */
    var embedCacheHits = 0
        private set
    var embedCacheMisses = 0
        private set

    /** 归一化文本：去标点、多余空格、小写，用于 L2 模糊匹配 */
    private fun normalize(text: String): String {
        return text
            .lowercase(Locale.ROOT)
            .filter { it.isLetterOrDigit() || it.isWhitespace() }
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * 生成文本的嵌入向量，用于语义搜索。
     * 内置三层查找：精确匹配 → 归一化匹配 → API 调用。
     * @return FloatArray 嵌入向量，失败返回 null
     */
    suspend fun embed(text: String, characterId: Long = 0): FloatArray? {
        // L1：精确匹配
        synchronized(embedCacheLock) {
            embedCache[text]?.let {
                embedCacheHits++
                pendingEmbedHits.incrementAndGet()
                if (characterId > 0) {
                    perCharacterStats.getOrPut(characterId) { CharacterCacheStats() }.embedHits++
                }
                flushStatsToDatabase()
                return it
            }
        }

        // L2：归一化匹配
        val normalized = normalize(text)
        if (normalized != text.lowercase(Locale.ROOT).trim()) {
            synchronized(embedCacheLock) {
                embedCache[normalized]?.let {
                    embedCacheHits++
                    pendingEmbedHits.incrementAndGet()
                    if (characterId > 0) {
                        perCharacterStats.getOrPut(characterId) { CharacterCacheStats() }.embedHits++
                    }
                    // 用精确 key 也存一份，下次直中
                    embedCache[text] = it
                    flushStatsToDatabase()
                    return it
                }
            }
        }

        // 请求合并：如果已有相同的进行中请求，等待其结果（避免重复 API 调用）
        pendingEmbedRequests[text]?.let { deferred ->
            return deferred.await()
        }

        // 再次检查缓存：另一个协程可能在 L2 检查和 pending 检查之间完成了同一请求
        synchronized(embedCacheLock) {
            embedCache[text]?.let {
                embedCacheHits++
                pendingEmbedHits.incrementAndGet()
                if (characterId > 0) {
                    perCharacterStats.getOrPut(characterId) { CharacterCacheStats() }.embedHits++
                }
                flushStatsToDatabase()
                return it
            }
        }

        // L3：调 API
        embedCacheMisses++
        pendingEmbedMisses.incrementAndGet()
        if (characterId > 0) {
            perCharacterStats.getOrPut(characterId) { CharacterCacheStats() }.embedMisses++
        }
        val deferred = kotlinx.coroutines.CompletableDeferred<FloatArray?>()
        pendingEmbedRequests[text] = deferred
        return try {
            val provider = providerRepository.getDefault()
            if (provider == null) {
                deferred.complete(null)
                flushStatsToDatabase()
                return null
            }
            val apiKey = provider.apiKey.ifBlank { settingsRepository.getApiKey() }
            if (apiKey.isBlank()) {
                deferred.complete(null)
                flushStatsToDatabase()
                return null
            }
            val aiProvider = providerFactory.create(provider)
            val result = when (aiProvider) {
                is OpenAiCompatibleProvider -> aiProvider.embed(text, apiKey, provider.baseUrl)
                else -> null // Anthropic/Gemini embedding 暂不支持
            }
            // 存入缓存（精确 key + 归一化 key）
            if (result != null) {
                synchronized(embedCacheLock) {
                    embedCache[text] = result
                    if (normalized != text.lowercase(Locale.ROOT).trim()) {
                        embedCache[normalized] = result
                    }
                }
            }
            deferred.complete(result)
            flushStatsToDatabase()
            result
        } catch (e: Exception) {
            Log.w("AiService", "Embedding failed: ${e.message}")
            deferred.complete(null)
            flushStatsToDatabase()
            null
        } finally {
            // 防御：确保 deferred 在任何情况下都被完成（包括 CancellationException 等非 Exception 异常）
            if (!deferred.isCompleted) {
                deferred.complete(null)
            }
            pendingEmbedRequests.remove(text)
        }
    }
}
