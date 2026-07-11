package com.kurisuapi.ui.screen.memory

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kurisuapi.data.entity.MemoryEntity
import com.kurisuapi.ui.viewmodel.MemoryViewModel
import com.kurisuapi.util.sdp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MemoryListScreen(
    characterId: Long,
    onNavigateBack: () -> Unit,
    viewModel: MemoryViewModel = hiltViewModel()
) {
    val memories by viewModel.memories.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    val isOptimizing by viewModel.isOptimizing.collectAsState()
    val optimizeProgress by viewModel.optimizeProgress.collectAsState()
    val message by viewModel.message.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingMemory by remember { mutableStateOf<MemoryEntity?>(null) }
    var profileExpanded by remember { mutableStateOf(false) }
    var assignMemory by remember { mutableStateOf<MemoryEntity?>(null) }
    var assignTargetIds by remember { mutableStateOf<List<Long>>(emptyList()) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showBatchDeleteDialog by remember { mutableStateOf(false) }
    var showBatchAssignDialog by remember { mutableStateOf(false) }
    var memoryToDelete by remember { mutableStateOf<MemoryEntity?>(null) }
    var expandedMemoryIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val sessionTitles by viewModel.sessionTitles.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var searchText by remember { mutableStateOf("") }

    // 记忆列表：搜索时显示搜索结果，否则显示完整列表
    val displayMemories = if (isSearching) (searchResults ?: memories) else memories

    LaunchedEffect(characterId) {
        viewModel.setCharacterId(characterId)
        viewModel.loadSessionTitles()
    }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (selectedIds.isNotEmpty()) {
                TopAppBar(
                    title = { },
                    navigationIcon = { },
                    actions = {
                        OutlinedButton(
                            onClick = { showBatchAssignDialog = true },
                            modifier = Modifier.padding(end = sdp(8.dp))
                        ) { Text("分配 (${selectedIds.size})") }
                        Button(
                            onClick = { showBatchDeleteDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.padding(end = sdp(8.dp))
                        ) { Text("删除 (${selectedIds.size})") }
                        TextButton(onClick = { selectedIds = emptySet() }) { Text("取消") }
                    },
                    colors = com.kurisuapi.ui.theme.topBarColors()
                )
            } else {
                TopAppBar(
                    title = { Text("记忆管理", fontWeight = FontWeight.SemiBold) },
                    actions = {
                        if (isOptimizing) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(end = sdp(8.dp))
                            ) {
                                if (optimizeProgress.isNotBlank()) {
                                    Text(optimizeProgress, style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary)
                                }
                                CircularProgressIndicator(
                                    modifier = Modifier.size(sdp(18.dp)).padding(start = sdp(4.dp)),
                                    strokeWidth = 2.dp
                                )
                            }
                        } else {
                            IconButton(onClick = { viewModel.optimizeMemories() }) {
                                Icon(Icons.Outlined.Refresh, contentDescription = "优化记忆")
                            }
                        }
                    },
                    colors = com.kurisuapi.ui.theme.topBarColors()
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Outlined.Add, contentDescription = "添加记忆")
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentPadding = PaddingValues(sdp(16.dp)),
            verticalArrangement = Arrangement.spacedBy(sdp(8.dp))
        ) {
            item {
                val profileText = userProfile?.profileText
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                    ),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(modifier = Modifier.padding(sdp(16.dp))) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable(enabled = !profileText.isNullOrBlank()) {
                                profileExpanded = !profileExpanded
                            },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Person, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface)
                                Spacer(modifier = Modifier.width(sdp(8.dp)))
                                Text("AI 对你的了解", style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold)
                            }
                            if (!profileText.isNullOrBlank()) {
                                Icon(
                                    if (profileExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (profileText.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(sdp(4.dp)))
                            Text("还没有形成画像。多聊几句，或点击右上角 ✨ 主动提炼。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        } else {
                            AnimatedVisibility(visible = profileExpanded) {
                                Text(profileText, style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(top = sdp(8.dp)))
                            }
                            if (!profileExpanded) {
                                Text(profileText, style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(top = sdp(4.dp)))
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    "记忆条目",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = sdp(8.dp))
                )
            }

            // 搜索栏
            item {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { text ->
                        searchText = text
                        viewModel.search(text)
                    },
                    placeholder = { Text("搜索记忆...") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchText.isNotEmpty()) {
                            IconButton(onClick = {
                                searchText = ""
                                viewModel.clearSearch()
                            }) {
                                Icon(Icons.Outlined.Close, contentDescription = "清除搜索")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium
                )
            }

            if (isSearching && searchResults != null && searchResults!!.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = sdp(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("未找到匹配的记忆", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                }
            }

            if (memories.isEmpty() && !isSearching) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = sdp(32.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("暂无记忆", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                }
            } else if (displayMemories.isNotEmpty()) {
                items(displayMemories, key = { it.id }) { memory ->
                    val isSelected = selectedIds.contains(memory.id)
                    val isSelectionMode = selectedIds.isNotEmpty()
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    if (isSelectionMode) {
                                        selectedIds = if (isSelected) selectedIds - memory.id else selectedIds + memory.id
                                    }
                                },
                                onLongClick = { selectedIds = setOf(memory.id) }
                            ),
                        colors = if (isSelected) CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        ) else CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                        ),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        ListItem(
                            headlineContent = {
                                val isLong = memory.content.length > 27
                                val expanded = expandedMemoryIds.contains(memory.id)
                                Text(
                                    text = memory.content,
                                    maxLines = if (isLong && !expanded) 4 else 50,  // 50 行足够覆盖 99.9% 的记忆，避免极长内容撑爆屏幕
                                    modifier = if (isLong) Modifier.clickable {
                                        expandedMemoryIds = if (expanded) expandedMemoryIds - memory.id
                                        else expandedMemoryIds + memory.id
                                    } else Modifier
                                )
                            },
                            supportingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(sdp(6.dp))) {
                                    Text("重要度: ${memory.importance}/10")
                                }
                            },
                            leadingContent = {
                                if (isSelectionMode) {
                                    Checkbox(checked = isSelected, onCheckedChange = {
                                        selectedIds = if (isSelected) selectedIds - memory.id else selectedIds + memory.id
                                    })
                                } else {
                                    Badge { Text("${memory.importance}") }
                                }
                            },
                            trailingContent = {
                                if (!isSelectionMode) {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Row {
                                            IconButton(
                                                onClick = { assignMemory = memory; assignTargetIds = listOf(memory.id) },
                                                modifier = Modifier.size(sdp(36.dp))
                                            ) {
                                                Icon(Icons.Outlined.ChevronRight, contentDescription = "分配",
                                                    modifier = Modifier.size(sdp(18.dp)))
                                            }
                                            IconButton(onClick = { editingMemory = memory }) {
                                                Icon(Icons.Outlined.Edit, contentDescription = "编辑")
                                            }
                                            IconButton(onClick = { memoryToDelete = memory }) {
                                                Icon(Icons.Outlined.Delete, contentDescription = "删除",
                                                    tint = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                        val title = if (memory.sessionId > 0) sessionTitles[memory.sessionId] else null
                                        if (title != null) {
                                            Text(title, maxLines = 1, style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                        }
                                        Text(
                                            if (memory.source == "auto") "AI自动添加" else "手动添加",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                        )
                                        val (dateStr, timeStr) = formatTime(memory.createdAt)
                                        Text(dateStr, style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                                        Text(timeStr, style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // --- Keep all dialog logic identical to original ---

    if (showBatchAssignDialog) {
        val titles = sessionTitles
        var batchAssignTargetId by remember { mutableStateOf(0L) }
        AlertDialog(
            onDismissRequest = { showBatchAssignDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = { Text("将 ${selectedIds.size} 条记忆分配到对话") },
            text = {
                if (titles.isEmpty()) {
                    Text("暂无对话", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                } else {
                    Column {
                        titles.entries.forEach { (id, title) ->
                            Row(modifier = Modifier.fillMaxWidth().clickable { batchAssignTargetId = id },
                                verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = batchAssignTargetId == id, onClick = { batchAssignTargetId = id })
                                Text(title, modifier = Modifier.padding(start = sdp(4.dp)))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (batchAssignTargetId > 0) {
                        viewModel.batchAssignToSession(selectedIds.toList(), batchAssignTargetId)
                        selectedIds = emptySet()
                    }
                    showBatchAssignDialog = false
                }, enabled = batchAssignTargetId > 0) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showBatchAssignDialog = false }) { Text("取消") } }
        )
    }

    memoryToDelete?.let { memory ->
        AlertDialog(
            onDismissRequest = { memoryToDelete = null },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            icon = { Icon(Icons.Outlined.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("删除记忆") },
            text = { Text("确定删除这条记忆？\n\n「${memory.content.take(50)}」\n\n此操作无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteMemory(memory)
                    memoryToDelete = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { memoryToDelete = null }) { Text("取消") } }
        )
    }

    if (showBatchDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            icon = { Icon(Icons.Outlined.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("批量删除") },
            text = { Text("确定永久删除选中的 ${selectedIds.size} 条记忆？\n此操作无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteMemories(selectedIds.toList())
                    selectedIds = emptySet()
                    showBatchDeleteDialog = false
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showBatchDeleteDialog = false }) { Text("取消") } }
        )
    }

    if (showAddDialog) {
        MemoryDialog(
            title = "添加记忆", initialContent = "", initialImportance = 5,
            onDismiss = { showAddDialog = false },
            onConfirm = { content, importance ->
                viewModel.addMemory(content, importance)
                showAddDialog = false
            }
        )
    }

    assignMemory?.let { memory ->
        val titles = sessionTitles
        var selectedId by remember { mutableStateOf(0L) }
        AlertDialog(
            onDismissRequest = { assignMemory = null },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = { Text("选择所属对话") },
            text = {
                if (titles.isEmpty()) {
                    Text("暂无对话", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                } else {
                    Column {
                        Text("「${memory.content.take(20)}...」", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        Spacer(modifier = Modifier.height(sdp(8.dp)))
                        titles.entries.forEach { (id, title) ->
                            Row(modifier = Modifier.fillMaxWidth().clickable { selectedId = id },
                                verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = selectedId == id, onClick = { selectedId = id })
                                Text(title, modifier = Modifier.padding(start = sdp(4.dp)))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (selectedId > 0) viewModel.assignToSession(memory.id, selectedId)
                    assignMemory = null
                }, enabled = selectedId > 0) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { assignMemory = null }) { Text("取消") } }
        )
    }

    editingMemory?.let { memory ->
        MemoryDialog(
            title = "编辑记忆", initialContent = memory.content, initialImportance = memory.importance,
            onDismiss = { editingMemory = null },
            onConfirm = { content, importance ->
                viewModel.updateMemory(memory.copy(content = content, importance = importance))
                editingMemory = null
            }
        )
    }
}

private fun formatTime(timestamp: Long): Pair<String, String> {
    val sdfDate = java.text.SimpleDateFormat("yyyy年M月d日", java.util.Locale.CHINESE)
    val sdfTime = java.text.SimpleDateFormat("a h:mm", java.util.Locale.CHINESE)
    val date = java.util.Date(timestamp)
    return Pair(sdfDate.format(date), sdfTime.format(date))
}

@Composable
private fun MemoryDialog(
    title: String, initialContent: String, initialImportance: Int,
    onDismiss: () -> Unit, onConfirm: (String, Int) -> Unit
) {
    var content by remember { mutableStateOf(initialContent) }
    var importance by remember { mutableStateOf(initialImportance.toFloat()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(sdp(12.dp))) {
                OutlinedTextField(value = content, onValueChange = { content = it },
                    label = { Text("记忆内容") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                Text("重要度: ${importance.toInt()}/10")
                Slider(value = importance, onValueChange = { importance = it },
                    valueRange = 1f..10f, steps = 8)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(content, importance.toInt()) }, enabled = content.isNotBlank()) {
                Text(if (initialContent.isBlank()) "添加" else "保存")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
