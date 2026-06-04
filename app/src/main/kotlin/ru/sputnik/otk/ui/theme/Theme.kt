package ru.sputnik.otk.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = SputnikBlue,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = SputnikBluePale,
    onPrimaryContainer = SputnikBlue,
    secondary = SputnikOrange,
    onSecondary = androidx.compose.ui.graphics.Color.White,
    secondaryContainer = SputnikOrangePale,
    onSecondaryContainer = SputnikOrange,
    error = ErrorRed,
    onError = androidx.compose.ui.graphics.Color.White,
    errorContainer = ErrorRedPale,
    onErrorContainer = ErrorRed,
    background = FactoryBg,
    onBackground = androidx.compose.ui.graphics.Color(0xFF1A1A1A),
    surface = CardBg,
    onSurface = androidx.compose.ui.graphics.Color(0xFF1A1A1A),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFFEEEEEE),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF666666),
)

private val DarkColors = darkColorScheme(
    primary = SputnikBlueLight,
    onPrimary = androidx.compose.ui.graphics.Color.Black,
    primaryContainer = SputnikBlue,
    onPrimaryContainer = SputnikBluePale,
    secondary = SputnikOrange,
    onSecondary = androidx.compose.ui.graphics.Color.Black,
    error = ErrorRed,
    onError = androidx.compose.ui.graphics.Color.White,
)

@Composable
fun SputnikOtkTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        content = content,
    )
}
