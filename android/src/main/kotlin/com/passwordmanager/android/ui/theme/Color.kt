package com.passwordmanager.android.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.absoluteValue

/**
 * Color primitives — source of truth: docs/design-system/tokens.json (primitive.color).
 * Do not reference these directly from UI: go through the M3 [androidx.compose.material3.ColorScheme]
 * or [AppColors] (security/category intents). Direction: "Expressive & modern", accent indigo #4F46E5.
 */
internal object Palette {
    // Accent — steel blue (tonal ramp), direction "Calme & confiance"
    val accent10 = Color(0xFF0F1F3D)
    val accent20 = Color(0xFF1E3A6E)
    val accent30 = Color(0xFF2A4E94)
    val accent40 = Color(0xFF3B66C9)
    val accent50 = Color(0xFF5680DB)
    val accent60 = Color(0xFF6E92E8)
    val accent70 = Color(0xFF94B0EF)
    val accent80 = Color(0xFFBACCF5)
    val accent90 = Color(0xFFD7E2F7)
    val accent95 = Color(0xFFEAF0FB)

    // Neutrals (surfaces / text)
    val neutral0 = Color(0xFFFFFFFF)
    val neutral5 = Color(0xFFF7F7FB)
    val neutral10 = Color(0xFFEEEEF4)
    val neutral20 = Color(0xFFD9D9E3)
    val neutral30 = Color(0xFFBFBFCC)
    val neutral40 = Color(0xFF9595A3)
    val neutral50 = Color(0xFF71717F)
    val neutral60 = Color(0xFF52525E)
    val neutral70 = Color(0xFF3A3A44)
    val neutral80 = Color(0xFF2A2A33)
    val neutral90 = Color(0xFF1A1A20)
    val neutral95 = Color(0xFF141418)
    val neutral100 = Color(0xFF111116)

    // Security status (fixed — never altered by dynamic color), calm tones
    val green40 = Color(0xFF1E7A4F)
    val green50 = Color(0xFF2F9E68)
    val green60 = Color(0xFF46B883)
    val green90 = Color(0xFFE7F4ED)
    val amber40 = Color(0xFF8A5D08)
    val amber50 = Color(0xFFB97D08)
    val amber60 = Color(0xFFE0A23A)
    val amber90 = Color(0xFFFBF1DD)
    val red40 = Color(0xFFA23A3A)
    val red50 = Color(0xFFCF4747)
    val red60 = Color(0xFFE36464)
    val red90 = Color(0xFFFBEAEA)
    val blue40 = Color(0xFF234E86)
    val blue50 = Color(0xFF2F6FB0)
    val blue60 = Color(0xFF5E93D6)
    val blue90 = Color(0xFFE7F0F9)
    // Favorite accent (gold), distinct from status colors
    val gold50 = Color(0xFFC79A33)
    val gold60 = Color(0xFFD9B95B)
}

/**
 * Deterministic category palette — source: tokens.json (primitive.color.category).
 * 10 mid-tone hues that stay distinguishable in light and dark. Never the sole signal:
 * always paired with a label/initial (color-vision accessibility).
 */
val CategoryColors = listOf(
    Color(0xFF6366F1), // indigo
    Color(0xFF14B8A6), // teal
    Color(0xFFF43F5E), // rose
    Color(0xFFF59E0B), // amber
    Color(0xFFA855F7), // violet
    Color(0xFF06B6D4), // cyan
    Color(0xFF84CC16), // lime
    Color(0xFFF97316), // orange
    Color(0xFF0EA5E9), // sky
    Color(0xFF64748B), // slate
)

fun categoryColor(category: String?): Color {
    if (category.isNullOrBlank()) return CategoryColors.last()
    val index = category.hashCode().absoluteValue % CategoryColors.size
    return CategoryColors[index]
}
