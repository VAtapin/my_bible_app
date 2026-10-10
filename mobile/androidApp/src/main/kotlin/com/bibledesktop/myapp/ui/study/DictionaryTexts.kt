package com.bibledesktop.myapp.ui.study

internal data class DictionaryTexts(val title: String, val search: String, val back: String, val next: String, val retry: String, val error: String, val offline: String, val maps: String, val all: String, val links: String, val reset: String, val fullscreen: String)
internal fun dictionaryTexts(language: String) = when(language) {
 "de" -> DictionaryTexts("Wörterbücher und Karten", "Artikel suchen", "Zurück", "Weiter", "Erneut versuchen", "Quelle nicht verfügbar", "Geöffnete Seiten werden gespeichert. Veröffentlichte vollständige Quellen stehen unter Downloads.", "Atlanten", "Alle", "Verwandte Artikel", "Zoom zurücksetzen", "Vollbild")
 "uk" -> DictionaryTexts("Словники та карти", "Назва статті", "Назад", "Далі", "Повторити", "Джерело недоступне", "Відкриті сторінки зберігаються. Опубліковані повні джерела — у розділі завантажень.", "Атласи", "Усі", "Пов’язані статті", "Скинути масштаб", "Повний екран")
 "en" -> DictionaryTexts("Dictionaries and maps", "Article title", "Back", "Next", "Retry", "Source unavailable", "Opened pages are saved. Published full sources are available under Downloads.", "Atlases", "All", "Related articles", "Reset zoom", "Full screen")
 else -> DictionaryTexts("Словари и карты", "Название статьи", "Назад", "Далее", "Повторить", "Источник недоступен", "Открытые страницы сохраняются. Опубликованные полные источники — в разделе загрузок.", "Атласы", "Все", "Связанные статьи", "Сбросить масштаб", "Полный экран")
}
