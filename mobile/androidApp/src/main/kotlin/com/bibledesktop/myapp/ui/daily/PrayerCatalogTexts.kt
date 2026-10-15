package com.bibledesktop.myapp.ui.daily

internal data class PrayerCatalogTexts(val all:String,val groups:Map<String,String>,val description:String,val complete:String)
internal fun prayerCatalogTexts(language:String):PrayerCatalogTexts = when(language){
    "de"->PrayerCatalogTexts("Alle",mapOf("short" to "Kurze Gebete","rules" to "Gebetsregeln","occasions" to "Für verschiedene Anlässe","initial" to "Anfangsgebete"),"Beschreibung","Vollständige Ausgabe")
    "uk"->PrayerCatalogTexts("Усі",mapOf("short" to "Короткі молитви","rules" to "Молитовні правила","occasions" to "На різні випадки","initial" to "Початкові молитви"),"Опис","Повна редакція")
    "en"->PrayerCatalogTexts("All",mapOf("short" to "Short prayers","rules" to "Prayer rules","occasions" to "For various occasions","initial" to "Opening prayers"),"Description","Complete edition")
    else->PrayerCatalogTexts("Все",mapOf("short" to "Короткие молитвы","rules" to "Молитвенные правила","occasions" to "На разные случаи","initial" to "Начальные молитвы"),"Описание","Полная редакция")
}

internal fun prayerTopicLabels(language:String):Map<String,String> = com.bibledesktop.shared.api.prayerTopics.zip(when(language){
 "de"->listOf("Am Morgen","Am Abend und vor dem Schlafen","Zur Kommunion","Vor und nach dem Essen","Vor und nach der Arbeit","Lernen","Für die Lebenden","Für die Verstorbenen","Anfangsgebete","Kanones","Akathiste","Stundengebete","Troparien und Kondakien","Für verschiedene Anlässe")
 "uk"->listOf("Уранці","Увечері та перед сном","До Причастя","Перед і після їжі","Перед і після роботи","Навчання","За живих","За померлих","Початкові молитви","Канони","Акафісти","Часи","Тропарі та кондаки","На різні випадки")
 "en"->listOf("Morning","Evening and bedtime","Communion","Before and after meals","Before and after work","Study","For the living","For the departed","Opening prayers","Canons","Akathists","Hours","Troparia and kontakia","Various occasions")
 else->listOf("Утром","Вечером и перед сном","К Причастию","Перед и после еды","Перед и после работы","Учение","О живых","Об усопших","Начальные молитвы","Каноны","Акафисты","Часы","Тропари и кондаки","На разные случаи")
}).toMap()
