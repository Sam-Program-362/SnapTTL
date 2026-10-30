package dev.snapttl.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val SnapTtlPrimary = Color(0xFFFF4500)
private val SnapTtlOnPrimary = Color(0xFFFFFFFF)
private val SnapTtlSecondary = Color(0xFF2B6F8C)
private val SnapTtlBackgroundLight = Color(0xFFDAE0E6)
private val SnapTtlSurfaceLight = Color(0xFFFFFFFF)
private val SnapTtlBackgroundDark = Color(0xFF0E1113)
private val SnapTtlSurfaceDark = Color(0xFF181C1F)

private val LightColors = lightColorScheme(
    primary = SnapTtlPrimary,
    onPrimary = SnapTtlOnPrimary,
    secondary = SnapTtlSecondary,
    background = SnapTtlBackgroundLight,
    surface = SnapTtlSurfaceLight,
    surfaceVariant = Color(0xFFE9EEF0)
)

private val DarkColors = darkColorScheme(
    primary = SnapTtlPrimary,
    onPrimary = SnapTtlOnPrimary,
    secondary = SnapTtlSecondary,
    background = SnapTtlBackgroundDark,
    surface = SnapTtlSurfaceDark,
    surfaceVariant = Color(0xFF24292D)
)

private val SnapTtlTypography = Typography(
    headlineLarge = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp)
)

@Composable
fun SnapTtlTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = SnapTtlTypography,
        content = content
    )
}
