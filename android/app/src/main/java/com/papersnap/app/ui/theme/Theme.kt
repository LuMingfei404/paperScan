package com.papersnap.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Teal = Color(0xFF0D9488)
val TealBright = Color(0xFF14B8A6)
val Navy = Color(0xFF0F172A)
val Slate200 = Color(0xFFE2E8F0)
val Slate400 = Color(0xFF94A3B8)
val LightBg = Color(0xFFF1F5F9)

private val DarkColors = darkColorScheme(
    primary = TealBright,
    onPrimary = Color.White,
    secondary = Teal,
    background = Navy,
    onBackground = Slate200,
    surface = Color(0xFF1E293B),
    onSurface = Slate200,
    surfaceVariant = Color(0xFF1E293B),
    onSurfaceVariant = Slate400,
    outline = Color(0xFF334155)
)

private val LightColors = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    secondary = TealBright,
    background = LightBg,
    onBackground = Color(0xFF0F172A),
    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = LightBg,
    onSurfaceVariant = Color(0xFF64748B),
    outline = Color(0xFFE2E8F0)
)

@Composable
fun PaperSnapTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
