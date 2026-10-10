package com.bibledesktop.myapp.ui.daily

internal data class PrayerCatalogTexts(val all:String,val groups:Map<String,String>,val external:String,val externalOnly:String,val description:String,val complete:String)
internal fun prayerCatalogTexts(language:String):PrayerCatalogTexts = when(language){
    "de"->PrayerCatalogTexts("Alle",mapOf("short" to "Kurze Gebete","rules" to "Gebetsregeln","occasions" to "Für verschiedene Anlässe","initial" to "Anfangsgebete"),"Externe Quellen","Diese Quellen öffnen externe Websites. Ihre Texte werden nicht offline gespeichert.","Beschreibung","Vollständige Ausgabe")
    "uk"->PrayerCatalogTexts("Усі",mapOf("short" to "Короткі молитви","rules" to "Молитовні правила","occasions" to "На різні випадки","initial" to "Початкові молитви"),"Зовнішні джерела","Ці джерела відкривають зовнішні сайти. Їхні тексти не зберігаються офлайн.","Опис","Повна редакція")
    "en"->PrayerCatalogTexts("All",mapOf("short" to "Short prayers","rules" to "Prayer rules","occasions" to "For various occasions","initial" to "Opening prayers"),"External sources","These sources open external websites. Their texts are not saved offline.","Description","Complete edition")
    else->PrayerCatalogTexts("Все",mapOf("short" to "Короткие молитвы","rules" to "Молитвенные правила","occasions" to "На разные случаи","initial" to "Начальные молитвы"),"Внешние источники","Эти источники открываются на внешних сайтах. Их тексты не сохраняются офлайн.","Описание","Полная редакция")
}
