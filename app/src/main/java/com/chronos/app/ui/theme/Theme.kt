package com.chronos.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.chronos.app.data.repository.ThemeMode

private val DarkColors = darkColorScheme(
    primary = Violet400,
    onPrimary = DarkBackground,
    primaryContainer = Violet900,
    onPrimaryContainer = Violet200,
    secondary = Violet300,
    onSecondary = DarkBackground,
    secondaryContainer = DarkSurfaceHigh,
    onSecondaryContainer = Violet200,
    tertiary = Violet500,
    onTertiary = DarkBackground,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceContainer = DarkSurfaceVariant,
    surfaceContainerHigh = DarkSurfaceHigh,
    outline = DarkOutline,
    error = Danger,
    onError = Color.White,
)

private val LightColors = lightColorScheme(
    primary = Violet600,
    onPrimary = Color.White,
    primaryContainer = Violet200,
    onPrimaryContainer = Violet900,
    secondary = Violet500,
    onSecondary = Color.White,
    secondaryContainer = LightSurfaceVariant,
    onSecondaryContainer = Violet700,
    tertiary = Violet600,
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceContainer = LightSurfaceVariant,
    outline = LightOutline,
    error = Danger,
    onError = Color.White,
)

@Composable
fun ChronosTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colors,
        typography = ChronosTypography,
        shapes = ChronosShapes,
        content = content,
    )
}
