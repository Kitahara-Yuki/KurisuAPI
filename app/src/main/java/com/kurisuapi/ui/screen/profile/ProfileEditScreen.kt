package com.kurisuapi.ui.screen.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kurisuapi.ui.viewmodel.ProfileViewModel
import com.kurisuapi.util.sdp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditScreen(
    onNavigateBack: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val userName by viewModel.userName.collectAsState()
    val userGender by viewModel.userGender.collectAsState()
    val userRegion by viewModel.userRegion.collectAsState()
    val userBackground by viewModel.userBackground.collectAsState()
    val viewModelLoaded by viewModel.loaded.collectAsState()

    var editName by rememberSaveable { mutableStateOf("") }
    var editGender by rememberSaveable { mutableStateOf("") }
    var editRegion by rememberSaveable { mutableStateOf("") }
    var editBackground by rememberSaveable { mutableStateOf("") }
    var initialized by rememberSaveable { mutableStateOf(false) }
    var showSaveConfirmDialog by remember { mutableStateOf(false) }

    // 等 ViewModel 加载完成后，用真实值初始化编辑字段
    LaunchedEffect(viewModelLoaded) {
        if (viewModelLoaded && !initialized) {
            editName = userName
            editGender = userGender
            editRegion = userRegion
            editBackground = userBackground
            initialized = true
        }
    }

    fun saveAndExit() {
        viewModel.setUserName(editName.trim().ifBlank { userName })
        viewModel.setUserGender(editGender.trim())
        viewModel.setUserRegion(editRegion.trim())
        viewModel.setUserBackground(editBackground.trim())
        onNavigateBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("个人资料", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { showSaveConfirmDialog = true },
                        enabled = viewModelLoaded
                    ) {
                        Text("保存", fontWeight = FontWeight.SemiBold)
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
                .verticalScroll(rememberScrollState())
                .padding(sdp(16.dp)),
            verticalArrangement = Arrangement.spacedBy(sdp(12.dp))
        ) {
            OutlinedTextField(
                value = editName,
                onValueChange = { editName = it },
                label = { Text("名字") },
                placeholder = { Text("输入你的名字") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = editGender,
                onValueChange = { editGender = it },
                label = { Text("性别") },
                placeholder = { Text("例如：男 / 女 / 保密") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = editRegion,
                onValueChange = { editRegion = it },
                label = { Text("地区") },
                placeholder = { Text("例如：北京 / 上海 / 东京") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = editBackground,
                onValueChange = { editBackground = it },
                label = { Text("背景") },
                placeholder = { Text("简单介绍一下自己，让 AI 更了解你") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )

            Spacer(modifier = Modifier.height(sdp(16.dp)))

            Button(
                onClick = { showSaveConfirmDialog = true },
                enabled = viewModelLoaded,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("保存")
            }
        }

        if (showSaveConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showSaveConfirmDialog = false },
                title = { Text("提示") },
                text = {
                    Text("请确定填写是否想好与完成，后续更改可能影响AI判断")
                },
                confirmButton = {
                    TextButton(onClick = {
                        showSaveConfirmDialog = false
                        saveAndExit()
                    }) {
                        Text("确定")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSaveConfirmDialog = false }) {
                        Text("取消")
                    }
                }
            )
        }
    }
}
