package com.kurisuapi.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File

/**
 * 渲染带模糊+暗化效果的背景图片。
 * 图层：图片 → 暗化遮罩 → 模糊（通过图形层）
 */
@Composable
fun BlurOverlayImage(
    imagePath: String,
    blurRadius: Int,
    dimPercent: Int,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val dimAlpha = (dimPercent.coerceIn(0, 100) / 100f)

    Box(modifier = modifier) {
        // Layer 0: 原始图片
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(File(imagePath))
                .crossfade(true)
                .build(),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (blurRadius > 0) Modifier.blur(blurRadius.dp) else Modifier
                ),
            contentScale = ContentScale.Crop,
        )

        // Layer 1: 暗化遮罩
        if (dimAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = dimAlpha))
            )
        }
    }
}
