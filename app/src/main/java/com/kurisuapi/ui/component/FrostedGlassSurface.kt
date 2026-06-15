package com.kurisuapi.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kurisuapi.util.sdp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * 毛玻璃表面组件（胶囊形）
 *
 * 需要配合父级 Box 使用 hazeSource：
 * ```
 * Box {
 *     Column(Modifier.hazeSource(hazeState)) { ... }
 *     FrostedGlassSurface(hazeState, modifier = Modifier.align(Alignment.BottomCenter)) {
 *         // 内容
 *     }
 * }
 * ```
 *
 * @param hazeState Haze 模糊状态（在父级 Composable 中用 remember { HazeState() } 创建）
 * @param modifier 外部修饰符
 * @param cornerRadius 圆角大小，默认 28dp（胶囊形）
 * @param blurRadius 模糊半径，默认 15dp
 * @param tintAlpha 着色不透明度，默认 10%
 */
@Composable
fun FrostedGlassSurface(
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = sdp(28.dp),
    blurRadius: Dp = 15.dp,
    tintAlpha: Float = 0.10f,
    content: @Composable () -> Unit,
) {
    val darkTheme = isSystemInDarkTheme()
    val density = LocalDensity.current
    val cornerPx = with(density) { cornerRadius.toPx() }

    // 微光动画
    val infiniteTransition = rememberInfiniteTransition()
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -300f,
        targetValue = 800f,
        animationSpec = infiniteRepeatable(
            animation = tween(3500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    // Haze 模糊配置
    val glassTintColor = MaterialTheme.colorScheme.surface.copy(alpha = tintAlpha)
    val glassBgColor = MaterialTheme.colorScheme.surface
    val glassBlurStyle = remember(tintAlpha, blurRadius) {
        HazeStyle(
            blurRadius = blurRadius,
            tint = HazeTint(glassTintColor),
            backgroundColor = glassBgColor,
            fallbackTint = HazeTint(glassTintColor)
        )
    }

    val shape = RoundedCornerShape(cornerRadius)

    Surface(
        modifier = modifier
            .drawBehind {
                // 棱柱阴影 — 外层彩色投影（模拟玻璃厚度）
                drawRoundRect(
                    color = Color(0x1A007AFF),
                    topLeft = Offset(0f, 6.dp.toPx()),
                    size = Size(size.width, size.height),
                    cornerRadius = CornerRadius(cornerPx)
                )
                // 底部反光 — 玻璃底边缘的亮光
                drawRoundRect(
                    color = Color(0x0DFFFFFF),
                    topLeft = Offset(0f, (-3).dp.toPx()),
                    size = Size(size.width, size.height),
                    cornerRadius = CornerRadius(cornerPx)
                )
            }
            .clip(shape)
            .hazeEffect(state = hazeState, style = glassBlurStyle),
        shape = shape,
        color = Color.Transparent,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline)
    ) {
        Box {
            // 第一层：大范围柔光
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sdp(16.dp))
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = if (darkTheme) 0.03f else 0.06f),
                                Color.Transparent
                            )
                        ),
                        shape = RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius)
                    )
            )
            // 第二层：镜面亮边（极细）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sdp(4.dp))
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = if (darkTheme) 0.12f else 0.25f),
                                Color.White.copy(alpha = if (darkTheme) 0.03f else 0.06f),
                                Color.Transparent
                            )
                        ),
                        shape = RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius)
                    )
            )
            // 第三层：微光动画（shimmer）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sdp(24.dp))
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Transparent,
                                Color.White.copy(alpha = if (darkTheme) 0.04f else 0.07f),
                                Color.Transparent,
                                Color.Transparent
                            ),
                            startX = shimmerOffset - 150f,
                            endX = shimmerOffset + 150f
                        ),
                        shape = RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius)
                    )
            )
            // 用户内容
            content()
        }
    }
}
