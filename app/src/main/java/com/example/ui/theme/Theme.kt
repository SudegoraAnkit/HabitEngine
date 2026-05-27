package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import com.example.infrastructure.adapters.ui.ThemeMode

// Cyberpunk Dark Scheme
private val CyberColorScheme = darkColorScheme(
    primary = CyberPrimary,
    secondary = CyberSecondary,
    tertiary = CyberTertiary,
    background = CyberBg,
    surface = CyberCard,
    onPrimary = CyberBg,
    onSecondary = CyberText,
    onBackground = CyberText,
    onSurface = CyberText
)

// Sunset Warm Dusk Scheme
private val SunsetColorScheme = darkColorScheme(
    primary = SunsetPrimary,
    secondary = SunsetSecondary,
    tertiary = SunsetTertiary,
    background = SunsetBg,
    surface = SunsetCard,
    onPrimary = SunsetBg,
    onSecondary = SunsetText,
    onBackground = SunsetText,
    onSurface = SunsetText
)

@Composable
fun HabitEngineTheme(
    themeMode: ThemeMode = ThemeMode.CYBERPUNK,
    content: @Composable () -> Unit
) {
    val colorScheme = when (themeMode) {
        ThemeMode.CYBERPUNK -> CyberColorScheme
        ThemeMode.SUNSET -> SunsetColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
