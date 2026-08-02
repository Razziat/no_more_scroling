package com.antiscroll.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF176B45),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5F3E2),
    onPrimaryContainer = Color(0xFF082A1B),
    secondary = Color(0xFF4D6357),
    background = Color(0xFFF5F8F5),
    onBackground = Color(0xFF171D1A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF171D1A),
    surfaceVariant = Color(0xFFE8EEE9),
    onSurfaceVariant = Color(0xFF414944),
    outline = Color(0xFF89928C),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF79D6A6),
    onPrimary = Color(0xFF003823),
    primaryContainer = Color(0xFF005234),
    onPrimaryContainer = Color(0xFF98F3C1),
    secondary = Color(0xFFB4CCBD),
    background = Color(0xFF101512),
    onBackground = Color(0xFFE0E6E1),
    surface = Color(0xFF171D1A),
    onSurface = Color(0xFFE0E6E1),
    surfaceVariant = Color(0xFF29322C),
    onSurfaceVariant = Color(0xFFC0C9C2),
    outline = Color(0xFF89928C),
)

@Composable
fun AntiScrollTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = Typography(),
        content = content,
    )
}
