package com.bibledesktop.myapp.ui.study

internal data class SlavonicDictionaryTexts(val title: String, val catalog: String, val search: String,
    val favorites: String, val recent: String, val word: String, val meaning: String, val all: String,
    val more: String, val empty: String, val loading: String, val unavailable: String, val rate: String,
    val saved: String, val save: String, val remove: String, val source: String, val offline: String,
    val back: String, val close: String, val words: String)

internal fun slavonicDictionaryTexts(language: String) = when (language) {
    "de" -> SlavonicDictionaryTexts("Kirchenslawisches Wörterbuch", "Katalog", "Suchen", "Favoriten", "Verlauf",
        "Nach Wort", "Nach Bedeutung", "Alle Buchstaben", "Weitere anzeigen", "Keine Einträge gefunden.",
        "Wird geladen…", "Das Wörterbuch ist derzeit nicht verfügbar.", "Zu viele Anfragen. Bitte kurz warten.",
        "Das Wort konnte nicht gespeichert werden.", "Wort speichern", "Aus Favoriten entfernen", "Quelle öffnen",
        "Zum Laden neuer Wörter ist eine Internetverbindung erforderlich.", "Zurück", "Schließen", "Wörter")
    "uk" -> SlavonicDictionaryTexts("Церковнослов’янський словник", "Каталог", "Знайти", "Обране", "Історія",
        "За словом", "За значенням", "Усі літери", "Показати ще", "Записів не знайдено.",
        "Завантаження…", "Словник зараз недоступний.", "Забагато запитів. Зачекайте трохи.",
        "Не вдалося зберегти слово.", "Зберегти слово", "Прибрати з обраного", "Відкрити джерело",
        "Для завантаження нових слів потрібне підключення до інтернету.", "Назад", "Закрити", "Слів")
    "en" -> SlavonicDictionaryTexts("Church Slavonic dictionary", "Catalog", "Search", "Favorites", "History",
        "By word", "By meaning", "All letters", "Show more", "No entries found.",
        "Loading…", "The dictionary is currently unavailable.", "Too many requests. Please wait a little.",
        "Could not save this word.", "Save word", "Remove from favorites", "Open source",
        "An internet connection is required to load new words.", "Back", "Close", "Words")
    else -> SlavonicDictionaryTexts("Церковнославянский словарь", "Каталог", "Найти", "Избранное", "История",
        "По слову", "По значению", "Все буквы", "Показать ещё", "Записей не найдено.",
        "Загружаем…", "Словарь сейчас недоступен.", "Слишком много запросов. Подождите немного.",
        "Не удалось сохранить слово.", "Сохранить слово", "Убрать из избранного", "Открыть источник",
        "Для загрузки новых слов нужно подключение к интернету.", "Назад", "Закрыть", "Слов")
}
