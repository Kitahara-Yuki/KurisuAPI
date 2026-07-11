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
import com.kurisuapi.ui.component.SteinsGateEasterEgg
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
    var exampleDialogues by rememberSaveable { mutableStateOf("") }
    var nameError by rememberSaveable { mutableStateOf(false) }
    var ageError by rememberSaveable { mutableStateOf(false) }
    var initialized by rememberSaveable { mutableStateOf(false) }
    var showEasterEgg by remember { mutableStateOf(false) }

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
                exampleDialogues = it.exampleDialogues
            }
            initialized = true
        }
    }

    fun doSave() {
        if (name.isBlank()) {
            nameError = true
            return
        }
        // 年龄非空且非数字时提示错误
        if (age.isNotBlank() && age.toIntOrNull() == null) {
            ageError = true
            return
        }
        nameError = false
        ageError = false
        // 牧濑红莉栖彩蛋
        if (name.trim() == "牧濑红莉栖") {
            showEasterEgg = true
            return
        }
        viewModel.saveCharacter(
            id = characterId?.takeIf { it > 0 },
            name = name, avatar = avatar, gender = gender,
            age = age.toIntOrNull() ?: 0,
            personality = personality, appearance = appearance,
            speakingStyle = speakingStyle, background = background, systemPrompt = systemPrompt,
            exampleDialogues = exampleDialogues
        ) { onNavigateBack() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
                colors = com.kurisuapi.ui.theme.topBarColors()
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
            OutlinedTextField(value = age, onValueChange = { age = it; ageError = false }, label = { Text("年龄") }, isError = ageError, supportingText = if (ageError) {{ Text("年龄请输入数字") }} else null, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = personality, onValueChange = { personality = it }, label = { Text("性格") }, placeholder = { Text("定义角色的性格特征，影响 AI 回复的语气和态度") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            OutlinedTextField(value = appearance, onValueChange = { appearance = it }, label = { Text("外观") }, placeholder = { Text("描述角色的外貌特征，如发型、瞳色、体型、穿着等") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            OutlinedTextField(value = speakingStyle, onValueChange = { speakingStyle = it }, label = { Text("说话风格") }, placeholder = { Text("定义角色的说话方式和用词习惯") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            OutlinedTextField(value = background, onValueChange = { background = it }, label = { Text("角色背景") }, placeholder = { Text("定义角色的经历和身份背景，帮助 AI 理解角色的深度") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
            OutlinedTextField(value = systemPrompt, onValueChange = { systemPrompt = it }, label = { Text("系统提示词") }, placeholder = { Text("自由度最高的顶层指令，可定义角色的行为边界、特殊规则等，优先级高于其他字段") }, modifier = Modifier.fillMaxWidth(), minLines = 5)
            OutlinedTextField(value = exampleDialogues, onValueChange = { exampleDialogues = it }, label = { Text("对话示例") }, placeholder = { Text("写几条角色过去的聊天记录，AI 会模仿这个风格。例如：\n用户：在干嘛\n角色：刚睡醒哈哈哈哈 头发跟鸡窝一样\n\n建议不填写，让ai自行发挥") }, modifier = Modifier.fillMaxWidth(), minLines = 4)

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

    // 牧濑红莉栖彩蛋
    if (showEasterEgg) {
        SteinsGateEasterEgg(
            onComplete = {
                showEasterEgg = false
                // 彩蛋路径也要做表单验证（与 doSave 保持一致）
                when {
                    name.isBlank() -> { nameError = true }
                    age.isNotBlank() && age.toIntOrNull() == null -> { ageError = true }
                    else -> {
                        nameError = false
                        ageError = false
                        viewModel.saveCharacter(
                            id = characterId?.takeIf { it > 0 },
                            name = name, avatar = avatar, gender = gender,
                            age = age.toIntOrNull() ?: 0,
                            personality = personality, appearance = appearance,
                            speakingStyle = speakingStyle, background = background, systemPrompt = systemPrompt,
                            exampleDialogues = exampleDialogues
                        ) { onNavigateBack() }
                    }
                }
            }
        )
    }
    } // Box
}
