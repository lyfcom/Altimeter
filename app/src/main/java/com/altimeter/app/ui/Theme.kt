package com.altimeter.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.altimeter.app.data.ThemeMode

val Forest = Color(0xFF172B24)
val Signal = Color(0xFFD2EB87)
val MutedGreen = Color(0xFF9CAC9F)
private val Light =
    lightColorScheme(
        primary = Color(0xFF345F42),
        onPrimary = Color.White,
        primaryContainer = Signal,
        onPrimaryContainer = Forest,
        secondary = Color(0xFF516348),
        secondaryContainer = Color(0xFFE1E8D4),
        background = Color(0xFFF4F5EF),
        onBackground = Color(0xFF1E2D25),
        surface = Color(0xFFFCFDF7),
        onSurface = Color(0xFF1E2D25),
        surfaceVariant = Color(0xFFE9EDE3),
        onSurfaceVariant = Color(0xFF647063),
        outline = Color(0xFFABB4A5),
        outlineVariant = Color(0xFFDCE1D6),
    )
private val Dark =
    darkColorScheme(
        primary = Signal,
        onPrimary = Forest,
        primaryContainer = Color(0xFF344B34),
        onPrimaryContainer = Signal,
        secondary = Color(0xFFB9C9A7),
        secondaryContainer = Color(0xFF303F33),
        background = Color(0xFF101B16),
        onBackground = Color(0xFFE9F0E3),
        surface = Color(0xFF1B2921),
        onSurface = Color(0xFFE9F0E3),
        surfaceVariant = Color(0xFF25352A),
        onSurfaceVariant = Color(0xFFA7B5A7),
        outline = Color(0xFF6E806F),
        outlineVariant = Color(0xFF334637),
    )
private val Type =
    Typography(
        headlineLarge =
            TextStyle(fontSize = 32.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1).sp),
        headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.SemiBold),
        titleLarge = TextStyle(fontSize = 21.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
        bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 25.sp),
        bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 22.sp),
        bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
        labelSmall =
            TextStyle(fontSize = 10.sp, fontFamily = FontFamily.Monospace, letterSpacing = 1.sp),
    )

@Composable
fun AltimeterTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark =
        when (mode) {
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
    MaterialTheme(colorScheme = if (dark) Dark else Light, typography = Type, content = content)
}
