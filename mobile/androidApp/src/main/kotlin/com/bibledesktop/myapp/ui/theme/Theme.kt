package com.bibledesktop.myapp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Navy = Color(0xFF2F5B7C)
val PrimaryBlue = Color(0xFF5B7EA6)
val LightBlue = Color(0xFFE5ECF4)
val Cream = Color(0xFFFAF8F3)
val WarmBorder = Color(0xFFE6E0D5)
val Gold = Color(0xFFD4AF6B)
val Ink = Color(0xFF17324A)

private val BibleDesktopColors = lightColorScheme(
    primary = Navy,
    onPrimary = Color.White,
    secondary = PrimaryBlue,
    tertiary = Gold,
    background = Cream,
    surface = Color.White,
    onBackground = Ink,
    onSurface = Ink,
    outline = WarmBorder,
)

@Composable
fun BibleDesktopTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BibleDesktopColors,
        content = content,
    )
}
