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

/**
 * Tide palette: a light-cyan accent over **neutral** dark-gray surfaces.
 *
 * Round-7 direction: the ember/orange era is over — the whole app reads
 * light blue / light cyan now, and surfaces are pure grays with no warm
 * (orangish) tint. The dark primary (#7BD5ED) is bright enough to feel alive
 * without searing; a muted slate secondary keeps secondary actions quiet and
 * a soft lavender tertiary gives small accents separation. The light scheme
 * is a deep cyan (#00687D) that passes 4.5:1 on the cool-white background.
 */

private val LightColors = lightColorScheme(
    primary = Color(0xFF00687D),            // deep cyan
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB3EBFF),
    onPrimaryContainer = Color(0xFF001F2A),
    secondary = Color(0xFF4C626D),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCFE6F2),
    onSecondaryContainer = Color(0xFF061E27),
    tertiary = Color(0xFF65558F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE7DEFF),
    onTertiaryContainer = Color(0xFF251452),
    background = Color(0xFFF7F9FA),         // cool near-white
    onBackground = Color(0xFF191C1E),
    surface = Color(0xFFF7F9FA),
    onSurface = Color(0xFF191C1E),
    surfaceVariant = Color(0xFFDDE3E7),
    onSurfaceVariant = Color(0xFF414950),
    outline = Color(0xFF71787F),
    outlineVariant = Color(0xFFC1C7CD),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7BD5ED),            // light cyan
    onPrimary = Color(0xFF003441),
    primaryContainer = Color(0xFF004B5C),   // deep teal-blue
    onPrimaryContainer = Color(0xFFB5EBFF),
    secondary = Color(0xFFB4C9D4),          // muted slate
    onSecondary = Color(0xFF1F333D),
    secondaryContainer = Color(0xFF364A54),
    onSecondaryContainer = Color(0xFFD0E6F2),
    tertiary = Color(0xFFCBBDF4),           // soft lavender accent
    onTertiary = Color(0xFF332759),
    tertiaryContainer = Color(0xFF4A3D71),
    onTertiaryContainer = Color(0xFFE7DEFF),
    background = Color(0xFF141414),         // pure dark gray
    onBackground = Color(0xFFE4E6E7),
    surface = Color(0xFF1C1C1C),            // pure dark gray
    onSurface = Color(0xFFE4E6E7),
    surfaceVariant = Color(0xFF2A2B2D),
    onSurfaceVariant = Color(0xFFC3C6C9),
    outline = Color(0xFF8D9195),
    outlineVariant = Color(0xFF444648),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val SnapTtlTypography = Typography(
    headlineLarge = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium)
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
