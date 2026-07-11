package com.kurisuapi.ui.component

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.kurisuapi.util.sdp
import com.yalantis.ucrop.UCrop
import java.io.File

/**
 * 背景图片选择器：选图 → 裁剪 → 保存到本地。
 */
@Composable
fun BackgroundImagePicker(
    label: String,
    currentImagePath: String,
    onImageCropped: (localPath: String) -> Unit,
    onClear: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var pendingDestPath by remember { mutableStateOf<String?>(null) }

    // UCrop 裁剪结果
    val cropLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            // 直接用之前保存的目标路径，避免 content URI path 为空的问题
            pendingDestPath?.let { onImageCropped(it) }
            pendingDestPath = null
        }
    }

    // 图库选取
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { sourceUri ->
            val destFile = File(context.filesDir, "theme_bg/crop_${System.currentTimeMillis()}.jpg")
            destFile.parentFile?.mkdirs()
            pendingDestPath = destFile.absolutePath

            val cropIntent = UCrop.of(sourceUri, Uri.fromFile(destFile))
                .withAspectRatio(9f, 16f)
                .withMaxResultSize(1080, 1920)
                .getIntent(context)

            cropLauncher.launch(cropIntent)
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(sdp(12.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
    ) {
        Column(modifier = Modifier.padding(sdp(12.dp))) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(sdp(8.dp)))

            if (currentImagePath.isNotBlank()) {
                // 有图：预览 + 更换/清除
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(sdp(12.dp)),
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(File(currentImagePath))
                            .crossfade(true)
                            .build(),
                        contentDescription = "背景预览",
                        modifier = Modifier
                            .size(sdp(72.dp))
                            .clip(RoundedCornerShape(sdp(8.dp)))
                            .border(sdp(1.dp), MaterialTheme.colorScheme.outline, RoundedCornerShape(sdp(8.dp))),
                        contentScale = ContentScale.Crop,
                    )
                    Column {
                        FilledTonalButton(
                            onClick = {
                                galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            enabled = enabled,
                        ) {
                            Icon(Icons.Outlined.SwapHoriz, null, modifier = Modifier.size(sdp(16.dp)))
                            Spacer(modifier = Modifier.width(sdp(6.dp)))
                            Text("更换")
                        }
                        Spacer(modifier = Modifier.height(sdp(6.dp)))
                        TextButton(
                            onClick = onClear,
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        ) {
                            Text("清除图片")
                        }
                    }
                }
            } else {
                // 无图：选择按钮
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(sdp(72.dp))
                        .clip(RoundedCornerShape(sdp(8.dp)))
                        .border(
                            sdp(1.5.dp),
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            RoundedCornerShape(sdp(8.dp))
                        )
                        .clickable(enabled = enabled) {
                            galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Outlined.AddPhotoAlternate,
                            null,
                            modifier = Modifier.size(sdp(24.dp)),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(sdp(4.dp)))
                        Text(
                            "选择图片",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
