package com.bibledesktop.myapp.ui.daily

internal data class PrayerCatalogTexts(val all:String,val groups:Map<String,String>,val description:String,val complete:String)
internal fun prayerCatalogTexts(language:String):PrayerCatalogTexts = when(language){
    "de"->PrayerCatalogTexts("Alle",mapOf("short" to "Kurze Gebete","rules" to "Gebetsregeln","occasions" to "Für verschiedene Anlässe","initial" to "Anfangsgebete"),"Beschreibung","Vollständige Ausgabe")
    "uk"->PrayerCatalogTexts("Усі",mapOf("short" to "Короткі молитви","rules" to "Молитовні правила","occasions" to "На різні випадки","initial" to "Початкові молитви"),"Опис","Повна редакція")
    "en"->PrayerCatalogTexts("All",mapOf("short" to "Short prayers","rules" to "Prayer rules","occasions" to "For various occasions","initial" to "Opening prayers"),"Description","Complete edition")
    else->PrayerCatalogTexts("Все",mapOf("short" to "Короткие молитвы","rules" to "Молитвенные правила","occasions" to "На разные случаи","initial" to "Начальные молитвы"),"Описание","Полная редакция")
}
