package io.github.romulofer.foxhg.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** foxhg palette: warm neutrals with a fox-orange accent, light and dark. */
private val LightColors = lightColorScheme(
    primary = Color(0xFFC2410C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFE4D6),
    onPrimaryContainer = Color(0xFF5A1C03),
    secondary = Color(0xFF6B5B52),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFF0F766E),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFFAF7F4),
    onBackground = Color(0xFF2A2420),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF2A2420),
    surfaceVariant = Color(0xFFF1EBE6),
    onSurfaceVariant = Color(0xFF6E625B),
    outline = Color(0xFFD6CBC3),
    outlineVariant = Color(0xFFE7DED8),
    error = Color(0xFFB42318),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFEE4E2),
    onErrorContainer = Color(0xFF55160C),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF9461),
    onPrimary = Color(0xFF3A1100),
    primaryContainer = Color(0xFF4A2412),
    onPrimaryContainer = Color(0xFFFFDCCB),
    secondary = Color(0xFFCDBBB1),
    onSecondary = Color(0xFF30251F),
    tertiary = Color(0xFF5EEAD4),
    onTertiary = Color(0xFF00201C),
    background = Color(0xFF191513),
    onBackground = Color(0xFFEDE6E1),
    surface = Color(0xFF231E1B),
    onSurface = Color(0xFFEDE6E1),
    surfaceVariant = Color(0xFF2E2825),
    onSurfaceVariant = Color(0xFFB5A9A2),
    outline = Color(0xFF51463F),
    outlineVariant = Color(0xFF3A322E),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF3B0A06),
    errorContainer = Color(0xFF5A1C14),
    onErrorContainer = Color(0xFFFFDAD6),
)

/** Dense, desktop-sized type scale; data editors show a lot of text at once. */
private val FoxTypography = Typography(
    headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
)

private val FoxShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
)

object Fox {
    /** Monospace for ids and numbers, so columns line up. */
    val mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
    val fieldHeight = 34.dp
}

/** The system theme, unless the FOXHG_THEME environment variable says `dark` or `light`. */
@Composable
private fun defaultDark(): Boolean = when (System.getenv("FOXHG_THEME")?.lowercase()) {
    "dark" -> true
    "light" -> false
    else -> isSystemInDarkTheme()
}

@Composable
fun FoxTheme(dark: Boolean = defaultDark(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = FoxTypography,
        shapes = FoxShapes,
        content = content,
    )
}
