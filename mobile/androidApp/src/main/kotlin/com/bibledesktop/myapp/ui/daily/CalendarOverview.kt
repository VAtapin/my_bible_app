package com.bibledesktop.myapp.ui.daily

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bibledesktop.myapp.R
import com.bibledesktop.myapp.ui.setup.localized
import com.bibledesktop.myapp.ui.theme.*
import com.bibledesktop.shared.api.*
import kotlinx.coroutines.CancellationException
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Shared live month/day UI for the first launch and Today, without creating a profile. */
@Composable
internal fun CalendarOverview(language: String, client: BibleApiClient, modifier: Modifier = Modifier) {
    var selected by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var monthIso by rememberSaveable { mutableStateOf(selected.take(7)) }
    var days by remember { mutableStateOf<List<CalendarGridDay>?>(null) }
    var day by remember { mutableStateOf<CalendarDay?>(null) }
    var monthError by remember { mutableStateOf(false) }
    var dayError by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    val month = YearMonth.parse(monthIso)
    val locale = Locale.forLanguageTag(language)
    LaunchedEffect(monthIso, language, retry) {
        days = null
        monthError = false
        try { days = client.getCalendarMonth(month.year, month.monthValue, language) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { monthError = true }
    }
    LaunchedEffect(selected, language, retry) {
        day = null
        dayError = false
        try { day = client.getCalendarDay(selected, language) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { dayError = true }
    }
    Column(modifier.fillMaxWidth().testTag("calendar-overview"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(localized(R.string.calendar_title, language), color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        if (language == "en") Text(localized(R.string.calendar_corpus_ru, language), color = PrimaryBlue)
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        monthIso = month.minusMonths(1).toString()
                        selected = month.minusMonths(1).atDay(1).toString()
                    }, enabled = month.year > 1900 || month.monthValue > 1) {
                        Icon(Icons.Outlined.ChevronLeft, localized(R.string.calendar_previous_month, language))
                    }
                    Text(month.atDay(1).format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)),
                        Modifier.weight(1f), fontWeight = FontWeight.Bold, color = Ink)
                    IconButton(onClick = {
                        monthIso = month.plusMonths(1).toString()
                        selected = month.plusMonths(1).atDay(1).toString()
                    }, enabled = month.year < 2100 || month.monthValue < 12) {
                        Icon(Icons.Outlined.ChevronRight, localized(R.string.calendar_next_month, language))
                    }
                    TextButton(onClick = { selected = LocalDate.now().toString(); monthIso = selected.take(7) }) {
                        Text(localized(R.string.nav_today, language))
                    }
                }
                Row(Modifier.fillMaxWidth()) {
                    (1..7).forEach { weekday ->
                        Box(Modifier.weight(1f).height(30.dp), contentAlignment = Alignment.Center) {
                            Text(java.time.DayOfWeek.of(weekday).getDisplayName(java.time.format.TextStyle.SHORT, locale), fontSize = 12.sp)
                        }
                    }
                }
                val offset = month.atDay(1).dayOfWeek.value - 1
                val cells = ((offset + month.lengthOfMonth() + 6) / 7) * 7
                repeat(cells / 7) { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        repeat(7) { column ->
                            val number = row * 7 + column - offset + 1
                            val date = if (number in 1..month.lengthOfMonth()) month.atDay(number).toString() else null
                            val info = days?.firstOrNull { it.date == date }
                            val description = date?.let {
                                listOfNotNull(it, info?.foodLabel, info?.events?.firstOrNull()?.title).joinToString(". ")
                            }.orEmpty()
                            Box(Modifier.weight(1f).heightIn(min = 48.dp)
                                .padding(vertical = 2.dp)
                                .background(if (date == selected) LightBlue else Cream, RoundedCornerShape(8.dp))
                                .clickable(enabled = date != null) { selected = date!! }
                                .semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
                                if (date != null) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    val color = runCatching { Color(android.graphics.Color.parseColor(info?.dayStyle?.color)) }.getOrDefault(Ink)
                                    Text(number.toString(), color = color, fontWeight = if ((info?.dayStyle?.fontWeight ?: 400) >= 600) FontWeight.Bold else FontWeight.Normal)
                                    info?.oldStyleDate?.takeLast(2)?.toIntOrNull()?.let { Text(it.toString(), color = PrimaryBlue, fontSize = 10.sp) }
                                }
                            }
                        }
                    }
                }
                if (days == null && !monthError) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (monthError) {
                    Text(localized(R.string.calendar_error, language), color = Ink)
                    TextButton(onClick = { retry++ }) { Text(localized(R.string.retry, language)) }
                }
            }
        }
        Text(LocalDate.parse(selected).format(DateTimeFormatter.ofPattern("EEEE, d MMMM", locale)), color = Ink, fontWeight = FontWeight.Bold)
        day?.let { value ->
            Text(localized(R.string.calendar_old_style, language, value.oldStyleDate), color = PrimaryBlue, fontSize = 13.sp)
            value.food?.let { Text(it.label, color = Navy, modifier = Modifier.fillMaxWidth().background(LightBlue, RoundedCornerShape(12.dp)).padding(12.dp)) }
            value.events.take(3).forEach { Text(it.name, color = Ink, fontSize = 16.sp) }
        }
        if (day == null && !dayError) CircularProgressIndicator(Modifier.size(24.dp))
        if (dayError) {
            Text(localized(R.string.calendar_error, language), color = Ink)
            TextButton(onClick = { retry++ }) { Text(localized(R.string.retry, language)) }
        }
    }
}
