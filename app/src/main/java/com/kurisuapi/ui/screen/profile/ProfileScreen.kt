package com.kurisuapi.ui.screen.profile

import android.net.Uri
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.kurisuapi.ui.navigation.Screen
import com.kurisuapi.ui.viewmodel.ProfileViewModel
import com.kurisuapi.util.sdp
import com.yalantis.ucrop.UCrop
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onNavigate: (String) -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val userName by viewModel.userName.collectAsState()
    val userAvatar by viewModel.userAvatar.collectAsState()

    var pendingAvatarPath by remember { mutableStateOf<String?>(null) }

    // UCrop 裁剪结果
    val cropLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            pendingAvatarPath?.let { viewModel.saveAvatarFromPath(it) }
            pendingAvatarPath = null
        }
    }

    // 图库选取 → 裁剪
    val avatarPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { sourceUri ->
            val destFile = File(context.filesDir, "avatars/crop_${System.currentTimeMillis()}.jpg")
            destFile.parentFile?.mkdirs()
            pendingAvatarPath = destFile.absolutePath
            val cropIntent = UCrop.of(sourceUri, Uri.fromFile(destFile))
                .withAspectRatio(1f, 1f)        // 正方形裁剪，适配圆形头像
                .withMaxResultSize(512, 512)
                .getIntent(context)
            cropLauncher.launch(cropIntent)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("我", fontWeight = FontWeight.SemiBold) },
                colors = com.kurisuapi.ui.theme.topBarColors()
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(sdp(16.dp)),
            verticalArrangement = Arrangement.spacedBy(sdp(12.dp))
        ) {
            // —— 个人信息卡片 ——
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                    ),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(sdp(24.dp)),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 头像
                        Box(
                            modifier = Modifier
                                .size(sdp(88.dp))
                                .clip(CircleShape)
                                .clickable {
                                    avatarPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (userAvatar.isNotBlank()) {
                                val model: Any = if (userAvatar.startsWith("http://") ||
                                    userAvatar.startsWith("https://")
                                ) {
                                    userAvatar
                                } else {
                                    File(userAvatar)
                                }
                                AsyncImage(
                                    model = model,
                                    contentDescription = "用户头像",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = (userName.ifBlank { "Y" }).first().toString(),
                                        fontSize = 34.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(sdp(16.dp)))

                        // 名称行
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigate(Screen.ProfileEdit.route) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = userName.ifBlank { "Yuki" },
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                    }
                }
            }

            // —— 快捷操作标题 ——
            item {
                Text(
                    text = "快捷操作",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = sdp(8.dp))
                )
            }

            // —— 快捷操作列表 ——
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                    ),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column {
                        ListItem(
                            headlineContent = { Text("角色管理") },
                            supportingContent = { Text("查看和管理已创建的角色") },
                            leadingContent = {
                                Icon(
                                    Icons.Outlined.People,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            trailingContent = {
                                Icon(
                                    Icons.Outlined.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                )
                            },
                            modifier = Modifier.clickable { onNavigate(Screen.CharacterList.route) }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = sdp(16.dp)),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f)
                        )
                        ListItem(
                            headlineContent = { Text("设置") },
                            supportingContent = { Text("AI 模型、微信连接、行为偏好") },
                            leadingContent = {
                                Icon(
                                    Icons.Outlined.Settings,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            trailingContent = {
                                Icon(
                                    Icons.Outlined.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                )
                            },
                            modifier = Modifier.clickable { onNavigate(Screen.Settings.route) }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = sdp(16.dp)),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f)
                        )
                        ListItem(
                            headlineContent = { Text("主题") },
                            supportingContent = { Text("自定义 UI 颜色和背景") },
                            leadingContent = {
                                Icon(
                                    Icons.Outlined.Palette,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            trailingContent = {
                                Icon(
                                    Icons.Outlined.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                )
                            },
                            modifier = Modifier.clickable { onNavigate(Screen.ThemeConfig.route) }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = sdp(16.dp)),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f)
                        )
                        ListItem(
                            headlineContent = { Text("关于 红莉栖API") },
                            supportingContent = { Text("版本信息与作者") },
                            leadingContent = {
                                Icon(
                                    Icons.Outlined.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            trailingContent = {
                                Icon(
                                    Icons.Outlined.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                )
                            },
                            modifier = Modifier.clickable { onNavigate(Screen.SystemSettings.route) }
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(sdp(16.dp))) }
        }
    }

}
