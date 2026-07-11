package com.kurisuapi.ui.screen.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kurisuapi.data.entity.ThemeEntity
import com.kurisuapi.data.repository.ThemeRepository
import com.kurisuapi.ui.theme.*
import com.kurisuapi.util.parseColor
import com.kurisuapi.util.sdp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeListScreen(
    themeRepository: ThemeRepository,
    onNavigateBack: () -> Unit,
    onEditTheme: (Long) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val themes by themeRepository.observeAll().collectAsState(initial = emptyList())
    val activeTheme by themeRepository.observeActive().collectAsState(initial = null)
    val userThemes = themes.filter { !it.isBuiltIn }

    var deleteConfirmId by remember { mutableStateOf<Long?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("自定义主题", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = topBarColors()
            )
        }
    ) { paddingValues ->
        if (userThemes.isEmpty()) {
            // ── 空状态 ──
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(sdp(32.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .size(sdp(72.dp))
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.Palette,
                            null,
                            modifier = Modifier.size(sdp(32.dp)),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        )
                    }
                    Spacer(modifier = Modifier.height(sdp(16.dp)))
                    Text(
                        "还没有自定义主题",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(sdp(4.dp)))
                    Text(
                        "创建一个属于你自己的配色方案",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(sdp(20.dp)))
                    FilledTonalButton(
                        onClick = { onEditTheme(-1L) },
                        shape = RoundedCornerShape(sdp(12.dp)),
                    ) {
                        Icon(Icons.Outlined.Add, null, modifier = Modifier.size(sdp(18.dp)))
                        Spacer(modifier = Modifier.width(sdp(6.dp)))
                        Text("新建主题")
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentPadding = PaddingValues(start = sdp(16.dp), end = sdp(16.dp), top = sdp(8.dp), bottom = sdp(16.dp)),
                horizontalArrangement = Arrangement.spacedBy(sdp(12.dp)),
                verticalArrangement = Arrangement.spacedBy(sdp(12.dp)),
            ) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(sdp(150.dp))
                            .clip(RoundedCornerShape(sdp(16.dp)))
                            .border(sdp(2.dp), MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(sdp(16.dp)))
                            .clickable { onEditTheme(-1L) },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Outlined.Add, null, modifier = Modifier.size(sdp(28.dp)), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(sdp(8.dp)))
                            Text("新建主题", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                items(userThemes, key = { it.id }) { theme ->
                    UserThemeCard(
                        theme = theme,
                        isActive = theme.id == activeTheme?.id,
                        onClick = { scope.launch { themeRepository.applyTheme(theme.id) } },
                        onEdit = { onEditTheme(theme.id) },
                        onDelete = { deleteConfirmId = theme.id },
                    )
                }
            }
        }
    }

    // 删除确认
    deleteConfirmId?.let { id ->
        AlertDialog(
            onDismissRequest = { deleteConfirmId = null },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            icon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("删除主题") },
            text = { Text("确定要删除这个主题吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch { themeRepository.delete(id) }
                        deleteConfirmId = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmId = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun UserThemeCard(
    theme: ThemeEntity,
    isActive: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val seedColor = theme.seedColorHex.parseColor()
    val scheme = glassifiedDynamicScheme(seedColor = seedColor ?: AppleBlue, darkTheme = false)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(sdp(150.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(sdp(16.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isActive) sdp(4.dp) else sdp(1.dp)),
        border = if (isActive) androidx.compose.foundation.BorderStroke(sdp(2.5.dp), seedColor ?: AppleBlue) else null
    ) {
        Column(modifier = Modifier.padding(sdp(12.dp))) {
            // 预览区
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sdp(90.dp))
                    .clip(RoundedCornerShape(sdp(10.dp)))
                    .background(theme.chatBgColorHex.parseColor() ?: scheme.background)
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(sdp(32.dp))
                        .background(theme.bannerColorHex.parseColor() ?: scheme.primary)
                        .padding(horizontal = sdp(10.dp)),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text("预览", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = scheme.onPrimary)
                }
                Column(
                    modifier = Modifier.fillMaxSize().padding(sdp(8.dp)).padding(top = sdp(32.dp))
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(sdp(12.dp))
                            .clip(RoundedCornerShape(sdp(4.dp)))
                            .background(theme.cardColorHex.parseColor() ?: scheme.surface)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxHeight().width(sdp(36.dp))
                                .clip(RoundedCornerShape(2.dp))
                                .background(scheme.primary.copy(alpha = 0.15f))
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(sdp(4.dp))) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(sdp(6.dp), sdp(6.dp), sdp(6.dp), sdp(2.dp)))
                                .background(theme.bubbleAiColorHex.parseColor() ?: scheme.surfaceVariant)
                                .padding(horizontal = sdp(6.dp), vertical = sdp(2.dp))
                        ) { Text("Hi", fontSize = 8.sp, color = scheme.onSurface) }
                        Spacer(modifier = Modifier.weight(1f))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(sdp(6.dp), sdp(6.dp), sdp(2.dp), sdp(6.dp)))
                                .background(theme.bubbleUserColorHex.parseColor() ?: scheme.primary)
                                .padding(horizontal = sdp(6.dp), vertical = sdp(2.dp))
                        ) { Text("Hi", fontSize = 8.sp, color = scheme.onPrimary) }
                    }
                }
            }

            Spacer(modifier = Modifier.height(sdp(10.dp)))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(sdp(14.dp)).clip(CircleShape).background(seedColor ?: AppleBlue))
                Spacer(modifier = Modifier.width(sdp(8.dp)))
                Text(
                    text = theme.name,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                if (isActive) {
                    Icon(Icons.Outlined.Check, null, modifier = Modifier.size(sdp(16.dp)), tint = seedColor ?: AppleBlue)
                }
                var showMenu by remember { mutableStateOf(false) }
                Box {
                    Box(
                        modifier = Modifier.size(sdp(22.dp)).clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { showMenu = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.MoreHoriz, null, modifier = Modifier.size(sdp(16.dp)), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("编辑") },
                            onClick = { onEdit(); showMenu = false },
                            leadingIcon = { Icon(Icons.Outlined.Edit, null, modifier = Modifier.size(sdp(18.dp))) }
                        )
                        DropdownMenuItem(
                            text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                            onClick = { showMenu = false; onDelete() },
                            leadingIcon = { Icon(Icons.Outlined.Delete, null, modifier = Modifier.size(sdp(18.dp)), tint = MaterialTheme.colorScheme.error) }
                        )
                    }
                }
            }
        }
    }
}
