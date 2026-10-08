package com.bibledesktop.shared.api

data class ComparedVerse(val reference: String, val primary: BibleVerse?, val secondary: BibleVerse?)

/** Only exact canonical references match. Equal row indices or translated titles are not identities. */
fun compareVerses(primary: BibleChapter, secondary: BibleChapter): List<ComparedVerse> {
    require(primary.verses.map { it.osisRef }.distinct().size == primary.verses.size)
    require(secondary.verses.map { it.osisRef }.distinct().size == secondary.verses.size)
    val first = primary.verses.associateBy { it.osisRef }
    val second = secondary.verses.associateBy { it.osisRef }
    return (first.keys + second.keys).sortedWith(compareBy<String> {
        it.substringAfterLast('.').toIntOrNull() ?: Int.MAX_VALUE
    }.thenBy { it }).map { ComparedVerse(it, first[it], second[it]) }
}

class ComparisonUnavailable : Exception("Canonical book or chapter unavailable in this translation")

suspend fun loadComparison(primary: BibleChapter, code: String, source: BibleContentSource): BibleChapter {
    require(code.isNotBlank() && code != primary.translation.code)
    val canonical = source.getBooks(primary.translation.code).firstOrNull { it.slug == primary.book.slug }
        ?.canonicalBook?.osisCode ?: throw ComparisonUnavailable()
    val target = source.getBooks(code).firstOrNull { it.canonicalBook?.osisCode == canonical }
        ?: throw ComparisonUnavailable()
    if (primary.chapter.number !in 1..target.chaptersCount) throw ComparisonUnavailable()
    return source.getChapter(code, target.slug, primary.chapter.number).also {
        require(it.translation.code == code && it.book.slug == target.slug && it.chapter.number == primary.chapter.number)
        require(primary.verses.map { verse -> verse.osisRef }.distinct().size == primary.verses.size)
        require(it.verses.map { verse -> verse.osisRef }.distinct().size == it.verses.size)
    }
}
