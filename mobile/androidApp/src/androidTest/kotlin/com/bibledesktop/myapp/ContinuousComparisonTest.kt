package com.bibledesktop.myapp

import androidx.activity.ComponentActivity
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.bibledesktop.myapp.data.*
import com.bibledesktop.myapp.ui.bible.ComparisonRows
import com.bibledesktop.myapp.ui.bible.validateComparedContinuation
import com.bibledesktop.myapp.ui.theme.BibleDesktopTheme
import com.bibledesktop.shared.api.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.UUID

class ContinuousComparisonTest {
 @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
 @Test fun actualOfflineComparisonContinuesAndPrependingKeepsTheUpperVerse() {
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  val store=OfflineStore(File(context.cacheDir,"continuous-comparison-${UUID.randomUUID()}"))
  runBlocking{BundledBible.install(context,store)}
  val api=BibleApiClient()
  val source=OfflineContentRepository(object:BibleContentSource by api {
   override suspend fun getChapter(translationCode:String,bookSlug:String,chapterNumber:Int):BibleChapter=error("Network forbidden")
  },store,{false})
  val book=runBlocking{source.getBooks(BundledBible.code)}.first()
  val chapter=runBlocking{source.getChapter(BundledBible.code,book.slug,2)}
  var visible:String?=null;var offset=-1
  compose.setContent{BibleDesktopTheme {ComparisonRows("ru",chapter,chapter,19f,Modifier.fillMaxSize(),1,source,17,
   onVisible={_,first,_,position->visible=first.osisRef;offset=position})}}
  try{compose.waitUntil(10_000){visible==chapter.verses.first().osisRef}}catch(failure:Throwable){throw AssertionError("Upper verse=$visible offset=$offset expected=${chapter.verses.first().osisRef}",failure)}
  assertTrue(offset>=0)
  compose.onNodeWithTag("comparison-rows").performScrollToNode(hasTestTag("compared-Gen.2.25"))
  compose.waitUntil(10_000){compose.onAllNodesWithTag("compared-Gen.3.1").fetchSemanticsNodes().isNotEmpty()}
  compose.onNodeWithTag("comparison-rows").performScrollToNode(hasTestTag("compared-Gen.3.1"))
  // ScrollToNode only makes a row visible; it does not put it at the viewport's top.
  compose.onNodeWithTag("comparison-rows").performTouchInput {swipeUp()}
  compose.waitUntil(10_000){visible?.startsWith("Gen.3.")==true}
  assertTrue(visible?.startsWith("Gen.3.")==true)
  source.close()
 }
 @Test fun rejectsSameNumberFromAnotherCanonicalBookAndDuplicateReferences() {
  val translation=TranslationSummary("RST","Synodal",language=LanguageSummary("ru","Russian"))
  val initial=BibleChapter(translation,BibleBook("john","John",chaptersCount=21),ChapterSummary(3,1),listOf(BibleVerse(1,1,"John.3.1","Text","Text")))
  val next=initial.copy(chapter=ChapterSummary(4,1),verses=listOf(BibleVerse(2,1,"John.4.1","Text","Text")))
  validateComparedContinuation(initial,next,4)
  assertTrue(runCatching{validateComparedContinuation(initial,next.copy(verses=listOf(next.verses.first().copy(osisRef="Acts.4.1"))),4)}.isFailure)
  assertTrue(runCatching{validateComparedContinuation(initial,next.copy(verses=listOf(next.verses.first(),next.verses.first())),4)}.isFailure)
 }
}
