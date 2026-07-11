package com.kurisuapi.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kurisuapi.ui.viewmodel.DailyStats
import com.kurisuapi.ui.viewmodel.TokenUsageViewModel
import com.kurisuapi.util.sdp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** 日期格式化（与数据库一致） */
private val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")

/** 日期显示：今天/昨天/MM-dd */
private fun formatDateLabel(dateStr: String): String {
    val today = LocalDate.now().format(dateFmt)
    val yesterday = LocalDate.now().minusDays(1).format(dateFmt)
    return when (dateStr) {
        today -> "今天"
        yesterday -> "昨天"
        else -> {
            try {
                val d = LocalDate.parse(dateStr, dateFmt)
                "${d.monthValue}月${d.dayOfMonth}日"
            } catch (_: Exception) { dateStr }
        }
    }
}

@Composable
fun TokenUsageDialog(
    viewModel: TokenUsageViewModel,
    onDismiss: () -> Unit
) {
    val selectedDate by viewModel.selectedDate.collectAsState()
    val stats by viewModel.selectedStats.collectAsState()
    val allDates by viewModel.allDates.collectAsState()

    // 每次打开弹窗刷新数据
    LaunchedEffect(Unit) { viewModel.refresh() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(sdp(12.dp)),
            shape = RoundedCornerShape(sdp(20.dp)),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = sdp(8.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(sdp(20.dp))
            ) {
                // 标题栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "API 请求统计",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, contentDescription = "关闭")
                    }
                }

                Spacer(modifier = Modifier.height(sdp(12.dp)))

                // ── 日期选择器 ──
                if (allDates.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(sdp(8.dp))
                    ) {
                        items(allDates) { date ->
                            val isSelected = date == selectedDate
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.selectDate(date) },
                                label = {
                                    Text(
                                        formatDateLabel(date),
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(sdp(16.dp)))
                }

                // ── 统计数据 ──
                val s = stats
                if (s != null && s.totalRequests > 0) {
                    // 顶部概览数字
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(sdp(14.dp)))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f))
                            .padding(sdp(16.dp)),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatBig(
                            label = "命中",
                            value = s.totalHits,
                            color = Color(0xFF34C759)
                        )
                        StatBig(
                            label = "未命中",
                            value = s.totalMisses,
                            color = Color(0xFFFF9500)
                        )
                        StatBig(
                            label = "总请求",
                            value = s.totalRequests,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(sdp(16.dp)))

                    // 嵌入缓存卡片
                    CacheCard(
                        icon = Icons.Outlined.Memory,
                        title = "嵌入向量缓存",
                        lines = listOf(
                            StatLine("命中", s.embedHits, Color(0xFF34C759)),
                            StatLine("未命中", s.embedMisses, Color(0xFFFF9500))
                        ),
                        hitRate = if (s.totalEmbed > 0) (s.embedHits.toFloat() / s.totalEmbed * 100f) else 0f
                    )

                    Spacer(modifier = Modifier.height(sdp(12.dp)))

                    // 聊天缓存卡片
                    CacheCard(
                        icon = Icons.Outlined.Chat,
                        title = "聊天回复缓存",
                        lines = listOf(
                            StatLine("精确命中", s.chatL1L2Hits, Color(0xFF34C759)),
                            StatLine("语义命中", s.chatL3Hits, Color(0xFF5AC8FA)),
                            StatLine("未命中", s.chatMisses, Color(0xFFFF9500))
                        ),
                        hitRate = if (s.totalChat > 0) ((s.chatL1L2Hits + s.chatL3Hits).toFloat() / s.totalChat * 100f) else 0f
                    )
                } else {
                    // 无数据
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(sdp(32.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "暂无统计数据",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(sdp(8.dp)))

                // 底部说明
                Text(
                    "数据自动保存，关闭 APP 不会丢失",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// ── 顶部大数字 ──
@Composable
private fun StatBig(label: String, value: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "$value",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ── 缓存统计卡片 ──
@Composable
private fun CacheCard(
    icon: ImageVector,
    title: String,
    lines: List<StatLine>,
    hitRate: Float
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(sdp(14.dp))) {
            // 标题行
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(sdp(20.dp))
                )
                Spacer(modifier = Modifier.width(sdp(8.dp)))
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.weight(1f))
                // 命中率
                Text(
                    "${"%.0f".format(hitRate)}%",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = hitRateColor(hitRate)
                )
            }

            Spacer(modifier = Modifier.height(sdp(8.dp)))

            // 明细行
            lines.forEach { line ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = sdp(3.dp)),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        line.label,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // 小圆点指示色
                        Box(
                            modifier = Modifier
                                .size(sdp(8.dp))
                                .clip(CircleShape)
                                .background(line.color)
                        )
                        Spacer(modifier = Modifier.width(sdp(6.dp)))
                        Text(
                            "${line.value} 次",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = line.color
                        )
                    }
                }
            }

            // 进度条
            Spacer(modifier = Modifier.height(sdp(6.dp)))
            LinearProgressIndicator(
                progress = { (hitRate / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sdp(6.dp))
                    .clip(RoundedCornerShape(sdp(3.dp))),
                color = hitRateColor(hitRate),
                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            )
        }
    }
}

private data class StatLine(
    val label: String,
    val value: Int,
    val color: Color
)

private fun hitRateColor(rate: Float): Color = when {
    rate >= 80f -> Color(0xFF34C759)
    rate >= 50f -> Color(0xFFFF9500)
    else -> Color(0xFFFF3B30)
}
