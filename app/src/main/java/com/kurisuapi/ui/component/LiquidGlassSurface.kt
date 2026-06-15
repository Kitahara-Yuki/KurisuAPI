package com.kurisuapi.ui.component

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kurisuapi.util.sdp

/**
 * 液态玻璃表面组件（胶囊形）
 *
 * 需要配合父级 Box 使用 layerBackdrop：
 * ```
 * val backdrop = rememberLayerBackdrop()
 * Box {
 *     Column(Modifier.layerBackdrop(backdrop)) { ... }
 *     LiquidGlassSurface(backdrop, modifier = Modifier.align(Alignment.BottomCenter)) {
 *         // 内容
 *     }
 * }
 * ```
 *
 * 设计原则：
 * - 折射为主（lens），模糊极轻（仅让像素过渡自然）
 * - 高透明度（像水不是像雾）
 * - 靠高光 + 阴影 + 内阴影勾勒玻璃形状
 *
 * @param backdrop 背景捕获层（在父级用 rememberLayerBackdrop() 创建）
 * @param modifier 外部修饰符
 * @param cornerRadius 圆角大小，默认 28dp（胶囊形）
 * @param blurRadius 模糊半径，默认 4dp（极轻）
 * @param refractionHeight 折射高度，默认 20dp
 * @param refractionAmount 折射强度，默认 28dp
 */
@Composable
fun LiquidGlassSurface(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = sdp(28.dp),
    blurRadius: Dp = 4.dp,
    refractionHeight: Dp = 20.dp,
    refractionAmount: Dp = 28.dp,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val blurPx = with(density) { blurRadius.toPx() }
    val refractionHeightPx = with(density) { refractionHeight.toPx() }
    val refractionAmountPx = with(density) { refractionAmount.toPx() }
    val shape = { RoundedCornerShape(cornerRadius) }
    val canLens = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    Box(
        modifier = modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = shape,
                effects = {
                    // 极轻模糊 — 让像素过渡自然，不是磨砂效果
                    blur(blurPx)
                    // 核心：透镜折射 + 色差（API 33+）
                    if (canLens) {
                        lens(refractionHeightPx, refractionAmountPx, chromaticAberration = true)
                    }
                },
                highlight = {
                    // 环境高光 — 勾勒玻璃上边缘
                    Highlight.Ambient.copy(
                        alpha = 0.3f
                    )
                },
                shadow = {
                    // 投影 — 玻璃悬浮在背景之上
                    Shadow(
                        radius = 6.dp,
                        color = Color.Black.copy(alpha = 0.08f)
                    )
                },
                innerShadow = {
                    // 内阴影 — 玻璃厚度感，白色背景上也能看见
                    InnerShadow(
                        radius = 3.dp,
                        alpha = 0.15f
                    )
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
