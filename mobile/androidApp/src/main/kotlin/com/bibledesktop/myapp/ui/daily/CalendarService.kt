package com.bibledesktop.myapp.ui.daily

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bibledesktop.myapp.R
import com.bibledesktop.myapp.ui.setup.localized
import com.bibledesktop.myapp.ui.theme.readingFont
import com.bibledesktop.shared.api.BibleContentSource
import com.bibledesktop.shared.api.AutomaticCalendarServicePlan
import com.bibledesktop.shared.api.AutomaticCalendarText
import com.bibledesktop.shared.api.validateAutomaticCalendar
import kotlinx.coroutines.CancellationException

@Composable
internal fun CalendarService(date: String, language: String, client: BibleContentSource) {
    var opened by rememberSaveable { mutableStateOf(false) }
    var plan by remember { mutableStateOf<AutomaticCalendarServicePlan?>(null) }
    var error by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    var fontSize by rememberSaveable { mutableFloatStateOf(22f) }
    val labels=calendarTextPriorityLabels(language)
    LaunchedEffect(opened, date, language, retry) {
        plan = null; error = false
        if (opened) {
            try { plan = client.getAutomaticCalendarService(date, language).validateAutomaticCalendar(date,language) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = true }
        }
    }
    TextButton(onClick = { opened = !opened },modifier=Modifier.testTag("calendar-service-open")) { Text(localized(R.string.calendar_service, language)) }
    if (opened) {
        Text(labels.automatic)
        if (error) {
            Text(localized(R.string.calendar_service_error, language),Modifier.testTag("calendar-service-error"))
            TextButton(onClick = { retry++ }) { Text(localized(R.string.retry, language)) }
        } else if (plan == null) CircularProgressIndicator(Modifier.size(24.dp))
        plan?.let { value ->
            Row {
                TextButton(onClick = { fontSize = (fontSize - 2).coerceAtLeast(16f) }) { Text(localized(R.string.bible_font_smaller, language)) }
                TextButton(onClick = { fontSize = (fontSize + 2).coerceAtMost(40f) }) { Text(localized(R.string.bible_font_larger, language)) }
            }
            value.assignments.forEachIndexed { index, text ->
                key(date, language, "assignment", index) {
                    ServiceText(text,language,fontSize,"assignment-${text.textId?:index}")
                }
            }
            value.expansions.forEach { text ->
                key(date, language, "expansion", text.id) {
                    ServiceText(text,language,fontSize,"expansion-${text.id}")
                }
            }
            if (value.assignments.isEmpty() && value.expansions.isEmpty()) Text(localized(R.string.calendar_empty, language))
        }
    }
}

@Composable
private fun ServiceText(text:AutomaticCalendarText,language:String,fontSize:Float,identity:String) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("calendar-service-$identity")) {
        TextButton(onClick = { expanded = !expanded }) { Text(text.title, style = MaterialTheme.typography.titleMedium) }
        if(text.selection=="missing")Text(calendarTextPriorityLabels(language).missing,Modifier.padding(horizontal=16.dp).testTag("calendar-service-missing-$identity"))
        if (expanded) Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            text.rubric?.let { Text(it) }
            if(text.selection!="missing"){
                Text(automaticCalendarEditionLabel(text,language),style=MaterialTheme.typography.labelSmall)
                Text(text.text,Modifier.testTag("calendar-service-body-$identity"),fontFamily = readingFont(text.language.orEmpty()), fontSize = fontSize.sp, lineHeight = (fontSize * 1.6f).sp)
            }
        }
    }
}
