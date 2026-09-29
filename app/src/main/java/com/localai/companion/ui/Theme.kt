package com.localai.companion.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val DarkGraphite = Color(0xFF121212)
val SurfaceGraphite = Color(0xFF1E1E1E)
val CyanAccent = Color(0xFF00E5FF)
val PurpleAccent = Color(0xFFB388FF)
val ErrorRed = Color(0xFFFF5252)
val AmberAccent = Color(0xFFFFB300)

private val DarkColorScheme = darkColorScheme(
    primary = CyanAccent,
    secondary = PurpleAccent,
    tertiary = AmberAccent,
    background = DarkGraphite,
    surface = SurfaceGraphite,
    error = ErrorRed,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White
)

@Composable
fun CompanionTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
