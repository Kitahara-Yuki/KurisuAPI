package com.kurisuapi.ui.screen.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kurisuapi.data.entity.ChatHistoryEntity
import com.kurisuapi.ui.component.ChatBubble
import com.kurisuapi.ui.viewmodel.ContextUsage
import com.kurisuapi.ui.viewmodel.ChatViewModel
import com.kurisuapi.util.TokenEstimator
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import com.kurisuapi.util.sdp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatLogScreen(
    sessionId: Long,
    onNavigateBack: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val character by viewModel.activeCharacter.collectAsState()
    val session by viewModel.session.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val streamingText by viewModel.streamingText.collectAsState()
    val isStreaming by viewModel.isStreaming.collectAsState()
    val thinkingSeconds by viewModel.thinkingSeconds.collectAsState()
    val error by viewModel.error.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val hazeState = remember { HazeState() }
    val darkTheme = isSystemInDarkTheme()

    val isArchived = session?.isArchived == true
    val usage by viewModel.contextUsage.collectAsState()
    var showContextDialog by remember { mutableStateOf(false) }
    var showWarningDialog by remember { mutableStateOf(false) }
    val warnThreshold = if (usage.thinkingEnabled) 0.70f else 0.80f
    val contextOverWarn = usage.totalTokens > 0 && usage.usedTokens > usage.totalTokens * warnThreshold

    val lastAiMessage = messages.lastOrNull { it.sender == "ai" }
    val streamingAlreadyInDb = lastAiMessage != null && lastAiMessage.content == streamingText

    LaunchedEffect(sessionId) {
        viewModel.loadSession(sessionId)
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty() && !isStreaming) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    LaunchedEffect(isStreaming) {
        if (isStreaming) {
            while (isActive) {
                delay(100)
                val layoutInfo = listState.layoutInfo
                val totalItems = layoutInfo.totalItemsCount
                if (totalItems == 0) continue
                val lastView = layoutInfo.visibleItemsInfo.lastOrNull() ?: continue
                val distToBottom = layoutInfo.viewportEndOffset - lastView.offset - lastView.size
                val isNearBottom = distToBottom < 200
                if (isNearBottom) {
                    listState.scrollToItem(totalItems - 1)
                }
            }
        }
    }

    Scaffold(
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 背景层：消息列表铺满全屏，延伸到输入框背后（作为 Haze 模糊源）
            Column(modifier = Modifier.fillMaxSize().hazeSource(state = hazeState)) {
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

                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = sdp(16.dp)),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(sdp(8.dp)),
                    contentPadding = PaddingValues(top = sdp(8.dp), bottom = sdp(80.dp))
                ) {
                    items(messages, key = { it.id }) { message ->
                        ChatBubble(message = message, modifier = Modifier)
                    }
                    if (isStreaming && streamingText.isNotBlank() && !streamingAlreadyInDb) {
                        item(key = "streaming") {
                            SmoothStreamingBubble(
                                fullText = streamingText,
                                characterId = character?.id ?: 0,
                                sessionId = sessionId
                            )
                        }
                    } else if (isLoading && streamingText.isBlank()) {
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
            }

            // iOS 26 玻璃样式：模糊 + 高光
            val glassBlurStyle = HazeStyle(
                blurRadius = 15.dp,
                tint = HazeTint(MaterialTheme.colorScheme.surface.copy(alpha = 0.60f)),
                backgroundColor = MaterialTheme.colorScheme.surface
            )

            // 前景层：iOS 26 Liquid Glass 胶囊，悬浮在底部
            if (isArchived) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = sdp(12.dp), vertical = sdp(8.dp))
                        .clip(RoundedCornerShape(sdp(28.dp)))
                        .hazeEffect(state = hazeState, style = glassBlurStyle),
                    shape = RoundedCornerShape(sdp(28.dp)),
                    color = Color.Transparent,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    border = BorderStroke(
                        0.5.dp,
                        MaterialTheme.colorScheme.outline
                    )
                ) {
                    Box {
                        // 顶部玻璃高光
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(sdp(24.dp))
                                .align(Alignment.TopCenter)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = if (darkTheme) 0.06f else 0.12f),
                                            Color.White.copy(alpha = 0.02f),
                                            Color.Transparent
                                        )
                                    ),
                                    shape = RoundedCornerShape(topStart = sdp(28.dp), topEnd = sdp(28.dp))
                                )
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = sdp(16.dp), vertical = sdp(12.dp)),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Outlined.Lock,
                                contentDescription = null,
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
                    }
                }
            } else {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = sdp(12.dp), vertical = sdp(8.dp))
                        .clip(RoundedCornerShape(sdp(28.dp)))
                        .hazeEffect(state = hazeState, style = glassBlurStyle),
                    shape = RoundedCornerShape(sdp(28.dp)),
                    color = Color.Transparent,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    border = BorderStroke(
                        0.5.dp,
                        MaterialTheme.colorScheme.outline
                    )
                ) {
                    Box {
                        // 顶部玻璃高光
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(sdp(24.dp))
                                .align(Alignment.TopCenter)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = if (darkTheme) 0.06f else 0.12f),
                                            Color.White.copy(alpha = 0.02f),
                                            Color.Transparent
                                        )
                                    ),
                                    shape = RoundedCornerShape(topStart = sdp(28.dp), topEnd = sdp(28.dp))
                                )
                        )
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
            }
        }
    }

    if (showWarningDialog) {
        AlertDialog(
            onDismissRequest = { showWarningDialog = false },
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
        val yellowAt = if (usage.thinkingEnabled) 0.7f else 0.7f
        val redAt = if (usage.thinkingEnabled) 0.80f else 0.9f
        val barColor = when {
            !hasTotal -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            ratio > redAt -> MaterialTheme.colorScheme.error
            ratio > yellowAt -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.primary
        }
        AlertDialog(
            onDismissRequest = { showContextDialog = false },
            title = { Text("上下文额度") },
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
}

@Composable
private fun SmoothStreamingBubble(fullText: String, characterId: Long, sessionId: Long) {
    var displayPosition by remember { mutableIntStateOf(0) }
    val currentFullText by rememberUpdatedState(fullText)
    LaunchedEffect(Unit) {
        while (isActive) {
            val target = currentFullText.length
            val pos = displayPosition
            if (pos < target) { displayPosition = pos + 1; delay(40) }
            else { delay(80) }
        }
    }
    val displayText = fullText.take(displayPosition)
    ChatBubble(
        message = ChatHistoryEntity(
            id = -1, characterId = characterId, sessionId = sessionId,
            sender = "ai", content = displayText
        )
    )
}

@Composable
private fun CircularContextRing(usage: ContextUsage, thinkingEnabled: Boolean = false, modifier: Modifier = Modifier) {
    val hasTotal = usage.totalTokens > 0
    val ratio = if (hasTotal) (usage.usedTokens.toFloat() / usage.totalTokens).coerceIn(0f, 1f) else 0f
    val yellowAt = if (thinkingEnabled) 0.7f else 0.7f
    val redAt = if (thinkingEnabled) 0.8f else 0.9f
    val progressColor = when {
        !hasTotal -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        ratio > redAt -> MaterialTheme.colorScheme.error
        ratio > yellowAt -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
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
