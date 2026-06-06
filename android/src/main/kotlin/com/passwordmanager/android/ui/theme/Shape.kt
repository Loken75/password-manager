package com.passwordmanager.android.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Corner radii — source: docs/design-system/tokens.json (primitive.radius / semantic.shared.radius).
 * extraSmall 4 · small 8 · medium 12 (buttons/inputs) · large 16 (cards) · extraLarge 24 (sheets/dialogs).
 * Pill shapes (chips/badges/FAB) use [PillShape].
 */
val Shapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

val PillShape = RoundedCornerShape(percent = 50)
