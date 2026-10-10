package com.bibledesktop.myapp.ui.study
internal data class CommentaryRuleTexts(val save: String, val general: String, val book: String, val position: String,val saveRange:String,val ranges:String,val removeRange:String)
internal fun commentaryRuleTexts(language: String) = when(language) {
 "de" -> CommentaryRuleTexts("Für dieses Buch speichern", "Allgemeine Quellen", "Quellen für dieses Buch", "Vers oder Kapitel:Vers","Quellen auf diesen Abschnitt anwenden","Quellen nach Abschnitten","Regel entfernen")
 "uk" -> CommentaryRuleTexts("Запам’ятати для цієї книги", "Загальні джерела", "Джерела для цієї книги", "вірш або глава:вірш","Застосувати джерела до цього уривка","Джерела за уривками","Прибрати правило")
 "en" -> CommentaryRuleTexts("Remember for this book", "General sources", "Sources for this book", "verse or chapter:verse","Apply sources to this passage","Sources by passage","Remove rule")
 else -> CommentaryRuleTexts("Запомнить для этой книги", "Общие источники", "Источники для этой книги", "стих или глава:стих","Применить источники к этому отрывку","Источники по отрывкам","Снять правило")
}
