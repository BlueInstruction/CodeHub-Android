package codehub.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF4FC3F7),
    onPrimary = Color(0xFF002029),
    primaryContainer = Color(0xFF003644),
    onPrimaryContainer = Color(0xFFBFE9FF),
    secondary = Color(0xFF80CBC4),
    onSecondary = Color(0xFF00322E),
    background = Color(0xFF0B0E14),
    onBackground = Color(0xFFE6EDF3),
    surface = Color(0xFF11151E),
    onSurface = Color(0xFFE6EDF3),
    surfaceVariant = Color(0xFF1A1F2B),
    onSurfaceVariant = Color(0xFFB8C0CC),
    error = Color(0xFFFF5370),
    onError = Color(0xFF590008),
    outline = Color(0xFF444C5A)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF02617A),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF4A6360),
    background = Color(0xFFFAFCFE),
    onBackground = Color(0xFF191C1A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF191C1A),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    outline = Color(0xFF6F7975)
)

@Composable
fun CodeHubTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (useDarkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = CodeHubTypography,
        shapes = CodeHubShapes,
        content = content
    )
}
