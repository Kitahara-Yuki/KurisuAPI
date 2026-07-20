package com.kurisuapi.ui.screen.chat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import com.kurisuapi.ui.theme.LocalIsDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kurisuapi.data.entity.ChatHistoryEntity
import com.kurisuapi.ui.component.BlurOverlayImage
import com.kurisuapi.ui.component.ChatBubble
import com.kurisuapi.ui.component.LiquidGlassContainer
import com.kurisuapi.ui.theme.LocalActiveTheme
import com.kurisuapi.util.parseColor
import com.kurisuapi.ui.viewmodel.ContextUsage
import com.kurisuapi.ui.viewmodel.ChatViewModel
import com.kurisuapi.util.TokenEstimator
import com.kurisuapi.util.sdp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatLogScreen(
    sessionId: Long,
    onNavigateBack: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    // 每次从设置页返回时刷新上下文额度显示
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshContextUsage()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val character by viewModel.activeCharacter.collectAsState()
    val session by viewModel.session.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val typewriterText by viewModel.typewriterText.collectAsState()
    val isStreaming by viewModel.isStreaming.collectAsState()
    val thinkingSeconds by viewModel.thinkingSeconds.collectAsState()
    val error by viewModel.error.collectAsState()
    var inputText by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    val isArchived = session?.isArchived == true
    val usage by viewModel.contextUsage.collectAsState()
    var showContextDialog by remember { mutableStateOf(false) }
    var showWarningDialog by remember { mutableStateOf(false) }
    val warnThreshold = if (usage.thinkingEnabled) 0.70f else 0.80f
    val contextOverWarn = usage.totalTokens > 0 && usage.usedTokens > usage.totalTokens * warnThreshold

    LaunchedEffect(sessionId) {
        viewModel.loadSession(sessionId)
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty() && typewriterText.isBlank()) {
            listState.scrollToItem(messages.size - 1)
        }
    }

    // 流式/打字机期间自动跟随滚动
    if (isStreaming || typewriterText.isNotBlank()) {
        val autoScrollEnabled by remember { mutableStateOf(true) }
        LaunchedEffect(autoScrollEnabled) {
            snapshotFlow {
                val info = listState.layoutInfo
                val last = info.visibleItemsInfo.lastOrNull()
                if (last != null) info.viewportEndOffset - last.offset - last.size else 999
            }.collect { distToBottom ->
                if (distToBottom < 200) {
                    val total = listState.layoutInfo.totalItemsCount
                    if (total > 0) listState.scrollToItem(total - 1)
                }
            }
        }
    }

    val isDark = LocalIsDarkTheme.current
    val theme = LocalActiveTheme.current
    val hasChatBgImage = !theme?.chatBgImagePath.isNullOrBlank()
    val chatBackground = theme?.chatBgColorHex?.takeIf { it.isNotBlank() }?.parseColor()
        ?: if (isDark) Color(0xFF393C42) else Color(0xFFFFFFFF)  // 浅色模式纯白背景

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            // 始终用背景颜色打底，图片（如果有）作为装饰层覆盖在上方
            containerColor = chatBackground,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            session?.title?.ifBlank { character?.name ?: "聊天" } ?: (character?.name ?: "聊天"),
                            fontWeight = FontWeight.SemiBold
                        )
                        if (isArchived) {
                            Spacer(modifier = Modifier.width(sdp(8.dp)))
                            SuggestionChip(
                                onClick = {},
                                label = { Text("已归档", style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.height(sdp(24.dp))
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (contextOverWarn) {
                        IconButton(onClick = { showWarningDialog = true }) {
                            Text("❗", fontSize = MaterialTheme.typography.titleMedium.fontSize,
                                modifier = Modifier.size(sdp(20.dp)))
                        }
                    }
                    IconButton(onClick = { showContextDialog = true }) {
                        CircularContextRing(
                            usage = usage,
                            thinkingEnabled = usage.thinkingEnabled,
                            modifier = Modifier.size(sdp(32.dp))
                        )
                    }
                },
                colors = com.kurisuapi.ui.theme.topBarColors()
            )
        }
    ) { paddingValues ->
        LiquidGlassContainer(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            glassModifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = sdp(12.dp), vertical = sdp(8.dp)),
            bottomSpacing = sdp(26.dp),
            background = {
                // 自定义主题背景图片（颜色通过 Scaffold containerColor 始终打底）
                if (hasChatBgImage) {
                    BlurOverlayImage(
                        imagePath = theme!!.chatBgImagePath,
                        blurRadius = theme.chatBgBlurRadius,
                        dimPercent = theme.chatBgDimPercent,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                error?.let { err ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(sdp(8.dp)),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Row(modifier = Modifier.padding(sdp(12.dp)), verticalAlignment = Alignment.CenterVertically) {
                            Text(err, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer)
                            TextButton(onClick = { viewModel.clearError() }) { Text("关闭") }
                        }
                    }
                }

                // 打字机运行时隐藏最后一条 AI 消息（正在被逐字动画显示，避免重复）
                // 注意：caughtUp 必须在 derivedStateOf 内部计算，不能从外部捕获普通变量，
                // 否则闭包捕获的是首次组合时的过期值，导致消息错误地被 dropLast 掉。
                val typewriterCaughtUp by remember {
                    derivedStateOf {
                        val lastAiMsg = messages.lastOrNull()?.takeIf { it.sender == "ai" }
                        lastAiMsg != null &&
                            typewriterText.isNotBlank() &&
                            typewriterText == lastAiMsg.content
                    }
                }

                val displayMessages by remember {
                    derivedStateOf {
                        if (!typewriterCaughtUp && typewriterText.isNotBlank()
                            && messages.isNotEmpty() && messages.last().sender == "ai") {
                            messages.dropLast(1)
                        } else messages
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = sdp(16.dp)),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(sdp(8.dp)),
                    contentPadding = PaddingValues(top = sdp(8.dp), bottom = sdp(106.dp))
                ) {
                    items(displayMessages, key = { it.id }) { message ->
                        ChatBubble(message = message, modifier = Modifier)
                    }
                    if (displayMessages.isEmpty() && !isLoading) {
                        item(key = "empty_guide") {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(top = sdp(80.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "你好，想聊聊吗？",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                )
                            }
                        }
                    }
                    if (!typewriterCaughtUp && typewriterText.isNotBlank()) {
                        // key 固定即可：打字机动画由 ViewModel.typewriterText 驱动，
                        // 不依赖 Composable 的 remember 状态，key 变化也不会丢失进度。
                        item(key = "streaming") {
                            SmoothStreamingBubble(
                                fullText = typewriterText,
                                characterId = character?.id ?: 0,
                                sessionId = sessionId
                            )
                        }
                    } else if (!typewriterCaughtUp && isLoading && typewriterText.isBlank()) {
                        item {
                            Row(modifier = Modifier.padding(sdp(8.dp))) {
                                CircularProgressIndicator(modifier = Modifier.size(sdp(20.dp)), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(sdp(8.dp)))
                                Text(
                                    if (thinkingSeconds > 0) "思考中... ${thinkingSeconds}s" else "思考中...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            },
            glass = {
                if (isArchived) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = sdp(16.dp), vertical = sdp(12.dp)),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Outlined.Lock,
                            contentDescription = "已归档",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            modifier = Modifier.size(sdp(16.dp))
                        )
                        Spacer(modifier = Modifier.width(sdp(6.dp)))
                        Text(
                            "对话已归档，不能发送消息",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = sdp(12.dp), vertical = sdp(4.dp)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            modifier = Modifier.weight(1f),
                            placeholder = {
                                Text(
                                    "输入消息...",
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                )
                            },
                            maxLines = 3,
                            enabled = !isLoading,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                disabledBorderColor = Color.Transparent,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                cursorColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        Spacer(modifier = Modifier.width(sdp(8.dp)))
                        Box(
                            modifier = Modifier
                                .size(sdp(36.dp))
                                .clip(CircleShape)
                                .background(
                                    if (inputText.isNotBlank() && !isLoading)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                )
                                .clickable(
                                    enabled = inputText.isNotBlank() && !isLoading,
                                    onClick = {
                                        if (inputText.isNotBlank()) {
                                            viewModel.sendMessage(inputText.trim())
                                            inputText = ""
                                        }
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Outlined.Send,
                                contentDescription = "发送",
                                tint = if (inputText.isNotBlank() && !isLoading)
                                    MaterialTheme.colorScheme.onPrimary
                                else
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                modifier = Modifier.size(sdp(18.dp))
                            )
                        }
                    }
                }
            }
        )
    }

    if (showWarningDialog) {
        AlertDialog(
            onDismissRequest = { showWarningDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            textContentColor = MaterialTheme.colorScheme.onSurface,
            icon = { Icon(Icons.Outlined.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("上下文即将用完") },
            text = {
                val limitText = if (usage.thinkingEnabled) "80%" else "90%"
                Text("当前上下文用量即将超过${limitText}。可能导致AI出现幻觉与记忆衰退，建议开启新对话或在设置中调整上下文长度")
            },
            confirmButton = { TextButton(onClick = { showWarningDialog = false }) { Text("知道了") } }
        )
    }

    if (showContextDialog) {
        val hasTotal = usage.totalTokens > 0
        val ratio = if (hasTotal) (usage.usedTokens.toFloat() / usage.totalTokens).coerceIn(0f, 1f) else 0f
        val yellowAt = if (usage.thinkingEnabled) 0.60f else 0.70f
        val redAt = if (usage.thinkingEnabled) 0.80f else 0.90f
        val barColor = when {
            !hasTotal -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            ratio > redAt -> MaterialTheme.colorScheme.error
            ratio > yellowAt -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.primary
        }
        AlertDialog(
            onDismissRequest = { showContextDialog = false },
            title = { Text("上下文额度") },
            containerColor = MaterialTheme.colorScheme.surface,
            textContentColor = MaterialTheme.colorScheme.onSurface,
            text = {
                Column {
                    if (usage.modelDisplay.isNotBlank()) {
                        Text(
                            text = usage.modelDisplay,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(sdp(12.dp)))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (hasTotal) {
                                "${TokenEstimator.formatTokenCount(usage.usedTokens.toLong())} / ${
                                    TokenEstimator.formatTokenCount(usage.totalTokens)
                                }"
                            } else {
                                TokenEstimator.formatTokenCount(usage.usedTokens.toLong())
                            },
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (hasTotal) {
                            Text(
                                text = "${"%.2f".format(ratio * 100)}%",
                                style = MaterialTheme.typography.titleMedium,
                                color = barColor
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(sdp(8.dp)))
                    if (hasTotal) {
                        val reserved = usage.totalTokens / 10
                        val available = usage.totalTokens - reserved
                        Column {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("预留 (AI 回复)", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                Text(TokenEstimator.formatTokenCount(reserved), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("可用 (你的消息)", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                Text(TokenEstimator.formatTokenCount(available), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            }
                        }
                        Spacer(modifier = Modifier.height(sdp(8.dp)))
                    }
                    LinearProgressIndicator(
                        progress = { if (hasTotal) ratio else 0f },
                        modifier = Modifier.fillMaxWidth().height(sdp(8.dp)).clip(RoundedCornerShape(sdp(4.dp))),
                        color = barColor,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(sdp(8.dp)))
                    Text(
                        text = if (hasTotal) "当前已使用 ${"%.2f".format(ratio * 100)}% 的上下文窗口"
                               else "未设置上下文窗口上限",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showContextDialog = false }) { Text("关闭") } }
        )
    }
    } // Box closing
}

@Composable
private fun SmoothStreamingBubble(fullText: String, characterId: Long, sessionId: Long) {
    // 打字机节奏由 ChatViewModel.typewriterText 控制，此处直接渲染
    ChatBubble(
        message = ChatHistoryEntity(
            id = -1, characterId = characterId, sessionId = sessionId,
            sender = "ai", content = fullText
        )
    )
}

@Composable
private fun CircularContextRing(usage: ContextUsage, thinkingEnabled: Boolean = false, modifier: Modifier = Modifier) {
    val hasTotal = usage.totalTokens > 0
    val ratio = if (hasTotal) (usage.usedTokens.toFloat() / usage.totalTokens).coerceIn(0f, 1f) else 0f
    val yellowAt = if (thinkingEnabled) 0.60f else 0.70f
    val redAt = if (thinkingEnabled) 0.80f else 0.90f
    // 固定颜色：不受主题色影响，确保在任何顶栏背景下都可见
    val progressColor = when {
        !hasTotal -> Color.White.copy(alpha = 0.4f)
        ratio > redAt -> Color(0xFFFF453A)   // 红色警告
        ratio > yellowAt -> Color(0xFFFFD60A) // 黄色注意
        else -> Color.White                   // 白色正常
    }
    val trackColor = Color.White.copy(alpha = 0.25f)
    val percentageText = if (hasTotal) "${(ratio * 100).toInt()}" else "—"

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 3.dp.toPx()
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
            drawArc(color = trackColor, startAngle = -90f, sweepAngle = 360f,
                useCenter = false, topLeft = topLeft, size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
            if (hasTotal && ratio > 0f) {
                drawArc(color = progressColor, startAngle = -90f, sweepAngle = 360f * ratio,
                    useCenter = false, topLeft = topLeft, size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
            }
        }
        Text(text = percentageText, style = MaterialTheme.typography.labelSmall, color = progressColor)
    }
}

// ---------- 预览 ----------
@Preview(showBackground = true)
@Composable
private fun CircularContextRingPreview() {
    MaterialTheme {
        Row(
            modifier = Modifier.padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularContextRing(
                usage = ContextUsage(usedTokens = 4000, totalTokens = 10000),
                thinkingEnabled = false,
                modifier = Modifier.size(48.dp)
            )
            CircularContextRing(
                usage = ContextUsage(usedTokens = 8000, totalTokens = 10000),
                thinkingEnabled = false,
                modifier = Modifier.size(48.dp)
            )
            CircularContextRing(
                usage = ContextUsage(usedTokens = 0, totalTokens = 10000),
                thinkingEnabled = false,
                modifier = Modifier.size(48.dp)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ChatBubblePreview() {
    MaterialTheme {
        LiquidGlassContainer(
            modifier = Modifier.fillMaxWidth().height(500.dp),
            background = {
                // 液态玻璃背景
            },
            glass = {
                Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                    ChatBubble(
                        message = ChatHistoryEntity(
                            characterId = 1, sessionId = 1,
                            sender = "user", content = "你好！今天天气真好。"
                        )
                    )
                    Spacer(Modifier.height(8.dp))
                    ChatBubble(
                        message = ChatHistoryEntity(
                            characterId = 1, sessionId = 1,
                            sender = "ai", content = "是的！阳光明媚，适合出去走走。要不要一起去公园？"
                        )
                    )
                    Spacer(Modifier.height(8.dp))
                    ChatBubble(
                        message = ChatHistoryEntity(
                            characterId = 1, sessionId = 1,
                            sender = "user", content = "好呀，几点出发？"
                        )
                    )
                }
            }
        )
    }
}
