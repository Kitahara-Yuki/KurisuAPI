package com.kurisuapi.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 全局屏幕缩放比例，以 360dp 宽为标准。
 */
val LocalScreenScale = compositionLocalOf { 1f }

/**
 * 缩放 dp 值以适配不同屏幕尺寸。
 * 用法：Modifier.padding(sdp(16.dp))
 */
@Composable
fun sdp(value: Dp): Dp = value * LocalScreenScale.current

/**
 * 缩放 sp 值以适配不同屏幕尺寸。
 * 用法：fontSize = ssp(14.sp)
 */
@Composable
fun ssp(value: TextUnit): TextUnit = value * LocalScreenScale.current
