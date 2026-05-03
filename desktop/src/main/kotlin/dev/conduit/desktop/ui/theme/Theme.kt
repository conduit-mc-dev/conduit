package dev.conduit.desktop.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private val ConduitDarkColorScheme = darkColorScheme(
    primary = AccentBlue,
    onPrimary = TextPrimary,
    secondary = AccentPurple,
    onSecondary = TextPrimary,
    tertiary = StateRunning,
    error = StateCrashed,
    background = Background,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    surfaceContainer = Elevated,
    surfaceContainerHigh = Elevated,
    outline = Border,
)

@Composable
fun ConduitTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalConduitColors provides ConduitCustomColors()) {
        MaterialTheme(
            colorScheme = ConduitDarkColorScheme,
            shapes = ConduitShapes,
            typography = ConduitTypography,
            content = content,
        )
    }
}
