package com.bibledesktop.myapp.ui.daily

internal data class CalendarTextPriorityLabels(val automatic:String,val missing:String)
internal fun calendarTextPriorityLabels(language:String)=when(language){
 "uk"->CalendarTextPriorityLabels("Редакція вибирається окремо для кожного тексту за мовою календаря.","Для цього тексту відповідна опублікована редакція недоступна.")
 "de"->CalendarTextPriorityLabels("Die Ausgabe wird für jeden Text anhand der Kalendersprache ausgewählt.","Für diesen Text ist keine passende veröffentlichte Ausgabe verfügbar.")
 "en"->CalendarTextPriorityLabels("Each text uses an edition selected for the calendar language.","No suitable published edition is available for this text.")
 else->CalendarTextPriorityLabels("Редакция выбирается отдельно для каждого текста по языку календаря.","Для этого текста подходящая опубликованная редакция недоступна.")
}
