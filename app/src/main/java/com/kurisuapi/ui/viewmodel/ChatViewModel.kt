package com.kurisuapi.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurisuapi.data.entity.CharacterEntity
import com.kurisuapi.data.entity.ChatHistoryEntity
import com.kurisuapi.data.entity.ConversationSessionEntity
import com.kurisuapi.data.repository.CharacterRepository
import com.kurisuapi.data.repository.ChatHistoryRepository
import com.kurisuapi.data.repository.ConversationSessionRepository
import com.kurisuapi.data.repository.EmotionRepository
import com.kurisuapi.data.repository.SettingsRepository
import com.kurisuapi.domain.engine.ChatOutputFilter
import com.kurisuapi.domain.engine.CircadianModulator
import com.kurisuapi.domain.engine.EmotionEngine
import com.kurisuapi.domain.engine.PromptBuilder
import com.kurisuapi.domain.engine.RelationshipEngine
import com.kurisuapi.domain.engine.StageViolation
import com.kurisuapi.domain.engine.TypewriterEngine
import com.kurisuapi.domain.provider.StreamToken
import com.kurisuapi.domain.service.AiService
import com.kurisuapi.domain.bridge.WeChatBridge
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.kurisuapi.util.TokenEstimator
import java.time.LocalTime
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
    private val conversationSummarizer: com.kurisuapi.domain.engine.ConversationSummarizer,
    private val chatOutputFilter: com.kurisuapi.domain.engine.ChatOutputFilter,
    private val emotionRepository: EmotionRepository,
    private val circadianModulator: CircadianModulator
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

    /** 打字机动画引擎（独立黑盒，ViewModel 只需调用 start/awaitFinish/reset） */
    private val typewriter = TypewriterEngine(viewModelScope)
    val typewriterText: StateFlow<String> = typewriter.text

    /** 是否正在流式接收 AI 回复 */
    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    /** 思考计时（秒） */
    private val _thinkingSeconds = MutableStateFlow(0)
    val thinkingSeconds: StateFlow<Int> = _thinkingSeconds.asStateFlow()

    /** 一次性重置所有流式状态（typewriter + streaming + text） */
    private fun resetStreamingState() {
        typewriterCleanupJob?.cancel()
        typewriterCleanupJob = null
        typewriter.reset()
        _isStreaming.value = false
        _streamingText.value = ""
    }

    /** 防止快速连发导致多个并行 API 调用 */
    private val sendMutex = Mutex()

    /** 防止多次快速发送导致记忆提取并发重叠 */
    private val extracting = AtomicBoolean(false)

    /** 打字机播完后台清理 Job，新消息来时要取消避免竞态 */
    private var typewriterCleanupJob: Job? = null

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
        // 切换会话时清理上一个会话的流式残留状态
        resetStreamingState()
        _isLoading.value = false
        _thinkingSeconds.value = 0
        // 旧数据兼容：加载无摘要的旧会话时，后台检查是否需要生成摘要
        if (sessionId > 0) {
            viewModelScope.launch {
                try {
                    val session = sessionRepository.getById(sessionId)
                    if (session != null && session.summary.isNullOrBlank()) {
                        conversationSummarizer.summarizeIfNeeded(sessionId)
                    }
                    // 跨会话情感记忆：载入时让情绪追上流逝的时间
                    applyEmotionDecayIfNeeded(session?.characterId ?: 0L)
                    // 初始加载后刷新上下文额度
                    recalculateContextUsage()
                } catch (_: Exception) { }
            }
        }
    }

    /**
     * 跨会话情感记忆：载入会话时，让角色情绪追上流逝的时间。
     * 如果距上次更新超过 5 分钟，计算被动衰减并持久化。
     * 与 HomeViewModel.applyLonelinessDecayIfNeeded 互补：那个处理天级，这个处理小时级。
     */
    private suspend fun applyEmotionDecayIfNeeded(characterId: Long) {
        if (characterId <= 0) return
        try {
            val emotion = emotionEngine.getEmotion(characterId)
            val elapsed = System.currentTimeMillis() - emotion.updatedAt
            if (elapsed < 5 * 60_000L) return // 不到 5 分钟，跳过
            val circadianOn = settingsRepository.isCircadianEnabled()
            val fresh = emotionEngine.computePassiveDecay(emotion, elapsed, circadianOn)
            if (fresh != emotion) {
                emotionRepository.insertOrUpdate(fresh.copy(updatedAt = System.currentTimeMillis()))
            }
        } catch (_: Exception) { }
    }

    fun sendMessage(content: String) {
        val currentSession = session.value ?: return
        if (content.isBlank()) return

        // 归档会话不允许发送消息
        if (currentSession.isArchived) {
            _error.value = "该对话已归档，不能发送消息"
            return
        }

        val character = _activeCharacter.value
        if (character == null) {
            _error.value = "角色数据加载中，请稍后再试"
            return
        }
        if (character.id <= 0) {
            _error.value = "角色数据异常，请重新选择角色"
            return
        }

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

            var shouldCleanupTypewriter = true
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
                // 捕获 chatMode 到局部变量，防止流式过程中用户切换会话导致读到错误值
                val chatMode = cur.chatMode

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
                    chatMode = chatMode,
                    thinkingEnabled = aiService.isActiveProviderThinkingEnabled()
                )

                // 6. 调用 AI API（思考模式 120s，普通模式 30s）
                typewriterCleanupJob?.cancel()
                typewriter.reset() // 清空上一轮残留的 typewriterText
                _isStreaming.value = true
                _streamingText.value = ""
                typewriter.start { _streamingText.value }
                val fullResponse = StringBuilder()
                val reasoningBuilder = StringBuilder()
                var streamHadContent = false
                var apiPromptTokens = 0
                var apiCompletionTokens = 0
                // 提前判断聊天模式，避免流式回调中重复查询
                val isChatMode = chatMode != "story"
                // ViewModel 超时设为 AiService 内部超时的 3 倍，避免截断慢速流
                val streamTimeout = if (aiService.isActiveProviderThinkingEnabled()) 120_000L else 90_000L
                try {
                    kotlinx.coroutines.withTimeout(streamTimeout) {
                        aiService.chatStream(promptMessages, characterId = character.id).collect { token ->
                            val hasActualContent = token.content.isNotBlank()
                            val hasReasoning = token.reasoningContent.isNotBlank()
                            if (hasActualContent || hasReasoning) {
                                fullResponse.append(token.content)
                                reasoningBuilder.append(token.reasoningContent)
                                // streamHadContent 仅在实际内容到达时设置，
                                // 避免纯思考 token 阻止 fallback 降级
                                if (hasActualContent) {
                                    streamHadContent = true
                                }
                                _streamingText.value = if (isChatMode) {
                                    val filtered = chatOutputFilter.filter(fullResponse.toString())
                                    // 防止增量过滤产生空字符串导致流式气泡闪烁
                                    if (filtered.isNotBlank()) filtered else _streamingText.value
                                } else {
                                    fullResponse.toString()
                                }
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
                        resetStreamingState()
                        timerJob.cancel()
                        return@withLock
                    }
                    fullResponse.append("…")
                    _streamingText.value = fullResponse.toString()
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
                            _streamingText.value = if (isChatMode) {
                                chatOutputFilter.filter(fullResponse.toString())
                            } else {
                                fullResponse.toString()
                            }
                            if (fallback.promptTokens > 0) {
                                apiPromptTokens = fallback.promptTokens
                                apiCompletionTokens = fallback.completionTokens
                            }
                        } else {
                            _error.value = fallback.errorMessage ?: "AI 返回了空回复，请重试"
                            resetStreamingState()
                            timerJob.cancel()
                            return@withLock
                        }
                    } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                        _error.value = "回复超时，请重试"
                        resetStreamingState()
                        timerJob.cancel()
                        return@withLock
                    }
                }
                // 标记流已结束（必须在 fallback 之后，确保 _streamingText 已有内容）
                typewriter.markStreamComplete()

                var responseContent = fullResponse.toString().trim()
                val reasoningContent = reasoningBuilder.toString().trim()
                // 对话模式：过滤动作描写；剧情模式：强制空行分隔
                if (isChatMode) {
                    responseContent = chatOutputFilter.filter(responseContent)
                } else {
                    responseContent = chatOutputFilter.formatStory(responseContent)
                }

                // ═══════════════════════════════════════════
                // 方案二+三：阶段护栏检测 + 重新生成兜底
                // ═══════════════════════════════════════════
                if (isChatMode && responseContent.isNotEmpty()) {
                    val relStage = relationshipResult?.stage ?: "初识"
                    val violation = chatOutputFilter.validateByStage(
                        responseContent, relStage, character.personality
                    )
                    if (violation != null) {
                        android.util.Log.w("ChatVM",
                            "阶段护栏拦截: stage=$relStage type=${violation.type} matched=${violation.matchedText}")
                        // 护栏触发：暂停当前打字机，生成新回复后重新启动动画
                        responseContent = regenerateWithStageGuard(
                            aiService, promptMessages, responseContent,
                            violation, relStage, character
                        )
                        responseContent = chatOutputFilter.filter(responseContent)
                        _streamingText.value = responseContent
                        typewriter.restart { _streamingText.value }
                        typewriter.markStreamComplete()
                    }
                }

                // 先停掉思考计时器
                timerJob.cancel()
                _thinkingSeconds.value = 0
                // 立即存入 DB 并释放 sendMutex 锁，不再等待打字机播完
                if (responseContent.isNotEmpty()) {
                    chatHistoryRepository.insert(
                        ChatHistoryEntity(
                            characterId = character.id,
                            sessionId = sessionId,
                            sender = "ai",
                            content = responseContent,
                            reasoningContent = reasoningContent
                        )
                    )
                }
                // 打字机继续在后台逐字播出，不阻塞锁
                _isStreaming.value = false
                _isLoading.value = false
                shouldCleanupTypewriter = false
                typewriterCleanupJob = viewModelScope.launch {
                    typewriter.awaitFinish(timeoutMs = 60_000L)
                    _streamingText.value = ""
                    // 不 reset typewriter — 保留完整文字供 UI 内容匹配过渡
                }

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
            } finally {
                timerJob.cancel()
                _thinkingSeconds.value = 0
                _isLoading.value = false
                // 仅在异常/提前返回路径清理打字机；正常路径由后台协程接管
                if (shouldCleanupTypewriter) {
                    resetStreamingState()
                }
            }
        }
        // 回复处理完成后，检查是否触发记忆提取（在 mutex 外，独立运行不阻塞 UI）
        maybeExtractMemory(character.id, sessionId)
    }

    /**
     * 累计新消息达到阈值且开关开启时，后台提取记忆。失败静默。
     */
    private fun maybeExtractMemory(characterId: Long, sessionId: Long) {
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
                    val ok = memoryExtractor.extractAndStore(characterId, sessionId)
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
     * 方案三：阶段护栏触发后，用更严格的 prompt 重新生成回复。
     * 最多重试 2 次，全部失败返回安全兜底回复。
     */
    private suspend fun regenerateWithStageGuard(
        aiService: com.kurisuapi.domain.service.AiService,
        originalMessages: List<com.kurisuapi.data.api.ChatMessage>,
        originalResponse: String,
        violation: StageViolation,
        stage: String,
        character: CharacterEntity
    ): String {
        var retryCount = 0
        val maxRetries = 2
        var currentMessages = originalMessages

        while (retryCount < maxRetries) {
            retryCount++
            android.util.Log.w("ChatVM",
                "阶段护栏重试 $retryCount/$maxRetries: stage=$stage violation=${violation.type}")

            // 构建违规纠正指令
            val correctionPrompt = buildCorrectionPrompt(stage, violation, retryCount)
            val correctedMessages = currentMessages.toMutableList()
            correctedMessages.add(com.kurisuapi.data.api.ChatMessage(
                role = "system",
                content = correctionPrompt
            ))

            try {
                val result = kotlinx.coroutines.withTimeout(30_000L) {
                    aiService.chat(correctedMessages)
                }
                if (result.success && result.content.isNotBlank()) {
                    val newResponse = result.content.trim()
                    // 再次检测
                    val recheck = chatOutputFilter.validateByStage(newResponse, stage, character.personality)
                    if (recheck == null) {
                        android.util.Log.i("ChatVM", "阶段护栏重试成功 (第${retryCount}次)")
                        return newResponse
                    }
                    android.util.Log.w("ChatVM",
                        "阶段护栏重试仍违规: type=${recheck.type} matched=${recheck.matchedText}")
                    // 把失败的回复和反馈加回对话，让下一轮重试看到历史
                    currentMessages = currentMessages.toMutableList().apply {
                        add(com.kurisuapi.data.api.ChatMessage(role = "assistant", content = newResponse))
                        add(com.kurisuapi.data.api.ChatMessage(role = "user",
                            content = "（你的回复仍包含${recheck.type}违规内容，请严格遵守${stage}阶段的界限，重新回复）"))
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ChatVM", "阶段护栏重试异常: ${e.message}")
            }
        }

        // 全部失败 → 安全兜底
        val fallback = getSafeFallback(stage)
        android.util.Log.w("ChatVM", "阶段护栏全部重试失败，使用兜底: $fallback")
        return fallback
    }

    /**
     * 构建违规纠正指令，随重试次数递增严格程度。
     */
    private fun buildCorrectionPrompt(stage: String, violation: StageViolation, retryCount: Int): String {
        val severity = if (retryCount >= 2) "极其严重" else "严重"
        val strictness = if (retryCount >= 2) {
            "这是最后一次机会。如果你再次违反，你的回复将被丢弃。请像一个真正的陌生人一样，用最简短的礼貌回复。只说一两个字即可。"
        } else {
            "请重新回复，严格遵守当前关系阶段的规则。"
        }

        return """
            |【${severity}违规警告】
            |你刚才的回复违反了当前关系阶段(${stage})的规则。
            |违规类型: ${violation.type}
            |匹配内容: "${violation.matchedText}"
            |
            |$strictness
            |
            |当前阶段(${stage})的核心要求：
            |${getStageCoreRules(stage)}
            |
            |请直接输出新的回复，不要解释、不要道歉、不要提刚才的违规。
        """.trimMargin()
    }

    /**
     * 阶段核心规则（简短版，用于纠正指令）。
     */
    private fun getStageCoreRules(stage: String): String = when (stage) {
        "初识" -> "你们是陌生人。只能用「你」称呼。严禁亲昵称呼、爱意表达、深度情感分享。保持礼貌距离。回复简短(1-2句)。"
        "探索" -> "你们是认识的熟人但还不是朋友。严禁「老公」「老婆」等极端亲密称呼，严禁告白和灵魂伴侣级承诺。"
        "深入" -> "你们是知己。可以使用亲昵称呼，但严禁极端占有欲发言和自毁式表达。"
        "融合" -> "你们非常亲密。可以撒娇、用专属称呼。无禁忌话题。"
        "羁绊" -> "你们是灵魂伴侣。无条件接纳。"
        else -> "请遵守当前阶段的行为规则。"
    }

    /**
     * 安全兜底回复（按阶段预设）。
     */
    private fun getSafeFallback(stage: String): String {
        val replies = when (stage) {
            "初识" -> listOf("嗯。", "好的。", "了解了。", "嗯嗯。")
            "探索" -> listOf("好的呀。", "嗯嗯，知道了。", "哦这样啊。", "嗯好。")
            "深入" -> listOf("嗯，我在呢。", "好的，我听着呢。", "嗯嗯，继续说吧。")
            "融合" -> listOf("嗯嗯，我在呢～", "好的！", "知道啦～")
            "羁绊" -> listOf("嗯，我在。", "好，我一直在。", "嗯嗯，永远都在。")
            else -> listOf("嗯。", "好的。", "了解了。")
        }
        return replies.random()
    }

    /**
     * 没有时才用 TokenEstimator 估算作为兜底。
     */
    private suspend fun recalculateContextUsage() {
        try {
            val sid = _sessionId.value
            if (sid <= 0) return
            val totalTokens = aiService.getActiveContextWindow()
            val modelDisplay = aiService.getActiveModelDisplay()
            val providerName = aiService.getActiveProviderName()
            val session = sessionRepository.getById(sid)
            val usedTokens = if (session != null && session.lastPromptTokens > 0) {
                // 有 API 返回的真实值，直接用，不走估算
                session.lastPromptTokens
            } else {
                // 兜底：首次发送消息前，还没有真实值，从 DB 估算
                val allMessages = chatHistoryRepository.getBySession(sid).firstOrNull() ?: emptyList()
                val msgTokens = if (allMessages.isNotEmpty()) {
                    TokenEstimator.estimateTokens(allMessages.map { it.content }, providerName)
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
                        TokenEstimator.estimateTokens(sysMessages.map { it.content }, providerName)
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
