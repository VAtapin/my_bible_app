package com.bibledesktop.shared.api

import kotlinx.serialization.json.Json
import kotlin.test.*

class CommentaryBoundaryTest {
 private val translation=TranslationSummary("EDITION","Edition",language=LanguageSummary("en","English"))
 private fun chapter(module:Int,vararg refs:String)=BibleChapter(translation,BibleBook("joel","Joel",chaptersCount=3),ChapterSummary(module,refs.size),refs.mapIndexed{index,ref->BibleVerse(index.toLong()+1,ref.substringAfterLast('.').toInt(),ref,"Actual text","Actual text")})
 private fun entry(id:Long,chapter:Int,verse:Int=0,end:Int?=null,placement:CommentaryPlacement?=null)=CommentaryEntry(id,body="Actual article",chapterFrom=chapter,verseFrom=verse,chapterTo=end,moduleCode="SOURCE",moduleName="Source",placement=placement)

 @Test fun neutralAndPublishedPlacementAreDifferentAndVerseIntervalsStaySeparate(){
  val neutralBook=entry(1,0,99);val bookAfter=entry(2,0,placement=CommentaryPlacement.AFTER)
  val neutralChapter=entry(3,2);val chapterAfter=entry(4,1,end=2,placement=CommentaryPlacement.AFTER)
  val before=entry(5,2,placement=CommentaryPlacement.BEFORE);val verse=entry(6,2,1)
  val entries=listOf(neutralBook,bookAfter,neutralChapter,chapterAfter,before,verse)
  assertEquals(listOf(3L,5L),boundaryCommentaries(entries,CommentaryBoundary(2,false)).map{it.id})
  assertEquals(listOf(4L),boundaryCommentaries(entries,CommentaryBoundary(2,true)).map{it.id})
  assertEquals(listOf(1L,3L,5L),boundaryCommentaries(entries,CommentaryBoundary(2,false,book=true)).map{it.id})
  assertEquals(listOf(2L,4L),boundaryCommentaries(entries,CommentaryBoundary(2,true,book=true)).map{it.id})
  assertNull(neutralBook.placement);assertFalse(verseSpecificCommentary(neutralBook));assertTrue(verseSpecificCommentary(verse))
 }
 @Test fun sourceCanonicalChapterSplitDoesNotRepeatGeneralArticlesAtModuleBoundary(){
  val a=chapter(1,"Joel.1.1","Joel.1.2")
  val b=chapter(2,"Joel.1.3","Joel.2.1","Joel.2.2")
  val c=chapter(3,"Joel.2.3","Joel.2.4")
  val boundaries=readerCommentaryBoundaries(listOf(c,a,b))
  assertEquals(true,boundaries["Joel.1.1"]?.before?.book)
  assertEquals(1,boundaries["Joel.1.3"]?.after?.chapter)
  assertEquals(2,boundaries["Joel.2.1"]?.before?.chapter)
  assertNull(boundaries["Joel.1.2"]);assertNull(boundaries["Joel.2.2"]);assertNull(boundaries["Joel.2.3"])
  assertEquals(true,boundaries["Joel.2.4"]?.after?.book)
  assertNull(readerCommentaryBoundaries(listOf(b))["Joel.2.2"]?.after)
  val differentModuleNumbers=b.copy(verses=b.verses.mapIndexed{index,verse->verse.copy(number=index+1)})
  assertNull(readerCommentaryBoundaries(listOf(differentModuleNumbers))["Joel.1.3"]?.before)
 }
 @Test fun legacyAbsentAndNullPlacementDecodeAndUnknownPlacementFailsClosed(){
  val json="""{"id":1,"body":"Article","chapter_from":0,"verse_from":0,"module_code":"SOURCE","module_name":"Source"}"""
  assertNull(Json.decodeFromString(CommentaryEntry.serializer(),json).placement)
  assertNull(Json.decodeFromString(CommentaryEntry.serializer(),json.dropLast(1)+",\"placement\":null}").placement)
  assertEquals(CommentaryPlacement.AFTER,Json.decodeFromString(CommentaryEntry.serializer(),json.dropLast(1)+",\"placement\":\"after\"}").placement)
  assertFails{Json.decodeFromString(CommentaryEntry.serializer(),json.dropLast(1)+",\"placement\":\"introduction\"}")}
  val section=Json.decodeFromString(StudySection.serializer(),"""{"id":1,"chapter_from":0,"verse_from":0,"placement":"before"}""")
  assertEquals(CommentaryPlacement.BEFORE,section.placement)
 }
}
