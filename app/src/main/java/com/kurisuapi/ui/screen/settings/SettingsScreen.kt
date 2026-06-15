package com.kurisuapi.ui.screen.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import com.kurisuapi.BuildConfig
import com.kurisuapi.domain.bridge.ConnectionState
import com.kurisuapi.ui.navigation.Screen
import com.kurisuapi.ui.viewmodel.WeChatViewModel
import com.kurisuapi.util.sdp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigate: (String) -> Unit,
    weChatViewModel: WeChatViewModel = hiltViewModel()
) {
    val connectionState by weChatViewModel.connectionState.collectAsState()
    val botProactiveEnabled by weChatViewModel.botProactiveEnabled.collectAsState()
    val botProactiveInterval by weChatViewModel.botProactiveInterval.collectAsState()
    val showThinking by weChatViewModel.showThinking.collectAsState()
    val autoMemoryEnabled by weChatViewModel.autoMemoryEnabled.collectAsState()
    var intervalText by remember { mutableStateOf(botProactiveInterval.toString()) }
    LaunchedEffect(botProactiveInterval) {
        intervalText = botProactiveInterval.toString()
    }
    LaunchedEffect(Unit) {
        weChatViewModel.loadBackgroundModel()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置", fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(sdp(16.dp)),
            verticalArrangement = Arrangement.spacedBy(sdp(8.dp))
        ) {
            // Section: AI Provider
            item {
                Text(
                    "AI 模型",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = sdp(8.dp))
                )
            }
            item {
                SettingsItem(
                    icon = Icons.Outlined.Cloud,
                    title = "模型管理",
                    subtitle = "管理 AI 服务提供商",
                    onClick = { onNavigate(Screen.ProviderList.route) }
                )
            }

            // Section: WeChat
            item {
                Text(
                    "微信连接",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = sdp(8.dp))
                )
            }
            item {
                SettingsItem(
                    icon = Icons.Outlined.Chat,
                    title = "微信连接",
                    subtitle = when (connectionState) {
                        ConnectionState.POLLING, ConnectionState.LOGGED_IN -> "已连接"
                        ConnectionState.WAITING_SCAN -> "等待扫码"
                        ConnectionState.CONNECTING -> "连接中..."
                        else -> "未连接"
                    },
                    trailing = {
                        if (connectionState == ConnectionState.POLLING || connectionState == ConnectionState.LOGGED_IN) {
                            Icon(
                                Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    onClick = { onNavigate(Screen.WeChatLogin.route) }
                )
            }

            // Section: Bot Behavior
            item {
                Text(
                    "机器人行为",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = sdp(8.dp))
                )
            }
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
                            Column(modifier = Modifier.weight(1f)) {
                                Text("AI 主动消息", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "长时间不聊天时，AI 主动发消息找你",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                            Switch(
                                checked = botProactiveEnabled,
                                onCheckedChange = { weChatViewModel.setBotProactiveEnabled(it) }
                            )
                        }

                        if (botProactiveEnabled) {
                            Spacer(modifier = Modifier.height(sdp(12.dp)))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("沉默间隔", style = MaterialTheme.typography.bodyMedium)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(sdp(8.dp))
                                ) {
                                    OutlinedTextField(
                                        value = intervalText,
                                        onValueChange = { value ->
                                            intervalText = value.filter { it.isDigit() }
                                        },
                                        modifier = Modifier.width(sdp(80.dp))
                                            .onFocusChanged { focusState ->
                                                if (!focusState.isFocused) {
                                                    intervalText.toIntOrNull()?.let {
                                                        weChatViewModel.setBotProactiveInterval(it)
                                                    }
                                                    intervalText = botProactiveInterval.toString()
                                                }
                                            },
                                        singleLine = true
                                    )
                                    Text(
                                        "分钟",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text("微信中显示 AI 思考过程", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "开启后，AI 的推理过程会作为单独消息发送到微信",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Switch(
                            checked = showThinking,
                            onCheckedChange = { weChatViewModel.setShowThinking(it) }
                        )
                    }
                }
            }
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text("AI 自动记忆", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "开启后，AI 会自动从聊天中记住关于你的事，并不断完善对你的了解",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Switch(
                            checked = autoMemoryEnabled,
                            onCheckedChange = { weChatViewModel.setAutoMemoryEnabled(it) }
                        )
                    }
                }
            }

            // 后台任务专用模型
            item {
                val backgroundModel by weChatViewModel.backgroundModel.collectAsState()
                var bgModelText by remember(backgroundModel) { mutableStateOf(backgroundModel) }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                    ),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(modifier = Modifier.padding(sdp(16.dp))) {
                        Text("后台任务模型", style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(sdp(4.dp)))
                        Text(
                            "记忆提取、对话摘要等后台任务使用的模型。留空则使用默认模型。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(sdp(8.dp)))
                        OutlinedTextField(
                            value = bgModelText,
                            onValueChange = { bgModelText = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("留空使用默认") },
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(sdp(8.dp)))
                        Button(
                            onClick = { weChatViewModel.setBackgroundModel(bgModelText.trim()) },
                            enabled = bgModelText.trim() != backgroundModel
                        ) {
                            Text("保存")
                        }
                    }
                }
            }

            // Section: System
            item {
                Text(
                    "系统",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = sdp(8.dp))
                )
            }
            item {
                SettingsItem(
                    icon = Icons.Outlined.Info,
                    title = "关于此应用",
                    subtitle = "红莉栖API v${BuildConfig.VERSION_NAME}",
                    onClick = { onNavigate(Screen.SystemSettings.route) }
                )
            }
        }
    }
}

@Composable
private fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = subtitle?.let { { Text(it) } },
            leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            trailingContent = trailing ?: {
                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )
    }
}
