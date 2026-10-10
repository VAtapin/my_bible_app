package com.bibledesktop.myapp.ui.bible

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
internal fun ReaderTheme(preferences: ReaderPreferences, content: @Composable () -> Unit) {
    val colors = if(preferences.night) darkColorScheme(primary=Color(0xFFA8C9E9),surface=Color(0xFF17212D),background=Color(0xFF101821),onSurface=Color(0xFFE2EAF4),onBackground=Color(0xFFE2EAF4)) else MaterialTheme.colorScheme
    CompositionLocalProvider(LocalReaderPreferences provides preferences.effective()) {
        MaterialTheme(colorScheme=colors) {Surface(Modifier.fillMaxSize(),color=colors.background,contentColor=colors.onBackground,content=content)}
    }
}
