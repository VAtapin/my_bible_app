package com.bibledesktop.shared.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable data class VerseLocation(@SerialName("verse_id") val verseId: Long, @SerialName("osis_ref") val osis: String,
    @SerialName("book_slug") val book: String, @SerialName("chapter_number") val chapter: Int, @SerialName("verse_number") val verse: Int)

/** Resolves an explicitly stored canonical identity to an edition's actual chapter. */
suspend fun resolveStoredVerseLocations(code: String, references: List<String>, source: BibleContentSource, scanAll: Boolean = true,
    readChapter: suspend (String, Int) -> BibleChapter? = { book, number -> source.getChapter(code, book, number) }): List<VerseLocation> {
    require(references.size in 1..200 && references.all { Regex("[A-Za-z0-9]+\\.[1-9]\\d*\\.[1-9]\\d*").matches(it) })
    val books = source.getBooks(code)
    val result = mutableListOf<VerseLocation>()
    references.distinct().groupBy { it.substringBefore('.') }.forEach { (osis, refs) ->
        val book = books.firstOrNull { it.canonicalBook?.osisCode == osis } ?: return@forEach
        val remaining = refs.toMutableSet()
        val preferred = refs.map { it.split('.')[1].toInt() }.filter { it in 1..book.chaptersCount }.distinct()
        for (number in preferred + if (scanAll) (1..book.chaptersCount).filter { it !in preferred } else emptyList()) {
            val chapter = readChapter(book.slug, number) ?: continue
            require(chapter.translation.code == code && chapter.book.slug == book.slug && chapter.chapter.number == number)
            chapter.verses.filter { it.osisRef in remaining && it.plainText.isNotBlank() }.forEach {
                result += VerseLocation(it.id,it.osisRef,book.slug,number,it.number); remaining.remove(it.osisRef)
            }
            if (remaining.isEmpty()) break
        }
    }
    return result
}
