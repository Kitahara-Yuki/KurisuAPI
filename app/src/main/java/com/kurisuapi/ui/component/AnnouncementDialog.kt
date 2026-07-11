package com.kurisuapi.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * 更新计划公告弹窗。
 *
 * 用户打开 App 后自动弹出，可选择【知道了】关闭或【不再通知】永久关闭。
 */
@Composable
fun AnnouncementDialog(
    onDismiss: () -> Unit,
    onNeverShowAgain: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { /* 禁止点外部关闭 */ },
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = {
            Text("📢 更新公告", fontWeight = FontWeight.SemiBold)
        },
        text = {
            Text(
                text = "后续更新计划为：\n\n多模态支持\n\n语音通话与语音气泡\n\n剧情模式深度优化与完善\n\n自定义主题的优化与完善\n\n——北原友希",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("知道了")
            }
        },
        dismissButton = {
            TextButton(onClick = onNeverShowAgain) {
                Text("不再通知")
            }
        }
    )
}
