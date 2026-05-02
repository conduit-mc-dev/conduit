package dev.conduit.desktop.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val ConduitDarkColorScheme = darkColorScheme(
    background = Background,
    surface = Surface,
    surfaceContainerLow = Surface,
    surfaceContainer = SurfaceContainer,
    surfaceContainerHigh = SurfaceContainerHigh,
    primary = Primary,
    onPrimary = OnPrimary,
    secondary = Secondary,
    tertiary = Tertiary,
    error = Error,
    onSurface = OnSurface,
    onSurfaceVariant = OnSurfaceVariant,
    onBackground = OnSurface,
)

@Composable
fun ConduitTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ConduitDarkColorScheme,
        shapes = ConduitShapes,
        typography = ConduitTypography,
        content = content,
    )
}
