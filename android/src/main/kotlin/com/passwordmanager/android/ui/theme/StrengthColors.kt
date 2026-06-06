package com.passwordmanager.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.passwordmanager.crypto.PasswordStrengthAnalyzer.Strength

/**
 * Maps a [Strength] level to its fixed security-status color (fill, e.g. bars/icons/avatars).
 * Source: docs/design-system/tokens.json (semantic.*.color.status). Always pair with the
 * level label — color is never the sole signal.
 */
@Composable
@ReadOnlyComposable
fun strengthColor(strength: Strength): Color = with(MaterialTheme.appColors) {
    when (strength) {
        Strength.WEAK -> statusWeak
        Strength.MEDIUM -> statusMedium
        Strength.STRONG -> statusStrong
        Strength.VERY_STRONG -> statusVeryStrong
    }
}

/** Darker status tone meeting WCAG 4.5:1 for colored text on light surfaces. */
@Composable
@ReadOnlyComposable
fun strengthTextColor(strength: Strength): Color = with(MaterialTheme.appColors) {
    when (strength) {
        Strength.WEAK -> statusWeakText
        Strength.MEDIUM -> statusMediumText
        Strength.STRONG -> statusStrongText
        Strength.VERY_STRONG -> statusVeryStrongText
    }
}
