package com.kurisuapi.ui.screen.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kurisuapi.data.repository.SettingsRepository
import com.kurisuapi.data.repository.ThemeRepository
import com.kurisuapi.ui.theme.*
import com.kurisuapi.util.parseColor
import com.kurisuapi.util.sdp
import com.kurisuapi.util.toHex
import kotlinx.coroutines.launch

private val presetColors = listOf(
    Pair("翡翠绿", AppleGreen), Pair("日落橙", SunsetOrange),
    Pair("玫瑰红", AppleRed), Pair("樱花粉", SakuraPink),
    Pair("海洋青", AppleTeal), Pair("星云紫", AppleIndigo),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeConfigScreen(
    settingsRepository: SettingsRepository,
    themeRepository: ThemeRepository,
    onNavigateBack: () -> Unit,
    onNavigateToCustomThemes: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()

    val seedColorHex by settingsRepository
        .observeValue(SettingsRepository.KEY_THEME_SEED_COLOR)
        .collectAsState(initial = null)
    val darkMode by settingsRepository
        .observeValue(SettingsRepository.KEY_THEME_DARK_MODE)
        .collectAsState(initial = "system")
    val allThemes by themeRepository.observeAll().collectAsState(initial = emptyList())
    val activeTheme by themeRepository.observeActive().collectAsState(initial = null)

    val currentSeed = seedColorHex?.parseColor()
    val currentScheme = if (currentSeed != null) glassifiedDynamicScheme(currentSeed, darkTheme = false)
    else glassifiedDynamicScheme(AppleBlue, darkTheme = false)

    val active = activeTheme
    val hasCustomActive = active != null && !active.isBuiltIn
    val isDefault = seedColorHex.isNullOrBlank() && !hasCustomActive

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("主题", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = topBarColors()
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // ── 大预览卡 ──
            BigPreviewCard(
                scheme = currentScheme,
                modifier = Modifier
                    .padding(horizontal = sdp(16.dp), vertical = sdp(8.dp))
            )

            Spacer(modifier = Modifier.height(sdp(8.dp)))

            // ── 深色模式 ──
            Text(
                "外观",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = sdp(16.dp)),
            )
            Spacer(modifier = Modifier.height(sdp(8.dp)))

            Row(
                modifier = Modifier.padding(horizontal = sdp(16.dp)),
                horizontalArrangement = Arrangement.spacedBy(sdp(0.dp)),
            ) {
                listOf(
                    Triple("浅色", "light", darkMode == "light"),
                    Triple("深色", "dark", darkMode == "dark"),
                    Triple("跟随系统", "system", darkMode == "system" || darkMode == null),
                ).forEach { (label, value, selected) ->
                    FilterChip(
                        selected = selected,
                        onClick = {
                            scope.launch { settingsRepository.setValue(SettingsRepository.KEY_THEME_DARK_MODE, value) }
                        },
                        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(sdp(10.dp)),
                    )
                }
            }

            Spacer(modifier = Modifier.height(sdp(16.dp)))

            // ── 主题色选择（水平滚动圆形色块） ──
            Text(
                "主题色",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = sdp(16.dp)),
            )
            Spacer(modifier = Modifier.height(sdp(10.dp)))

            LazyRow(
                contentPadding = PaddingValues(horizontal = sdp(16.dp)),
                horizontalArrangement = Arrangement.spacedBy(sdp(16.dp)),
            ) {
                // 默认
                item {
                    ColorSwatch(
                        color = AppleBlue,
                        label = "默认",
                        selected = isDefault,
                        onClick = {
                            scope.launch {
                                settingsRepository.setValue(SettingsRepository.KEY_THEME_SEED_COLOR, "")
                                themeRepository.clearActive()
                            }
                        }
                    )
                }
                items(presetColors) { (name, color) ->
                    ColorSwatch(
                        color = color,
                        label = name,
                        selected = !hasCustomActive && currentSeed == color,
                        onClick = {
                            scope.launch {
                                settingsRepository.setValue(SettingsRepository.KEY_THEME_SEED_COLOR, color.toHex())
                                allThemes.find { it.isBuiltIn && it.name == name }?.let {
                                    themeRepository.applyTheme(it.id)
                                }
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(sdp(20.dp)))
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                modifier = Modifier.padding(horizontal = sdp(16.dp))
            )
            Spacer(modifier = Modifier.height(sdp(16.dp)))

            // ── 自定义主题入口 ──
            Text(
                "自定义",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = sdp(16.dp)),
            )
            Spacer(modifier = Modifier.height(sdp(8.dp)))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = sdp(16.dp))
                    .clickable { onNavigateToCustomThemes() },
                shape = RoundedCornerShape(sdp(16.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = sdp(1.dp)),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(sdp(16.dp)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(sdp(44.dp))
                            .clip(RoundedCornerShape(sdp(12.dp)))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.Style, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(sdp(22.dp)))
                    }
                    Column(modifier = Modifier.weight(1f).padding(horizontal = sdp(12.dp))) {
                        Text("自定义主题", fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
                        Text("创建和管理你自己的主题配色", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(sdp(20.dp)))
                }
            }

            Spacer(modifier = Modifier.height(sdp(32.dp)))
        }
    }
}

@Composable
private fun BigPreviewCard(
    scheme: androidx.compose.material3.ColorScheme,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(sdp(20.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = sdp(2.dp)),
    ) {
        Column {
            // 微型 app 预览
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sdp(140.dp))
                    .clip(RoundedCornerShape(topStart = sdp(20.dp), topEnd = sdp(20.dp)))
                    .background(scheme.background)
            ) {
                // 顶栏
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(sdp(48.dp))
                        .background(scheme.primary)
                        .padding(horizontal = sdp(16.dp)),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(sdp(8.dp))) {
                        Box(modifier = Modifier.size(sdp(6.dp), sdp(16.dp)).background(scheme.onPrimary.copy(alpha = 0.7f), RoundedCornerShape(2.dp)))
                        Text("预览", color = scheme.onPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                }

                Column(modifier = Modifier.fillMaxSize().padding(sdp(12.dp)).padding(top = sdp(48.dp))) {
                    // 卡片
                    Box(
                        modifier = Modifier.fillMaxWidth().height(sdp(22.dp))
                            .clip(RoundedCornerShape(sdp(8.dp)))
                            .background(scheme.surface)
                            .padding(horizontal = sdp(10.dp), vertical = sdp(4.dp)),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Box(modifier = Modifier.width(sdp(60.dp)).height(sdp(8.dp)).clip(RoundedCornerShape(sdp(4.dp))).background(scheme.primary.copy(alpha = 0.15f)))
                    }
                    Spacer(modifier = Modifier.height(sdp(6.dp)))
                    Box(modifier = Modifier.fillMaxWidth(0.6f).height(sdp(6.dp)).clip(RoundedCornerShape(sdp(3.dp))).background(scheme.onSurface.copy(alpha = 0.1f)))
                    Spacer(modifier = Modifier.height(sdp(4.dp)))
                    Box(modifier = Modifier.fillMaxWidth(0.4f).height(sdp(6.dp)).clip(RoundedCornerShape(sdp(3.dp))).background(scheme.onSurface.copy(alpha = 0.06f)))

                    Spacer(modifier = Modifier.weight(1f))

                    // 气泡
                    Row(horizontalArrangement = Arrangement.spacedBy(sdp(6.dp))) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(sdp(10.dp), sdp(10.dp), sdp(10.dp), sdp(3.dp)))
                                .background(scheme.surfaceVariant)
                                .padding(horizontal = sdp(8.dp), vertical = sdp(4.dp))
                        ) { Text("Hi", fontSize = 10.sp, color = scheme.onSurface) }
                        Spacer(modifier = Modifier.weight(1f))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(sdp(10.dp), sdp(10.dp), sdp(3.dp), sdp(10.dp)))
                                .background(scheme.primary.copy(alpha = 0.9f))
                                .padding(horizontal = sdp(8.dp), vertical = sdp(4.dp))
                        ) { Text("Hi", fontSize = 10.sp, color = scheme.onPrimary) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorSwatch(
    color: Color,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(sdp(64.dp))
            .clickable { onClick() }
            .padding(vertical = sdp(4.dp)),
    ) {
        Box(
            modifier = Modifier
                .size(sdp(52.dp))
                .clip(CircleShape)
                .background(color)
                .then(
                    if (selected) Modifier.border(sdp(3.dp), MaterialTheme.colorScheme.onSurface, CircleShape)
                    else Modifier
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = "已选中",
                    modifier = Modifier.size(sdp(22.dp)),
                    tint = if (color == AppleBlue || color == AppleGreen || color == AppleIndigo) Color.White else Color.White,
                )
            }
        }
        Spacer(modifier = Modifier.height(sdp(6.dp)))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}
