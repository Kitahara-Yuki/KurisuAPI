package com.kurisuapi.ui.screen.settings

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
import com.kurisuapi.data.entity.ProviderEntity
import com.kurisuapi.ui.navigation.Screen
import com.kurisuapi.ui.viewmodel.ProviderViewModel
import com.kurisuapi.util.sdp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderListScreen(
    onNavigateBack: () -> Unit,
    onNavigate: (String) -> Unit,
    viewModel: ProviderViewModel = hiltViewModel()
) {
    val providers by viewModel.providers.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val lastSyncTime by viewModel.lastSyncTime.collectAsState()
    val message by viewModel.message.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("模型管理", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.syncAllModels() }, enabled = !isSyncing) {
                        Icon(Icons.Outlined.Sync, contentDescription = "同步全部")
                    }
                    IconButton(onClick = { onNavigate(Screen.ProviderEdit.createRoute()) }) {
                        Icon(Icons.Outlined.Add, contentDescription = "添加")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentPadding = PaddingValues(sdp(16.dp)),
            verticalArrangement = Arrangement.spacedBy(sdp(8.dp))
        ) {
            if (isSyncing) {
                item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
            }
            if (lastSyncTime != null) {
                item {
                    val time = lastSyncTime
                    if (time != null) {
                        Text(
                            "上次同步: ${formatSyncTime(time)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            }
            message?.let { msg ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        ),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(msg, modifier = Modifier.padding(sdp(8.dp)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }

            items(providers, key = { it.id }) { provider ->
                ProviderCard(
                    provider = provider,
                    onEdit = { onNavigate(Screen.ProviderEdit.createRoute(provider.id)) },
                    onSetDefault = { viewModel.setDefaultProvider(provider.id) },
                    onDelete = { viewModel.deleteProvider(provider) }
                )
            }
        }
    }
}

private fun formatSyncTime(timestamp: Long): String {
    val seconds = (System.currentTimeMillis() - timestamp) / 1000
    return when {
        seconds < 60 -> "刚刚"
        seconds < 3600 -> "${seconds / 60} 分钟前"
        seconds < 86400 -> "${seconds / 3600} 小时前"
        else -> "${seconds / 86400} 天前"
    }
}

@Composable
private fun ProviderCard(
    provider: ProviderEntity,
    onEdit: () -> Unit,
    onSetDefault: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onEdit,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        ListItem(
            headlineContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(provider.name, fontWeight = FontWeight.SemiBold)
                    if (provider.isDefault) {
                        Spacer(modifier = Modifier.width(sdp(8.dp)))
                        SuggestionChip(onClick = {}, label = { Text("默认", style = MaterialTheme.typography.labelSmall) })
                    }
                }
            },
            supportingContent = {
                Column {
                    Text(provider.baseUrl, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), maxLines = 1)
                    if (provider.model.isNotBlank()) {
                        Text("模型: ${provider.model}", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(sdp(4.dp))) {
                        if (provider.thinkingEnabled) {
                            AssistChip(onClick = {}, label = { Text("深度思考", style = MaterialTheme.typography.labelSmall) })
                        }
                        if (provider.apiKey.isBlank()) {
                            AssistChip(
                                onClick = {},
                                label = { Text("未配置 Key", style = MaterialTheme.typography.labelSmall) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                )
                            )
                        }
                    }
                }
            },
            leadingContent = {
                Icon(
                    if (provider.isEnabled) Icons.Outlined.Cloud else Icons.Outlined.CloudOff,
                    contentDescription = null,
                    tint = if (provider.isEnabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            },
            trailingContent = {
                Row {
                    if (!provider.isDefault) {
                        IconButton(onClick = onSetDefault) {
                            Icon(Icons.Outlined.StarOutline, contentDescription = "设为默认")
                        }
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "删除",
                            tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除模型管理") },
            text = { Text("确定删除 \"${provider.name}\"？相关模型数据也会被删除。") },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteDialog = false }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("取消") } }
        )
    }
}
