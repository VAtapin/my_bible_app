package com.bibledesktop.shared.api

import kotlinx.coroutines.runBlocking
import kotlin.test.*

class PersonalStudyTest {
    private fun chapter(number: Int = 1, texts: List<Pair<Int, String>> = listOf(1 to "Первый", 2 to "Второй", 3 to "Третий")) = BibleChapter(
        TranslationSummary("RST", "Russian", language = LanguageSummary("ru", "Russian")), BibleBook("john", "John", chaptersCount = 3),
        ChapterSummary(number, texts.size), texts.map { (verse, text) -> BibleVerse(verse.toLong(), verse, "John.$number.$verse", text, text) })
    @Test fun crossChapterPassageIncludesActualTextAndFormatsBothBounds() = runBlocking {
        val passage = collectPassage(chapter(), PassagePoint(1, 3), PassagePoint(2, 2)) { chapter(it) }
        assertEquals(listOf("John.1.3", "John.2.1", "John.2.2"), passage.verses.map { it.osis })
        assertEquals("John 1:3–2:2", passage.reference)
        assertEquals("Третий\nПервый\nВторой", formatPassage(passage, reference = false, translation = false, numbers = false))
        assertTrue(passage.contains(chapter(2), chapter(2).verses.first()))
        assertFalse(passage.contains(chapter(), chapter().verses.first()))
    }
    @Test fun incompleteOrWrongSourceNeverProducesAUsableSelection() = runBlocking {
        assertFails { collectPassage(chapter(texts = listOf(1 to "One", 3 to "Three")), PassagePoint(1, 1), PassagePoint(1, 3)) { chapter(it) } }
        assertFails { collectPassage(chapter(), PassagePoint(1, 3), PassagePoint(2, 2)) { chapter(it).copy(translation = chapter().translation.copy(code = "DE")) } }
        assertFails { collectPassage(chapter(texts = listOf(1 to "")), PassagePoint(1, 1), PassagePoint(1, 1)) { chapter(it) } }
        assertFails { parsePassagePoint("1:0") }; assertFails { parsePassagePoint("1:2x") }
        Unit
    }
    @Test fun anchorsAreEditionAndExactTextSpecificAndErasingRetainsBothSides() {
        val verse = chapter(texts = listOf(1 to "Бог и Бог")).verses.first()
        val mark = WordMark("a", "RST", verse.osisRef, verse.plainText, 0, 9, verse.plainText, note = "Note")
        assertTrue(mark.matches("RST", verse)); assertFalse(mark.matches("DE", verse)); assertFalse(mark.matches("RST", verse.copy(plainText = "Бог и Бог!")))
        val parts = eraseWordMarks(listOf(mark), "RST", verse, 3, 6) { "b" }
        assertEquals(listOf("Бог", "Бог"), parts.map { it.quote }); assertEquals(listOf("a", "b"), parts.map { it.id }); assertTrue(parts.all { it.note == "Note" })
        assertEquals(listOf(mark), eraseWordMarks(listOf(mark), "DE", verse, 3, 6) { "b" })
    }
 @Test fun noteEditPreservesExactAnchorAndFreshStyleRejectingConcurrentChanges(){
  val mark=WordMark("note","RST","John.1.1","Бог и Бог",6,9,"Бог","blue",true,"original")
  val value=PersonalStudy(marks=listOf(mark.copy(color="green")));val result=updateWordNote(value,mark,"edited")
  assertEquals(mark.copy(color="green",note="edited"),result.marks.single())
  assertFailsWith<IllegalArgumentException>{updateWordNote(result,mark,"overwrite")}
  assertFailsWith<IllegalArgumentException>{updateWordNote(value.copy(marks=listOf(mark.copy(start=0,end=3))),mark,"wrong anchor")}
 }

 @Test fun trimmedWordsKeepUtf16AnchorsAndWhitespaceIsNotActionable(){
  val body=" \tБог 𐍈 λόγος\u00a0 "
  assertEquals(2 to body.length-2,trimWordSelection(body,body.length,0))
  val selected=requireNotNull(trimWordSelection(body,0,body.length))
  assertEquals("Бог 𐍈 λόγος",body.substring(selected.first,selected.second))
  assertEquals(null,trimWordSelection(body,0,2));assertEquals(null,trimWordSelection(body,0,0));assertEquals(null,trimWordSelection(body,-1,3))
 }
}
