package com.kurisuapi.ui.screen.diary

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kurisuapi.data.entity.DiaryEntryEntity
import com.kurisuapi.ui.theme.*
import com.kurisuapi.ui.viewmodel.DiaryViewModel
import com.kurisuapi.util.sdp
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryListScreen(
    characterId: Long,
    onNavigateBack: () -> Unit,
    viewModel: DiaryViewModel = hiltViewModel()
) {
    val entries by viewModel.entries.collectAsState()
    val character by viewModel.character.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    val error by viewModel.error.collectAsState()
    val expandedIds by viewModel.expandedIds.collectAsState()
    val userName by viewModel.userName.collectAsState()

    // 创建日记弹窗
    var showCreateDialog by remember { mutableStateOf(false) }
    var showManualDialog by remember { mutableStateOf(false) }

    LaunchedEffect(characterId) {
        viewModel.loadCharacter(characterId)
    }

    // 错误提示
    LaunchedEffect(error) {
        if (error != null) {
            kotlinx.coroutines.delay(3000)
            viewModel.clearError()
        }
    }

    // 按月份分组
    val grouped = remember(entries) {
        entries
            .sortedByDescending { it.date }
            .groupBy { it.date.take(7) } // "2026-06"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Book, contentDescription = null, tint = AppleOrange)
                        Spacer(Modifier.width(sdp(8.dp)))
                        Text("日记", fontWeight = FontWeight.SemiBold)
                    }
                },
                actions = {
                    IconButton(onClick = { showCreateDialog = true }) {
                        Icon(Icons.Outlined.Add, contentDescription = "写日记")
                    }
                },
                colors = com.kurisuapi.ui.theme.topBarColors()
            )
        },
        snackbarHost = {
            error?.let { msg ->
                Snackbar(modifier = Modifier.padding(sdp(16.dp))) {
                    Text(msg)
                }
            }
        }
    ) { paddingValues ->
        if (entries.isEmpty() && !isGenerating) {
            // 空状态
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.Book,
                        contentDescription = null,
                        modifier = Modifier.size(sdp(64.dp)),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                    )
                    Spacer(Modifier.height(sdp(16.dp)))
                    Text(
                        "还没有日记",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Spacer(Modifier.height(sdp(8.dp)))
                    Text(
                        "每天早上 7 点，AI 会为你写一篇昨天的日记",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                    Spacer(Modifier.height(sdp(24.dp)))
                    FilledTonalButton(
                        onClick = {
                            val yesterday = LocalDate.now(ZoneId.of("Asia/Shanghai")).minusDays(1)
                            viewModel.generateDiary(yesterday.format(DateTimeFormatter.ISO_LOCAL_DATE))
                        }
                    ) {
                        Icon(Icons.Outlined.Edit, contentDescription = null)
                        Spacer(Modifier.width(sdp(8.dp)))
                        Text("现在写一篇")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = sdp(16.dp)),
                verticalArrangement = Arrangement.spacedBy(sdp(12.dp)),
                contentPadding = PaddingValues(vertical = sdp(16.dp))
            ) {
                // 加载中提示
                if (isGenerating) {
                    item {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = AppleOrange
                        )
                        Spacer(Modifier.height(sdp(8.dp)))
                    }
                }

                // 按月份分组显示
                grouped.forEach { (monthKey, monthEntries) ->
                    item {
                        MonthHeader(monthKey)
                    }

                    items(monthEntries, key = { it.id }) { entry ->
                        val isExpanded = entry.id in expandedIds

                        DiaryCard(
                            entry = entry,
                            isExpanded = isExpanded,
                            characterName = character?.name ?: "AI",
                            userName = userName,
                            onToggle = { viewModel.toggleExpanded(entry.id) },
                            onDelete = { viewModel.deleteEntry(entry.id) }
                        )
                    }
                }

                // 底部留白
                item { Spacer(Modifier.height(sdp(32.dp))) }
            }
        }
    }

    // ── 创建日记选择弹窗 ──
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Edit, contentDescription = null, tint = AppleOrange)
                    Spacer(Modifier.width(sdp(8.dp)))
                    Text("写日记", fontWeight = FontWeight.SemiBold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(sdp(8.dp))) {
                    Text(
                        "选择写日记的方式：",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(Modifier.height(sdp(4.dp)))
                    // AI 自动写
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showCreateDialog = false
                                val yesterday = LocalDate.now(ZoneId.of("Asia/Shanghai")).minusDays(1)
                                viewModel.generateDiary(yesterday.format(DateTimeFormatter.ISO_LOCAL_DATE))
                            },
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier.padding(sdp(12.dp)),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.SmartToy, contentDescription = null, tint = AppleBlue)
                            Spacer(Modifier.width(sdp(12.dp)))
                            Column {
                                Text("AI 自动写日记", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                Text("根据昨天的对话自动生成", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            }
                        }
                    }
                    // 手写日记
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showCreateDialog = false
                                showManualDialog = true
                            },
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier.padding(sdp(12.dp)),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.Draw, contentDescription = null, tint = AppleOrange)
                            Spacer(Modifier.width(sdp(12.dp)))
                            Column {
                                Text("手写日记", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                Text("自己写一篇日记", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("取消") }
            }
        )
    }

    // ── 手写日记弹窗 ──
    if (showManualDialog) {
        val today = LocalDate.now(ZoneId.of("Asia/Shanghai"))
        var manualDate by remember { mutableStateOf(today.format(DateTimeFormatter.ISO_LOCAL_DATE)) }
        var manualContent by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showManualDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Draw, contentDescription = null, tint = AppleOrange)
                    Spacer(Modifier.width(sdp(8.dp)))
                    Text("手写日记", fontWeight = FontWeight.SemiBold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(sdp(12.dp))) {
                    OutlinedTextField(
                        value = manualDate,
                        onValueChange = { manualDate = it },
                        label = { Text("日期（如 2026-06-21）") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = manualContent,
                        onValueChange = { manualContent = it },
                        label = { Text("日记内容") },
                        minLines = 5,
                        maxLines = 10,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (manualContent.isNotBlank()) {
                            viewModel.saveManualDiary(manualDate, manualContent)
                            showManualDialog = false
                        }
                    },
                    enabled = manualContent.isNotBlank()
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualDialog = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun MonthHeader(monthKey: String) {
    val display = try {
        val year = monthKey.substring(0, 4).toInt()
        val month = monthKey.substring(5, 7).toInt()
        "${year}年${month}月"
    } catch (_: Exception) { monthKey }

    Text(
        text = display,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        modifier = Modifier.padding(
            start = sdp(4.dp),
            top = sdp(16.dp),
            bottom = sdp(4.dp)
        )
    )
}

@Composable
private fun DiaryCard(
    entry: DiaryEntryEntity,
    isExpanded: Boolean,
    characterName: String,
    userName: String,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // 解析日期
    val dateDisplay = try {
        val date = LocalDate.parse(entry.date, DateTimeFormatter.ISO_LOCAL_DATE)
        val dow = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.CHINESE)
        "${date.monthValue}月${date.dayOfMonth}日 $dow"
    } catch (_: Exception) { entry.date }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(sdp(16.dp))) {
            // 日期行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        dateDisplay,
                        style = MaterialTheme.typography.labelLarge,
                        color = AppleOrange,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        if (entry.isManual) "${userName}的日记" else "${characterName}的日记",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
                Row {
                    IconButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.size(sdp(32.dp))
                    ) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = "删除",
                            modifier = Modifier.size(sdp(18.dp)),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    }
                }
            }

            Spacer(Modifier.height(sdp(8.dp)))

            // 日记正文
            AnimatedContent(
                targetState = isExpanded,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                }
            ) { expanded ->
                if (expanded) {
                    Column {
                        Text(
                            entry.content,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontStyle = FontStyle.Normal
                            ),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                        )
                        Spacer(Modifier.height(sdp(8.dp)))
                        TextButton(onClick = onToggle) {
                            Text("收起", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                } else {
                    Column {
                        Text(
                            entry.content,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                        )
                        Spacer(Modifier.height(sdp(4.dp)))
                        TextButton(onClick = onToggle) {
                            Text("展开全文", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }

    // 删除确认弹窗
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = { Text("删除日记") },
            text = { Text("删除后无法恢复，确定要删除 ${dateDisplay} 的日记吗？") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    showDeleteConfirm = false
                }) {
                    Text("删除", color = AppleRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }
}
