package com.kurisuapi.ui.screen.home

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kurisuapi.ui.component.EmotionBar
import com.kurisuapi.ui.navigation.Screen
import com.kurisuapi.ui.theme.*
import com.kurisuapi.ui.viewmodel.HomeViewModel
import com.kurisuapi.ui.viewmodel.WeChatViewModel
import com.kurisuapi.domain.bridge.ConnectionState
import com.kurisuapi.util.sdp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigate: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
    weChatViewModel: WeChatViewModel = hiltViewModel()
) {
    val character by viewModel.activeCharacter.collectAsState()
    val emotion by viewModel.emotion.collectAsState()
    val relationship by viewModel.relationship.collectAsState()
    val recentMessages by viewModel.recentMessages.collectAsState()
    val connectionState by weChatViewModel.connectionState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "红莉栖API",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                colors = com.kurisuapi.ui.theme.topBarColors()
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(sdp(16.dp)),
            verticalArrangement = Arrangement.spacedBy(sdp(16.dp))
        ) {
            // Status Cards
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(sdp(12.dp))
                ) {
                    StatusCard(
                        title = "微信",
                        status = when (connectionState) {
                            ConnectionState.DISCONNECTED -> "未连接"
                            ConnectionState.CONNECTING -> "连接中"
                            ConnectionState.WAITING_SCAN -> "等待扫码"
                            ConnectionState.SCANED -> "已扫码"
                            ConnectionState.LOGGED_IN -> "已登录"
                            ConnectionState.POLLING -> "已连接"
                            ConnectionState.ERROR -> "错误"
                        },
                        icon = Icons.Outlined.Chat,
                        color = if (connectionState == ConnectionState.POLLING || connectionState == ConnectionState.LOGGED_IN)
                            AppleGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f).clickable {
                            onNavigate(Screen.WeChatLogin.route)
                        }
                    )
                    StatusCard(
                        title = "记忆",
                        status = "管理记忆",
                        icon = Icons.Outlined.Psychology,
                        color = AppleIndigo,
                        modifier = Modifier.weight(1f).clickable {
                            character?.let { onNavigate(Screen.MemoryList.createRoute(it.id)) }
                        }
                    )
                }
            }

            // Active Character
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigate(Screen.CharacterList.route) },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                    ),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(modifier = Modifier.padding(sdp(16.dp))) {
                        val char = character
                        if (char != null) {
                            Text(
                                text = "当前角色: ${char.name}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (char.personality.isNotBlank()) {
                                Text(
                                    text = char.personality,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        } else {
                            Text(
                                text = "未选择角色",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "点击选择角色",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }

            // Emotion State
            val currentEmotion = emotion
            if (currentEmotion != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                        ),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(modifier = Modifier.padding(sdp(16.dp))) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("情绪状态", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                TextButton(onClick = {
                                    character?.let { onNavigate(Screen.EmotionDetail.createRoute(it.id)) }
                                }) {
                                    Text("详情")
                                }
                            }
                            Spacer(modifier = Modifier.height(sdp(8.dp)))
                            EmotionBar(label = "开心", value = currentEmotion.happy, color = AppleGreen)
                            EmotionBar(label = "难过", value = currentEmotion.sad, color = AppleBlue)
                            EmotionBar(label = "生气", value = currentEmotion.angry, color = AppleRed)
                            EmotionBar(label = "孤独", value = currentEmotion.lonely, color = AppleIndigo)
                            EmotionBar(label = "好感", value = currentEmotion.affection, color = ApplePink)
                        }
                    }
                }
            }

            // Relationship
            val currentRelationship = relationship
            if (currentRelationship != null) {
                item {
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
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("关系等级", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = currentRelationship.level,
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("关系值", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    text = "${currentRelationship.score}",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            TextButton(onClick = {
                                character?.let { onNavigate(Screen.RelationshipDetail.createRoute(it.id)) }
                            }) {
                                Text("详情")
                            }
                        }
                    }
                }
            }

            // Recent Messages
            if (recentMessages.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("最近消息", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        character?.let {
                            TextButton(onClick = { onNavigate(Screen.ConversationList.createRoute(it.id)) }) {
                                Text("全部聊天")
                            }
                        }
                    }
                }
                items(recentMessages.reversed(), key = { it.id }) { msg ->
                    ListItem(
                        headlineContent = { Text(msg.content, maxLines = 1) },
                        supportingContent = { Text(if (msg.sender == "user") "我" else character?.name ?: "AI") },
                        leadingContent = {
                            Icon(
                                if (msg.sender == "user") Icons.Outlined.Person else Icons.Outlined.SmartToy,
                                contentDescription = if (msg.sender == "user") "用户" else "AI"
                            )
                        }
                    )
                }
            } else {
                // 空状态提示
                item {
                    Text(
                        text = "暂无消息",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier.padding(vertical = sdp(8.dp))
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusCard(
    title: String,
    status: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(sdp(16.dp)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = title, tint = color, modifier = Modifier.size(sdp(28.dp)))
            Spacer(modifier = Modifier.height(sdp(6.dp)))
            Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(status, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = color)
        }
    }
}
