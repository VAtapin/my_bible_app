package com.bibledesktop.shared.api

enum class VerseSearchMatch { EXACT, PHRASE, PARTIAL, STRONG, MORPHOLOGY }
enum class VerseSearchScope { ALL, OLD, NEW, PSALMS }
private val searchWords = Regex("[\\p{L}\\p{M}\\p{N}]+")
private fun wordTokens(value: String) = searchWords.findAll(value.lowercase()).map { it.value }.toList()
fun verseMatches(text: String, query: String, match: VerseSearchMatch, raw: String = text): Boolean {
    require(match != VerseSearchMatch.MORPHOLOGY) { "Language-aware stemming is supplied by the local index" }
    if (match == VerseSearchMatch.STRONG) {
        val number = query.trim().uppercase()
        return Regex("[HG]\\d{1,5}").matches(number) && Regex("\\b[HG]\\d{1,5}\\b").findAll(raw).any { it.value == number }
    }
    val tokens = wordTokens(query)
    if (tokens.isEmpty()) return false
    if (match == VerseSearchMatch.PARTIAL) return tokens.all { text.lowercase().contains(it) }
    val source = wordTokens(text)
    if (match == VerseSearchMatch.EXACT) return tokens.all(source::contains)
    return source.indices.any { start -> tokens.indices.all { n -> source.getOrNull(start + n) == tokens[n] } }
}
data class VerseSearchHit(val translation: String, val book: String, val bookName: String, val chapter: Int, val verse: Int, val reference: String, val text: String)
data class LocalVerseSearchPage(val results: List<VerseSearchHit>, val total: Int, val unavailableChapters: Int)
data class VerseSearchHighlight(val text: String, val match: Boolean)
fun verseHighlights(text: String, query: String, match: VerseSearchMatch): List<VerseSearchHighlight> {
    if (match == VerseSearchMatch.STRONG) return listOf(VerseSearchHighlight(text, false))
    val tokens = wordTokens(query)
    val result = mutableListOf<VerseSearchHighlight>()
    var start = 0
    searchWords.findAll(text).forEach { word ->
        if (word.range.first > start) result += VerseSearchHighlight(text.substring(start, word.range.first), false)
        val value = word.value.lowercase()
        result += VerseSearchHighlight(word.value, tokens.any { if (match == VerseSearchMatch.PARTIAL) value.contains(it) else value == it })
        start = word.range.last + 1
    }
    if (start < text.length) result += VerseSearchHighlight(text.substring(start), false)
    return result
}
