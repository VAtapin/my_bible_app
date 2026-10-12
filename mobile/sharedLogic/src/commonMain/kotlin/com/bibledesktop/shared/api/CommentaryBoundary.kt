package com.bibledesktop.shared.api

/** A real source boundary, not a guess from a module chapter number or article title. */
data class CommentaryBoundary(val chapter: Int, val after: Boolean, val book: Boolean = false, val chapterBoundary: Boolean = true)
data class VerseCommentaryBoundaries(val before: CommentaryBoundary? = null, val after: CommentaryBoundary? = null)

fun readerCommentaryBoundaries(chapters: List<BibleChapter>): Map<String, VerseCommentaryBoundaries> {
    val ordered = chapters.distinctBy { it.chapter.number }.sortedBy { it.chapter.number }
    val rows = ordered.flatMap { chapter -> chapter.verses.map { chapter to it } }
    return rows.mapIndexedNotNull { index, (chapter, verse) ->
        val position = canonicalStudyPosition(verse.osisRef)
        val previous = rows.getOrNull(index - 1)
        val next = rows.getOrNull(index + 1)
        val bookStart = chapter.chapter.number == 1 && chapter.verses.firstOrNull()?.osisRef == verse.osisRef
        val bookEnd = chapter.chapter.number == chapter.book.chaptersCount && chapter.verses.lastOrNull()?.osisRef == verse.osisRef
        fun adjacent(other: Pair<BibleChapter, BibleVerse>?): Boolean = other != null &&
            kotlin.math.abs(other.first.chapter.number - chapter.chapter.number) <= 1
        val starts = position.verse == 1 || (adjacent(previous) && canonicalStudyPosition(previous!!.second.osisRef).chapter != position.chapter)
        val ends = bookEnd || (adjacent(next) && canonicalStudyPosition(next!!.second.osisRef).chapter != position.chapter)
        val before = if (bookStart || starts) CommentaryBoundary(position.chapter, false, bookStart, starts) else null
        val after = if (bookEnd || ends) CommentaryBoundary(position.chapter, true, bookEnd, ends) else null
        if (before == null && after == null) null else verse.osisRef to VerseCommentaryBoundaries(before, after)
    }.toMap()
}

fun boundaryCommentaries(entries: List<CommentaryEntry>, boundary: CommentaryBoundary): List<CommentaryEntry> = entries.filter { entry ->
    val placedHere = if (boundary.after) entry.placement == CommentaryPlacement.AFTER else entry.placement != CommentaryPlacement.AFTER
    placedHere && when {
        entry.chapterFrom == 0 -> boundary.book
        entry.chapterFrom > 0 && entry.verseFrom == 0 -> boundary.chapterBoundary &&
            (if (boundary.after) entry.chapterTo ?: entry.chapterFrom else entry.chapterFrom) == boundary.chapter
        else -> false
    }
}.distinctBy { it.moduleCode to it.id }

fun verseSpecificCommentary(entry: CommentaryEntry): Boolean = entry.chapterFrom > 0 && entry.verseFrom > 0
