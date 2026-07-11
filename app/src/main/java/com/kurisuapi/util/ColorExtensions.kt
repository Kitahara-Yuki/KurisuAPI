package com.kurisuapi.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

fun Color.toHex(): String {
    val argb = this.toArgb()
    val a = (argb shr 24) and 0xFF
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    return "#%02X%02X%02X%02X".format(a, r, g, b)
}

fun String.parseColor(): Color? {
    return try {
        val hex = if (startsWith("#")) this else "#$this"
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: Exception) { null }
}

fun parseColorOrNull(hex: String?): Color? {
    return hex?.takeIf { it.isNotBlank() }?.parseColor()
}
