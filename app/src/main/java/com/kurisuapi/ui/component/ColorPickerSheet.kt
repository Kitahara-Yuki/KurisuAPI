package com.kurisuapi.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kurisuapi.ui.theme.*
import com.kurisuapi.util.parseColor
import com.kurisuapi.util.sdp
import com.kurisuapi.util.toHex

data class PresetColor(val name: String, val color: Color)

private val applePresets = listOf(
    PresetColor("天空蓝", AppleBlue),
    PresetColor("翡翠绿", AppleGreen),
    PresetColor("日落橙", SunsetOrange),
    PresetColor("玫瑰红", AppleRed),
    PresetColor("樱花粉", ApplePink),
    PresetColor("海洋青", AppleTeal),
    PresetColor("星云紫", AppleIndigo),
)

private val neutralPresets = listOf(
    PresetColor("纯白", Color(0xFFF8F8FA)),
    PresetColor("暖灰", Color(0xFFE8E5E0)),
    PresetColor("石墨", Color(0xFF3C3C43)),
    PresetColor("墨色", Color(0xFF1C1C1E)),
    PresetColor("米色", Color(0xFFF5F0E8)),
)

private val allPresets = applePresets + neutralPresets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorPickerSheet(
    title: String,
    currentColor: Color?,
    onColorSelected: (Color) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    var hexInput by remember(currentColor) {
        mutableStateOf(currentColor?.toHex() ?: "")
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = sdp(24.dp), topEnd = sdp(24.dp)),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = sdp(10.dp))
                    .size(sdp(36.dp), sdp(4.dp))
                    .clip(RoundedCornerShape(sdp(2.dp)))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = sdp(24.dp))
                .padding(bottom = sdp(40.dp)),
            verticalArrangement = Arrangement.spacedBy(sdp(20.dp)),
        ) {
            // ── 标题 + 当前色预览 ──
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(sdp(14.dp)),
            ) {
                // 大色块预览
                Box(
                    modifier = Modifier
                        .size(sdp(52.dp))
                        .clip(RoundedCornerShape(sdp(14.dp)))
                        .background(currentColor ?: Color.Transparent)
                        .border(
                            sdp(2.dp),
                            if (currentColor != null) currentColor.copy(alpha = 0.4f)
                            else MaterialTheme.colorScheme.outline,
                            RoundedCornerShape(sdp(14.dp))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (currentColor == null) {
                        Text(
                            "?",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        currentColor?.toHex() ?: "默认 · 使用主题色",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            // ── 预设色块 — 大圆形 56dp ──
            Text(
                "预设颜色",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                horizontalArrangement = Arrangement.spacedBy(sdp(10.dp)),
                verticalArrangement = Arrangement.spacedBy(sdp(10.dp)),
                modifier = Modifier.heightIn(max = sdp(260.dp)),
            ) {
                items(allPresets) { preset ->
                    val isSelected = currentColor == preset.color
                    Box(
                        modifier = Modifier
                            .size(sdp(52.dp))
                            .clip(CircleShape)
                            .background(preset.color)
                            .then(
                                if (isSelected) {
                                    Modifier.border(sdp(3.dp), MaterialTheme.colorScheme.onSurface, CircleShape)
                                } else Modifier
                            )
                            .clickable { onColorSelected(preset.color) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isSelected) {
                            Icon(
                                Icons.Outlined.Check,
                                contentDescription = "已选中",
                                modifier = Modifier.size(sdp(22.dp)),
                                tint = if (preset.color == Color(0xFFF8F8FA)) Color.Black else Color.White,
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            // ── hex 输入 ──
            OutlinedTextField(
                value = hexInput,
                onValueChange = { input ->
                    hexInput = input
                    input.parseColor()?.let { onColorSelected(it) }
                },
                placeholder = { Text("#AARRGGBB") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                ),
                shape = RoundedCornerShape(sdp(12.dp)),
            )

            // ── 重置 ──
            if (currentColor != null) {
                TextButton(
                    onClick = onReset,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text("重置为默认", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
