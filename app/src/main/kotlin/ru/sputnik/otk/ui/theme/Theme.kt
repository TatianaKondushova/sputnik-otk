package ru.sputnik.otk.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = SputnikBlue,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = SputnikBlueLight,
    secondary = SputnikBlueDark,
)

private val DarkColors = darkColorScheme(
    primary = SputnikBlueLight,
    onPrimary = androidx.compose.ui.graphics.Color.Black,
    primaryContainer = SputnikBlueDark,
    secondary = SputnikBlue,
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
