package com.bibledesktop.myapp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
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
        typography = BibleDesktopTypography,
        content = content,
    )
}

private val defaults = Typography()
private val BibleDesktopTypography = Typography(
    displayLarge = defaults.displayLarge.copy(fontFamily = ReadingSerif),
    displayMedium = defaults.displayMedium.copy(fontFamily = ReadingSerif),
    displaySmall = defaults.displaySmall.copy(fontFamily = ReadingSerif),
    headlineLarge = defaults.headlineLarge.copy(fontFamily = ReadingSerif),
    headlineMedium = defaults.headlineMedium.copy(fontFamily = ReadingSerif),
    headlineSmall = defaults.headlineSmall.copy(fontFamily = ReadingSerif),
    titleLarge = defaults.titleLarge.copy(fontFamily = InterfaceFont),
    titleMedium = defaults.titleMedium.copy(fontFamily = InterfaceFont),
    titleSmall = defaults.titleSmall.copy(fontFamily = InterfaceFont),
    bodyLarge = defaults.bodyLarge.copy(fontFamily = InterfaceFont),
    bodyMedium = defaults.bodyMedium.copy(fontFamily = InterfaceFont),
    bodySmall = defaults.bodySmall.copy(fontFamily = InterfaceFont),
    labelLarge = defaults.labelLarge.copy(fontFamily = InterfaceFont),
    labelMedium = defaults.labelMedium.copy(fontFamily = InterfaceFont),
    labelSmall = defaults.labelSmall.copy(fontFamily = InterfaceFont),
)
