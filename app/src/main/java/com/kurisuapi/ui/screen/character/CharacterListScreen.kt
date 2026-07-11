package com.kurisuapi.ui.screen.character

import androidx.compose.foundation.clickable
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
import com.kurisuapi.data.entity.CharacterEntity
import com.kurisuapi.ui.navigation.Screen
import com.kurisuapi.ui.viewmodel.CharacterListViewModel
import com.kurisuapi.ui.viewmodel.CharacterViewModel
import com.kurisuapi.util.sdp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterListScreen(
    onNavigate: (String) -> Unit,
    onNavigateBack: () -> Unit,
    characterViewModel: CharacterViewModel = hiltViewModel(),
    listViewModel: CharacterListViewModel = hiltViewModel()
) {
    val characters by characterViewModel.characters.collectAsState()
    val activeCharacterId by listViewModel.activeCharacterId.collectAsState()
    var characterToDelete by remember { mutableStateOf<CharacterEntity?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }

    characterToDelete?.let { character ->
        AlertDialog(
            onDismissRequest = { characterToDelete = null },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = { Text("确认删除") },
            text = { Text("确定要删除角色「${character.name}」吗？\n\n此操作将同时删除该角色的所有聊天记录、记忆、情绪和关系数据，且不可恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        characterViewModel.deleteCharacter(character)
                        characterToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { characterToDelete = null }) { Text("取消") }
            }
        )
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = { Text("创建角色", fontWeight = FontWeight.SemiBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(sdp(12.dp))) {
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable {
                            showCreateDialog = false
                            onNavigate(Screen.CharacterGenerate.route)
                        },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        ),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(modifier = Modifier.padding(sdp(16.dp))) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.AutoAwesome, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(sdp(22.dp)))
                                Spacer(modifier = Modifier.width(sdp(10.dp)))
                                Text("自动生成", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                            }
                            Spacer(modifier = Modifier.height(sdp(6.dp)))
                            Text("按照要求自动生成角色", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable {
                            showCreateDialog = false
                            onNavigate(Screen.CharacterEdit.createRoute())
                        },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(modifier = Modifier.padding(sdp(16.dp))) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Edit, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(sdp(22.dp)))
                                Spacer(modifier = Modifier.width(sdp(10.dp)))
                                Text("手动创建", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                            }
                            Spacer(modifier = Modifier.height(sdp(6.dp)))
                            Text("自己逐项填写", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showCreateDialog = false }) { Text("取消") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("角色管理", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showCreateDialog = true }) {
                        Icon(Icons.Outlined.Add, contentDescription = "添加角色")
                    }
                },
                colors = com.kurisuapi.ui.theme.topBarColors()
            )
        }
    ) { paddingValues ->
        if (characters.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.Person,
                        contentDescription = null,
                        modifier = Modifier.size(sdp(64.dp)),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                    Spacer(modifier = Modifier.height(sdp(16.dp)))
                    Text("还没有角色", style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(sdp(8.dp)))
                    Button(onClick = { showCreateDialog = true }) {
                        Text("创建第一个角色")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(sdp(16.dp)),
                verticalArrangement = Arrangement.spacedBy(sdp(8.dp))
            ) {
                items(characters, key = { it.id }) { character ->
                    val isActive = character.id == activeCharacterId
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                        ),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(sdp(16.dp)),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                Icons.Outlined.Person,
                                contentDescription = null,
                                tint = if (isActive) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = sdp(4.dp), end = sdp(16.dp))
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        character.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Row {
                                        IconButton(onClick = {
                                            listViewModel.setActiveCharacter(character.id)
                                        }) {
                                            Icon(
                                                if (isActive) Icons.Outlined.CheckCircle else Icons.Outlined.AccountCircle,
                                                contentDescription = "设为微信聊天角色",
                                                tint = if (isActive) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton(onClick = {
                                            onNavigate(Screen.CharacterEdit.createRoute(character.id))
                                        }) {
                                            Icon(Icons.Outlined.Edit, contentDescription = "编辑")
                                        }
                                        IconButton(onClick = {
                                            characterToDelete = character
                                        }) {
                                            Icon(Icons.Outlined.Delete, contentDescription = "删除",
                                                tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    Text(
                                        if (character.personality.isNotBlank()) character.personality
                                        else character.speakingStyle.ifBlank { "未设置个性" },
                                        maxLines = 2,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (isActive) {
                                        SuggestionChip(
                                            onClick = {},
                                            label = { Text("微信聊天中", style = MaterialTheme.typography.labelSmall) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
