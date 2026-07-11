package com.kurisuapi.ui.screen.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.flow.first
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kurisuapi.data.entity.ThemeEntity
import com.kurisuapi.data.repository.ThemeRepository
import com.kurisuapi.ui.component.BackgroundImagePicker
import com.kurisuapi.ui.component.ColorPickerSheet
import com.kurisuapi.ui.theme.*
import com.kurisuapi.util.parseColor
import com.kurisuapi.util.sdp
import com.kurisuapi.util.toHex
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeEditorScreen(
    themeId: Long,
    themeRepository: ThemeRepository,
    onNavigateBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    // 编辑状态
    var name by remember { mutableStateOf("") }
    var seedColorHex by remember { mutableStateOf("") }
    var bannerColorHex by remember { mutableStateOf("") }
    var bannerTextColorHex by remember { mutableStateOf("") }
    var fontColorHex by remember { mutableStateOf("") }
    var cardColorHex by remember { mutableStateOf("") }
    var chatBgColorHex by remember { mutableStateOf("") }
    var bubbleUserColorHex by remember { mutableStateOf("") }
    var bubbleAiColorHex by remember { mutableStateOf("") }
    var chatBgImagePath by remember { mutableStateOf("") }
    var chatBgBlurRadius by remember { mutableStateOf(8) }
    var chatBgDimPercent by remember { mutableStateOf(20) }

    // 记录原始 createdAt，编辑保存时不重置创建时间
    var originalCreatedAt by remember { mutableStateOf(0L) }

    // 加载已有主题（一次性读取，不用 Flow.collect 避免后续 DB 写入覆盖用户正在编辑的内容）
    LaunchedEffect(themeId) {
        if (themeId > 0) {
            val themes = themeRepository.observeAll().first()
            themes.find { it.id == themeId }?.let { t ->
                name = t.name
                seedColorHex = t.seedColorHex
                bannerColorHex = t.bannerColorHex
                bannerTextColorHex = t.bannerTextColorHex
                fontColorHex = t.fontColorHex
                cardColorHex = t.cardColorHex
                chatBgColorHex = t.chatBgColorHex
                bubbleUserColorHex = t.bubbleUserColorHex
                bubbleAiColorHex = t.bubbleAiColorHex
                chatBgImagePath = t.chatBgImagePath
                chatBgBlurRadius = t.chatBgBlurRadius
                chatBgDimPercent = t.chatBgDimPercent
                originalCreatedAt = t.createdAt
            }
        }
    }

    var pickerTarget by remember { mutableStateOf<String?>(null) }

    fun save() {
        scope.launch {
            val theme = ThemeEntity(
                id = if (themeId > 0) themeId else 0,
                name = name.ifBlank { "未命名" },
                seedColorHex = seedColorHex,
                bannerColorHex = bannerColorHex,
                bannerTextColorHex = bannerTextColorHex,
                fontColorHex = fontColorHex,
                cardColorHex = cardColorHex,
                chatBgColorHex = chatBgColorHex,
                bubbleUserColorHex = bubbleUserColorHex,
                bubbleAiColorHex = bubbleAiColorHex,
                chatBgImagePath = chatBgImagePath,
                chatBgBlurRadius = chatBgBlurRadius,
                chatBgDimPercent = chatBgDimPercent,
                createdAt = if (originalCreatedAt > 0) originalCreatedAt else System.currentTimeMillis(),
            )
            if (themeId == -1L) themeRepository.createAndApply(theme)
            else {
                themeRepository.update(theme)
                themeRepository.applyTheme(themeId)
            }
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (themeId == -1L) "新建主题" else "编辑主题", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack) { Text("取消") }
                },
                colors = topBarColors()
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = sdp(8.dp),
            ) {
                Button(
                    onClick = { save() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = sdp(16.dp), vertical = sdp(12.dp)),
                    shape = RoundedCornerShape(sdp(14.dp)),
                    enabled = name.isNotBlank(),
                ) {
                    Text("保存", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = sdp(4.dp)))
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // ── 名称 ──
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("主题名称") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = sdp(16.dp), vertical = sdp(4.dp)),
                singleLine = true,
                shape = RoundedCornerShape(sdp(12.dp)),
            )

            // ── 手机壳预览 ──
            PhoneMockupPreview(
                bannerColorHex = bannerColorHex,
                cardColorHex = cardColorHex,
                chatBgColorHex = chatBgColorHex,
                bubbleUserColorHex = bubbleUserColorHex,
                bubbleAiColorHex = bubbleAiColorHex,
                seedColor = seedColorHex.parseColor() ?: AppleBlue,
                modifier = Modifier.padding(horizontal = sdp(16.dp), vertical = sdp(8.dp)),
            )

            // ── 配色卡片网格 ──
            Text(
                "配色",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = sdp(16.dp), vertical = sdp(4.dp)),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = sdp(16.dp)),
                horizontalArrangement = Arrangement.spacedBy(sdp(12.dp)),
            ) {
                ColorCard(label = "主题色", hex = seedColorHex, defaultDesc = "蓝", modifier = Modifier.weight(1f), onClick = { pickerTarget = "seed" })
                ColorCard(label = "顶栏", hex = bannerColorHex, defaultDesc = "主色", modifier = Modifier.weight(1f), onClick = { pickerTarget = "banner" })
            }
            Spacer(modifier = Modifier.height(sdp(12.dp)))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = sdp(16.dp)),
                horizontalArrangement = Arrangement.spacedBy(sdp(12.dp)),
            ) {
                ColorCard(label = "顶栏文字", hex = bannerTextColorHex, defaultDesc = "白", modifier = Modifier.weight(1f), onClick = { pickerTarget = "bannerText" })
                ColorCard(label = "卡片", hex = cardColorHex, defaultDesc = "白", modifier = Modifier.weight(1f), onClick = { pickerTarget = "card" })
            }
            Spacer(modifier = Modifier.height(sdp(12.dp)))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = sdp(16.dp)),
                horizontalArrangement = Arrangement.spacedBy(sdp(12.dp)),
            ) {
                ColorCard(label = "字体", hex = fontColorHex, defaultDesc = "深", modifier = Modifier.weight(1f), onClick = { pickerTarget = "font" })
                ColorCard(label = "聊天背景", hex = chatBgColorHex, defaultDesc = "灰", modifier = Modifier.weight(1f), onClick = { pickerTarget = "chatBg" })
            }

            Text(
                "聊天气泡",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = sdp(16.dp), vertical = sdp(4.dp)),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = sdp(16.dp)),
                horizontalArrangement = Arrangement.spacedBy(sdp(12.dp)),
            ) {
                ColorCard(label = "用户气泡", hex = bubbleUserColorHex, defaultDesc = "蓝", modifier = Modifier.weight(1f), onClick = { pickerTarget = "bubbleUser" })
                ColorCard(label = "AI气泡", hex = bubbleAiColorHex, defaultDesc = "白", modifier = Modifier.weight(1f), onClick = { pickerTarget = "bubbleAi" })
            }

            // ── 背景图像 ──
            Text(
                "背景图像",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = sdp(16.dp), vertical = sdp(4.dp)),
            )

            BackgroundImagePicker(
                label = "聊天背景图片",
                currentImagePath = chatBgImagePath,
                onImageCropped = { chatBgImagePath = it },
                onClear = { chatBgImagePath = "" },
                modifier = Modifier.padding(horizontal = sdp(16.dp), vertical = sdp(4.dp)),
            )

            if (chatBgImagePath.isNotBlank()) {
                Text(
                    "模糊强度: ${chatBgBlurRadius}px",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = sdp(16.dp)),
                )
                Slider(
                    value = chatBgBlurRadius.toFloat(),
                    onValueChange = { chatBgBlurRadius = it.toInt() },
                    valueRange = 0f..25f,
                    steps = 24,
                    modifier = Modifier.padding(horizontal = sdp(16.dp)),
                )

                Text(
                    "暗化程度: ${chatBgDimPercent}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = sdp(16.dp)),
                )
                Slider(
                    value = chatBgDimPercent.toFloat(),
                    onValueChange = { chatBgDimPercent = it.toInt() },
                    valueRange = 0f..100f,
                    steps = 9,
                    modifier = Modifier.padding(horizontal = sdp(16.dp)),
                )
            }

            Spacer(modifier = Modifier.height(sdp(16.dp)))
        }
    }

    // ── 颜色选择器 ──
    val currentPickerHex = when (pickerTarget) {
        "seed" -> seedColorHex; "banner" -> bannerColorHex; "bannerText" -> bannerTextColorHex
        "card" -> cardColorHex; "font" -> fontColorHex; "chatBg" -> chatBgColorHex
        "bubbleUser" -> bubbleUserColorHex; "bubbleAi" -> bubbleAiColorHex
        else -> null
    }
    val currentPickerTitle = when (pickerTarget) {
        "seed" -> "主题色"; "banner" -> "顶栏"; "bannerText" -> "顶栏文字"
        "card" -> "卡片"; "font" -> "字体"; "chatBg" -> "聊天背景"
        "bubbleUser" -> "用户气泡"; "bubbleAi" -> "AI气泡"
        else -> ""
    }

    if (pickerTarget != null) {
        ColorPickerSheet(
            title = currentPickerTitle,
            currentColor = currentPickerHex?.parseColor(),
            onColorSelected = { color ->
                val hex = color.toHex()
                when (pickerTarget) {
                    "seed" -> seedColorHex = hex; "banner" -> bannerColorHex = hex
                    "bannerText" -> bannerTextColorHex = hex; "card" -> cardColorHex = hex
                    "font" -> fontColorHex = hex; "chatBg" -> chatBgColorHex = hex
                    "bubbleUser" -> bubbleUserColorHex = hex; "bubbleAi" -> bubbleAiColorHex = hex
                }
            },
            onReset = {
                when (pickerTarget) {
                    "seed" -> seedColorHex = ""; "banner" -> bannerColorHex = ""
                    "bannerText" -> bannerTextColorHex = ""; "card" -> cardColorHex = ""
                    "font" -> fontColorHex = ""; "chatBg" -> chatBgColorHex = ""
                    "bubbleUser" -> bubbleUserColorHex = ""; "bubbleAi" -> bubbleAiColorHex = ""
                }
                pickerTarget = null
            },
            onDismiss = { pickerTarget = null }
        )
    }
}

@Composable
private fun ColorCard(
    label: String,
    hex: String?,
    defaultDesc: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val color = hex?.parseColor()

    Card(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(sdp(12.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
    ) {
        Row(
            modifier = Modifier.padding(sdp(12.dp)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(sdp(36.dp))
                    .clip(CircleShape)
                    .background(color ?: Color.Transparent)
                    .border(sdp(1.5.dp), MaterialTheme.colorScheme.outline, CircleShape)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = sdp(10.dp))
            ) {
                Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    if (color != null) color.toHex().substring(1, 7) else defaultDesc,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun PhoneMockupPreview(
    bannerColorHex: String?,
    cardColorHex: String?,
    chatBgColorHex: String?,
    bubbleUserColorHex: String?,
    bubbleAiColorHex: String?,
    seedColor: Color,
    modifier: Modifier = Modifier,
) {
    val scheme = glassifiedDynamicScheme(seedColor, darkTheme = false)
    val banner = bannerColorHex?.parseColor() ?: scheme.primary
    val card = cardColorHex?.parseColor() ?: scheme.surface
    val bg = chatBgColorHex?.parseColor() ?: scheme.background
    val userBubble = bubbleUserColorHex?.parseColor() ?: scheme.primary
    val aiBubble = bubbleAiColorHex?.parseColor() ?: scheme.surfaceVariant

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(sdp(20.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = sdp(2.dp)),
    ) {
        Column(
            modifier = Modifier.padding(sdp(16.dp)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("预览", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(sdp(10.dp)))

            // 手机框
            Box(
                modifier = Modifier
                    .width(sdp(200.dp))
                    .clip(RoundedCornerShape(sdp(24.dp)))
                    .border(sdp(2.5.dp), MaterialTheme.colorScheme.outline, RoundedCornerShape(sdp(24.dp)))
                    .background(bg)
            ) {
                Column {
                    // 状态栏
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(sdp(22.dp))
                            .padding(horizontal = sdp(14.dp)),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(sdp(4.dp))) {
                            Text("●●●", fontSize = 6.sp, color = Color.White.copy(alpha = 0.5f))
                        }
                    }
                    // 顶栏
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(sdp(30.dp))
                            .background(banner)
                            .padding(horizontal = sdp(12.dp)),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text("聊天", color = scheme.onPrimary, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                    }
                    // 卡片
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(sdp(36.dp))
                            .padding(sdp(6.dp))
                            .clip(RoundedCornerShape(sdp(8.dp)))
                            .background(card)
                            .padding(sdp(8.dp)),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Box(
                            modifier = Modifier
                                .width(sdp(60.dp))
                                .height(sdp(6.dp))
                                .clip(RoundedCornerShape(sdp(3.dp)))
                                .background(scheme.onSurface.copy(alpha = 0.15f))
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    // 气泡
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(sdp(6.dp)),
                        horizontalArrangement = Arrangement.spacedBy(sdp(4.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(sdp(8.dp), sdp(8.dp), sdp(8.dp), sdp(2.dp)))
                                .background(aiBubble)
                                .padding(horizontal = sdp(6.dp), vertical = sdp(3.dp))
                        ) { Text("Hi", fontSize = 8.sp, color = scheme.onSurface) }
                        Spacer(modifier = Modifier.weight(1f))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(sdp(8.dp), sdp(8.dp), sdp(2.dp), sdp(8.dp)))
                                .background(userBubble)
                                .padding(horizontal = sdp(6.dp), vertical = sdp(3.dp))
                        ) { Text("Hi", fontSize = 8.sp, color = scheme.onPrimary) }
                    }
                    Spacer(modifier = Modifier.height(sdp(6.dp)))
                }
            }
        }
    }
}
