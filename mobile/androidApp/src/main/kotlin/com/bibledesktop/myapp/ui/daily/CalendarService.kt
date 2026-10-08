package com.bibledesktop.myapp.ui.daily

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bibledesktop.myapp.R
import com.bibledesktop.myapp.ui.setup.localized
import com.bibledesktop.myapp.ui.theme.readingFont
import com.bibledesktop.shared.api.BibleContentSource
import com.bibledesktop.shared.api.CalendarServicePlan
import kotlinx.coroutines.CancellationException

@Composable
internal fun CalendarService(date: String, language: String, client: BibleContentSource) {
    var opened by rememberSaveable { mutableStateOf(false) }
    var plan by remember { mutableStateOf<CalendarServicePlan?>(null) }
    var error by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    var fontSize by rememberSaveable { mutableFloatStateOf(22f) }
    var textLanguage by rememberSaveable { mutableStateOf("cu-civil") }
    LaunchedEffect(opened, date, textLanguage, retry) {
        plan = null; error = false
        if (opened) {
            try { plan = client.getCalendarService(date, textLanguage) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = true }
        }
    }
    TextButton(onClick = { opened = !opened }) { Text(localized(R.string.calendar_service, language)) }
    if (opened) {
        Text(localized(R.string.prayer_text_language, language))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("cu-civil", "cu", "ru", "uk", "de").forEach { code ->
                val title = when (code) {
                    "cu-civil" -> localized(R.string.prayer_cu_civil, language)
                    "cu" -> localized(R.string.prayer_cu, language)
                    "ru" -> "Русский"
                    "uk" -> "Українська"
                    else -> "Deutsch"
                }
                FilterChip(selected = textLanguage == code, onClick = { textLanguage = code }, label = { Text(title) })
            }
        }
        if (error) {
            Text(localized(R.string.calendar_service_error, language))
            TextButton(onClick = { retry++ }) { Text(localized(R.string.retry, language)) }
        } else if (plan == null) CircularProgressIndicator(Modifier.size(24.dp))
        plan?.let { value ->
            Text(localized(R.string.calendar_service_office, language))
            value.properCoverage?.message?.let { Text(it) }
            Row {
                TextButton(onClick = { fontSize = (fontSize - 2).coerceAtLeast(16f) }) { Text(localized(R.string.bible_font_smaller, language)) }
                TextButton(onClick = { fontSize = (fontSize + 2).coerceAtMost(40f) }) { Text(localized(R.string.bible_font_larger, language)) }
            }
            value.assignments.forEachIndexed { index, text ->
                key(date, language, "assignment", index) {
                    ServiceText(text.title, text.text, text.rubric, value.textLanguage, fontSize)
                }
            }
            value.expansions.forEach { text ->
                key(date, language, "expansion", text.id) {
                    ServiceText(text.title, text.text, null, value.textLanguage, fontSize)
                }
            }
            if (value.assignments.isEmpty() && value.expansions.isEmpty()) Text(localized(R.string.calendar_empty, language))
        }
    }
}

@Composable
private fun ServiceText(title: String, body: String, rubric: String?, textLanguage: String, fontSize: Float) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        TextButton(onClick = { expanded = !expanded }) { Text(title, style = MaterialTheme.typography.titleMedium) }
        if (expanded) Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            rubric?.let { Text(it) }
            Text(body, fontFamily = readingFont(textLanguage), fontSize = fontSize.sp, lineHeight = (fontSize * 1.6f).sp)
        }
    }
}
