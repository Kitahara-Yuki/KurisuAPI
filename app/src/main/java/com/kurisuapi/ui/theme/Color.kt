package com.kurisuapi.ui.theme

import androidx.compose.ui.graphics.Color

// Apple System Colors
val AppleBlue = Color(0xFF007AFF)
val AppleGreen = Color(0xFF34C759)
val AppleOrange = Color(0xFFFF9500)
val AppleRed = Color(0xFFFF3B30)
val AppleGray = Color(0xFF8E8E93)
val ApplePink = Color(0xFFFF2D55)
val AppleTeal = Color(0xFF5AC8FA)
val AppleIndigo = Color(0xFF5856D6)

// Glassmorphism overlay colors
val GlassWhite = Color(0xCCFFFFFF) // rgba(255,255,255,0.80) — light mode card
val GlassDark = Color(0xCC1C1C1E)  // rgba(28,28,30,0.80) — dark mode card
val GlassBorderLight = Color(0x33FFFFFF) // subtle white border
val GlassBorderDark = Color(0x1AFFFFFF)  // very subtle border for dark

// Light Theme
val md_theme_light_primary = AppleBlue
val md_theme_light_onPrimary = Color.White
val md_theme_light_primaryContainer = Color(0xFFE3F2FF)
val md_theme_light_onPrimaryContainer = Color(0xFF001D34)
val md_theme_light_secondary = Color(0xFF5C5C5C)
val md_theme_light_onSecondary = Color.White
val md_theme_light_secondaryContainer = Color(0xFFF2F2F7)
val md_theme_light_onSecondaryContainer = Color(0xFF1C1C1E)
val md_theme_light_tertiary = AppleOrange
val md_theme_light_onTertiary = Color.White
val md_theme_light_tertiaryContainer = Color(0xFFFFF3E0)
val md_theme_light_onTertiaryContainer = Color(0xFF3D1D00)
val md_theme_light_background = Color(0xFFF2F2F7) // iOS system background
val md_theme_light_onBackground = Color(0xFF1C1C1E)
val md_theme_light_surface = GlassWhite
val md_theme_light_onSurface = Color(0xFF1C1C1E)
val md_theme_light_surfaceVariant = Color(0xCCF2F2F7)
val md_theme_light_onSurfaceVariant = Color(0xFF3C3C43)
val md_theme_light_error = AppleRed
val md_theme_light_onError = Color.White
val md_theme_light_errorContainer = Color(0xFFFFE5E5)
val md_theme_light_onErrorContainer = Color(0xFF5C0000)
val md_theme_light_outline = Color(0x33000000)

// Dark Theme
val md_theme_dark_primary = Color(0xFF5EACFF)
val md_theme_dark_onPrimary = Color(0xFF001D34)
val md_theme_dark_primaryContainer = Color(0xFF004B87)
val md_theme_dark_onPrimaryContainer = Color(0xFFE3F2FF)
val md_theme_dark_secondary = Color(0xFFC7C7CC)
val md_theme_dark_onSecondary = Color(0xFF1C1C1E)
val md_theme_dark_secondaryContainer = Color(0xFF3A3A3C)
val md_theme_dark_onSecondaryContainer = Color(0xFFF2F2F7)
val md_theme_dark_tertiary = Color(0xFFFFB84D)
val md_theme_dark_onTertiary = Color(0xFF3D1D00)
val md_theme_dark_tertiaryContainer = Color(0xFF663200)
val md_theme_dark_onTertiaryContainer = Color(0xFFFFF3E0)
val md_theme_dark_background = Color(0xFF000000) // iOS dark background
val md_theme_dark_onBackground = Color(0xFFF2F2F7)
val md_theme_dark_surface = GlassDark
val md_theme_dark_onSurface = Color(0xFFF2F2F7)
val md_theme_dark_surfaceVariant = Color(0xCC1C1C1E)
val md_theme_dark_onSurfaceVariant = Color(0xFFC7C7CC)
val md_theme_dark_error = Color(0xFFFF6961)
val md_theme_dark_onError = Color(0xFF5C0000)
val md_theme_dark_errorContainer = Color(0xFF8C000A)
val md_theme_dark_onErrorContainer = Color(0xFFFFE5E5)
val md_theme_dark_outline = Color(0x33FFFFFF)  // 20% 白，深色模式可见边框
