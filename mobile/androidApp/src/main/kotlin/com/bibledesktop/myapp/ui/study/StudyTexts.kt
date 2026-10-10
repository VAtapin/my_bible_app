package com.bibledesktop.myapp.ui.study

internal data class StudyTexts(val sources: String, val all: String, val order: String, val occurrences: String,
    val back: String, val temporary: String, val missing: String, val unknownStrong: String)
internal fun studyTexts(language: String) = when (language) {
    "de" -> StudyTexts("Quellen", "Alle", "Bibelreihenfolge", "Vorkommen suchen", "Zum Vergleich zurück", "Verknüpfter Abschnitt", "Die genaue Stelle fehlt in dieser Übersetzung.", "Die Quelle gibt keinen hebräischen oder griechischen Präfix an.")
    "uk" -> StudyTexts("Джерела", "Усі", "За біблійними книгами", "Знайти вживання", "Повернутися до порівняння", "Пов’язаний уривок", "Точне місце відсутнє в обраному перекладі.", "Джерело не вказує єврейський чи грецький префікс.")
    "en" -> StudyTexts("Sources", "All", "Biblical order", "Find occurrences", "Return to comparison", "Linked passage", "The exact passage is absent from this translation.", "The source gives no Hebrew or Greek prefix.")
    else -> StudyTexts("Источники", "Все", "По библейским книгам", "Найти употребления", "Вернуться к сравнению", "Связанный отрывок", "Точное место отсутствует в выбранном переводе.", "Источник не указывает еврейский или греческий префикс.")
}
