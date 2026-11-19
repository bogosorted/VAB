package com.aragao.vab.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val VabDarkColorScheme = darkColorScheme(
    primary = Color(0xFF7C9BFF),
    onPrimary = Color(0xFF0B1120),
    primaryContainer = Color(0xFF1E2946),
    onPrimaryContainer = Color(0xFFDCE4FF),
    secondary = Color(0xFF5CE1E6),
    onSecondary = Color(0xFF062022),
    background = Color(0xFF0E1117),
    onBackground = Color(0xFFE6EDF3),
    surface = Color(0xFF161B22),
    onSurface = Color(0xFFE6EDF3),
    surfaceVariant = Color(0xFF21262D),
    onSurfaceVariant = Color(0xFF9EA7B3),
    error = Color(0xFFFF6B6B)
)

@Composable
fun VabTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = VabDarkColorScheme,
        content = content
    )
}
