package com.ainewsbroker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.ainewsbroker.data.DefaultQueries

/** Accent color per category so cards and chips are scannable at a glance. */
@Composable
fun categoryAccent(categoryId: String): Color {
    val dark = MaterialTheme.colorScheme.background.luminanceApprox() < 0.5f
    return when (categoryId) {
        DefaultQueries.CLAUDE -> if (dark) Color(0xFFFFB68F) else Color(0xFFB4501A)
        DefaultQueries.COPILOT -> if (dark) Color(0xFFB9C3FF) else Color(0xFF3949AB)
        DefaultQueries.PARADIGMS -> if (dark) Color(0xFF7DD8C8) else Color(0xFF00796B)
        else -> MaterialTheme.colorScheme.primary
    }
}

private val avatarPalette = listOf(
    Color(0xFF5C6BC0), Color(0xFF26A69A), Color(0xFFEF6C00), Color(0xFF8E24AA),
    Color(0xFF00897B), Color(0xFFD81B60), Color(0xFF3949AB), Color(0xFF6D4C41),
    Color(0xFF1E88E5), Color(0xFF43A047),
)

fun sourceColor(source: String): Color = avatarPalette[(source.hashCode() and 0x7fffffff) % avatarPalette.size]

private fun Color.luminanceApprox(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue
