package com.bibledesktop.shared.api

import kotlinx.serialization.Serializable

@Serializable
data class BiblePackage(
    val translation: TranslationSummary, val books: List<BibleBook>,
    val done: Int = 0, val total: Int = books.sumOf { it.chaptersCount },
    val bytes: Long = 0, val complete: Boolean = false,
    val unavailable: List<String> = emptyList(),
)
