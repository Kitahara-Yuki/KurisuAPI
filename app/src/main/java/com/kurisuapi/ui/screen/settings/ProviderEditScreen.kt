package com.kurisuapi.ui.screen.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kurisuapi.data.entity.ProviderEntity
import com.kurisuapi.domain.provider.ConnectionTestResult
import com.kurisuapi.ui.viewmodel.ProviderViewModel
import com.kurisuapi.util.sdp

private data class ApiFormat(val type: String, val label: String, val description: String)

private val API_FORMATS = listOf(
    ApiFormat("openai_compatible", "标准格式", "兼容 DeepSeek / OpenAI / 大多数 API"),
    ApiFormat("anthropic", "Claude 格式", "Anthropic Claude 系列 API"),
    ApiFormat("gemini", "Gemini 格式", "Google Gemini 系列 API"),
)

private val FORMAT_URL_MAP = mapOf(
    "DeepSeek" to mapOf("openai_compatible" to "https://api.deepseek.com/"),
    "OpenAI" to mapOf("openai_compatible" to "https://api.openai.com/v1/"),
    "Anthropic" to mapOf("anthropic" to "https://api.anthropic.com/"),
    "Google Gemini" to mapOf("gemini" to "https://generativelanguage.googleapis.com/v1beta/"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderEditScreen(
    providerId: Long,
    onNavigateBack: () -> Unit,
    viewModel: ProviderViewModel = hiltViewModel()
) {
    val editingProvider by viewModel.editingProvider.collectAsState()
    val models by viewModel.filteredModels.collectAsState()
    val testResult by viewModel.connectionTestResult.collectAsState()
    val isTesting by viewModel.isTesting.collectAsState()
    val isFetching by viewModel.isFetchingModels.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val message by viewModel.message.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val deprecationWarning by viewModel.deprecationWarning.collectAsState()

    val isBuiltIn = editingProvider?.isBuiltIn == true

    var name by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf("openai_compatible") }
    var baseUrl by rememberSaveable { mutableStateOf("") }
    var apiKey by rememberSaveable { mutableStateOf("") }
    var modelsUrlOverride by rememberSaveable { mutableStateOf("") }
    var model by rememberSaveable { mutableStateOf("") }
    var temperature by rememberSaveable { mutableStateOf("0.7") }
    var maxTokens by rememberSaveable { mutableStateOf("2048") }
    var isDefault by rememberSaveable { mutableStateOf(false) }
    var thinkingEnabled by rememberSaveable { mutableStateOf(true) }
    var reasoningEffort by rememberSaveable { mutableStateOf("high") }
    var thinkingBudgetTokens by rememberSaveable { mutableStateOf("2048") }
    var contextWindow by rememberSaveable { mutableStateOf("") }
    var showAddModelDialog by rememberSaveable { mutableStateOf(false) }
    var showAdvancedFormat by rememberSaveable { mutableStateOf(false) }
    var showApiKey by rememberSaveable { mutableStateOf(false) }
    var formInitialized by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(providerId) {
        if (providerId > 0) viewModel.loadProvider(providerId)
        else viewModel.createNewProvider()
    }

    LaunchedEffect(editingProvider) {
        if (!formInitialized && editingProvider != null) {
            editingProvider?.let {
                name = it.name; type = it.type; baseUrl = it.baseUrl; apiKey = it.apiKey
                modelsUrlOverride = it.modelsUrlOverride ?: ""; model = it.model
                temperature = it.temperature.toString(); maxTokens = it.maxTokens.toString()
                isDefault = it.isDefault; thinkingEnabled = it.thinkingEnabled
                reasoningEffort = it.reasoningEffort
                thinkingBudgetTokens = if (it.thinkingBudgetTokens > 0) it.thinkingBudgetTokens.toString() else ""
                contextWindow = if (it.contextWindow > 0) it.contextWindow.toString() else ""
            }
            formInitialized = true
        }
    }

    LaunchedEffect(message) {
        message?.let { kotlinx.coroutines.delay(5000); viewModel.clearMessage() }
    }

    fun onFormatChanged(newType: String) {
        type = newType
        val urlMap = FORMAT_URL_MAP[name]
        if (urlMap != null) urlMap[newType]?.let { baseUrl = it }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isBuiltIn || providerId > 0) name.ifBlank { editingProvider?.name ?: "编辑模型管理" }
                        else "添加模型管理",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.saveProvider(
                            id = if (providerId > 0) providerId else null,
                            name = name, type = type, baseUrl = baseUrl, apiKey = apiKey,
                            modelsUrlOverride = modelsUrlOverride.ifBlank { null },
                            model = model, temperature = temperature.toDoubleOrNull() ?: 0.7,
                            maxTokens = maxTokens.toIntOrNull() ?: 2048, isDefault = isDefault,
                            thinkingEnabled = thinkingEnabled, reasoningEffort = reasoningEffort,
                            thinkingBudgetTokens = thinkingBudgetTokens.toIntOrNull() ?: 0,
                            contextWindow = contextWindow.toLongOrNull() ?: 0,
                            onDone = onNavigateBack
                        )
                    }) { Icon(Icons.Outlined.Save, contentDescription = "保存") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(sdp(16.dp)),
            verticalArrangement = Arrangement.spacedBy(sdp(16.dp))
        ) {
            if (!isBuiltIn) {
                item { Text("基本信息", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
                item {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("名称") },
                        placeholder = { Text("我的 API") }, modifier = Modifier.fillMaxWidth())
                }
            }

            item {
                OutlinedTextField(value = apiKey, onValueChange = { apiKey = it }, label = { Text("API Key") },
                    placeholder = { Text("sk-...") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = { TextButton(onClick = { showApiKey = !showApiKey }) { Text(if (showApiKey) "隐藏" else "显示") } })
            }

            if (!isBuiltIn) {
                item { Text("API 格式", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(modifier = Modifier.padding(sdp(12.dp))) {
                            API_FORMATS.forEach { format ->
                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = sdp(4.dp)),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = type == format.type, onClick = { onFormatChanged(format.type) })
                                    Column(modifier = Modifier.padding(start = sdp(8.dp))) {
                                        Text(format.label, fontWeight = FontWeight.Medium)
                                        Text(format.description, style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    OutlinedTextField(value = baseUrl, onValueChange = { baseUrl = it }, label = { Text("Base URL") },
                        placeholder = { Text("https://api.example.com/v1/") }, modifier = Modifier.fillMaxWidth())
                }
            } else {
                item {
                    OutlinedTextField(value = baseUrl, onValueChange = { baseUrl = it }, label = { Text("Base URL") },
                        modifier = Modifier.fillMaxWidth())
                }
                val availableFormats = FORMAT_URL_MAP[name]
                if (availableFormats != null && availableFormats.size > 1) {
                    item {
                        TextButton(onClick = { showAdvancedFormat = !showAdvancedFormat }) {
                            Icon(if (showAdvancedFormat) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                contentDescription = null)
                            Spacer(modifier = Modifier.width(sdp(4.dp)))
                            Text("高级: API 格式切换")
                        }
                    }
                    item {
                        AnimatedVisibility(visible = showAdvancedFormat) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Column(modifier = Modifier.padding(sdp(12.dp))) {
                                    Text("切换 API 格式会自动更改 Base URL", style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                    Spacer(modifier = Modifier.height(sdp(8.dp)))
                                    availableFormats.forEach { (formatType, url) ->
                                        val formatInfo = API_FORMATS.find { it.type == formatType }
                                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = sdp(4.dp)),
                                            verticalAlignment = Alignment.CenterVertically) {
                                            RadioButton(selected = type == formatType,
                                                onClick = { onFormatChanged(formatType) })
                                            Column(modifier = Modifier.padding(start = sdp(8.dp))) {
                                                Text(formatInfo?.label ?: formatType, fontWeight = FontWeight.Medium)
                                                Text(url, style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item { Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = isDefault, onCheckedChange = { isDefault = it }); Text("设为默认")
            }}

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                    ),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(modifier = Modifier.padding(sdp(16.dp))) {
                        Row(modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(if (thinkingEnabled) "✓ 深度思考" else "✗ 深度思考已关闭",
                                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text(if (thinkingEnabled) "模型将先进行思维链推理再回复" else "模型将直接回复，不进行思维链推理",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            }
                            Switch(checked = thinkingEnabled, onCheckedChange = { thinkingEnabled = it })
                        }
                        AnimatedVisibility(visible = thinkingEnabled) {
                            Column(modifier = Modifier.padding(top = sdp(12.dp))) {
                                if (type == "openai_compatible") {
                                    Text("推理强度", style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                                    Spacer(modifier = Modifier.height(sdp(8.dp)))
                                    Row(horizontalArrangement = Arrangement.spacedBy(sdp(8.dp))) {
                                        listOf("low" to "低", "medium" to "中", "high" to "高").forEach { (value, label) ->
                                            FilterChip(selected = reasoningEffort == value,
                                                onClick = { reasoningEffort = value }, label = { Text(label) },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = MaterialTheme.colorScheme.primary))
                                        }
                                    }
                                }
                                if (type == "anthropic" || type == "gemini") {
                                    Text("思考预算 (tokens)", style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                                    Spacer(modifier = Modifier.height(sdp(4.dp)))
                                    OutlinedTextField(
                                        value = thinkingBudgetTokens,
                                        onValueChange = { v -> thinkingBudgetTokens = v.filter { it.isDigit() } },
                                        label = { Text("思考预算") }, placeholder = { Text("留空自动计算") },
                                        modifier = Modifier.width(sdp(160.dp)), singleLine = true)
                                }
                            }
                        }
                    }
                }
            }

            item { Text("模型与参数", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            item {
                OutlinedTextField(value = model, onValueChange = { model = it }, label = { Text("当前模型") },
                    placeholder = { Text("获取模型后自动选择，或手动输入") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(sdp(12.dp))) {
                    OutlinedTextField(value = temperature, onValueChange = { temperature = it }, label = { Text("温度") },
                        placeholder = { Text("0.7") }, modifier = Modifier.weight(1f), singleLine = true)
                    var showMaxPresets by remember { mutableStateOf(false) }
                    OutlinedTextField(value = maxTokens, onValueChange = { maxTokens = it },
                        label = { Text("最大回复长度") }, placeholder = { Text("2048") },
                        trailingIcon = {
                            Box {
                                IconButton(onClick = { showMaxPresets = true }, modifier = Modifier.size(sdp(40.dp))) {
                                    Icon(Icons.Outlined.ArrowDropDown, contentDescription = "预设选项",
                                        modifier = Modifier.size(sdp(24.dp)))
                                }
                                DropdownMenu(expanded = showMaxPresets,
                                    onDismissRequest = { showMaxPresets = false }) {
                                    listOf(
                                        Triple("512", "512 tokens", "节约"), Triple("1024", "1,024 tokens", "平衡"),
                                        Triple("2048", "2,048 tokens", "推荐"), Triple("4096", "4,096 tokens", "剧情"),
                                        Triple("8192", "8,192 tokens", "不可思议"),
                                    ).forEach { (value, desc, tag) ->
                                        DropdownMenuItem(text = {
                                            Column {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(value, fontWeight = FontWeight.Medium)
                                                    Spacer(Modifier.width(sdp(8.dp)))
                                                    Text(desc, style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                                }
                                                Spacer(Modifier.height(sdp(4.dp)))
                                                AssistChip(onClick = {}, label = { Text(tag, style = MaterialTheme.typography.labelSmall) },
                                                    modifier = Modifier.height(sdp(24.dp)))
                                            }
                                        }, onClick = { maxTokens = value; showMaxPresets = false },
                                            leadingIcon = { if (maxTokens == value) Icon(Icons.Outlined.Check,
                                                contentDescription = null, tint = MaterialTheme.colorScheme.primary) })
                                    }
                                }
                            }
                        }, modifier = Modifier.weight(1f), singleLine = true)
                }
            }

            // Context window - 带预设选项的下拉按钮
            item {
                var showContextPresets by remember { mutableStateOf(false) }
                OutlinedTextField(
                    value = contextWindow, onValueChange = { contextWindow = it },
                    label = { Text("上下文长度") }, placeholder = { Text("0 表示不限制") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    trailingIcon = {
                        Box {
                            IconButton(onClick = { showContextPresets = true }, modifier = Modifier.size(sdp(40.dp))) {
                                Icon(Icons.Outlined.ArrowDropDown, contentDescription = "预设选项",
                                    modifier = Modifier.size(sdp(24.dp)))
                            }
                            DropdownMenu(expanded = showContextPresets,
                                onDismissRequest = { showContextPresets = false }) {
                                listOf(
                                    Triple("32768", "32K", ""),
                                    Triple("65536", "64K", ""),
                                    Triple("131072", "128K", ""),
                                    Triple("524288", "512K", ""),
                                    Triple("1048576", "1M", "需模型支持"),
                                ).forEach { (value, label, tag) ->
                                    DropdownMenuItem(text = {
                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(label, fontWeight = FontWeight.Medium)
                                                Spacer(Modifier.width(sdp(8.dp)))
                                                Text("$value tokens", style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                            }
                                            if (tag.isNotEmpty()) {
                                                Spacer(Modifier.height(sdp(4.dp)))
                                                AssistChip(onClick = {}, label = { Text(tag, style = MaterialTheme.typography.labelSmall) },
                                                    modifier = Modifier.height(sdp(24.dp)))
                                            }
                                        }
                                    }, onClick = { contextWindow = value; showContextPresets = false },
                                        leadingIcon = { if (contextWindow == value) Icon(Icons.Outlined.Check,
                                            contentDescription = null, tint = MaterialTheme.colorScheme.primary) })
                                }
                            }
                        }
                    }
                )
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(sdp(8.dp))) {
                    Button(
                        onClick = {
                            viewModel.testConnection(ProviderEntity(name = name, type = type, baseUrl = baseUrl,
                                apiKey = apiKey, modelsUrlOverride = modelsUrlOverride.ifBlank { null }))
                        },
                        enabled = !isTesting && apiKey.isNotBlank(), modifier = Modifier.weight(1f)
                    ) {
                        if (isTesting) { CircularProgressIndicator(modifier = Modifier.size(sdp(16.dp)), strokeWidth = 2.dp); Spacer(modifier = Modifier.width(sdp(8.dp))) }
                        Text("测试连接")
                    }
                    OutlinedButton(
                        onClick = {
                            viewModel.fetchModels(ProviderEntity(id = providerId, name = name, type = type,
                                baseUrl = baseUrl, apiKey = apiKey, modelsUrlOverride = modelsUrlOverride.ifBlank { null }))
                        },
                        enabled = !isFetching && !isSyncing && apiKey.isNotBlank(), modifier = Modifier.weight(1f)
                    ) {
                        if (isFetching || isSyncing) { CircularProgressIndicator(modifier = Modifier.size(sdp(16.dp)), strokeWidth = 2.dp); Spacer(modifier = Modifier.width(sdp(8.dp))) }
                        Text("同步模型")
                    }
                }
            }

            testResult?.let { result -> item { ConnectionTestResultCard(result) } }

            message?.let { msg ->
                item {
                    val isError = msg.startsWith("获取失败") || msg.contains("错误") || msg.contains("失败")
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isError) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                            else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        ),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(msg, modifier = Modifier.padding(sdp(12.dp)),
                            color = if (isError) MaterialTheme.colorScheme.onErrorContainer
                            else MaterialTheme.colorScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            deprecationWarning?.let { warning ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Row(modifier = Modifier.padding(sdp(12.dp)), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(sdp(8.dp)))
                            Text(warning, style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("模型列表 (${models.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = { if (providerId > 0) showAddModelDialog = true }) {
                        Icon(Icons.Outlined.Add, contentDescription = null); Text("手动添加")
                    }
                }
            }

            item {
                OutlinedTextField(value = searchQuery, onValueChange = { viewModel.updateSearchQuery(it) },
                    label = { Text("搜索模型") }, placeholder = { Text("输入模型名称筛选") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    trailingIcon = { if (searchQuery.isNotBlank()) IconButton(onClick = { viewModel.updateSearchQuery("") }) { Icon(Icons.Outlined.Clear, contentDescription = "清除") } })
            }

            items(models, key = { it.modelId }) { modelItem ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
                    shape = MaterialTheme.shapes.medium
                ) {
                    ListItem(
                        headlineContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(modelItem.displayName)
                                if (modelItem.status == "deprecated") {
                                    Spacer(modifier = Modifier.width(sdp(8.dp)))
                                    SuggestionChip(onClick = {}, label = {
                                        Text("⚠ ${modelItem.deprecatedAt ?: "弃用"}", style = MaterialTheme.typography.labelSmall)
                                    }, colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer))
                                }
                            }
                        },
                        supportingContent = {
                            Column {
                                Text(modelItem.modelId, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                Row(horizontalArrangement = Arrangement.spacedBy(sdp(4.dp)),
                                    modifier = Modifier.padding(top = sdp(4.dp))) {
                                    if (modelItem.supportsReasoning)
                                        AssistChip(onClick = {}, label = { Text("✓ 深度思考", style = MaterialTheme.typography.labelSmall) })
                                    if (modelItem.supportsTools)
                                        AssistChip(onClick = {}, label = { Text("Tool Calls", style = MaterialTheme.typography.labelSmall) })
                                    if (modelItem.supportsVision)
                                        AssistChip(onClick = {}, label = { Text("视觉", style = MaterialTheme.typography.labelSmall) })
                                    if (modelItem.contextWindow > 0)
                                        AssistChip(onClick = {}, label = { Text("${formatTokenCount(modelItem.contextWindow)} ctx", style = MaterialTheme.typography.labelSmall) })
                                }
                            }
                        },
                        trailingContent = {
                            IconButton(onClick = { viewModel.deleteModel(modelItem) }) {
                                Icon(Icons.Outlined.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(sdp(240.dp))) }
        }
    }

    if (showAddModelDialog) {
        if (providerId <= 0) { showAddModelDialog = false }
        else {
            AddModelDialog(
                onDismiss = { showAddModelDialog = false },
                onConfirm = { modelId, displayName ->
                    viewModel.addCustomModel(providerId, modelId, displayName)
                    showAddModelDialog = false
                }
            )
        }
    }
}

@Composable
private fun ConnectionTestResultCard(result: ConnectionTestResult) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (result.success) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
            else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(sdp(16.dp))) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (result.success) Icons.Outlined.CheckCircle else Icons.Outlined.Error,
                    contentDescription = null,
                    tint = if (result.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.width(sdp(8.dp)))
                Text(if (result.success) "连接成功" else "连接失败", fontWeight = FontWeight.SemiBold)
            }
            if (result.success) {
                Text("延迟: ${result.latencyMs}ms", style = MaterialTheme.typography.bodyMedium)
                Text("模型数量: ${result.modelCount}", style = MaterialTheme.typography.bodyMedium)
            }
            result.errorMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun AddModelDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var modelId by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("添加模型") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(sdp(8.dp))) {
                OutlinedTextField(value = modelId, onValueChange = { modelId = it }, label = { Text("模型 ID") },
                    placeholder = { Text("gpt-5.5") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = displayName, onValueChange = { displayName = it },
                    label = { Text("显示名称") }, placeholder = { Text("GPT-5.5") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(modelId, displayName.ifBlank { modelId }) },
            enabled = modelId.isNotBlank()) { Text("添加") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

private fun formatTokenCount(count: Long): String = when {
    count >= 1_048_576 -> "${count / 1_048_576}M"
    count >= 1_024 -> "${count / 1_024}K"
    else -> count.toString()
}
