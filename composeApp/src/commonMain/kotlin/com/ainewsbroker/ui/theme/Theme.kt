package com.ainewsbroker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.ainewsbroker.model.ThemeMode
import com.ainewsbroker.resources.Res
import com.ainewsbroker.resources.inter_bold
import com.ainewsbroker.resources.inter_medium
import com.ainewsbroker.resources.inter_regular
import com.ainewsbroker.resources.inter_semibold
import org.jetbrains.compose.resources.Font

private val LightColors = lightColorScheme(
    primary = Color(0xFF3F51B5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDEE0FF),
    onPrimaryContainer = Color(0xFF00105C),
    secondary = Color(0xFF5B5D72),
    secondaryContainer = Color(0xFFE0E1F9),
    onSecondaryContainer = Color(0xFF181A2C),
    tertiary = Color(0xFF00897B),
    tertiaryContainer = Color(0xFFB2F1E8),
    onTertiaryContainer = Color(0xFF00201C),
    background = Color(0xFFFBF8FF),
    onBackground = Color(0xFF1B1B21),
    surface = Color(0xFFFBF8FF),
    onSurface = Color(0xFF1B1B21),
    surfaceVariant = Color(0xFFE3E1EC),
    onSurfaceVariant = Color(0xFF46464F),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF5F2FA),
    surfaceContainer = Color(0xFFEFEDF4),
    surfaceContainerHigh = Color(0xFFE9E7EF),
    surfaceContainerHighest = Color(0xFFE4E1E9),
    outline = Color(0xFF767680),
    outlineVariant = Color(0xFFC7C5D0),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFBAC3FF),
    onPrimary = Color(0xFF08218A),
    primaryContainer = Color(0xFF293CA0),
    onPrimaryContainer = Color(0xFFDEE0FF),
    secondary = Color(0xFFC3C5DD),
    secondaryContainer = Color(0xFF434659),
    onSecondaryContainer = Color(0xFFE0E1F9),
    tertiary = Color(0xFF84D5C9),
    tertiaryContainer = Color(0xFF005048),
    onTertiaryContainer = Color(0xFFB2F1E8),
    background = Color(0xFF121318),
    onBackground = Color(0xFFE4E1E9),
    surface = Color(0xFF121318),
    onSurface = Color(0xFFE4E1E9),
    surfaceVariant = Color(0xFF46464F),
    onSurfaceVariant = Color(0xFFC7C5D0),
    surfaceContainerLowest = Color(0xFF0D0E13),
    surfaceContainerLow = Color(0xFF1B1B21),
    surfaceContainer = Color(0xFF1F1F25),
    surfaceContainerHigh = Color(0xFF29292F),
    surfaceContainerHighest = Color(0xFF34343A),
    outline = Color(0xFF90909A),
    outlineVariant = Color(0xFF46464F),
)

@Composable
private fun interFamily() = FontFamily(
    Font(Res.font.inter_regular, FontWeight.Normal),
    Font(Res.font.inter_medium, FontWeight.Medium),
    Font(Res.font.inter_semibold, FontWeight.SemiBold),
    Font(Res.font.inter_bold, FontWeight.Bold),
)

@Composable
private fun appTypography(): Typography {
    val inter = interFamily()
    val base = Typography()
    fun TextStyle.inter(weight: FontWeight, size: Int, line: Int, spacing: Double = 0.0) =
        copy(fontFamily = inter, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp, letterSpacing = spacing.em)
    return Typography(
        displaySmall = base.displaySmall.inter(FontWeight.Bold, 34, 40, -0.02),
        headlineLarge = base.headlineLarge.inter(FontWeight.Bold, 30, 36, -0.02),
        headlineMedium = base.headlineMedium.inter(FontWeight.Bold, 26, 32, -0.015),
        headlineSmall = base.headlineSmall.inter(FontWeight.SemiBold, 22, 28, -0.01),
        titleLarge = base.titleLarge.inter(FontWeight.SemiBold, 20, 26, -0.01),
        titleMedium = base.titleMedium.inter(FontWeight.SemiBold, 17, 24, -0.005),
        titleSmall = base.titleSmall.inter(FontWeight.SemiBold, 15, 20),
        bodyLarge = base.bodyLarge.inter(FontWeight.Normal, 16, 24),
        bodyMedium = base.bodyMedium.inter(FontWeight.Normal, 15, 22),
        bodySmall = base.bodySmall.inter(FontWeight.Normal, 13, 18),
        labelLarge = base.labelLarge.inter(FontWeight.SemiBold, 14, 20),
        labelMedium = base.labelMedium.inter(FontWeight.Medium, 13, 16, 0.005),
        labelSmall = base.labelSmall.inter(FontWeight.Medium, 12, 16, 0.01),
    )
}

/** Returns a wallpaper-based scheme where the platform supports it (Android 12+), else null. */
@Composable
expect fun platformDynamicColorScheme(dark: Boolean): ColorScheme?

@Composable
fun AppTheme(themeMode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val colors = platformDynamicColorScheme(dark) ?: if (dark) DarkColors else LightColors
    PlatformSystemBars(dark)
    MaterialTheme(colorScheme = colors, typography = appTypography(), content = content)
}

/** Keeps status/navigation bar icon contrast in sync with the app theme. */
@Composable
expect fun PlatformSystemBars(dark: Boolean)
