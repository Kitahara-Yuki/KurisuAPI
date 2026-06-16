package com.kurisuapi.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurisuapi.data.entity.CharacterEntity
import com.kurisuapi.data.entity.ChatHistoryEntity
import com.kurisuapi.data.entity.ConversationSessionEntity
import com.kurisuapi.data.repository.CharacterRepository
import com.kurisuapi.data.repository.ChatHistoryRepository
import com.kurisuapi.data.repository.ConversationSessionRepository
import com.kurisuapi.data.repository.SettingsRepository
import com.kurisuapi.domain.engine.EmotionEngine
import com.kurisuapi.domain.engine.PromptBuilder
import com.kurisuapi.domain.engine.RelationshipEngine
import com.kurisuapi.domain.provider.StreamToken
import com.kurisuapi.domain.service.AiService
import com.kurisuapi.domain.bridge.WeChatBridge
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.kurisuapi.util.TokenEstimator
import javax.inject.Inject
import java.util.concurrent.atomic.AtomicBoolean

data class ContextUsage(
    val usedTokens: Int = 0,
    val totalTokens: Long = 0,
    val modelDisplay: String = "",  // "DeepSeek / deepseek-v4-flash"
    val thinkingEnabled: Boolean = false
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val characterRepository: CharacterRepository,
    private val chatHistoryRepository: ChatHistoryRepository,
    private val sessionRepository: ConversationSessionRepository,
    private val settingsRepository: SettingsRepository,
    private val emotionEngine: EmotionEngine,
    private val relationshipEngine: RelationshipEngine,
    private val promptBuilder: PromptBuilder,
    private val aiService: AiService,
    private val weChatBridge: WeChatBridge,
    private val memoryExtractor: com.kurisuapi.domain.engine.MemoryExtractor,
    private val conversationSummarizer: com.kurisuapi.domain.engine.ConversationSummarizer
) : ViewModel() {

    private val _sessionId = MutableStateFlow(0L)

    // 会话实体
    val session: StateFlow<ConversationSessionEntity?> = _sessionId
        .flatMapLatest { id ->
            if (id > 0) sessionRepository.observeById(id)
            else flowOf(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // 会话对应角色的 characterId → 观察角色数据
    private val _activeCharacterId: StateFlow<Long> = session
        .map { it?.characterId ?: 0L }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    // 自动观察数据库中的角色数据，编辑后实时更新
    private val _activeCharacter: StateFlow<CharacterEntity?> = _activeCharacterId
        .flatMapLatest { id ->
            if (id > 0) characterRepository.observeById(id)
            else flowOf(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val activeCharacter: StateFlow<CharacterEntity?> = _activeCharacter

    // Bug fix: DAO returns DESC, but chat UI needs ASC (oldest first).
    // Previously used .reversed() on every emission creating new lists.
    // Now uses a pre-allocated mutable copy to avoid allocation pressure.
    val messages: StateFlow<List<ChatHistoryEntity>> = _sessionId
        .flatMapLatest { id ->
            if (id > 0) chatHistoryRepository.getBySession(id)
            else flowOf(emptyList())
        }
        .map { list -> if (list.size <= 1) list else list.asReversed() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _contextUsage = MutableStateFlow(ContextUsage())
    val contextUsage: StateFlow<ContextUsage> = _contextUsage.asStateFlow()

    /** 流式输出：当前正在生成的 AI 回复文本 */
    private val _streamingText = MutableStateFlow("")
    val streamingText: StateFlow<String> = _streamingText.asStateFlow()

    /** 是否正在流式接收 AI 回复 */
    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    /** 思考计时（秒） */
    private val _thinkingSeconds = MutableStateFlow(0)
    val thinkingSeconds: StateFlow<Int> = _thinkingSeconds.asStateFlow()

    /** 防止快速连发导致多个并行 API 调用 */
    private val sendMutex = Mutex()

    /** 防止多次快速发送导致记忆提取并发重叠 */
    private val extracting = AtomicBoolean(false)

    init {
        // Bug 13 fix: 收集微信消息事件流，供 UI 层感知新消息到达
        viewModelScope.launch {
            weChatBridge.incomingMessages.collect { _ ->
                // 消息入站事件已由 WeChatBridge 的 auto-reply 处理，
                // 这里仅消费事件防止 SharedFlow 缓冲区溢出
            }
        }
    }

    /** 强制刷新上下文额度，用于从设置页返回后更新显示 */
    fun refreshContextUsage() {
        viewModelScope.launch {
            try { recalculateContextUsage() } catch (_: Exception) { }
        }
    }

    fun loadSession(sessionId: Long) {
        _sessionId.value = sessionId
        // 旧数据兼容：加载无摘要的旧会话时，后台检查是否需要生成摘要
        if (sessionId > 0) {
            viewModelScope.launch {
                try {
                    val session = sessionRepository.getById(sessionId)
                    if (session != null && session.summary.isNullOrBlank()) {
                        conversationSummarizer.summarizeIfNeeded(sessionId)
                    }
                    // 初始加载后刷新上下文额度
                    recalculateContextUsage()
                } catch (_: Exception) { }
            }
        }
    }

    fun sendMessage(content: String) {
        val currentSession = session.value ?: return
        if (content.isBlank()) return

        // 归档会话不允许发送消息
        if (currentSession.isArchived) {
            _error.value = "该对话已归档，不能发送消息"
            return
        }

        val character = _activeCharacter.value ?: return
        if (character.id <= 0) return

        viewModelScope.launch {
            processMessage(character, currentSession.id, content, peerId = null)
        }
    }

    /**
     * 更新会话标题（取第一条用户消息的前30字作为标题）
     */
    private suspend fun maybeSetSessionTitle(sessionId: Long) {
        val currentSession = sessionRepository.getById(sessionId) ?: return
        if (currentSession.title != "新对话") return  // 已有自定义标题，不覆盖

        val count = chatHistoryRepository.countBySession(sessionId)
        if (count == 1) { // 只有刚发送的用户消息
            val all = chatHistoryRepository.getBySession(sessionId).firstOrNull() ?: return
            val lastMsg = all.firstOrNull() ?: return
            if (lastMsg.sender == "user") {
                val title = lastMsg.content.take(30)
                sessionRepository.updateTitle(sessionId, title)
            }
        }
    }

    private suspend fun processMessage(character: CharacterEntity, sessionId: Long, content: String, peerId: String?) {
        sendMutex.withLock {
            _isLoading.value = true
            _error.value = null

            // 启动思考计时器
            val timerJob = viewModelScope.launch {
                _thinkingSeconds.value = 0
                try {
                    while (true) {
                        delay(1000)
                        _thinkingSeconds.value += 1
                    }
                } catch (_: CancellationException) { }
            }

            try {
                // 1. 保存用户消息
                chatHistoryRepository.insert(
                    ChatHistoryEntity(
                        characterId = character.id,
                        sessionId = sessionId,
                        sender = "user",
                        content = content
                    )
                )

                // 更新会话时间
                val cur = sessionRepository.getById(sessionId) ?: run {
                    _error.value = "会话已不存在"; _isLoading.value = false; timerJob.cancel()
                    return@withLock
                }
                if (cur.isArchived) {
                    _error.value = "该对话已归档，不能发送消息"; _isLoading.value = false; timerJob.cancel()
                    return@withLock
                }
                sessionRepository.update(cur.copy(updatedAt = System.currentTimeMillis()))

                // 自动设置会话标题
                maybeSetSessionTitle(sessionId)

                // 2 & 3. 并行更新情绪和关系（supervisorScope + try-catch：任一引擎失败不阻断聊天）
                val (emotionResult, relationshipResult) = supervisorScope {
                    val emotion = async {
                        try { emotionEngine.updateEmotion(character.id, content, character.personality); emotionEngine.getEmotion(character.id) }
                        catch (e: Exception) { null }
                    }
                    val relationship = async {
                        try { relationshipEngine.updateRelationship(character.id, content, character.personality); relationshipEngine.getRelationship(character.id) }
                        catch (e: Exception) { null }
                    }
                    Pair(emotion.await(), relationship.await())
                }

                // 5. 构建 Prompt（引擎失败时用默认情绪/关系值，不阻断聊天）
                // 聊天模式从 session 实体读取，每个对话独立
                val ctxWindow = aiService.getActiveContextWindow()
                val promptMessages = promptBuilder.buildMessages(
                    character = character,
                    emotion = emotionResult ?: com.kurisuapi.data.entity.EmotionStateEntity(characterId = character.id),
                    relationship = relationshipResult ?: com.kurisuapi.data.entity.RelationshipEntity(characterId = character.id),
                    sessionId = sessionId,
                    userMessage = content,
                    contextWindow = ctxWindow,
                    chatMode = session.value?.chatMode ?: com.kurisuapi.data.entity.ConversationSessionEntity.CHAT_MODE_CHAT,
                    thinkingEnabled = aiService.isActiveProviderThinkingEnabled()
                )

                // 6. 调用 AI API（思考模式 120s，普通模式 30s）
                _isStreaming.value = true
                _streamingText.value = ""
                val fullResponse = StringBuilder()
                val reasoningBuilder = StringBuilder()
                var streamHadContent = false
                var apiPromptTokens = 0
                var apiCompletionTokens = 0
                val streamTimeout = if (aiService.isActiveProviderThinkingEnabled()) 40_000L else 30_000L
                try {
                    kotlinx.coroutines.withTimeout(streamTimeout) {
                        aiService.chatStream(promptMessages).collect { token ->
                            val hasContent = token.content.isNotBlank() || token.reasoningContent.isNotBlank()
                            if (hasContent) {
                                streamHadContent = true
                                fullResponse.append(token.content)
                                reasoningBuilder.append(token.reasoningContent)
                                _streamingText.value = fullResponse.toString()
                            }
                            if (token.promptTokens > 0) {
                                apiPromptTokens = token.promptTokens
                                apiCompletionTokens = token.completionTokens
                            }
                        }
                    }
                } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                    android.util.Log.w("ChatVM", "流式超时，已收到 ${fullResponse.length} 字")
                    if (fullResponse.isEmpty()) {
                        _error.value = "回复超时，请重试"
                        _isStreaming.value = false
                        _streamingText.value = ""
                        timerJob.cancel()
                        return@withLock
                    }
                    fullResponse.append("…")
                    _error.value = "回复可能被截断，请重试"
                    // 已有部分内容，追加省略号标记后继续保存
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    android.util.Log.e("ChatVM", "流式异常: ${e.message}", e)
                    // 降级到非流式
                }
                // 降级：流式未返回任何内容 → 调用非流式，同时让 streamingText 有内容以启用打字机动画
                if (!streamHadContent) {
                    try {
                        val fallback = kotlinx.coroutines.withTimeout(streamTimeout) {
                            aiService.chat(promptMessages)
                        }
                        if (fallback.success && fallback.content.isNotBlank()) {
                            fullResponse.append(fallback.content)
                            reasoningBuilder.append(fallback.reasoningContent)
                            _streamingText.value = fullResponse.toString()
                            if (fallback.promptTokens > 0) {
                                apiPromptTokens = fallback.promptTokens
                                apiCompletionTokens = fallback.completionTokens
                            }
                        } else {
                            _error.value = fallback.errorMessage ?: "AI 返回了空回复，请重试"
                            _isStreaming.value = false
                            _streamingText.value = ""
                            timerJob.cancel()
                            return@withLock
                        }
                    } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                        _error.value = "回复超时，请重试"
                        _isStreaming.value = false
                        _streamingText.value = ""
                        timerJob.cancel()
                        return@withLock
                    }
                }

                val responseContent = fullResponse.toString().trim()
                val reasoningContent = reasoningBuilder.toString().trim()
                // 先停掉思考计时器和加载状态
                timerJob.cancel()
                _thinkingSeconds.value = 0
                _isLoading.value = false
                // 等打字机动画播完，再存数据库和隐藏流式气泡
                if (responseContent.isNotEmpty()) {
                    val animMs = (responseContent.length * 40L).coerceIn(600, 4000)
                    kotlinx.coroutines.delay(animMs)
                }
                chatHistoryRepository.insert(
                    ChatHistoryEntity(
                        characterId = character.id,
                        sessionId = sessionId,
                        sender = "ai",
                        content = responseContent,
                        reasoningContent = reasoningContent
                    )
                )

                // 用 API 返回的真实 prompt_tokens 更新显示，并保存到会话
                // 下次进入时直接读取，不再走估算
                if (apiPromptTokens > 0) {
                    _contextUsage.value = _contextUsage.value.copy(usedTokens = apiPromptTokens)
                    sessionRepository.updateLastPromptTokens(sessionId, apiPromptTokens)
                }

                // 7.5. 后台触发对话摘要（不阻塞 UI）
                viewModelScope.launch {
                    conversationSummarizer.summarizeIfNeeded(sessionId)
                }

                // 8. 如果消息来自微信，回复到微信
                if (peerId != null) {
                    val sendResult = weChatBridge.sendMessage(peerId, responseContent)
                    if (sendResult.isFailure) {
                        _error.value = "微信回复发送失败：${sendResult.exceptionOrNull()?.message ?: "未知错误"}"
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _error.value = "消息发送失败，请检查网络连接后重试：${e.localizedMessage ?: "未知错误"}"
                _isStreaming.value = false
                _streamingText.value = ""
            } finally {
                timerJob.cancel()
                _thinkingSeconds.value = 0
                _isLoading.value = false
                _isStreaming.value = false
                _streamingText.value = ""
            }
        }
        // 回复处理完成后，检查是否触发记忆提取（在 mutex 外，独立运行不阻塞 UI）
        maybeExtractMemory(character.id)
    }

    /**
     * 累计新消息达到阈值且开关开启时，后台提取记忆。失败静默。
     */
    private fun maybeExtractMemory(characterId: Long) {
        // 防止并发重叠：同一时间只允许一个提取任务运行
        if (!extracting.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                if (!settingsRepository.isAutoMemoryEnabled()) return@launch
                if (characterId <= 0) return@launch

                val total = chatHistoryRepository.countByCharacter(characterId)
                val lastExtract = settingsRepository.getLastExtractCount(characterId)
                val interval = settingsRepository.getMemoryInterval()

                if (total - lastExtract >= interval) {
                    val sid = _sessionId.value
                    val ok = memoryExtractor.extractAndStore(characterId, sid)
                    if (ok) settingsRepository.setLastExtractCount(characterId, total)
                }
            } catch (e: Exception) {
                // 记忆提取失败不影响聊天
            } finally {
                extracting.set(false)
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    /**
     * 会话加载后刷新上下文额度显示。
     * 优先使用数据库中保存的上次 API 返回的真实 prompt_tokens，
     * 没有时才用 TokenEstimator 估算作为兜底。
     */
    private suspend fun recalculateContextUsage() {
        try {
            val sid = _sessionId.value
            if (sid <= 0) return
            val totalTokens = aiService.getActiveContextWindow()
            val modelDisplay = aiService.getActiveModelDisplay()
            val session = sessionRepository.getById(sid)
            val usedTokens = if (session != null && session.lastPromptTokens > 0) {
                // 有 API 返回的真实值，直接用，不走估算
                session.lastPromptTokens
            } else {
                // 兜底：首次发送消息前，还没有真实值，从 DB 估算
                val allMessages = chatHistoryRepository.getBySession(sid).firstOrNull() ?: emptyList()
                val msgTokens = if (allMessages.isNotEmpty()) {
                    TokenEstimator.estimateTokens(allMessages.map { it.content })
                } else 0
                val systemOverhead = try {
                    val character = characterRepository.getById(session?.characterId ?: 0)
                    if (character != null) {
                        val emotion = emotionEngine.getEmotion(character.id)
                        val relationship = relationshipEngine.getRelationship(character.id)
                        val sysMessages = promptBuilder.buildMessages(
                            sessionId = sid, character = character,
                            emotion = emotion, relationship = relationship,
                            recentMessageLimit = 0, contextWindow = 0,
                            chatMode = session?.chatMode ?: com.kurisuapi.data.entity.ConversationSessionEntity.CHAT_MODE_CHAT
                        )
                        TokenEstimator.estimateTokens(sysMessages.map { it.content })
                    } else 0
                } catch (_: Exception) { 0 }
                msgTokens + systemOverhead
            }
            val thinking = try { aiService.isActiveProviderThinkingEnabled() } catch (_: Exception) { false }
            _contextUsage.value = ContextUsage(
                usedTokens = usedTokens,
                totalTokens = totalTokens,
                modelDisplay = modelDisplay,
                thinkingEnabled = thinking
            )
        } catch (_: Exception) { }
    }
}
