package com.kurisuapi.ui.screen.character

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kurisuapi.ui.viewmodel.CharacterViewModel
import com.kurisuapi.util.sdp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterEditScreen(
    onNavigateBack: () -> Unit,
    characterId: Long?,
    viewModel: CharacterViewModel = hiltViewModel()
) {
    val editingCharacter by viewModel.editingCharacter.collectAsState()

    var name by rememberSaveable { mutableStateOf("") }
    var avatar by rememberSaveable { mutableStateOf("") }
    var gender by rememberSaveable { mutableStateOf("") }
    var age by rememberSaveable { mutableStateOf("") }
    var personality by rememberSaveable { mutableStateOf("") }
    var appearance by rememberSaveable { mutableStateOf("") }
    var speakingStyle by rememberSaveable { mutableStateOf("") }
    var background by rememberSaveable { mutableStateOf("") }
    var systemPrompt by rememberSaveable { mutableStateOf("") }
    var nameError by rememberSaveable { mutableStateOf(false) }
    var initialized by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(characterId) {
        if (characterId != null && characterId > 0) {
            viewModel.loadCharacter(characterId)
        }
    }

    LaunchedEffect(editingCharacter) {
        if (!initialized && editingCharacter != null) {
            editingCharacter?.let {
                name = it.name
                avatar = it.avatar
                gender = it.gender
                age = if (it.age > 0) it.age.toString() else ""
                personality = it.personality
                appearance = it.appearance
                speakingStyle = it.speakingStyle
                background = it.background
                systemPrompt = it.systemPrompt
            }
            initialized = true
        }
    }

    fun doSave() {
        if (name.isBlank()) {
            nameError = true
            return
        }
        nameError = false
        viewModel.saveCharacter(
            id = characterId?.takeIf { it > 0 },
            name = name, avatar = avatar, gender = gender,
            age = age.toIntOrNull() ?: 0,
            personality = personality, appearance = appearance,
            speakingStyle = speakingStyle, background = background, systemPrompt = systemPrompt
        ) { onNavigateBack() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (characterId != null && characterId > 0) "编辑角色" else "创建角色",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { doSave() }) {
                        Icon(Icons.Outlined.Save, contentDescription = "保存")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(sdp(16.dp))
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(sdp(12.dp))
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; nameError = false },
                label = { Text("角色名称 *") },
                isError = nameError,
                supportingText = if (nameError) {{ Text("角色名称不能为空") }} else null,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(value = gender, onValueChange = { gender = it }, label = { Text("性别") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = age, onValueChange = { age = it }, label = { Text("年龄") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = personality, onValueChange = { personality = it }, label = { Text("性格") }, placeholder = { Text("定义角色的性格特征，影响 AI 回复的语气和态度") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            OutlinedTextField(value = appearance, onValueChange = { appearance = it }, label = { Text("外观") }, placeholder = { Text("描述角色的外貌特征，如发型、瞳色、体型、穿着等") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            OutlinedTextField(value = speakingStyle, onValueChange = { speakingStyle = it }, label = { Text("说话风格") }, placeholder = { Text("定义角色的说话方式和用词习惯") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            OutlinedTextField(value = background, onValueChange = { background = it }, label = { Text("角色背景") }, placeholder = { Text("定义角色的经历和身份背景，帮助 AI 理解角色的深度") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
            OutlinedTextField(value = systemPrompt, onValueChange = { systemPrompt = it }, label = { Text("系统提示词") }, placeholder = { Text("自由度最高的顶层指令，可定义角色的行为边界、特殊规则等，优先级高于其他字段") }, modifier = Modifier.fillMaxWidth(), minLines = 5)

            Spacer(modifier = Modifier.height(sdp(16.dp)))

            Button(
                onClick = { doSave() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Outlined.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(sdp(8.dp)))
                Text("保存角色")
            }
        }
    }
}
