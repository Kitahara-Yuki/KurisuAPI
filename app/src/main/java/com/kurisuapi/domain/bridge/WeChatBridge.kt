package com.kurisuapi.domain.bridge

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.kurisuapi.data.api.ChatMessage
import com.kurisuapi.data.entity.CharacterEntity
import com.kurisuapi.data.entity.ChatHistoryEntity
import com.kurisuapi.data.repository.CharacterRepository
import com.kurisuapi.data.repository.ChatHistoryRepository
import com.kurisuapi.data.repository.ConversationSessionRepository
import com.kurisuapi.data.repository.SettingsRepository
import com.kurisuapi.data.wechat.*
import com.kurisuapi.domain.engine.EmotionEngine
import com.kurisuapi.domain.engine.PromptBuilder
import com.kurisuapi.domain.engine.RelationshipEngine
import com.kurisuapi.domain.service.AiService
import com.kurisuapi.service.WeChatBotService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WeChatBridge @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val weChatApiService: WeChatApiService,
    private val weChatRepository: WeChatRepository,
    private val gson: Gson,
    private val settingsRepository: SettingsRepository,
    private val characterRepository: CharacterRepository,
    private val chatHistoryRepository: ChatHistoryRepository,
    private val sessionRepository: ConversationSessionRepository,
    private val emotionEngine: EmotionEngine,
    private val relationshipEngine: RelationshipEngine,
    private val promptBuilder: PromptBuilder,
    private val aiService: AiService,
    private val chatOutputFilter: com.kurisuapi.domain.engine.ChatOutputFilter,
    private val memoryExtractor: com.kurisuapi.domain.engine.MemoryExtractor,
    private val conversationSummarizer: com.kurisuapi.domain.engine.ConversationSummarizer
) : MessageBridge {

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<IncomingMessage>(extraBufferCapacity = 64)
    override val incomingMessages: SharedFlow<IncomingMessage> = _incomingMessages.asSharedFlow()

    private var pollJob: Job? = null
    // Bug fix: use a resettable scope so that destroy() + connect() works correctly.
    // Previously scope was a val tied to a SupervisorJob; cancelling that job
    // made the scope permanently dead, causing subsequent connect() calls to fail.
    @Volatile private var scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** 防止多条消息并行处理导致 AI 调用冲突 */
    private val replyMutex = Mutex()

    /** 主动消息追踪 */
    @Volatile private var lastUserMessageTime: Long = System.currentTimeMillis()
    @Volatile private var proactiveMessageSent: Boolean = false
    private var proactiveJob: Job? = null

    override suspend fun connect(): Result<Unit> = runCatching {
        if (!weChatRepository.isLoggedIn) {
            _connectionState.value = ConnectionState.DISCONNECTED
            throw IllegalStateException("未登录，请先扫码登录")
        }

        _connectionState.value = ConnectionState.CONNECTING

        // Try to get updates to verify session is valid
        startPolling()
    }

    override suspend fun disconnect() {
        pollJob?.cancel()
        pollJob = null
        proactiveJob?.cancel()
        proactiveJob = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    /**
     * 完全关闭协程作用域，在应用退出时调用。
     * 关闭后 scope 会被重建，允许后续 connect() 正常工作。
     */
    fun destroy() {
        pollJob?.cancel()
        pollJob = null
        proactiveJob?.cancel()
        proactiveJob = null
        scope.cancel()
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    }

    override suspend fun startLogin(): Result<QrCodeData> = runCatching {
        _connectionState.value = ConnectionState.WAITING_SCAN

        val rawJson = withContext(Dispatchers.IO) {
            weChatApiService.getQrCode()
        }

        // 安全解析 JSON
        val json = try {
            gson.fromJson(rawJson, JsonObject::class.java)
        } catch (e: Exception) {
            throw IllegalStateException("服务器返回了无法识别的数据，请稍后再试")
        }

        // 检查错误
        val errcode = json.get("errcode")?.takeIf { !it.isJsonNull }?.asInt
        val ret = json.get("ret")?.takeIf { !it.isJsonNull }?.asInt
        if (errcode != null && errcode != 0) {
            throw IllegalStateException("微信 API 错误: ${json.get("errmsg")?.asString ?: "未知"} ($errcode)")
        }
        if (ret != null && ret != 0) {
            throw IllegalStateException("微信 API 错误: ret=$ret")
        }

        // 兼容多种字段名和嵌套格式
        val qrcodeId = json.getString("qrcode", "qr_code", "data.qrcode", "data.qr_code")
            ?: throw IllegalStateException("API 返回的二维码数据为空，请稍后重试")

        // 优先查找图片数据（base64或URL），找不到则使用 qrcodeId 由客户端生成二维码
        val imageContent = json.getString(
            "qrcode_img_content",
            "data.qrcode_img_content"
        )
        val qrCodeUrl = json.getString(
            "qr_code_url", "url",
            "data.qr_code_url", "data.url"
        )

        // 优先使用图片数据 > 二维码标识符（客户端用 ZXing 生成）
        val displayContent = imageContent ?: qrcodeId

        QrCodeData(
            qrcode = qrcodeId,
            qrcodeImgContent = displayContent
        )
    }

    // 从 JsonObject 中按优先级查找多个可能的字段名（支持嵌套如 "data.qrcode"）
    private fun JsonObject.getString(vararg paths: String): String? {
        for (path in paths) {
            val parts = path.split(".")
            var current: JsonObject? = this
            var found = true
            for (i in 0 until parts.size - 1) {
                current = current?.get(parts[i])?.takeIf { it.isJsonObject }?.asJsonObject
                if (current == null) { found = false; break }
            }
            if (found) {
                val value = current?.get(parts.last())?.takeIf { !it.isJsonNull }?.asString
                if (!value.isNullOrBlank()) return value
            }
        }
        return null
    }

    override suspend fun checkLoginStatus(qrcode: String): Result<LoginStatus> = runCatching {
        val rawJson = withContext(Dispatchers.IO) {
            weChatApiService.checkQrCodeStatus(qrcode)
        }

        // 安全解析 JSON
        val json = try {
            gson.fromJson(rawJson, JsonObject::class.java)
        } catch (e: Exception) {
            throw IllegalStateException("服务器返回了无法识别的数据，请稍后再试")
        }

        // 检查错误码
        val errcode = json.get("errcode")?.takeIf { !it.isJsonNull }?.asInt
        val ret = json.get("ret")?.takeIf { !it.isJsonNull }?.asInt
        if (errcode != null && errcode != 0) {
            throw IllegalStateException("微信 API 错误: ${json.get("errmsg")?.asString ?: "未知"} ($errcode)")
        }
        if (ret != null && ret != 0) {
            throw IllegalStateException("微信 API 错误: ret=$ret")
        }

        // 兼容 status 为字符串或对象的情况
        val statusElement = json.get("status")
        val statusStr = when {
            statusElement == null || statusElement.isJsonNull -> ""
            statusElement.isJsonPrimitive -> statusElement.asString
            statusElement.isJsonObject -> {
                // status 是对象时，尝试读取 state/status 子字段
                val obj = statusElement.asJsonObject
                obj.get("state")?.asString ?: obj.get("status")?.asString ?: ""
            }
            else -> ""
        }

        val botToken = json.getString("bot_token", "data.bot_token")
        val baseurl = json.getString("baseurl", "data.baseurl")
        val botId = json.getString("ilink_bot_id", "data.ilink_bot_id")
        val userId = json.getString("ilink_user_id", "data.ilink_user_id")
        val redirectHost = json.getString("redirect_host", "data.redirect_host")

        when (statusStr) {
            "wait" -> LoginStatus.Waiting
            "scaned" -> {
                _connectionState.value = ConnectionState.SCANED
                LoginStatus.Scaned
            }
            "scaned_but_redirect" -> {
                if (!redirectHost.isNullOrBlank()) {
                    weChatRepository.baseUrl = "https://$redirectHost"
                }
                LoginStatus.Waiting
            }
            "confirmed" -> {
                if (botToken != null && botId != null && userId != null) {
                    weChatRepository.saveSession(botToken, botId, userId, baseurl)
                    _connectionState.value = ConnectionState.LOGGED_IN

                    LoginStatus.Confirmed(
                        botToken = botToken,
                        accountId = botId,
                        userId = userId,
                        baseUrl = baseurl
                    )
                } else {
                    LoginStatus.Error("登录返回数据不完整")
                }
            }
            "expired" -> LoginStatus.Expired
            else -> LoginStatus.Waiting
        }
    }

    override suspend fun sendMessage(peerId: String, content: String): Result<Unit> = runCatching {
        val contextToken = weChatRepository.contextToken
            ?: throw IllegalStateException("无 contextToken，可能未连接")
        val botToken = weChatRepository.botToken
            ?: throw IllegalStateException("无 botToken，可能未登录")

        val request = SendMessageRequest(
            msg = SendMessageBody(
                toUserId = peerId,
                contextToken = contextToken,
                messageType = MessageType.BOT,
                messageState = MessageState.FINISH,
                itemList = listOf(
                    MessageItemContent(
                        type = MessageItemType.TEXT,
                        textItem = TextItem(text = content)
                    )
                )
            )
        )

        withContext(Dispatchers.IO) {
            weChatApiService.sendMessage(
                request = request,
                uin = weChatRepository.generateUin(),
                authorization = "Bearer $botToken"
            )
        }
    }

    private fun startPolling() {
        pollJob?.cancel()
        proactiveJob?.cancel()
        pollJob = scope.launch {
            _connectionState.value = ConnectionState.POLLING
            var failures = 0

            while (isActive) {
                // Bug 4 fix: 检查登录状态，防止令牌失效后僵尸轮询
                if (!weChatRepository.isLoggedIn) {
                    delay(5_000)
                    continue
                }
                val botToken = weChatRepository.botToken
                if (botToken == null) {
                    delay(5_000)
                    continue
                }
                try {
                    val request = GetUpdatesRequest(
                        getUpdatesBuf = weChatRepository.updatesBuf
                    )

                    val response = weChatApiService.getUpdates(
                        request = request,
                        uin = weChatRepository.generateUin(),
                        authorization = "Bearer $botToken"
                    )

                    // Check for session expired
                    val isErr = (response.ret != 0) || (response.errCode != null && response.errCode != 0)
                    // -14 是已知的 token 过期码；连续失败过多也视为过期，覆盖未知过期码
                    val isSessionExpired = isErr && (response.errCode == -14 || response.ret == -14 || failures >= 10)

                    if (isSessionExpired) {
                        weChatRepository.clearSession()
                        _connectionState.value = ConnectionState.DISCONNECTED
                        // Bug 8 fix: 停止主动消息检查循环，避免协程泄漏
                        proactiveJob?.cancel()
                        proactiveJob = null
                        // 通知前台服务停止
                        appContext.sendBroadcast(Intent(WeChatBotService.ACTION_STOP))
                        break
                    }

                    if (isErr) {
                        failures++
                        if (failures >= 3) {
                            delay(30_000)
                        } else {
                            delay(2_000)
                        }
                        continue
                    }

                    failures = 0
                    _connectionState.value = ConnectionState.POLLING

                    // Save updates buffer
                    response.getUpdatesBuf?.let {
                        weChatRepository.updatesBuf = it
                    }

                    // Process messages
                    val hasMessages = !response.msgs.isNullOrEmpty()
                    response.msgs?.forEach { msg ->
                        msg.contextToken?.let {
                            weChatRepository.contextToken = it
                        }

                        val textContent = msg.itemList?.firstOrNull {
                            it.type == MessageItemType.TEXT
                        }?.textItem?.text

                        if (!textContent.isNullOrBlank()) {
                            val incoming = IncomingMessage(
                                messageId = msg.messageId ?: System.currentTimeMillis().toString(),
                                peerId = msg.fromUserId ?: "",
                                peerName = "",
                                content = textContent,
                                timestamp = msg.createTimeMs ?: System.currentTimeMillis(),
                                type = MessageItemType.TEXT,
                                contextToken = msg.contextToken
                            )

                            // 发射到 SharedFlow（供 ChatViewModel 等 UI 监听）
                            _incomingMessages.tryEmit(incoming)

                            // 记录用户消息时间，重置主动消息标记
                            lastUserMessageTime = System.currentTimeMillis()
                            proactiveMessageSent = false

                            // 自动回复：独立于 UI 运行
                            scope.launch {
                                try {
                                    handleAutoReply(incoming)
                                    // 回复完成后，检查是否需要触发记忆提取（不阻塞回复，独立运行）
                                    maybeExtractMemory()
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    Log.e("WeChatBridge", "自动回复失败", e)
                                }
                            }
                        }
                    }
                    // 无消息时加最小延迟，避免服务端快速返回空结果时疯狂打 API
                    if (!hasMessages) {
                        delay(1_000)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failures++
                    delay(if (failures >= 3) 30_000 else 2_000)
                }
            }
        }

        // 启动主动消息检查
        startProactiveCheck()
    }

    /**
     * 主动消息检查：定期检查是否需要发送主动消息
     */
    private fun startProactiveCheck() {
        proactiveJob?.cancel()
        proactiveJob = scope.launch {
            while (isActive) {
                try {
                    delay(60_000)  // 每分钟检查一次

                    // 检查是否启用主动消息
                    if (!settingsRepository.isBotProactiveEnabled()) continue

                    // 检查是否已登录且正在轮询
                    if (!weChatRepository.isLoggedIn) continue
                    if (_connectionState.value != ConnectionState.POLLING) continue

                    // 检查是否已经发过主动消息
                    if (proactiveMessageSent) continue

                    // 检查沉默时间是否超过阈值
                    val intervalMs = settingsRepository.getBotProactiveInterval() * 60_000L
                    val silenceDuration = System.currentTimeMillis() - lastUserMessageTime
                    if (silenceDuration < intervalMs) continue

                    // 触发主动消息
                    sendProactiveMessage()
                    // Bug 14 fix: proactiveMessageSent 在 try 内设置，若 sendProactiveMessage 抛异常则保持 false，下次继续重试
                    proactiveMessageSent = true

                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e("WeChatBridge", "主动消息检查异常", e)
                    delay(60_000)
                }
            }
        }
    }

    /**
     * 生成并发送主动消息：根据角色性格和当前情绪生成自然的主动消息
     */
    private suspend fun sendProactiveMessage() {
        val botToken = weChatRepository.botToken ?: return
        // 获取活跃角色
        var activeCharacterId = settingsRepository.getActiveCharacterId()
        var character = if (activeCharacterId != null && activeCharacterId > 0) {
            characterRepository.getById(activeCharacterId)
        } else null

        if (character == null) {
            val allCharacters = characterRepository.getAllOnce()
            if (allCharacters.isNotEmpty()) {
                character = allCharacters.first()
            } else return
        }

        // 获取情绪状态
        val emotion = emotionEngine.getEmotion(character.id)

        // 计算沉默时长
        val silenceMinutes = ((System.currentTimeMillis() - lastUserMessageTime) / 60_000).toInt()

        // 构建主动消息的 prompt
        val proactivePrompt = buildProactivePrompt(character, emotion, silenceMinutes)

        replyMutex.withLock {
            // 显示"正在输入"
            val typingTicket = try {
                val configResponse = weChatApiService.getConfig(
                    request = GetConfigRequest(
                        ilinkUserId = weChatRepository.userId ?: "",
                        contextToken = weChatRepository.contextToken ?: ""
                    ),
                    uin = weChatRepository.generateUin(),
                    authorization = "Bearer $botToken"
                )
                configResponse.typingTicket
            } catch (e: Exception) {
                Log.w("WeChatBridge", "获取 typingTicket 失败", e)
                null
            }

            try {
                if (typingTicket != null) {
                    weChatApiService.sendTyping(
                        request = SendTypingRequest(
                            typingTicket = typingTicket,
                            status = 1,
                            ilinkUserId = weChatRepository.userId ?: ""
                        ),
                        uin = weChatRepository.generateUin(),
                        authorization = "Bearer $botToken"
                    )
                }

                // 调用 AI 生成主动消息
                val response = aiService.chat(listOf(
                    ChatMessage(role = "system", content = proactivePrompt),
                    ChatMessage(role = "user", content = "[系统提示：你已经很久没有收到对方的消息了，请根据你的角色设定和当前情绪，发一条自然的主动消息。只输出消息内容，不要加任何前缀或解释。]")
                ))

                if (response.success && response.content.isNotBlank()) {
                    // 保存到聊天记录（含思考过程），优先使用绑定的机器人会话
                    val sessionId = resolveBotSessionId(character.id)
                    chatHistoryRepository.insert(
                        ChatHistoryEntity(
                            characterId = character.id,
                            sessionId = sessionId,
                            sender = "ai",
                            content = response.content,
                            reasoningContent = response.reasoningContent
                        )
                    )

                    // 发送到微信（拆分为多条消息）
                    val peerId = weChatRepository.userId ?: ""
                    if (peerId.isNotBlank()) {
                        val parts = splitReplyMessages(response.content)
                        for ((index, part) in parts.withIndex()) {
                            val sendResult = sendMessage(peerId, part)
                            if (sendResult.isFailure) {
                                Log.w("WeChatBridge", "主动消息发送失败: ${sendResult.exceptionOrNull()?.message}")
                                break
                            }
                            if (index < parts.size - 1) {
                                val typingDelay = (part.length * 80L + 500L).coerceIn(800L, 3000L)
                                delay(typingDelay)
                            }
                        }
                    }

                    Log.i("WeChatBridge", "主动消息已发送: ${response.content}")
                }
            } finally {
                if (typingTicket != null) {
                    try {
                        weChatApiService.sendTyping(
                            request = SendTypingRequest(
                                typingTicket = typingTicket,
                                status = 2,
                                ilinkUserId = weChatRepository.userId ?: ""
                            ),
                            uin = weChatRepository.generateUin(),
                            authorization = "Bearer $botToken"
                        )
                    } catch (e: Exception) {
                        Log.w("WeChatBridge", "停止typing失败", e)
                    }
                }
            }
        }
    }

    /**
     * 构建主动消息的 prompt：让 AI 根据角色性格和沉默时长生成自然的主动消息
     */
    private fun buildProactivePrompt(
        character: CharacterEntity,
        emotion: com.kurisuapi.data.entity.EmotionStateEntity,
        silenceMinutes: Int
    ): String {
        return buildString {
            appendLine("请完全扮演${character.name}这个角色。")
            if (character.personality.isNotBlank()) appendLine("性格: ${character.personality}")
            if (character.appearance.isNotBlank()) appendLine("外观: ${character.appearance}")
            if (character.speakingStyle.isNotBlank()) appendLine("说话风格: ${character.speakingStyle}")
            appendLine()
            appendLine("## 当前状态")
            appendLine("开心: ${emotion.happy}/100, 难过: ${emotion.sad}/100, 生气: ${emotion.angry}/100, 孤独: ${emotion.lonely}/100, 好感: ${emotion.affection}/100")
            appendLine("对方已经 ${silenceMinutes} 分钟没有回复了。")
            appendLine()
            appendLine("## 要求")
            appendLine("- 发一条自然的主动消息，像真人在微信上找人聊天一样")
            appendLine("- 根据当前情绪决定说什么：孤独时可能想找人聊天，开心时可能想分享，难过时可能想要安慰")
            appendLine("- 不要说\"你好久没回我了\"这种直接提及沉默的话，要自然地开启话题")
            appendLine("- 回复要简短，1-2句话，像微信聊天的节奏")
            appendLine("- 不要加任何前缀、解释或括号描述")
        }
    }

    /**
     * 处理自动回复：读取活跃角色 → 保存用户消息 → 更新情绪/关系 → 构建 prompt → 调用 AI → 保存回复 → 发送回复
     */
    private suspend fun handleAutoReply(incoming: IncomingMessage) {
        val botToken = weChatRepository.botToken ?: return
        var aiSuccess = false
        var filteredContent = ""
        var outTypingTicket: String? = null

        replyMutex.withLock {
            // 1. 获取活跃角色
            var activeCharacterId = settingsRepository.getActiveCharacterId()
            var character = if (activeCharacterId != null && activeCharacterId > 0) {
                characterRepository.getById(activeCharacterId)
            } else null

            // 如果未设置活跃角色，自动使用第一个可用角色
            if (character == null) {
                val allCharacters = characterRepository.getAllOnce()
                if (allCharacters.isNotEmpty()) {
                    character = allCharacters.first()
                    // 自动设置为活跃角色，后续消息不再需要查找
                    settingsRepository.setValue(SettingsRepository.KEY_ACTIVE_CHARACTER, character.id.toString())
                    Log.i("WeChatBridge", "未设置活跃角色，已自动选择: ${character.name}")
                } else {
                    Log.w("WeChatBridge", "没有任何角色，无法自动回复。请先在 App 中创建一个角色")
                    return
                }
            }

            // 2. 保存用户消息到聊天记录（优先使用绑定的机器人会话）
            val sessionId = resolveBotSessionId(character.id)
            chatHistoryRepository.insert(
                ChatHistoryEntity(
                    characterId = character.id,
                    sessionId = sessionId,
                    sender = "user",
                    content = incoming.content
                )
            )

            // 3. 并行更新情绪和关系（supervisorScope + try-catch：任一引擎失败不阻断回复）
            val (emotionResult, relationshipResult) = supervisorScope {
                val emotionDeferred = async {
                    try {
                        emotionEngine.updateEmotion(character.id, incoming.content, character.personality)
                        emotionEngine.getEmotion(character.id)
                    } catch (e: Exception) { null }
                }
                val relationshipDeferred = async {
                    try {
                        relationshipEngine.updateRelationship(character.id, incoming.content, character.personality)
                        relationshipEngine.getRelationship(character.id)
                    } catch (e: Exception) { null }
                }
                Pair(emotionDeferred.await(), relationshipDeferred.await())
            }

            // 4. 构建 Prompt（仅当前绑定会话的上下文 + 相关性记忆匹配）
            // 引擎失败时使用默认情绪/关系值，不阻断聊天
            // 聊天模式从 session 实体读取，每个对话独立
            val ctxWindow = aiService.getActiveContextWindow()
            val chatMode = sessionRepository.getById(sessionId)?.chatMode
                ?: com.kurisuapi.data.entity.ConversationSessionEntity.CHAT_MODE_CHAT
            val promptMessages = promptBuilder.buildMessages(
                character = character,
                emotion = emotionResult ?: com.kurisuapi.data.entity.EmotionStateEntity(characterId = character.id),
                relationship = relationshipResult ?: com.kurisuapi.data.entity.RelationshipEntity(characterId = character.id),
                sessionId = sessionId,
                userMessage = incoming.content,
                contextWindow = ctxWindow,
                chatMode = chatMode,
                thinkingEnabled = aiService.isActiveProviderThinkingEnabled()
            )

            // 5. 显示"对方正在输入..."
            val typingTicket = try {
                val configResponse = weChatApiService.getConfig(
                    request = GetConfigRequest(
                        ilinkUserId = weChatRepository.userId ?: "",
                        contextToken = weChatRepository.contextToken ?: ""
                    ),
                    uin = weChatRepository.generateUin(),
                    authorization = "Bearer $botToken"
                )
                configResponse.typingTicket
            } catch (e: Exception) {
                Log.w("WeChatBridge", "获取 typingTicket 失败", e)
                null
            }

            try {
                if (typingTicket != null) {
                    weChatApiService.sendTyping(
                        request = SendTypingRequest(
                            typingTicket = typingTicket,
                            status = 1,  // 1=正在输入
                            ilinkUserId = weChatRepository.userId ?: ""
                        ),
                        uin = weChatRepository.generateUin(),
                        authorization = "Bearer $botToken"
                    )
                }

                // 6. 调用 AI
                val response = aiService.chat(promptMessages)

                if (response.success && response.content.isNotBlank()) {
                    // 7. 如果有思考过程且用户开启显示，先发送思考内容
                    val showThinking = settingsRepository.isBotShowThinkingEnabled()
                    if (showThinking && response.reasoningContent.isNotBlank()) {
                        val thinkingMsg = buildThinkingMessage(response.reasoningContent)
                        sendMessage(incoming.peerId, thinkingMsg)
                        // 短暂延迟，模拟真人在"思考"然后回复
                        delay(500)
                    }

                    // 8. 保存 AI 回复到聊天记录
                    chatHistoryRepository.insert(
                        ChatHistoryEntity(
                            characterId = character.id,
                            sessionId = sessionId,
                            sender = "ai",
                            content = response.content,
                            reasoningContent = response.reasoningContent
                        )
                    )

                    // 存储结果供 mutex 外发送（剧情模式：强制空行分隔）
                    aiSuccess = true
                    filteredContent = if (chatMode == "story") {
                        chatOutputFilter.formatStory(response.content)
                    } else {
                        response.content
                    }
                    outTypingTicket = typingTicket

                    // 8.5. 后台触发对话摘要（不阻塞主流程）
                    scope.launch {
                        try {
                            conversationSummarizer.summarizeIfNeeded(sessionId)
                        } catch (e: Exception) {
                            Log.e("WeChatBridge", "摘要任务异常", e)
                        }
                    }

                } else {
                    Log.w("WeChatBridge", "AI 回复为空或失败: ${response.errorMessage}")
                    outTypingTicket = typingTicket
                }
            } catch (e: Exception) {
                Log.e("WeChatBridge", "AI 调用异常", e)
                outTypingTicket = typingTicket
            }
        } // replyMutex 释放——消息发送不持锁

        // 9. 拆分回复为多条消息，在 mutex 外发送
        if (aiSuccess && filteredContent.isNotBlank()) {
            val parts = splitReplyMessages(filteredContent)
            for ((index, part) in parts.withIndex()) {
                val sendResult = sendMessage(incoming.peerId, part)
                if (sendResult.isFailure) {
                    Log.e("WeChatBridge", "发送微信回复失败", sendResult.exceptionOrNull())
                    break
                }
                if (index < parts.size - 1) {
                    val typingDelay = (part.length * 80L + 500L).coerceIn(800L, 3000L)
                    delay(typingDelay)
                    if (outTypingTicket != null) {
                        try {
                            weChatApiService.sendTyping(
                                request = SendTypingRequest(
                                    typingTicket = outTypingTicket,
                                    status = 1,
                                    ilinkUserId = weChatRepository.userId ?: ""
                                ),
                                uin = weChatRepository.generateUin(),
                                authorization = "Bearer $botToken"
                            )
                        } catch (e: Exception) {
                            Log.w("WeChatBridge", "发送typing状态失败", e)  // B30 fix: 原来是"停止typing失败"，日志文案修正
                        }
                    }
                }
            }
        }

        // 10. 停止"正在输入"状态，在 mutex 外
        if (outTypingTicket != null) {
            try {
                weChatApiService.sendTyping(
                    request = SendTypingRequest(
                        typingTicket = outTypingTicket,
                        status = 2,
                        ilinkUserId = weChatRepository.userId ?: ""
                    ),
                    uin = weChatRepository.generateUin(),
                    authorization = "Bearer $botToken"
                )
            } catch (e: Exception) {
                Log.w("WeChatBridge", "停止 typing 状态失败", e)
            }
        }
    }

    /**
     * 解析机器人回复的目标会话 ID。
     * 优先使用用户在对话列表中绑定的会话，未绑定时回退到自动活跃会话。
     */
    private suspend fun resolveBotSessionId(characterId: Long): Long {
        val boundSessionId = settingsRepository.getBotSessionId(characterId)
        if (boundSessionId != null && boundSessionId > 0) {
            // 验证绑定会话仍然存在且未归档
            val session = sessionRepository.getById(boundSessionId)
            if (session != null && !session.isArchived) return boundSessionId
            // 会话已删除或已归档，清除绑定
            settingsRepository.setBotSessionId(characterId, null)
        }
        return sessionRepository.getOrCreateActiveSession(characterId)
    }

    /**
     * 格式化思考过程消息，用于在微信中发送。
     * 超长内容会截断并加提示。
     */
    private fun buildThinkingMessage(reasoning: String): String {
        val maxLen = 300
        val display = if (reasoning.length > maxLen) {
            reasoning.take(maxLen) + "...\n（思考过程过长，已截断）"
        } else {
            reasoning
        }
        return "💭 思考过程：\n$display"
    }

    /**
     * 检查是否需要触发记忆提取：累计新消息数达到阈值且开关开启时，后台提取。
     * 失败静默，不影响聊天。
     */
    private suspend fun maybeExtractMemory() {
        try {
            if (!settingsRepository.isAutoMemoryEnabled()) return

            val characterId = settingsRepository.getActiveCharacterId() ?: return
            if (characterId <= 0) return

            val total = chatHistoryRepository.countByCharacter(characterId)
            val lastExtract = settingsRepository.getLastExtractCount(characterId)
            val interval = settingsRepository.getMemoryInterval()

            if (total - lastExtract >= interval) {
                val sid = resolveBotSessionId(characterId)
                val ok = memoryExtractor.extractAndStore(characterId, sid)
                if (ok) {
                    settingsRepository.setLastExtractCount(characterId, total)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("WeChatBridge", "记忆提取检查失败", e)
        }
    }

    // Bug fix: cache regex patterns (created on every call before) as companion constants
    companion object {
        private val SENTENCE_ENDERS = Regex("(?<=[。！？～!?~.])")
        private val SUB_SENTENCE_SPLIT = Regex("(?<=[，,;；])")
    }

    /**
     * 将 AI 回复拆分为多条短消息，模拟真人逐条发送。
     *
     * 拆分规则：
     * 1. 按换行符拆分
     * 2. 按中文/英文句号、感叹号、问号拆分（保留标点）
     * 3. 短消息合并（少于 5 字的合并到前一条）
     * 4. 超长消息按 80 字截断
     */
    private fun splitReplyMessages(text: String): List<String> {
        if (text.isBlank()) return listOf(text)

        // 第一步：按换行符拆分
        val lines = text.split("\n").filter { it.isNotBlank() }

        // 第二步：对每一行按句末标点进一步拆分
        val rawParts = mutableListOf<String>()
        for (line in lines) {
            val sentences = line.split(SENTENCE_ENDERS).filter { it.isNotBlank() }
            rawParts.addAll(sentences)
        }

        if (rawParts.isEmpty()) return listOf(text.trim())

        // 第三步：合并过短的消息（少于 5 字）到前一条
        val merged = mutableListOf<String>()
        for (part in rawParts) {
            val trimmed = part.trim()
            if (trimmed.isEmpty()) continue
            if (merged.isNotEmpty() && trimmed.length < 5) {
                merged[merged.size - 1] = merged.last() + trimmed
            } else {
                merged.add(trimmed)
            }
        }

        // 第四步：超长消息截断
        val result = mutableListOf<String>()
        for (part in merged) {
            if (part.length <= 80) {
                result.add(part)
            } else {
                // 按逗号、分号等次级标点截断
                val subParts = part.split(SUB_SENTENCE_SPLIT).filter { it.isNotBlank() }
                val buffer = StringBuilder()
                for (sub in subParts) {
                    if (buffer.length + sub.length > 80 && buffer.isNotEmpty()) {
                        result.add(buffer.toString().trim())
                        buffer.clear()
                    }
                    buffer.append(sub)
                }
                if (buffer.isNotEmpty()) {
                    result.add(buffer.toString().trim())
                }
            }
        }

        return result.ifEmpty { listOf(text.trim()) }
    }

    fun isLoggedIn(): Boolean = weChatRepository.isLoggedIn
}
