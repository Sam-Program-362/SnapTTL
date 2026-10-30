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
 * Ember palette: warm charcoal surfaces with a soft ember-orange accent.
 *
 * The accent deliberately avoids the old neon #FF4500 — on today's mostly-dark
 * phones a tone-80 ember (#FFB86B) reads as clearly "orange" without searing
 * the eye, while deep amber-brown containers keep filled elements rich rather
 * than washed out. A muted teal tertiary gives secondary actions separation.
 */

private val LightColors = lightColorScheme(
    primary = Color(0xFFA13E00),            // burnt orange
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDBC8),
    onPrimaryContainer = Color(0xFF351000),
    secondary = Color(0xFF77593F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDBC8),
    onSecondaryContainer = Color(0xFF2C1704),
    tertiary = Color(0xFF4E6356),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD0E9D8),
    onTertiaryContainer = Color(0xFF0B1F15),
    background = Color(0xFFFFF8F4),
    onBackground = Color(0xFF221B15),
    surface = Color(0xFFFFF8F4),
    onSurface = Color(0xFF221B15),
    surfaceVariant = Color(0xFFF3DED0),
    onSurfaceVariant = Color(0xFF52443A),
    outline = Color(0xFF857467),
    outlineVariant = Color(0xFFD8C3B5),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB86B),            // soft ember
    onPrimary = Color(0xFF4A2100),
    primaryContainer = Color(0xFF6A3B0D),   // deep amber-brown
    onPrimaryContainer = Color(0xFFFFDCC0),
    secondary = Color(0xFFD7BFA2),
    onSecondary = Color(0xFF3A2E1E),
    secondaryContainer = Color(0xFF52432C),
    onSecondaryContainer = Color(0xFFF4E0C7),
    tertiary = Color(0xFF9CD3C6),           // muted teal accent
    onTertiary = Color(0xFF08372F),
    tertiaryContainer = Color(0xFF235047),
    onTertiaryContainer = Color(0xFFB8EFE3),
    background = Color(0xFF141210),         // warm near-black
    onBackground = Color(0xFFE7E1DA),
    surface = Color(0xFF1C1917),
    onSurface = Color(0xFFE7E1DA),
    surfaceVariant = Color(0xFF4A4541),
    onSurfaceVariant = Color(0xFFCDC5BD),
    outline = Color(0xFF968F87),
    outlineVariant = Color(0xFF4A4541),
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
