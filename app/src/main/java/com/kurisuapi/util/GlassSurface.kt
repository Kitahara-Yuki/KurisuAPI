package com.kurisuapi.util

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 液态玻璃效果组件 — 封装毛玻璃背景、微边框、圆角。
 *
 * Android 12+ (API 31+): 叠加 blur 效果。
 * Android 8-11 (API 26-30): 只用半透明背景（无模糊），视觉上仍有玻璃感。
 *
 * @param modifier 外部修饰符
 * @param blurRadius 模糊半径（仅 API 31+ 生效），默认 20dp
 * @param cornerRadius 圆角大小，默认 16dp
 * @param content 毛玻璃内的内容
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    blurRadius: Dp = 20.dp,
    cornerRadius: Dp = 16.dp,
    content: @Composable () -> Unit
) {
    val isBlurSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val shape = RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier
            .clip(shape)
            .then(
                if (isBlurSupported) {
                    Modifier.blur(blurRadius)
                } else {
                    Modifier
                }
            )
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 0.5.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = shape
            ),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        content()
    }
}
