package com.kurisuapi.ui.component

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.kurisuapi.ui.theme.AppleGreen
import com.kurisuapi.ui.theme.AppleBlue
import com.kurisuapi.ui.theme.AppleRed
import com.kurisuapi.util.OemPermissionHelper
import com.kurisuapi.util.sdp

/**
 * 首次启动权限引导弹窗。
 * 引导用户完成：通知权限 → 电池优化白名单 → 厂商自启动管理。
 * 所有操作都是可选跳转，不强制用户必须完成。
 */
@Composable
fun PermissionSetupDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val brandName = remember { OemPermissionHelper.getBrandName() }

    // 检查各权限状态
    val notificationGranted = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
        } else true
    }
    val batteryIgnored = remember { OemPermissionHelper.isBatteryOptimizationIgnored(context) }

    var notificationOk by remember { mutableStateOf(notificationGranted) }
    var batteryOk by remember { mutableStateOf(batteryIgnored) }
    var autoStartDone by remember { mutableStateOf(false) }

    // 所有项都完成时自动关闭
    val allDone = notificationOk && batteryOk && autoStartDone
    LaunchedEffect(allDone) {
        if (allDone) {
            kotlinx.coroutines.delay(500L)
            onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Shield, contentDescription = null, tint = AppleBlue)
                Spacer(Modifier.width(sdp(8.dp)))
                Text("后台运行设置", fontWeight = FontWeight.SemiBold)
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "为确保消息及时送达，请完成以下设置：",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                // ── 1. 通知权限 ──
                PermissionItem(
                    icon = Icons.Outlined.Notifications,
                    title = "通知权限",
                    description = "用于显示后台运行状态",
                    isDone = notificationOk,
                    buttonText = if (notificationOk) "已开启" else "去开启",
                    enabled = !notificationOk,
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            // 通知权限需要在 Activity 中请求，这里跳转应用设置
                            OemPermissionHelper.openAppDetailSettings(context)
                        }
                    }
                )

                HorizontalDivider()

                // ── 2. 电池优化 ──
                PermissionItem(
                    icon = Icons.Outlined.BatterySaver,
                    title = "关闭省电限制",
                    description = "防止系统在后台关闭消息服务",
                    isDone = batteryOk,
                    buttonText = if (batteryOk) "已关闭" else "去设置",
                    enabled = !batteryOk,
                    onClick = {
                        OemPermissionHelper.openBatteryOptimizationSettings(context)
                    }
                )

                HorizontalDivider()

                // ── 3. 厂商自启动 ──
                PermissionItem(
                    icon = Icons.Outlined.PlayArrow,
                    title = "允许自启动",
                    description = "（${brandName}手机）允许应用后台自动运行",
                    isDone = autoStartDone,
                    buttonText = if (autoStartDone) "已设置" else "去设置",
                    enabled = !autoStartDone,
                    onClick = {
                        OemPermissionHelper.openAutoStartSettings(context)
                        // 标记为已引导（无法检测厂商自启动是否真的开启了）
                        autoStartDone = true
                    }
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    "你随时可以在系统设置中修改这些权限。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(if (allDone) "完成" else "稍后再说")
            }
        },
        icon = {
            Icon(Icons.Outlined.Info, contentDescription = null)
        }
    )
}

@Composable
private fun PermissionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    isDone: Boolean,
    buttonText: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (isDone) AppleGreen else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.size(sdp(24.dp))
        )
        Spacer(Modifier.width(sdp(12.dp)))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        Spacer(Modifier.width(sdp(8.dp)))
        if (isDone) {
            Icon(
                Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = AppleGreen,
                modifier = Modifier.size(sdp(20.dp))
            )
        } else {
            TextButton(onClick = onClick, enabled = enabled) {
                Text(buttonText, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
