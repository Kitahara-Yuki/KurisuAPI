package com.kurisuapi.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.kurisuapi.data.entity.ThemeEntity
import com.kurisuapi.util.parseColor
import com.materialkolor.rememberDynamicColorScheme

// Apple-style rounded shapes
val GlassShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
)

/** 是否启用了自定义主题 */
val LocalIsCustomTheme = staticCompositionLocalOf { false }

/** 当前活跃的 ThemeEntity，供 ChatBubble/ChatLogScreen 读取自定义颜色 */
val LocalActiveTheme = staticCompositionLocalOf<ThemeEntity?> { null }

/** 当前是否深色模式（尊重 App 内设置，非系统） */
val LocalIsDarkTheme = staticCompositionLocalOf { false }

/**
 * 从 seed color + darkTheme 生成 ColorScheme（用于预览卡片）。
 */
@Composable
fun glassifiedDynamicScheme(seedColor: Color, darkTheme: Boolean): ColorScheme {
    val generated = rememberDynamicColorScheme(seedColor = seedColor, isDark = false)
    return if (darkTheme) {
        generated.copy(
            background = md_theme_dark_background,
            onBackground = md_theme_dark_onBackground,
            surface = md_theme_dark_surface,
            onSurface = md_theme_dark_onSurface,
            surfaceVariant = md_theme_dark_surfaceVariant,
            onSurfaceVariant = md_theme_dark_onSurfaceVariant,
            outline = md_theme_dark_outline,
            outlineVariant = md_theme_dark_outline,
        )
    } else {
        generated.copy(
            background = md_theme_light_background,
            onBackground = md_theme_light_onBackground,
            surface = md_theme_light_surface,
            onSurface = md_theme_light_onSurface,
            surfaceVariant = md_theme_light_surfaceVariant,
            onSurfaceVariant = md_theme_light_onSurfaceVariant,
            outline = md_theme_light_outline,
            outlineVariant = md_theme_light_outline,
        )
    }
}

/**
 * 从 ThemeEntity + darkTheme 构建 ColorScheme。
 */
@Composable
fun themeEntityToColorScheme(theme: ThemeEntity?, darkTheme: Boolean): ColorScheme {
    val seedColor = theme?.seedColorHex?.takeIf { it.isNotBlank() }?.parseColor()
    // 只有用户自建主题（isBuiltIn = false）才完全独立于 dark/light 模式
    val isCustom = theme != null && !theme.isBuiltIn
    val base = if (seedColor != null && isCustom) {
        val generated = rememberDynamicColorScheme(seedColor = seedColor, isDark = false)
        generated.copy(
            background = theme.chatBgColorHex.takeIf { it.isNotBlank() }?.parseColor()
                ?: generated.background,
            onBackground = theme.fontColorHex.takeIf { it.isNotBlank() }?.parseColor()
                ?: generated.onBackground,
            surface = theme.cardColorHex.takeIf { it.isNotBlank() }?.parseColor()
                ?: generated.surface,
            onSurface = theme.fontColorHex.takeIf { it.isNotBlank() }?.parseColor()
                ?: generated.onSurface,
            surfaceVariant = generated.surfaceVariant,
            onSurfaceVariant = generated.onSurfaceVariant,
            outline = generated.outline,
        )
    } else if (seedColor != null) {
        // 内置主题：primary 色固定（浅色生成），只有背景/表面色叠加 dark/light 模式
        val generated = rememberDynamicColorScheme(seedColor = seedColor, isDark = false)
        if (darkTheme) {
            generated.copy(
                background = md_theme_dark_background,
                onBackground = md_theme_dark_onBackground,
                surface = md_theme_dark_surface,
                onSurface = md_theme_dark_onSurface,
                surfaceVariant = md_theme_dark_surfaceVariant,
                onSurfaceVariant = md_theme_dark_onSurfaceVariant,
                outline = md_theme_dark_outline,
                outlineVariant = md_theme_dark_outline,
            )
        } else {
            generated.copy(
                background = md_theme_light_background,
                onBackground = md_theme_light_onBackground,
                surface = md_theme_light_surface,
                onSurface = md_theme_light_onSurface,
                surfaceVariant = md_theme_light_surfaceVariant,
                onSurfaceVariant = md_theme_light_onSurfaceVariant,
                outline = md_theme_light_outline,
                outlineVariant = md_theme_light_outline,
            )
        }
    } else {
        // 默认主题：primary 色固定（浅色生成），只有背景/表面色叠加 dark/light 模式
        val generated = rememberDynamicColorScheme(seedColor = AppleBlue, isDark = false)
        if (darkTheme) {
            generated.copy(
                background = md_theme_dark_background,
                onBackground = md_theme_dark_onBackground,
                surface = md_theme_dark_surface,
                onSurface = md_theme_dark_onSurface,
                surfaceVariant = md_theme_dark_surfaceVariant,
                onSurfaceVariant = md_theme_dark_onSurfaceVariant,
                outline = md_theme_dark_outline,
                outlineVariant = md_theme_dark_outline,
            )
        } else {
            generated.copy(
                background = md_theme_light_background,
                onBackground = md_theme_light_onBackground,
                surface = md_theme_light_surface,
                onSurface = md_theme_light_onSurface,
                surfaceVariant = md_theme_light_surfaceVariant,
                onSurfaceVariant = md_theme_light_onSurfaceVariant,
                outline = md_theme_light_outline,
                outlineVariant = md_theme_light_outline,
            )
        }
    }

    return base
}

/**
 * 智能 TopAppBar 配色：
 * 优先使用主题中手动指定的顶栏颜色，其次看是否有自定义主题。
 */
@Composable
fun topBarColors(): TopAppBarColors {
    val theme = LocalActiveTheme.current
    val scheme = MaterialTheme.colorScheme

    val bannerColor = theme?.bannerColorHex?.takeIf { it.isNotBlank() }?.parseColor()
    val bannerTextColor = theme?.bannerTextColorHex?.takeIf { it.isNotBlank() }?.parseColor()

    val isLight = !LocalIsDarkTheme.current
    val container = bannerColor
        ?: if (theme?.seedColorHex?.isNotBlank() == true) scheme.primary
           else if (isLight) scheme.primary           // 浅色模式：横幅用主题色
           else scheme.surface                         // 深色模式：横幅用暗色表面
    val content = bannerTextColor
        ?: if (theme?.seedColorHex?.isNotBlank() == true) scheme.onPrimary
           else if (isLight) scheme.onPrimary
           else scheme.onSurface

    return TopAppBarDefaults.topAppBarColors(
        containerColor = container,
        titleContentColor = content,
        navigationIconContentColor = content,
        actionIconContentColor = content,
    )
}

private val LightColorScheme = lightColorScheme(
    primary = md_theme_light_primary,
    onPrimary = md_theme_light_onPrimary,
    primaryContainer = md_theme_light_primaryContainer,
    onPrimaryContainer = md_theme_light_onPrimaryContainer,
    secondary = md_theme_light_secondary,
    onSecondary = md_theme_light_onSecondary,
    secondaryContainer = md_theme_light_secondaryContainer,
    onSecondaryContainer = md_theme_light_onSecondaryContainer,
    tertiary = md_theme_light_tertiary,
    onTertiary = md_theme_light_onTertiary,
    tertiaryContainer = md_theme_light_tertiaryContainer,
    onTertiaryContainer = md_theme_light_onTertiaryContainer,
    background = md_theme_light_background,
    onBackground = md_theme_light_onBackground,
    surface = md_theme_light_surface,
    onSurface = md_theme_light_onSurface,
    surfaceVariant = md_theme_light_surfaceVariant,
    onSurfaceVariant = md_theme_light_onSurfaceVariant,
    error = md_theme_light_error,
    onError = md_theme_light_onError,
    errorContainer = md_theme_light_errorContainer,
    onErrorContainer = md_theme_light_onErrorContainer,
    outline = md_theme_light_outline,
)

private val DarkColorScheme = darkColorScheme(
    primary = md_theme_dark_primary,
    onPrimary = md_theme_dark_onPrimary,
    primaryContainer = md_theme_dark_primaryContainer,
    onPrimaryContainer = md_theme_dark_onPrimaryContainer,
    secondary = md_theme_dark_secondary,
    onSecondary = md_theme_dark_onSecondary,
    secondaryContainer = md_theme_dark_secondaryContainer,
    onSecondaryContainer = md_theme_dark_onSecondaryContainer,
    tertiary = md_theme_dark_tertiary,
    onTertiary = md_theme_dark_onTertiary,
    tertiaryContainer = md_theme_dark_tertiaryContainer,
    onTertiaryContainer = md_theme_dark_onTertiaryContainer,
    background = md_theme_dark_background,
    onBackground = md_theme_dark_onBackground,
    surface = md_theme_dark_surface,
    onSurface = md_theme_dark_onSurface,
    surfaceVariant = md_theme_dark_surfaceVariant,
    onSurfaceVariant = md_theme_dark_onSurfaceVariant,
    error = md_theme_dark_error,
    onError = md_theme_dark_onError,
    errorContainer = md_theme_dark_errorContainer,
    onErrorContainer = md_theme_dark_onErrorContainer,
    outline = md_theme_dark_outline,
)

@Composable
fun KurisuAPITheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    activeTheme: ThemeEntity? = null,
    content: @Composable () -> Unit
) {
    val colorScheme = themeEntityToColorScheme(activeTheme, darkTheme)
    val isCustom = activeTheme != null && !activeTheme.isBuiltIn

    CompositionLocalProvider(
        LocalIsCustomTheme provides isCustom,
        LocalActiveTheme provides activeTheme,
        LocalIsDarkTheme provides darkTheme,
    ) {
        // 适配系统导航栏（小白条）颜色，与 App 背景一致
        val view = LocalView.current
        if (!view.isInEditMode) {
            val context = LocalContext.current
            SideEffect {
                val window = (context as? Activity)?.window ?: return@SideEffect
                window.navigationBarColor = colorScheme.background.toArgb()
                WindowCompat.getInsetsController(window, view)
                    .isAppearanceLightNavigationBars = !darkTheme
            }
        }

        MaterialTheme(
            colorScheme = colorScheme,
            shapes = GlassShapes,
            typography = Typography(),
            content = content
        )
    }
}
