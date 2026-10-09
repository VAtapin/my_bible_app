package com.bibledesktop.shared.api

import kotlinx.serialization.Serializable

@Serializable
data class BiblePackage(
    val translation: TranslationSummary, val books: List<BibleBook>,
    val done: Int = 0, val total: Int = books.sumOf { it.chaptersCount },
    val bytes: Long = 0, val complete: Boolean = false,
    val unavailable: List<String> = emptyList(),
    val missingVerses: List<String> = emptyList(),
)

/** Finished installation may have explicitly missing source chapters; never call that a complete Bible. */
val BiblePackage.isInstalled: Boolean
    get() = books.isNotEmpty() && total == books.sumOf { it.chaptersCount } && done in 1..total &&
        unavailable.distinct().size == unavailable.size &&
        ((complete && done == total && unavailable.isEmpty() && missingVerses.isEmpty()) || (!complete && done + unavailable.size == total))
