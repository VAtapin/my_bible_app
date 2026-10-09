package com.bibledesktop.shared.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StudyBook(val id: Long, val slug: String, val title: String, val author: String? = null,
    val description: String? = null, @SerialName("module_code") val moduleCode: String)
@Serializable
data class StudyBookPage(val data: List<StudyBook>, val total: Int)
@Serializable
data class StudySection(val id: Long, val title: String? = null, val author: String? = null,
    @SerialName("chapter_from") val chapterFrom: Int, @SerialName("verse_from") val verseFrom: Int,
    @SerialName("chapter_to") val chapterTo: Int? = null, @SerialName("verse_to") val verseTo: Int? = null,
    @SerialName("book_osis_code") val bookOsisCode: String? = null, val body: String? = null)
@Serializable
data class BookContents(val book: StudyBook, val sections: List<StudySection>, val total: Int)
@Serializable
data class CommentaryModule(val code: String, val name: String, @SerialName("short_name") val shortName: String? = null,
    @SerialName("entries_count") val entriesCount: Int)
@Serializable
data class CommentaryEntry(val id: Long, val title: String? = null, val author: String? = null, val body: String,
    @SerialName("chapter_from") val chapterFrom: Int, @SerialName("verse_from") val verseFrom: Int,
    @SerialName("chapter_to") val chapterTo: Int? = null, @SerialName("verse_to") val verseTo: Int? = null,
    @SerialName("commentary_book_id") val commentaryBookId: Long? = null,
    @SerialName("module_code") val moduleCode: String, @SerialName("module_name") val moduleName: String)
@Serializable
data class CommentaryPage(val book: String, val chapter: Int? = null, val entries: List<CommentaryEntry>, val total: Int)
@Serializable
internal data class CanonBooks(val books: List<CanonBook>)
@Serializable
internal data class CanonBook(val slug: String, @SerialName("osis_code") val osisCode: String)

fun commentaryOverlaps(entry: CommentaryEntry, chapter: Int, first: Int, last: Int): Boolean {
    if (entry.chapterFrom == 0) return true
    val endChapter = entry.chapterTo ?: entry.chapterFrom
    if (entry.chapterFrom > chapter || endChapter < chapter) return false
    if (entry.verseFrom == 0) return true
    val endVerse = entry.verseTo ?: if (entry.chapterTo == null) entry.verseFrom else Int.MAX_VALUE
    return (entry.chapterFrom < chapter || entry.verseFrom <= last) && (endChapter > chapter || endVerse >= first)
}
