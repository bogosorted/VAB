package com.aragao.vab.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object VabPalette {
    val CanvasBg = Color(0xFFF7F5F0)       // Alabastro quente estilo Claude
    val CardSurface = Color(0xFFFFFFFF)    // Card limpo estilo Jira
    val MutedSurface = Color(0xFFEFEDE7)   // Superfície secundária quente
    val CardBorder = Color(0xFFE2E0D8)     // Borda sutil 1dp
    val InkPrimary = Color(0xFF1F1E1D)     // Texto principal carvão
    val InkSecondary = Color(0xFF5E6C84)   // Cinza ardósia Jira
    val Terracotta = Color(0xFFC96442)     // Acento principal Claude
    val TerracottaSoft = Color(0xFFFAE8E0)
    val JiraBlue = Color(0xFF0052CC)       // Acento estrutural Jira
    val JiraBlueSoft = Color(0xFFE9F2FF)
    val P1Red = Color(0xFFDE350B)
    val P1RedSoft = Color(0xFFFFEBE6)
    val P2Amber = Color(0xFFB65C02)
    val P2AmberSoft = Color(0xFFFFF4E5)
    val SuccessGreen = Color(0xFF1F845A)
    val SuccessGreenSoft = Color(0xFFDCFFF1)
    val WarningAmberBg = Color(0xFFFFF8E1)
    val WarningAmberText = Color(0xFF8A5300)
}

private val VabCleanLightColorScheme = lightColorScheme(
    primary = VabPalette.Terracotta,
    onPrimary = Color.White,
    primaryContainer = VabPalette.TerracottaSoft,
    onPrimaryContainer = Color(0xFF6B2A14),
    secondary = VabPalette.JiraBlue,
    onSecondary = Color.White,
    secondaryContainer = VabPalette.JiraBlueSoft,
    onSecondaryContainer = Color(0xFF09326C),
    background = VabPalette.CanvasBg,
    onBackground = VabPalette.InkPrimary,
    surface = VabPalette.CardSurface,
    onSurface = VabPalette.InkPrimary,
    surfaceVariant = VabPalette.MutedSurface,
    onSurfaceVariant = VabPalette.InkSecondary,
    outline = VabPalette.CardBorder,
    outlineVariant = VabPalette.CardBorder,
    error = VabPalette.P1Red,
    onError = Color.White,
    errorContainer = VabPalette.P1RedSoft,
    onErrorContainer = Color(0xFF7A1800)
)

@Composable
fun VabTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = VabCleanLightColorScheme,
        content = content
    )
}
