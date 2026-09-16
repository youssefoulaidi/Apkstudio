package com.apkstudio.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Green40 = Color(0xFF2DA44E)
private val GreenDark = Color(0xFF238636)
private val GreenDeep = Color(0xFF1A7F37)
private val DarkBg = Color(0xFF0D1117)
private val DarkSurface = Color(0xFF161B22)

private val LightScheme = lightColorScheme(
    primary = GreenDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8F3DC),
    onPrimaryContainer = Color(0xFF0B3D1B),
    secondary = GreenDark,
    onSecondary = Color.White,
    tertiary = Color(0xFF1F6FEB),
    error = Color(0xFFCF222E)
)

private val DarkScheme = darkColorScheme(
    primary = Green40,
    onPrimary = Color(0xFF06210F),
    primaryContainer = Color(0xFF0B3D1B),
    onPrimaryContainer = Color(0xFFD8F3DC),
    secondary = Green40,
    onSecondary = Color(0xFF06210F),
    tertiary = Color(0xFF58A6FF),
    background = DarkBg,
    surface = DarkSurface,
    error = Color(0xFFFF7B72)
)

@Composable
fun ApkStudioTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (dark) DarkScheme else LightScheme,
        content = content
    )
}
