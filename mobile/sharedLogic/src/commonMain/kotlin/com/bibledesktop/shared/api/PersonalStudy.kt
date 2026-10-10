package com.bibledesktop.shared.api

import kotlinx.serialization.Serializable

@Serializable data class PassagePoint(val chapter: Int, val verse: Int) : Comparable<PassagePoint> {
    override fun compareTo(other: PassagePoint): Int = chapter.compareTo(other.chapter).takeIf { it != 0 } ?: verse.compareTo(other.verse)
}
@Serializable data class PassageVerse(val chapter: Int, val verse: Int, val osis: String, val text: String)
@Serializable data class SavedPassage(val translationCode: String, val translationName: String, val bookSlug: String, val bookName: String,
    val start: PassagePoint, val end: PassagePoint, val verses: List<PassageVerse>) {
    val reference: String get() = "$bookName ${start.chapter}:${start.verse}" + when {
        start == end -> ""
        start.chapter == end.chapter -> "–${end.verse}"
        else -> "–${end.chapter}:${end.verse}"
    }
    fun contains(chapter: BibleChapter, verse: BibleVerse): Boolean = translationCode == chapter.translation.code && bookSlug == chapter.book.slug && PassagePoint(chapter.chapter.number, verse.number) in start..end
}
@Serializable data class StudyBookmark(val id: String, val passage: SavedPassage, val title: String, val description: String = "", val color: String = "yellow", val collection: String = "", val order: Int = 0)
@Serializable data class MemoryCard(val id: String, val passage: SavedPassage, val learned: Boolean = false)
@Serializable data class WordMark(val id: String, val code: String, val osis: String, val sourceText: String, val start: Int, val end: Int, val quote: String,
    val color: String = "yellow", val underline: Boolean = false, val note: String = "") {
    fun matches(translation: String, verse: BibleVerse): Boolean = code == translation && osis == verse.osisRef && sourceText == verse.plainText && start >= 0 && end > start && end <= sourceText.length && sourceText.substring(start, end) == quote
}
@Serializable data class StudyColor(val day: String, val night: String)
@Serializable data class PersonalStudy(val version: Int = 1, val bookmarks: List<StudyBookmark> = emptyList(), val cards: List<MemoryCard> = emptyList(), val marks: List<WordMark> = emptyList(),
    val palette: Map<String, StudyColor> = mapOf("yellow" to StudyColor("#ffe49a", "#66521b"), "green" to StudyColor("#b5e3bb", "#245737"), "blue" to StudyColor("#b8d9fb", "#254f72"), "pink" to StudyColor("#f7bfd8", "#702d51")))

fun parsePassagePoint(raw: String): PassagePoint {
    val match = Regex("^(\\d+):(\\d+)$").matchEntire(raw.trim()) ?: error("Invalid passage")
    return PassagePoint(match.groupValues[1].toInt(), match.groupValues[2].toInt()).also { require(it.chapter > 0 && it.verse > 0) }
}
suspend fun collectPassage(source: BibleChapter, start: PassagePoint, end: PassagePoint, load: suspend (Int) -> BibleChapter): SavedPassage {
    require(start <= end && start.chapter > 0 && start.verse > 0 && end.verse > 0 && end.chapter <= source.book.chaptersCount && end.chapter - start.chapter <= 200)
    val verses = buildList {
        for (number in start.chapter..end.chapter) {
            val chapter = if (number == source.chapter.number) source else load(number)
            check(chapter.translation.code == source.translation.code && chapter.book.slug == source.book.slug && chapter.chapter.number == number)
            val first = if (number == start.chapter) start.verse else 1
            val last = if (number == end.chapter) end.verse else chapter.verses.maxOfOrNull { it.number } ?: 0
            val selected = chapter.verses.filter { it.number in first..last }.sortedBy { it.number }
            check(first > 0 && last >= first && selected.size == last - first + 1 && selected.map { it.number }.distinct().size == selected.size && selected.first().number == first && selected.last().number == last && selected.all { it.plainText.isNotBlank() })
            addAll(selected.map { PassageVerse(number, it.number, it.osisRef, it.plainText) })
        }
    }
    return SavedPassage(source.translation.code, source.translation.shortName ?: source.translation.name, source.book.slug, source.book.name, start, end, verses)
}
fun formatPassage(passage: SavedPassage, reference: Boolean = true, translation: Boolean = true, numbers: Boolean = true): String {
    val body = passage.verses.joinToString("\n") { verse -> (if (numbers) (if (passage.start.chapter == passage.end.chapter) "${verse.verse} " else "${verse.chapter}:${verse.verse} ") else "") + verse.text }
    return listOf(body, if (reference) passage.reference else "", if (translation) passage.translationName else "").filter { it.isNotBlank() }.joinToString("\n\n")
}
fun eraseWordMarks(marks: List<WordMark>, code: String, verse: BibleVerse, start: Int, end: Int, nextId: () -> String): List<WordMark> = marks.flatMap { mark ->
    if (!mark.matches(code, verse) || mark.end <= start || mark.start >= end) listOf(mark) else buildList {
        if (mark.start < start) add(mark.copy(end = start, quote = verse.plainText.substring(mark.start, start)))
        if (mark.end > end) add(mark.copy(id = nextId(), start = end, quote = verse.plainText.substring(end, mark.end)))
    }
}

fun updateWordNote(value:PersonalStudy,expected:WordMark,note:String):PersonalStudy {
    val current=value.marks.firstOrNull{it.id==expected.id} ?: error("Word note absent")
    require(current.code==expected.code&&current.osis==expected.osis&&current.sourceText==expected.sourceText&&current.start==expected.start&&current.end==expected.end&&current.quote==expected.quote&&current.note==expected.note)
    return value.copy(marks=value.marks.map{if(it.id==current.id)it.copy(note=note)else it})
}

fun trimWordSelection(text:String,start:Int,end:Int):Pair<Int,Int>? {
    var first=minOf(start,end);var last=maxOf(start,end)
    if(first<0||last>text.length)return null
    fun whitespace(char:Char)=char.isWhitespace()||char=='\uFEFF'
    while(first<last&&whitespace(text[first]))first++
    while(last>first&&whitespace(text[last-1]))last--
    return if(first<last)first to last else null
}
