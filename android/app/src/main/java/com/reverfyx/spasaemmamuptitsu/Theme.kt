package com.reverfyx.spasaemmamuptitsu

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Bg = Color(0xFF0A0A0C)
val Surface = Color(0xFF111216)
val Surface2 = Color(0xFF17181D)
val Line = Color(0xFF26272E)
val TextPrimary = Color(0xFFF4F4F5)
val TextMuted = Color(0xFF9899A2)
val Accent = Color(0xFFFF4567)
val AccentSoft = Color(0xFF7C5CFC)
val Good = Color(0xFF35D487)
val Warn = Color(0xFFFFB648)

private val Scheme = darkColorScheme(
    primary = Accent,
    secondary = AccentSoft,
    background = Bg,
    surface = Surface,
    surfaceVariant = Surface2,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    outline = Line
)

@Composable
fun SpasaemTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
