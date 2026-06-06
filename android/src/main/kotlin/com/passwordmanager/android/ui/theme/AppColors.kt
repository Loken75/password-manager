package com.passwordmanager.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Semantic colors that live OUTSIDE the Material 3 [androidx.compose.material3.ColorScheme] because
 * they must stay fixed regardless of Material You dynamic color: security status signals and the
 * favorite accent. Source: docs/design-system/tokens.json (semantic.*.color.status / color.favorite).
 *
 * `*Text` variants are the darker tones meeting WCAG 4.5:1 for colored text on light surfaces;
 * the plain variant is the fill used for bars/icons/badges.
 */
data class AppColors(
    val statusVeryStrong: Color,
    val statusVeryStrongText: Color,
    val statusStrong: Color,
    val statusStrongText: Color,
    val statusMedium: Color,
    val statusMediumText: Color,
    val statusWeak: Color,
    val statusWeakText: Color,
    val onStatus: Color,
    val favorite: Color,
)

internal val LightAppColors = AppColors(
    statusVeryStrong = Palette.blue50,
    statusVeryStrongText = Palette.blue40,
    statusStrong = Palette.green50,
    statusStrongText = Palette.green40,
    statusMedium = Palette.amber50,
    statusMediumText = Palette.amber40,
    statusWeak = Palette.red50,
    statusWeakText = Palette.red40,
    onStatus = Palette.neutral0,
    favorite = Palette.gold50,
)

internal val DarkAppColors = AppColors(
    statusVeryStrong = Palette.blue60,
    statusVeryStrongText = Palette.blue60,
    statusStrong = Palette.green60,
    statusStrongText = Palette.green60,
    statusMedium = Palette.amber60,
    statusMediumText = Palette.amber60,
    statusWeak = Palette.red60,
    statusWeakText = Palette.red60,
    onStatus = Palette.neutral100,
    favorite = Palette.gold60,
)

internal val LocalAppColors = staticCompositionLocalOf { LightAppColors }

/** Access security/favorite intents: `MaterialTheme.appColors.statusWeak`. */
val MaterialTheme.appColors: AppColors
    @Composable
    @ReadOnlyComposable
    get() = LocalAppColors.current
