package com.passwordmanager.android.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext

/**
 * Motion tokens — source: docs/design-system/tokens.json (primitive.motion).
 * Default to the Standard scheme; reserve Expressive (slight bounce) for a few delight
 * moments (unlock success, "Copied" confirmation, favorite morph). Always honor the
 * system "remove animations" setting via [animationsEnabled] (WCAG 2.3.3 reduced motion).
 */
object Motion {
    // Durations (ms)
    const val DURATION_FAST = 150
    const val DURATION_BASE = 200
    const val DURATION_EMPHASIZED = 300
    const val DURATION_LARGE = 375

    // Easings (cubic-bezier)
    val Standard: Easing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)
    val Decelerate: Easing = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)
    val Accelerate: Easing = CubicBezierEasing(0.4f, 0.0f, 1.0f, 1.0f)
    val Sharp: Easing = CubicBezierEasing(0.4f, 0.0f, 0.6f, 1.0f)
}

/**
 * True unless the user disabled animations system-wide (Developer options / accessibility
 * "Remove animations" → ANIMATOR_DURATION_SCALE == 0). Gate non-essential motion on this:
 * fall back to opacity-only / instant transitions.
 */
@Composable
@ReadOnlyComposable
fun animationsEnabled(): Boolean {
    val context = LocalContext.current
    val scale = Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1.0f
    )
    return scale != 0.0f
}
