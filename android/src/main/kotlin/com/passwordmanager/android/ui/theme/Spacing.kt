package com.passwordmanager.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Spacing scale (8px base grid) — source: docs/design-system/tokens.json
 * (primitive.space / semantic.shared.space). Replaces hardcoded dp values across screens.
 */
data class Spacing(
    // primitive steps
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
    val xxxl: Dp = 48.dp,
    // semantic aliases
    val cardPadding: Dp = 16.dp,
    val cardGap: Dp = 16.dp,
    val screenEdge: Dp = 16.dp,
    val fieldGap: Dp = 12.dp,
    val sectionGap: Dp = 24.dp,
)

internal val LocalSpacing = staticCompositionLocalOf { Spacing() }

/** Access spacing tokens: `MaterialTheme.spacing.cardPadding`. */
val MaterialTheme.spacing: Spacing
    @Composable
    @ReadOnlyComposable
    get() = LocalSpacing.current
