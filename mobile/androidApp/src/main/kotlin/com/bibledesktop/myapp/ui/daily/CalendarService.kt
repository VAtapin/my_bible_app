package com.bibledesktop.myapp.ui.daily

import androidx.compose.foundation.layout.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.TextStyle
import com.bibledesktop.myapp.ui.study.LargeStudyBody
import com.bibledesktop.myapp.ui.study.largeStudyBodyThreshold
import com.bibledesktop.myapp.ui.reading.ReadingViewport
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
                    ServiceText(text,language,fontSize,"assignment-${text.textId?:index}"){fontSize=it}
                }
            }
            value.expansions.forEach { text ->
                key(date, language, "expansion", text.id) {
                    ServiceText(text,language,fontSize,"expansion-${text.id}"){fontSize=it}
                }
            }
            if (value.assignments.isEmpty() && value.expansions.isEmpty()) Text(localized(R.string.calendar_empty, language))
        }
    }
}

@Composable
private fun ServiceText(text:AutomaticCalendarText,language:String,fontSize:Float,identity:String,onFontSize:(Float)->Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("calendar-service-$identity")) {
        TextButton(onClick = { expanded = !expanded }) { Text(text.title, style = MaterialTheme.typography.titleMedium) }
        if(text.selection=="missing")Text(calendarTextPriorityLabels(language).missing,Modifier.padding(horizontal=16.dp).testTag("calendar-service-missing-$identity"))
    }
    if(expanded)Dialog(onDismissRequest={expanded=false},properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)){
        BackHandler{expanded=false}
        Surface(Modifier.fillMaxSize()){
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().testTag("calendar-text-dialog")){
                Row(Modifier.fillMaxWidth().padding(horizontal=10.dp)){
                    Text(text.title,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                    TextButton(onClick={expanded=false},modifier=Modifier.testTag("calendar-text-close")){Text(localized(R.string.study_close,language))}
                }
                text.rubric?.let{Text(it,Modifier.padding(horizontal=10.dp))}
                if(text.selection=="missing")Text(calendarTextPriorityLabels(language).missing,Modifier.padding(10.dp))
                else {
                    Text(automaticCalendarEditionLabel(text,language),Modifier.padding(horizontal=10.dp),style=MaterialTheme.typography.labelSmall)
                    ReadingViewport(Modifier.weight(1f)){
                        val style=TextStyle(fontFamily=readingFont(text.language.orEmpty()),fontSize=fontSize.sp,lineHeight=(fontSize*1.6f).sp)
                        if(text.text.length>=largeStudyBodyThreshold)LargeStudyBody(text.text,Modifier.fillMaxSize().padding(horizontal=10.dp,vertical=8.dp).testTag("calendar-service-body-$identity"),style=style,plainText=true)
                        else SelectionContainer{Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=10.dp,vertical=8.dp)){
                            Text(text.text,Modifier.testTag("calendar-service-body-$identity"),style=style)
                        }}
                    }
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly){
                    TextButton(onClick={onFontSize((fontSize-2).coerceAtLeast(16f))},enabled=fontSize>16f){Text(localized(R.string.bible_font_smaller,language))}
                    TextButton(onClick={onFontSize((fontSize+2).coerceAtMost(40f))},enabled=fontSize<40f){Text(localized(R.string.bible_font_larger,language))}
                }
            }
        }
    }
}
