package com.bibledesktop.shared.api
@kotlinx.serialization.Serializable
data class StudyPosition(val chapter: Int, val verse: Int) : Comparable<StudyPosition> {
 override fun compareTo(other: StudyPosition): Int = chapter.compareTo(other.chapter).takeIf { it != 0 } ?: verse.compareTo(other.verse)
}
fun canonicalStudyPosition(osis:String):StudyPosition { val parts=Regex("[A-Za-z0-9]+\\.(\\d+)\\.(\\d+)").matchEntire(osis) ?: error("Canonical identity unavailable");return StudyPosition(parts.groupValues[1].toInt(),parts.groupValues[2].toInt()) }
fun studyPosition(value: String, chapter: Int): StudyPosition? {
 val match = Regex("(?:(\\d+):)?(\\d+)").matchEntire(value.trim()) ?: return null
 val result = StudyPosition(if(match.groupValues[1].isEmpty()) chapter else match.groupValues[1].toIntOrNull() ?: return null, match.groupValues[2].toIntOrNull() ?: return null)
 return result.takeIf { it.chapter > 0 && it.verse > 0 }
}
fun commentaryOverlapsRange(entry: CommentaryEntry, first: StudyPosition, last: StudyPosition): Boolean {
 if(entry.chapterFrom == 0) return true
 val start = StudyPosition(entry.chapterFrom, entry.verseFrom)
 val end = StudyPosition(entry.chapterTo ?: entry.chapterFrom, if(entry.verseFrom == 0) Int.MAX_VALUE else entry.verseTo ?: if(entry.chapterTo == null) entry.verseFrom else Int.MAX_VALUE)
 return start <= last && end >= first
}
suspend fun loadCommentaryRange(first: StudyPosition, last: StudyPosition, fetch: suspend (Int, Int) -> CommentaryPage): List<CommentaryEntry> {
 require(first <= last)
 val articles = linkedMapOf<Long, CommentaryEntry>()
 for(chapter in first.chapter..last.chapter) { var offset = 0; while(true) { val page = fetch(chapter, offset); page.entries.filter { commentaryOverlapsRange(it, first, last) }.forEach { articles[it.id] = it }; offset += page.entries.size; if(page.entries.isEmpty() || offset >= page.total) break } }
 return articles.values.toList()
}
