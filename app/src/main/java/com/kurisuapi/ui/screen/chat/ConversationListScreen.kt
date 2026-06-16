package com.kurisuapi.ui.screen.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kurisuapi.data.entity.ConversationFolderEntity
import com.kurisuapi.data.entity.ConversationSessionEntity
import com.kurisuapi.data.entity.MemoryEntity
import com.kurisuapi.ui.viewmodel.ConversationListViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.kurisuapi.util.sdp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ConversationListScreen(
    characterId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToChat: (Long) -> Unit,
    viewModel: ConversationListViewModel = hiltViewModel()
) {
    val sessions by viewModel.filteredSessions.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val deletedSessions by viewModel.deletedSessions.collectAsState()
    val selectedFolderId by viewModel.selectedFolderId.collectAsState()
    val botSessionId by viewModel.botSessionId.collectAsState()
    val effectiveBotSessionId by viewModel.effectiveBotSessionId.collectAsState()

    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showArchiveDialog by remember { mutableStateOf<Long?>(null) }
    var showDeleteDialog by remember { mutableStateOf<Long?>(null) }
    var showMoveDialog by remember { mutableStateOf<Long?>(null) }
    var showRenameDialog by remember { mutableStateOf<Pair<Long, String>?>(null) }
    var showRenameFolderDialog by remember { mutableStateOf<Pair<Long, String>?>(null) }
    var showDeleteFolderDialog by remember { mutableStateOf<Long?>(null) }
    var hideModeLabels by rememberSaveable { mutableStateOf(false) }
    var showRecycleBin by remember { mutableStateOf(false) }
    var showNewSessionDialog by remember { mutableStateOf(false) }

    LaunchedEffect(characterId) {
        viewModel.loadCharacter(characterId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("对话管理", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showRecycleBin = true }) {
                        if (deletedSessions.isNotEmpty()) {
                            BadgedBox(badge = {
                                Badge { Text("${deletedSessions.size}") }
                            }) {
                                Icon(Icons.Outlined.Delete, contentDescription = "回收站")
                            }
                        } else {
                            Icon(Icons.Outlined.Delete, contentDescription = "回收站")
                        }
                    }
                    IconButton(onClick = { showCreateFolderDialog = true }) {
                        Icon(Icons.Outlined.CreateNewFolder, contentDescription = "新建文件夹")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showNewSessionDialog = true }
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "新对话")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (folders.isNotEmpty()) {
                FolderChipsRow(
                    folders = folders,
                    selectedFolderId = selectedFolderId,
                    onSelectFolder = { folderId ->
                        viewModel.selectFolder(folderId)
                    },
                    onRenameFolder = { folderId, name ->
                        showRenameFolderDialog = Pair(folderId, name)
                    },
                    onDeleteFolder = { folderId ->
                        showDeleteFolderDialog = folderId
                    }
                )
            }

            if (sessions.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "暂无对话",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(sdp(8.dp)))
                        Text(
                            "点击右下角 + 创建新对话",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = sdp(16.dp), vertical = sdp(8.dp)),
                    verticalArrangement = Arrangement.spacedBy(sdp(8.dp))
                ) {
                    items(sessions, key = { it.id }) { session ->
                        ConversationCard(
                            session = session,
                            isBotTarget = effectiveBotSessionId == session.id,
                            isExplicitlyBound = botSessionId == session.id,
                            hideModeLabels = hideModeLabels,
                            onToggleHideModeLabels = { hideModeLabels = !hideModeLabels },
                            onClick = { onNavigateToChat(session.id) },
                            onArchive = { showArchiveDialog = session.id },
                            onMove = { showMoveDialog = session.id },
                            onRename = { showRenameDialog = Pair(session.id, session.title) },
                            onDelete = { showDeleteDialog = session.id },
                            onBindBot = { viewModel.setBotSession(session.id) },
                            onUnbindBot = { viewModel.setBotSession(null) }
                        )
                    }
                }
            }
        }
    }

    // --- Dialogs (unchanged business logic, visual only) ---

    if (showNewSessionDialog) {
        NewSessionModeDialog(
            onDismiss = { showNewSessionDialog = false },
            onSelectMode = { chatMode ->
                showNewSessionDialog = false
                viewModel.createSession(chatMode) { newId ->
                    onNavigateToChat(newId)
                }
            }
        )
    }

    if (showCreateFolderDialog) {
        CreateFolderDialog(
            onDismiss = { showCreateFolderDialog = false },
            onCreate = { name ->
                viewModel.createFolder(name)
                showCreateFolderDialog = false
            }
        )
    }

    showRenameFolderDialog?.let { (folderId, currentName) ->
        RenameFolderDialog(
            currentName = currentName,
            onDismiss = { showRenameFolderDialog = null },
            onRename = { newName ->
                viewModel.renameFolder(folderId, newName)
                showRenameFolderDialog = null
            }
        )
    }

    showDeleteFolderDialog?.let { folderId ->
        AlertDialog(
            onDismissRequest = { showDeleteFolderDialog = null },
            title = { Text("删除文件夹") },
            text = { Text("删除文件夹后，文件夹内的对话将移回「全部」。确定删除吗？") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteFolder(folderId)
                    showDeleteFolderDialog = null
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteFolderDialog = null }) { Text("取消") }
            }
        )
    }

    showArchiveDialog?.let { sessionId ->
        AlertDialog(
            onDismissRequest = { showArchiveDialog = null },
            title = { Text("完成对话") },
            text = { Text("完成对话后，将不能对话") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.archiveSession(sessionId)
                    showArchiveDialog = null
                }) { Text("确认完成") }
            },
            dismissButton = { TextButton(onClick = { showArchiveDialog = null }) { Text("取消") } }
        )
    }

    showRenameDialog?.let { (sessionId, currentTitle) ->
        RenameSessionDialog(
            currentTitle = currentTitle,
            onDismiss = { showRenameDialog = null },
            onRename = { newTitle ->
                viewModel.renameSession(sessionId, newTitle)
                showRenameDialog = null
            }
        )
    }

    showMoveDialog?.let { sessionId ->
        MoveToFolderDialog(
            folders = folders,
            onDismiss = { showMoveDialog = null },
            onMove = { folderId ->
                viewModel.moveSession(sessionId, folderId)
                showMoveDialog = null
            }
        )
    }

    showDeleteDialog?.let { sessionId ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("删除对话") },
            text = { Text("删除该对话后记忆也会随着聊天记录一起消失，是否删除该对话？") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSession(sessionId)
                    showDeleteDialog = null
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) { Text("取消") }
            }
        )
    }

    if (showRecycleBin) {
        RecycleBinDialog(
            deletedSessions = deletedSessions,
            onDismiss = { showRecycleBin = false },
            onRestore = { sessionId ->
                viewModel.restoreSession(sessionId)
            },
            onDelete = { sessionId ->
                viewModel.permanentlyDeleteSession(sessionId)
            }
        )
    }
}

// --- Keep all helper composables as-is (already UI-only, theme references are dynamic) ---

@Composable
private fun RecycleBinDialog(
    deletedSessions: List<ConversationSessionEntity>,
    onDismiss: () -> Unit,
    onRestore: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    viewModel: com.kurisuapi.ui.viewmodel.ConversationListViewModel = hiltViewModel()
) {
    var sessionToDelete by remember { mutableStateOf<Long?>(null) }
    var sessionToShowMemories by remember { mutableStateOf<Long?>(null) }
    var deletedMemories by remember { mutableStateOf<List<MemoryEntity>>(emptyList()) }

    sessionToShowMemories?.let { sid ->
        val sessionTitle = deletedSessions.find { it.id == sid }?.title?.ifBlank { "新对话" } ?: "新对话"
        LaunchedEffect(sid) {
            deletedMemories = viewModel.getDeletedMemories(sid)
        }
        AlertDialog(
            onDismissRequest = { sessionToShowMemories = null },
            title = { Text("「${sessionTitle}」的记忆") },
            text = {
                if (deletedMemories.isEmpty()) {
                    Text("此对话没有关联的记忆", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(sdp(6.dp)),
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    ) {
                        deletedMemories.forEach { memory ->
                            Surface(
                                shape = RoundedCornerShape(sdp(6.dp)),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(sdp(8.dp))) {
                                    Text(memory.content, style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        "重要度 ${memory.importance}/10",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { sessionToShowMemories = null }) { Text("关闭") } },
            dismissButton = null
        )
    }

    sessionToDelete?.let { sid ->
        val sessionTitle = deletedSessions.find { it.id == sid }?.title?.ifBlank { "新对话" } ?: "新对话"
        AlertDialog(
            onDismissRequest = { sessionToDelete = null },
            icon = { Icon(Icons.Outlined.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("永久删除") },
            text = {
                Text("确定永久删除「${sessionTitle}」？\n\n所有聊天记录和相关记忆将被彻底清除，无法恢复。")
            },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(sid)
                    sessionToDelete = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { sessionToDelete = null }) { Text("取消") } }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🗑️ 回收站") },
        text = {
            if (deletedSessions.isEmpty()) {
                Text("回收站为空", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(sdp(8.dp))) {
                    Text(
                        "以下对话将在删除满7天后自动清理",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    deletedSessions.forEach { session ->
                        val daysLeft = ((session.deletedAt + 7 * 24 * 60 * 60 * 1000L - System.currentTimeMillis()) / (24 * 60 * 60 * 1000L)).toInt().coerceAtLeast(0)
                        val daysText = when {
                            daysLeft <= 0 -> "今天删除"
                            daysLeft == 1 -> "1天后删除"
                            else -> "${daysLeft}天后删除"
                        }
                        Surface(
                            shape = RoundedCornerShape(sdp(8.dp)),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(sdp(12.dp)),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        session.title.ifBlank { "新对话" },
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        daysText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                                    )
                                }
                                TextButton(onClick = { onRestore(session.id) }) { Text("还原") }
                                TextButton(onClick = { sessionToShowMemories = session.id }) { Text("记忆") }
                                TextButton(onClick = { sessionToDelete = session.id }) {
                                    Text("删除", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
        dismissButton = null
    )
}

@Composable
private fun FolderChipsRow(
    folders: List<ConversationFolderEntity>,
    selectedFolderId: Long?,
    onSelectFolder: (Long?) -> Unit,
    onRenameFolder: (Long, String) -> Unit,
    onDeleteFolder: (Long) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = sdp(16.dp), vertical = sdp(8.dp)),
        horizontalArrangement = Arrangement.spacedBy(sdp(8.dp))
    ) {
        item {
            val isAllSelected = selectedFolderId == null
            Surface(
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(),
                    onClick = { onSelectFolder(null) }
                ),
                shape = RoundedCornerShape(sdp(8.dp)),
                color = if (isAllSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                border = if (isAllSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null
            ) {
                Row(
                    modifier = Modifier
                        .defaultMinSize(minHeight = sdp(32.dp))
                        .padding(horizontal = sdp(12.dp)),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(sdp(6.dp))
                ) {
                    if (isAllSelected) {
                        Icon(
                            Icons.Outlined.Check,
                            contentDescription = null,
                            modifier = Modifier.size(sdp(18.dp)),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Text(
                        "全部",
                        maxLines = 1,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isAllSelected) MaterialTheme.colorScheme.onSecondaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        items(folders, key = { it.id }) { folder ->
            FolderChipWithMenu(
                folder = folder,
                isSelected = selectedFolderId == folder.id,
                onSelect = { onSelectFolder(folder.id) },
                onRename = { onRenameFolder(folder.id, folder.name) },
                onDelete = { onDeleteFolder(folder.id) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderChipWithMenu(
    folder: ConversationFolderEntity,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val isSystem = folder.isSystem

    Box {
        Surface(
            modifier = if (isSystem) {
                Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(),
                    onClick = onSelect
                )
            } else {
                Modifier.combinedClickable(
                    onClick = onSelect,
                    onLongClick = { showMenu = true },
                    indication = ripple(),
                    interactionSource = remember { MutableInteractionSource() }
                )
            },
            shape = RoundedCornerShape(sdp(8.dp)),
            color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
            border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null
        ) {
            Row(
                modifier = Modifier
                    .defaultMinSize(minHeight = sdp(32.dp))
                    .padding(horizontal = sdp(12.dp)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(sdp(6.dp))
            ) {
                if (isSelected) {
                    Icon(
                        Icons.Outlined.Check,
                        contentDescription = null,
                        modifier = Modifier.size(sdp(18.dp)),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Text(
                    folder.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = sdp(100.dp)),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (!isSystem) {
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("重命名文件夹") },
                    onClick = { showMenu = false; onRename() },
                    leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) }
                )
                DropdownMenuItem(
                    text = { Text("删除文件夹", color = MaterialTheme.colorScheme.error) },
                    onClick = { showMenu = false; onDelete() },
                    leadingIcon = {
                        Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    }
                )
            }
        }
    }
}

@Composable
private fun ConversationCard(
    session: ConversationSessionEntity,
    isBotTarget: Boolean,
    isExplicitlyBound: Boolean,
    hideModeLabels: Boolean,
    onToggleHideModeLabels: () -> Unit,
    onClick: () -> Unit,
    onArchive: () -> Unit,
    onMove: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onBindBot: () -> Unit,
    onUnbindBot: () -> Unit
) {
    val isArchived = session.isArchived
    val containerColor = if (isArchived) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
    }
    val contentAlpha = if (isArchived) 0.5f else 1f
    var showMoreMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(sdp(12.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when {
                    isBotTarget && !isArchived -> Icons.Outlined.SmartToy
                    isArchived -> Icons.Outlined.Lock
                    else -> Icons.Outlined.Chat
                },
                contentDescription = null,
                tint = when {
                    isBotTarget && !isArchived -> MaterialTheme.colorScheme.tertiary
                    isArchived -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    else -> MaterialTheme.colorScheme.primary
                },
                modifier = Modifier.size(sdp(24.dp))
            )
            Spacer(modifier = Modifier.width(sdp(12.dp)))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(sdp(6.dp))
                ) {
                    Text(
                        text = session.title.ifBlank { "新对话" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
                    )
                    if (isBotTarget && !isArchived) {
                        Text(
                            "微信",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(sdp(4.dp)))
                                .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f))
                                .padding(horizontal = sdp(6.dp), vertical = 2.dp)
                        )
                    }
                    if (isArchived) {
                        Text(
                            "已归档",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(sdp(4.dp)))
                                .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f))
                                .padding(horizontal = sdp(6.dp), vertical = 2.dp)
                        )
                    }
                    if (!hideModeLabels) {
                        val isStory = session.chatMode == ConversationSessionEntity.CHAT_MODE_STORY
                        Text(
                            text = if (isStory) "📖 剧情模式" else "💬 对话模式",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isStory) MaterialTheme.colorScheme.tertiary
                                    else MaterialTheme.colorScheme.secondary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(sdp(4.dp)))
                                .background(
                                    if (isStory) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
                                    else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                                )
                                .padding(horizontal = sdp(6.dp), vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = formatTimestamp(session.updatedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f * contentAlpha)
                )
            }

            Box {
                IconButton(
                    onClick = { showMoreMenu = true },
                    modifier = Modifier.size(sdp(32.dp))
                ) {
                    Icon(
                        Icons.Outlined.MoreVert,
                        contentDescription = "更多",
                        modifier = Modifier.size(sdp(18.dp))
                    )
                }
                DropdownMenu(
                    expanded = showMoreMenu,
                    onDismissRequest = { showMoreMenu = false }
                ) {
                    if (!isArchived) {
                        DropdownMenuItem(
                            text = { Text("完成对话") },
                            onClick = { showMoreMenu = false; onArchive() },
                            leadingIcon = { Icon(Icons.Outlined.Check, contentDescription = null) }
                        )
                        if (isExplicitlyBound) {
                            DropdownMenuItem(
                                text = { Text("取消微信绑定") },
                                onClick = { showMoreMenu = false; onUnbindBot() },
                                leadingIcon = { Icon(Icons.Outlined.LinkOff, contentDescription = null) }
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("微信对话") },
                                onClick = { showMoreMenu = false; onBindBot() },
                                leadingIcon = { Icon(Icons.Outlined.SmartToy, contentDescription = null) }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("移动到文件夹") },
                            onClick = { showMoreMenu = false; onMove() },
                            leadingIcon = { Icon(Icons.Outlined.Folder, contentDescription = null) }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("重命名") },
                        onClick = { showMoreMenu = false; onRename() },
                        leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(if (hideModeLabels) "显示模式标签" else "隐藏模式显示") },
                        onClick = { onToggleHideModeLabels(); showMoreMenu = false },
                        leadingIcon = { Icon(Icons.Outlined.Visibility, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                        onClick = { showMoreMenu = false; onDelete() },
                        leadingIcon = {
                            Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        }
                    )
                }
            }
        }
    }
}

// --- Dialog composables (kept identical, already using dynamic theme) ---

@Composable
private fun CreateFolderDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var folderName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建文件夹") },
        text = {
            OutlinedTextField(
                value = folderName, onValueChange = { folderName = it },
                label = { Text("文件夹名称") }, singleLine = true, modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { TextButton(onClick = { onCreate(folderName) }, enabled = folderName.isNotBlank()) { Text("创建") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun RenameSessionDialog(currentTitle: String, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    var newTitle by remember { mutableStateOf(currentTitle) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名对话") },
        text = {
            OutlinedTextField(
                value = newTitle, onValueChange = { newTitle = it },
                label = { Text("对话名称") }, singleLine = true, modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { TextButton(onClick = { onRename(newTitle) }, enabled = newTitle.isNotBlank()) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun RenameFolderDialog(currentName: String, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    var newName by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名文件夹") },
        text = {
            OutlinedTextField(
                value = newName, onValueChange = { newName = it },
                label = { Text("文件夹名称") }, singleLine = true, modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { TextButton(onClick = { onRename(newName) }, enabled = newName.isNotBlank()) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun MoveToFolderDialog(folders: List<ConversationFolderEntity>, onDismiss: () -> Unit, onMove: (Long?) -> Unit) {
    var selectedFolderId by remember { mutableStateOf<Long?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("移动到文件夹") },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(sdp(8.dp))).clickable { selectedFolderId = null }
                        .padding(horizontal = sdp(12.dp), vertical = sdp(8.dp)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = selectedFolderId == null, onClick = { selectedFolderId = null })
                    Spacer(modifier = Modifier.width(sdp(8.dp)))
                    Text("无文件夹")
                }
                folders.forEach { folder ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(sdp(8.dp))).clickable { selectedFolderId = folder.id }
                            .padding(horizontal = sdp(12.dp), vertical = sdp(8.dp)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selectedFolderId == folder.id, onClick = { selectedFolderId = folder.id })
                        Spacer(modifier = Modifier.width(sdp(8.dp)))
                        Text(folder.name)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onMove(selectedFolderId) }) { Text("移动") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun NewSessionModeDialog(onDismiss: () -> Unit, onSelectMode: (String) -> Unit) {
    var selectedMode by remember { mutableStateOf(ConversationSessionEntity.CHAT_MODE_CHAT) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建对话") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(sdp(8.dp))) {
                Text("请选择对话模式", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { selectedMode = ConversationSessionEntity.CHAT_MODE_CHAT },
                    colors = CardDefaults.cardColors(
                        containerColor = if (selectedMode == ConversationSessionEntity.CHAT_MODE_CHAT)
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(modifier = Modifier.padding(sdp(12.dp))) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = selectedMode == ConversationSessionEntity.CHAT_MODE_CHAT,
                                onClick = { selectedMode = ConversationSessionEntity.CHAT_MODE_CHAT })
                            Text("💬 对话模式", fontWeight = FontWeight.SemiBold)
                        }
                        Text("纯粹的互发消息，没有其他多余的内容，就像真正的微信聊天",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                }
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { selectedMode = ConversationSessionEntity.CHAT_MODE_STORY },
                    colors = CardDefaults.cardColors(
                        containerColor = if (selectedMode == ConversationSessionEntity.CHAT_MODE_STORY)
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(modifier = Modifier.padding(sdp(12.dp))) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = selectedMode == ConversationSessionEntity.CHAT_MODE_STORY,
                                onClick = { selectedMode = ConversationSessionEntity.CHAT_MODE_STORY })
                            Text("📖 剧情模式", fontWeight = FontWeight.SemiBold)
                        }
                        Text("推荐给拥有庞大世界观和丰富设定的角色使用，该模式下角色会描述周围的环境与动作姿态",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSelectMode(selectedMode) }) { Text("创建") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

private fun formatTimestamp(timestamp: Long): String {
    if (timestamp == 0L) return ""
    return try {
        val instant = Instant.ofEpochMilli(timestamp)
        val today = Instant.now().atZone(ZoneId.systemDefault()).toLocalDate()
        val date = instant.atZone(ZoneId.systemDefault()).toLocalDate()
        val timeFormat = DateTimeFormatter.ofPattern("HH:mm")
        val time = instant.atZone(ZoneId.systemDefault()).format(timeFormat)
        when {
            date == today -> "今天 $time"
            date == today.minusDays(1) -> "昨天 $time"
            else -> {
                val dateFormat = DateTimeFormatter.ofPattern("MM-dd HH:mm")
                instant.atZone(ZoneId.systemDefault()).format(dateFormat)
            }
        }
    } catch (e: Exception) { "" }
}
