package com.bibledesktop.myapp.ui.daily

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bibledesktop.myapp.R
import com.bibledesktop.myapp.ui.setup.localized
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun CalendarMonthPicker(language: String, current: YearMonth, onSelect: (YearMonth) -> Unit, onClose: () -> Unit) {
    var yearText by rememberSaveable { mutableStateOf(current.year.toString()) }
    val year = yearText.toIntOrNull()?.takeIf { it in 1900..2100 }
    AlertDialog(onDismissRequest = onClose,
        title = { Text(localized(R.string.calendar_choose_month, language)) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(yearText, { value -> if (value.length <= 4 && value.all(Char::isDigit)) yearText = value },
                    Modifier.fillMaxWidth().testTag("calendar-month-year"), singleLine = true,
                    label = { Text(localized(R.string.calendar_year, language)) },
                    supportingText = { Text(localized(R.string.calendar_year_range, language)) }, isError = year == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                repeat(4) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeat(3) { column ->
                            val month = row * 3 + column + 1
                            OutlinedButton(enabled = year != null, onClick = { year?.let { onSelect(YearMonth.of(it, month)) } },
                                modifier = Modifier.weight(1f).testTag("calendar-month-$month"), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 12.dp)) {
                                Text(YearMonth.of(current.year, month).atDay(1).format(DateTimeFormatter.ofPattern("LLLL", Locale.forLanguageTag(language))))
                            }
                        }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = onClose) { Text(localized(R.string.study_close, language)) } })
}
