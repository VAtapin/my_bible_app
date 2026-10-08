package com.bibledesktop.myapp.ui.reading

import android.text.Html
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bibledesktop.myapp.R
import com.bibledesktop.myapp.ui.setup.localized
import com.bibledesktop.myapp.ui.theme.*
import com.bibledesktop.shared.api.PrayerDetail

/** Parse both intro and body before testing for emptiness. Keep the edition's characters. */
internal fun readingText(html: String): String =
    Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString().replace('\u00a0', ' ').trim()

@Composable
internal fun ReadingHeader(title: String, language: String, onBack: () -> Unit, onHome: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, localized(R.string.action_back, language), tint = Navy)
        }
        Text(title, Modifier.weight(1f).padding(horizontal = 4.dp), color = Ink,
            fontFamily = ReadingSerif, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        IconButton(onClick = onHome) {
            Icon(Icons.Outlined.Home, localized(R.string.reader_home, language), tint = Navy)
        }
    }
}

/** Phone uses the available width; wide windows keep a comfortable reading measure. */
@Composable
internal fun ReadingViewport(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = 840.dp).fillMaxWidth().testTag("reading-measure")) { content() }
    }
}

@Composable
internal fun PrayerReadingContent(prayer: PrayerDetail, fontSize: Float) {
    val intro = remember(prayer.intro) { prayer.intro?.let(::readingText)?.takeIf(String::isNotBlank) }
    val body = remember(prayer.body) { readingText(prayer.body) }
    SelectionContainer {
        Column(Modifier.padding(horizontal = 22.dp, vertical = 18.dp)) {
            intro?.let {
                Text(it, color = PrimaryBlue, fontFamily = ReadingSerif, fontSize = 16.sp, lineHeight = 24.sp)
                Spacer(Modifier.height(16.dp))
            }
            Text(body, Modifier.testTag("prayer-body"), color = Ink, fontFamily = readingFont(prayer.languageCode),
                fontSize = fontSize.sp, lineHeight = (fontSize * 1.5f).sp)
        }
    }
}
