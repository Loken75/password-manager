package com.passwordmanager.android.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import com.passwordmanager.config.ThemeMode

/**
 * Color schemes — source: docs/design-system/tokens.json (semantic.light / semantic.dark).
 * Used when Material You dynamic color is unavailable (< API 31) or as the brand fallback.
 * Accent seed: indigo #4F46E5.
 */
private val LightColorScheme = lightColorScheme(
    primary = Palette.accent40,
    onPrimary = Palette.neutral0,
    primaryContainer = Palette.accent95,
    onPrimaryContainer = Palette.accent20,
    secondary = Palette.accent50,
    onSecondary = Palette.neutral0,
    secondaryContainer = Palette.accent90,
    onSecondaryContainer = Palette.accent20,
    tertiary = Palette.accent30,
    onTertiary = Palette.neutral0,
    background = Palette.neutral0,
    onBackground = Palette.neutral100,
    surface = Palette.neutral0,
    onSurface = Palette.neutral100,
    surfaceVariant = Palette.neutral10,
    onSurfaceVariant = Palette.neutral50,
    surfaceContainerLowest = Palette.neutral0,
    surfaceContainerLow = Palette.neutral5,
    surfaceContainer = Palette.neutral5,
    surfaceContainerHigh = Palette.neutral10,
    surfaceContainerHighest = Palette.neutral20,
    outline = Palette.neutral30,
    outlineVariant = Palette.neutral20,
    error = Palette.red50,
    onError = Palette.neutral0,
    errorContainer = Palette.red90,
    onErrorContainer = Palette.red40,
)

private val DarkColorScheme = darkColorScheme(
    primary = Palette.accent60,
    onPrimary = Palette.neutral100,
    primaryContainer = Palette.accent20,
    onPrimaryContainer = Palette.accent90,
    secondary = Palette.accent70,
    onSecondary = Palette.neutral100,
    secondaryContainer = Palette.accent20,
    onSecondaryContainer = Palette.accent90,
    tertiary = Palette.accent80,
    onTertiary = Palette.neutral100,
    background = Palette.neutral95,
    onBackground = Palette.neutral5,
    surface = Palette.neutral95,
    onSurface = Palette.neutral5,
    surfaceVariant = Palette.neutral80,
    onSurfaceVariant = Palette.neutral40,
    surfaceContainerLowest = Palette.neutral100,
    surfaceContainerLow = Palette.neutral95,
    surfaceContainer = Palette.neutral90,
    surfaceContainerHigh = Palette.neutral80,
    surfaceContainerHighest = Palette.neutral70,
    outline = Palette.neutral60,
    outlineVariant = Palette.neutral80,
    error = Palette.red60,
    onError = Palette.neutral100,
    errorContainer = Palette.red40,
    onErrorContainer = Palette.red90,
)

@Composable
fun PasswordManagerTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    // Security status / favorite intents stay fixed regardless of dynamic color.
    val appColors = if (darkTheme) DarkAppColors else LightAppColors

    CompositionLocalProvider(
        LocalAppColors provides appColors,
        LocalSpacing provides Spacing(),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = Shapes,
            content = content
        )
    }
}
