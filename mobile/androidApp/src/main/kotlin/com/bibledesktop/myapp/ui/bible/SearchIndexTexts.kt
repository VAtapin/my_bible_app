package com.bibledesktop.myapp.ui.bible

internal data class SearchIndexText(val preparing:String,val updating:String)
internal fun searchIndexText(language:String)=when(language){
    "de"->SearchIndexText("Die Suche ist verfügbar, sobald der Index nach der Installation im Hintergrund vorbereitet wurde.","Die Suche verwendet den bisherigen fertigen Index, bis die Aktualisierung abgeschlossen ist.")
    "en"->SearchIndexText("Search will be available after the index is prepared in the background following installation.","Search uses the previous ready index until the update is complete.")
    "uk"->SearchIndexText("Пошук стане доступним після фонової підготовки індексу після встановлення тексту.","До завершення оновлення пошук використовує попередній готовий індекс.")
    else->SearchIndexText("Поиск станет доступен после фоновой подготовки индекса при установке текста.","До завершения обновления поиск использует прежний готовый индекс.")
}
