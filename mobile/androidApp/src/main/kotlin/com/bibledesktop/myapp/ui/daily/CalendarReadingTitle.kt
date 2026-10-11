package com.bibledesktop.myapp.ui.daily

import androidx.compose.runtime.Composable
import com.bibledesktop.myapp.R
import com.bibledesktop.myapp.ui.setup.localized
import com.bibledesktop.shared.api.CalendarReading
import com.bibledesktop.shared.api.lectionaryNumber

@Composable internal fun calendarReadingTitle(reading:CalendarReading,language:String):String {
    val title=reading.displayRef.ifBlank{reading.title}
    return reading.lectionaryNumber()?.let{"$title · ${localized(R.string.calendar_lectionary,language)} $it"}?:title
}
