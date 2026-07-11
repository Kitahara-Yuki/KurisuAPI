package com.kurisuapi.ui.screen.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import com.kurisuapi.BuildConfig
import com.kurisuapi.domain.bridge.ConnectionState
import com.kurisuapi.ui.component.TokenUsageDialog
import com.kurisuapi.ui.navigation.Screen
import com.kurisuapi.ui.viewmodel.TokenUsageViewModel
import com.kurisuapi.ui.viewmodel.WeChatViewModel
import com.kurisuapi.util.sdp
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigate: (String) -> Unit,
    onNavigateBack: (() -> Unit)? = null,
    weChatViewModel: WeChatViewModel = hiltViewModel()
) {
    val tokenUsageViewModel: TokenUsageViewModel = hiltViewModel()
    val connectionState by weChatViewModel.connectionState.collectAsState()
    val botProactiveEnabled by weChatViewModel.botProactiveEnabled.collectAsState()
    val botProactiveInterval by weChatViewModel.botProactiveInterval.collectAsState()
    val showThinking by weChatViewModel.showThinking.collectAsState()
    val autoMemoryEnabled by weChatViewModel.autoMemoryEnabled.collectAsState()
    val circadianEnabled by weChatViewModel.circadianEnabled.collectAsState()
    val proactiveMaxPerDay by weChatViewModel.proactiveMaxPerDay.collectAsState()
    val proactiveTodayCount by weChatViewModel.proactiveTodayCount.collectAsState()
    val proactiveQuietStart by weChatViewModel.proactiveQuietStart.collectAsState()
    val proactiveQuietEnd by weChatViewModel.proactiveQuietEnd.collectAsState()
    var showHelpDialog by remember { mutableStateOf(false) }
    var showProactiveDialog by remember { mutableStateOf(false) }
    var showTokenDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    // 弹窗内的编辑状态
    var dInterval by remember { mutableStateOf(botProactiveInterval.toString()) }
    var dMax by remember { mutableStateOf(proactiveMaxPerDay.toString()) }
    var dQuietS by remember { mutableStateOf(proactiveQuietStart.toString()) }
    var dQuietE by remember { mutableStateOf(proactiveQuietEnd.toString()) }
    LaunchedEffect(botProactiveInterval) { dInterval = botProactiveInterval.toString() }
    LaunchedEffect(proactiveMaxPerDay) { dMax = proactiveMaxPerDay.toString() }
    LaunchedEffect(proactiveQuietStart) { dQuietS = proactiveQuietStart.toString() }
    LaunchedEffect(proactiveQuietEnd) { dQuietE = proactiveQuietEnd.toString() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showHelpDialog = true },
                        modifier = Modifier.padding(end = sdp(8.dp))
                    ) {
                        Icon(
                            Icons.Outlined.HelpOutline,
                            contentDescription = "使用帮助",
                            tint = Color.White
                        )
                    }
                },
                colors = com.kurisuapi.ui.theme.topBarColors()
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
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
                            Text(
                                "今日已发: $proactiveTodayCount / $proactiveMaxPerDay  ·  间隔: ${botProactiveInterval}分钟",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (proactiveTodayCount >= proactiveMaxPerDay)
                                    MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            if (proactiveTodayCount < proactiveMaxPerDay) {
                                val now = java.time.LocalTime.now()
                                val next = now.plusMinutes(botProactiveInterval.toLong())
                                Text(
                                    "预计下次: 约 ${String.format("%02d:%02d", next.hour, next.minute)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                    fontWeight = FontWeight.Medium
                                )
                            } else {
                                Text("今日已达上限，明天恢复",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
                            }
                            Spacer(modifier = Modifier.height(sdp(8.dp)))
                            OutlinedButton(
                                onClick = { showProactiveDialog = true },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("详细设置") }
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
                            Text("昼夜节律感知", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "开启后，角色的情绪基线和回复速度会随真实时间自然变化（清晨慵懒、下午活跃、深夜安静）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Switch(
                            checked = circadianEnabled,
                            onCheckedChange = { weChatViewModel.setCircadianEnabled(it) }
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
                    icon = Icons.Outlined.DataUsage,
                    title = "Tokens 用量信息",
                    subtitle = "查看缓存命中率和 API 调用节省量",
                    onClick = { showTokenDialog = true }
                )
            }
            item {
                SettingsItem(
                    icon = Icons.Outlined.Article,
                    title = "软件日志",
                    subtitle = "查看应用运行日志",
                    onClick = { onNavigate(Screen.LogViewer.route) }
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

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        if (showProactiveDialog) {
            ProactiveSettingsDialog(
                interval = dInterval, onIntervalChange = { dInterval = it.filter { it.isDigit() } },
                maxPerDay = dMax, onMaxChange = { dMax = it.filter { it.isDigit() } },
                quietStart = dQuietS, onQuietStartChange = { dQuietS = it.filter { it.isDigit() } },
                quietEnd = dQuietE, onQuietEndChange = { dQuietE = it.filter { it.isDigit() } },
                onSave = {
                    // 范围验证
                    val intervalVal = dInterval.toIntOrNull()
                    val maxVal = dMax.toIntOrNull()
                    val quietSVal = dQuietS.toIntOrNull()
                    val quietEVal = dQuietE.toIntOrNull()
                    if (intervalVal == null || intervalVal !in 10..1440 ||
                        maxVal == null || maxVal !in 1..20 ||
                        quietSVal == null || quietSVal !in 0..23 ||
                        quietEVal == null || quietEVal !in 0..23
                    ) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("输入不合法：间隔 10-1440 分钟，每日上限 1-20 条，时段 0-23 点")
                        }
                        return@ProactiveSettingsDialog
                    }
                    weChatViewModel.setBotProactiveInterval(intervalVal)
                    weChatViewModel.setProactiveMaxPerDay(maxVal)
                    weChatViewModel.setProactiveQuietStart(quietSVal)
                    weChatViewModel.setProactiveQuietEnd(quietEVal)
                    showProactiveDialog = false
                    coroutineScope.launch {
                        val next = java.time.LocalTime.now().plusMinutes(intervalVal.toLong())
                        snackbarHostState.showSnackbar(
                            "设置已保存 · 预计下次消息: ${String.format("%02d:%02d", next.hour, next.minute)}"
                        )
                    }
                },
                onDismiss = { showProactiveDialog = false }
            )
        }

        if (showHelpDialog) {
            HelpDialog(onDismiss = { showHelpDialog = false })
        }

        if (showTokenDialog) {
            TokenUsageDialog(
                viewModel = tokenUsageViewModel,
                onDismiss = { showTokenDialog = false }
            )
        }
    }
}

@Composable
private fun ProactiveSettingsDialog(
    interval: String, onIntervalChange: (String) -> Unit,
    maxPerDay: String, onMaxChange: (String) -> Unit,
    quietStart: String, onQuietStartChange: (String) -> Unit,
    quietEnd: String, onQuietEndChange: (String) -> Unit,
    onSave: () -> Unit, onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = { Text("主动消息设置", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(sdp(12.dp))) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("消息间隔\n(10-1440 分钟)\n用户最后一条消息后多久触发", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(value = interval, onValueChange = onIntervalChange, modifier = Modifier.width(sdp(80.dp)), singleLine = true)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("每日上限\n(1-20 条)", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(value = maxPerDay, onValueChange = onMaxChange, modifier = Modifier.width(sdp(80.dp)), singleLine = true)
                }
                Text("安静时段（此时间内不发送）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(sdp(8.dp)), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = quietStart, onValueChange = onQuietStartChange, modifier = Modifier.weight(1f), singleLine = true, label = { Text("开始") })
                    Text("—")
                    OutlinedTextField(value = quietEnd, onValueChange = onQuietEndChange, modifier = Modifier.weight(1f), singleLine = true, label = { Text("结束") })
                    Text("点")
                }
            }
        },
        confirmButton = { TextButton(onClick = onSave) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
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

@Composable
private fun HelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = { Text("常见问题") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(sdp(8.dp))
            ) {
                // 第1条：怎么开始聊天
                Text("Q: 怎么开始和 AI 聊天？", fontWeight = FontWeight.SemiBold)
                Text("A: 先确保在「首页」选了一个角色。如果没有角色，去「首页」→ 点击角色区域 → 创建或 AI 生成一个角色。然后切到「对话」标签 → 点击右下角新建对话 → 选一个模式，就可以开始聊了。")
                Spacer(modifier = Modifier.height(sdp(4.dp)))

                // 第2条：怎么添加 API
                Text("Q: 怎么添加自己的 AI 服务（API）？", fontWeight = FontWeight.SemiBold)
                Text("A: 去「我」→「设置」→「API 服务商管理」→ 点击右下角 + 号。选一个服务商类型，填上 API Key 和接口地址，保存后在首页或聊天界面左上角切换即可。支持 OpenAI 兼容、Anthropic、Gemini 等主流接口。")
                Spacer(modifier = Modifier.height(sdp(4.dp)))

                // 第3条：记忆功能
                Text("Q: AI 会自动记住我说过的话吗？", fontWeight = FontWeight.SemiBold)
                Text("A: 会的。聊大约 6-8 条消息后，AI 会自动从对话中提取关于你的事（喜好、计划、个人信息等），存到「记忆」页面。你可以在设置 → 记忆设置里关闭自动提取，也可以手动删除或编辑单条记忆。")
                Spacer(modifier = Modifier.height(sdp(4.dp)))

                // 第4条：对话模式
                Text("Q: 对话模式和故事模式有什么区别？", fontWeight = FontWeight.SemiBold)
                Text("A: 对话模式是纯聊天，AI 以角色身份回复你，自动过滤动作描写，更像真人发消息。故事模式保留完整的叙述性文字，适合创作和角色扮演。创建对话时选定后不能改，想换就新建一个对话。")
                Spacer(modifier = Modifier.height(sdp(4.dp)))

                // 第5条：换主题
                Text("Q: 怎么换主题、换颜色？", fontWeight = FontWeight.SemiBold)
                Text("A: 去「我」→「主题配置」→ 选一个内置主题，或点击「新建主题」自定义。你可以调种子色（决定整体色调）、顶栏颜色、聊天气泡颜色、聊天背景颜色，甚至设置一张背景图片。自定义主题不受浅色/深色模式影响。")
                Spacer(modifier = Modifier.height(sdp(4.dp)))

                // 第6条：微信机器人
                Text("Q: 怎么让 AI 在微信里回复我？", fontWeight = FontWeight.SemiBold)
                Text("A: 去「我」→「微信连接」→ 扫码登录。登录后在首页打开「微信自动回复」开关，选择要用的角色和对话。别人（或你自己）给这个微信号发消息，AI 会自动回复。注意：需要一台单独的微信小号。")
                Spacer(modifier = Modifier.height(sdp(4.dp)))

                // 第7条：上下文用量
                Text("Q: 聊天界面顶部的环是什么？为什么变红？", fontWeight = FontWeight.SemiBold)
                Text("A: 那是上下文用量环，显示当前对话占用了多少 AI 记忆空间。绿色正常，黄色代表快满了，红色代表超过安全线。满了之后 AI 可能会「失忆」或出现幻觉。点击它可以看到详情，建议开启新对话，或在设置中调大上下文长度。")
                Spacer(modifier = Modifier.height(sdp(4.dp)))

                // 第8条：角色设定
                Text("Q: 怎么让 AI 角色更像真人？", fontWeight = FontWeight.SemiBold)
                Text("A: 编辑角色时，重点填好三个地方：1）性格和说话风格——越具体越好；2）系统提示词——告诉 AI 它是谁、怎么说话；3）情绪和关系——AI 会随着对话自动调整对你的态度和心情。聊得越多，AI 越懂你。")
                Spacer(modifier = Modifier.height(sdp(4.dp)))

                // 第9条：删除与恢复
                Text("Q: 删了对话能恢复吗？", fontWeight = FontWeight.SemiBold)
                Text("A: 对话删除后会在回收站保留一段时间，去对话列表 → 右上角菜单 → 查看已删除的对话，可以恢复。过期后会被永久清理。重要对话建议手动归档而非删除。")
                Spacer(modifier = Modifier.height(sdp(4.dp)))

                // 第10条：消息没发出去
                Text("Q: 消息发不出去 / 一直转圈怎么办？", fontWeight = FontWeight.SemiBold)
                Text("A: 依次检查：1）API Key 有没有填对、有没有过期；2）网络是否正常；3）API 服务商的余额是否充足；4）当前选的是不是正确的 API 服务商。如果都没问题，去设置里关掉「思考模式」试试（思考模式会多消耗 Token 且延迟更高）。")
                Spacer(modifier = Modifier.height(sdp(4.dp)))

                // 第11条：数据安全
                Text("Q: 我的 API Key 和聊天记录安全吗？", fontWeight = FontWeight.SemiBold)
                Text("A: API Key 存在手机加密存储里，不会上传到任何第三方。聊天记录和记忆存在手机本地数据库，只有你自己能看到。卸载 App 会删除所有数据，建议定期备份。")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("知道了") } }
    )
}
