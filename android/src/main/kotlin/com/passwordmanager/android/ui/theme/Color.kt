package com.passwordmanager.android.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.absoluteValue

val Blue40 = Color(0xFF1565C0)
val Blue80 = Color(0xFF90CAF9)
val BlueGrey40 = Color(0xFF546E7A)
val BlueGrey80 = Color(0xFFB0BEC5)

val StrengthWeak = Color(0xFFE53935)
val StrengthMedium = Color(0xFFFB8C00)
val StrengthStrong = Color(0xFF43A047)
val StrengthVeryStrong = Color(0xFF1E88E5)

val CategoryColors = listOf(
    Color(0xFF1E88E5), // Blue
    Color(0xFF43A047), // Green
    Color(0xFFE53935), // Red
    Color(0xFF8E24AA), // Purple
    Color(0xFFFB8C00), // Orange
    Color(0xFF00ACC1), // Cyan
    Color(0xFF3949AB), // Indigo
    Color(0xFFD81B60), // Pink
    Color(0xFF6D4C41), // Brown
    Color(0xFF546E7A), // Blue Grey
)

fun categoryColor(category: String?): Color {
    if (category.isNullOrBlank()) return CategoryColors.last()
    val index = category.hashCode().absoluteValue % CategoryColors.size
    return CategoryColors[index]
}
