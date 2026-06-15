package com.kurisuapi.ui.component

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

/**
 * 液态玻璃容器（一键集成）
 *
 * 自动处理 backdrop 创建、背景捕获、API 降级。
 * 任何页面要加液态玻璃，只需指定背景内容和玻璃内容即可。
 *
 * 用法：
 * ```
 * LiquidGlassContainer(
 *     glassModifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
 *     background = {
 *         // 消息列表、图片、任何被玻璃折射的内容
 *     },
 *     glass = {
 *         // 玻璃里面显示的内容（输入框、按钮等）
 *     }
 * )
 * ```
 *
 * @param modifier 容器修饰符（通常填满屏幕）
 * @param glassModifier 玻璃面板修饰符（控制位置、边距等）
 * @param background 背景内容 — 会在玻璃背后显示，被折射
 * @param glass 玻璃内部内容
 */
@Composable
fun LiquidGlassContainer(
    modifier: Modifier = Modifier,
    glassModifier: Modifier = Modifier,
    background: @Composable () -> Unit,
    glass: @Composable () -> Unit,
) {
    val backdrop = rememberLayerBackdrop()
    val hazeState = remember { HazeState() }
    val canUseBackdrop = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    Box(modifier = modifier) {
        // 背景层 — 同时注册到 backdrop 和 Haze（按 API 自动选）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (canUseBackdrop) Modifier.layerBackdrop(backdrop)
                    else Modifier.hazeSource(state = hazeState)
                )
        ) {
            background()
        }

        // 前景层 — 液态玻璃（或毛玻璃降级）
        if (canUseBackdrop) {
            LiquidGlassSurface(
                backdrop = backdrop,
                modifier = glassModifier
            ) {
                glass()
            }
        } else {
            FrostedGlassSurface(
                hazeState = hazeState,
                modifier = glassModifier
            ) {
                glass()
            }
        }
    }
}
