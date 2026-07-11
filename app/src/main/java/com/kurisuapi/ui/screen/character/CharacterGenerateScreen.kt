package com.kurisuapi.ui.screen.character

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kurisuapi.ui.component.SteinsGateEasterEgg
import com.kurisuapi.ui.viewmodel.CharacterViewModel
import com.kurisuapi.util.sdp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterGenerateScreen(
    onNavigateBack: () -> Unit,
    onGenerated: (Long) -> Unit,
    viewModel: CharacterViewModel = hiltViewModel()
) {
    var description by rememberSaveable { mutableStateOf("") }
    val isGenerating by viewModel.isGenerating.collectAsState()
    val generateError by viewModel.generateError.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showEasterEgg by remember { mutableStateOf(false) }

    LaunchedEffect(generateError) {
        generateError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearGenerateError()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("自动生成角色", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = com.kurisuapi.ui.theme.topBarColors()
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = sdp(16.dp))
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(sdp(16.dp))
        ) {
            Spacer(modifier = Modifier.height(sdp(8.dp)))

            // 说明卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(sdp(16.dp)),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(sdp(20.dp))
                    )
                    Spacer(modifier = Modifier.width(sdp(10.dp)))
                    Text(
                        "描述你想要的角色，AI 会自动生成名字、性别、年龄、性格、外观、说话风格、背景和系统提示词。写得越具体，效果越好。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }

            // 输入框
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("角色描述") },
                placeholder = {
                    Text(
                                "命运石之门的牧濑红莉栖。"
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                minLines = 8,
                enabled = !isGenerating
            )

            // 提示：写出关键信息效果更好
            Text(
                "建议包含：性别、年龄、性格、爱好、说话风格、特殊习惯等。越详细越像真人。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )

            // 生成按钮
            Button(
                onClick = {
                    if (description.isNotBlank()) {
                        if (description.contains("牧濑红莉栖")) {
                            showEasterEgg = true
                        } else {
                            viewModel.generateCharacter(description) { newId ->
                                if (newId != null) onGenerated(newId)
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(sdp(48.dp)),
                enabled = description.isNotBlank() && !isGenerating
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(sdp(20.dp)),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(sdp(10.dp)))
                    Text("生成中...")
                } else {
                    Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(sdp(20.dp)))
                    Spacer(modifier = Modifier.width(sdp(8.dp)))
                    Text("开始生成")
                }
            }

            Spacer(modifier = Modifier.height(sdp(16.dp)))
        }
    }

    // 牧濑红莉栖彩蛋
    if (showEasterEgg) {
        SteinsGateEasterEgg(
            onComplete = {
                showEasterEgg = false
                viewModel.generateCharacter(description) { newId ->
                    if (newId != null) onGenerated(newId)
                }
            }
        )
    }
    } // Box
}
