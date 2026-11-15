package de.uekala.schichtwerk.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Bg = Color(0xFF0E0F12)
val Surface = Color(0xFF17191E)
val SurfaceHigh = Color(0xFF20232A)
val Accent = Color(0xFFFF7A1A)
val TextMain = Color(0xFFF2F3F5)
val TextDim = Color(0xFF9AA0AA)
val Track = Color(0xFF2A2D34)
val Good = Color(0xFF3DDC97)
val Bad = Color(0xFFFF5A5F)
val Warn = Color(0xFFFFC145)

private val Scheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF1A0E03),
    secondary = Accent,
    onSecondary = Color(0xFF1A0E03),
    background = Bg,
    onBackground = TextMain,
    surface = Surface,
    onSurface = TextMain,
    surfaceVariant = SurfaceHigh,
    onSurfaceVariant = TextDim,
    surfaceContainer = Surface,
    surfaceContainerHigh = SurfaceHigh,
    surfaceContainerHighest = SurfaceHigh,
    outline = Track,
    outlineVariant = Track,
    error = Bad,
    onError = Color(0xFF1A0303),
    secondaryContainer = Color(0xFF3A2410),
    onSecondaryContainer = Accent,
)

@Composable
fun SchichtwerkTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
