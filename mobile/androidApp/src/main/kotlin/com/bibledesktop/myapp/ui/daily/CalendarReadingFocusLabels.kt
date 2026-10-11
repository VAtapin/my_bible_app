package com.bibledesktop.myapp.ui.daily

internal fun calendarReadingFocusLabels(language:String):Pair<String,String> = when(language){
    "de" -> "Beginn der Lesung" to "Ende der Lesung"
    "uk" -> "Початок читання" to "Кінець читання"
    "en" -> "Reading begins" to "Reading ends"
    else -> "Начало чтения" to "Конец чтения"
}
