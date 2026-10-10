package com.bibledesktop.shared.api

import kotlinx.coroutines.runBlocking
import kotlin.test.*

class ComparisonChaptersTest {
 private fun chapter(code:String,number:Int,vararg refs:String)=BibleChapter(TranslationSummary(code,code,language=LanguageSummary("en","English")),BibleBook("ps", "Psalms",chaptersCount=150),ChapterSummary(number,refs.size),refs.mapIndexed{i,ref->BibleVerse((number*100+i).toLong(),i+1,ref,ref,ref)})
 @Test fun loadsSplitActualChaptersAndRetainsSourceIdentity()=runBlocking {
  val primary=chapter("A",2,"Ps.2.1","Ps.2.2");val first=chapter("B",3,"Ps.2.1");val second=chapter("B",4,"Ps.2.2","Ps.3.1")
  val api=BibleApiClient();val fetched=mutableListOf<Int>()
  val source=object:BibleContentSource by api {
   override suspend fun getVerseLocations(translationCode:String,references:List<String>)=listOf(first,second).flatMap{c->c.verses.filter{it.osisRef in references}.map{VerseLocation(it.id,it.osisRef,c.book.slug,c.chapter.number,it.number)}}
   override suspend fun getChapter(translationCode:String,bookSlug:String,chapterNumber:Int):BibleChapter{require(translationCode=="B"&&bookSlug=="ps");fetched+=chapterNumber;return when(chapterNumber){3->first;4->second;else->error("No guessed chapter")}}
  }
  try{val loaded=loadComparisonChapters(primary,"B",source);assertEquals(listOf(3,4),fetched);val row=compareVerses(primary,loaded).first{it.reference=="Ps.2.2"};assertEquals(second,row.secondaryChapter);assertEquals(400L,row.secondary?.id)}finally{api.close()}
 }
 @Test fun streamDeduplicatesOverlapAndSortsCanonicalChapterBeforeVerse(){
  val first=chapter("A",1,"Ps.2.10");val second=chapter("A",2,"Ps.3.1");val other=chapter("B",7,"Ps.2.10","Ps.3.1","Ps.3.2")
  val rows=comparisonFrameRows(listOf(ComparisonChapterFrame(first,other),ComparisonChapterFrame(second,other)))
  assertEquals(listOf("Ps.2.10","Ps.3.1","Ps.3.2"),rows.map{it.row.reference});assertEquals(second,rows[1].frame.primary);assertEquals(other,rows[2].row.secondaryChapter)
 }
}
