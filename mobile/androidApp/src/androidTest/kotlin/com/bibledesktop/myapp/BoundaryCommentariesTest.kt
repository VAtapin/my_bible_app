package com.bibledesktop.myapp

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.bibledesktop.myapp.ui.bible.*
import com.bibledesktop.myapp.ui.study.*
import com.bibledesktop.myapp.ui.theme.BibleDesktopTheme
import com.bibledesktop.shared.api.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.io.IOException
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text

class BoundaryCommentariesTest {
 @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
 private val context=InstrumentationRegistry.getInstrumentation().targetContext
 private val preferences=context.getSharedPreferences("bible-desktop-study",Context.MODE_PRIVATE)
 private var saved=mapOf<String,Any?>()
 private val api=BibleApiClient()
 private val chapter=BibleChapter(TranslationSummary("EDITION","Actual edition",language=LanguageSummary("en","English"),canonCode="CANON"),BibleBook("genesis","Genesis",chaptersCount=1),ChapterSummary(1,3),
  (1..3).map{BibleVerse(it.toLong(),it,"Gen.1.$it","Bible text $it","Bible text $it")})
 private val entries=listOf(
  CommentaryEntry(1,title="Neutral book article",body="Full neutral body. "+"Actual source sentence. ".repeat(20),chapterFrom=0,verseFrom=44,moduleCode="BOOK",moduleName="Selected source"),
  CommentaryEntry(2,title="Published after article",body="Actual book after body",chapterFrom=0,verseFrom=0,moduleCode="BOOK",moduleName="Selected source",placement=CommentaryPlacement.AFTER),
  CommentaryEntry(3,title="Chapter introduction",body="Actual chapter introduction",chapterFrom=1,verseFrom=0,moduleCode="BOOK",moduleName="Selected source",placement=CommentaryPlacement.BEFORE),
  CommentaryEntry(4,title="Verse-specific article",body="Verse-specific actual text",chapterFrom=1,verseFrom=1,moduleCode="BOOK",moduleName="Selected source"))
 @Before fun savePreferences(){saved=preferences.all.toMap();preferences.edit().clear().putStringSet("sources",setOf("GLOBAL")).putStringSet("sources-book:Gen",setOf("BOOK")).commit()}
 @After fun restore(){val edit=preferences.edit().clear();saved.forEach{(key,value)->when(value){is String->edit.putString(key,value);is Boolean->edit.putBoolean(key,value);is Int->edit.putInt(key,value);is Long->edit.putLong(key,value);is Float->edit.putFloat(key,value);is Set<*>->edit.putStringSet(key,value.filterIsInstance<String>().toSet())}};edit.commit();api.close()}
 private fun source(gate:CompletableDeferred<Unit>,requests:MutableList<List<String>>)=object:BibleContentSource by api{
  override suspend fun getCanonicalSlug(canonCode:String,osisCode:String):String{assertEquals("CANON",canonCode);assertEquals("Gen",osisCode);return "genesis"}
  override suspend fun getCommentaries(book:String,chapter:Int?,modules:List<String>,offset:Int):CommentaryPage{
   assertEquals("genesis",book);requests+=modules;gate.await();val available=if(chapter==null)entries.filter{it.chapterFrom==0}else entries
   return CommentaryPage(book,chapter,available.drop(offset).take(1),available.size)
  }
 }
 @Test fun readerRemainsVisibleWhileGeneralArticlesLoadAndPopupKeepsNeutralAndAfterPlacementSeparate(){
  val gate=CompletableDeferred<Unit>();val requests=CopyOnWriteArrayList<List<String>>();val source=source(gate,requests)
  compose.setContent{BibleDesktopTheme{ContinuousChapterContent("ru",chapter,source,19f,emptySet(),{_,_->},{_,_->},{_,_->},null,1,Modifier.fillMaxSize(),null)}}
  compose.onNodeWithText("Bible text 1").assertIsDisplayed()
  compose.onAllNodesWithTag("boundary-commentaries:EDITION:Gen.1.1:start").assertCountEquals(0)
  gate.complete(Unit)
  compose.waitUntil(10_000){compose.onAllNodesWithTag("boundary-commentaries:EDITION:Gen.1.1:start").fetchSemanticsNodes().isNotEmpty()}
  assertTrue(requests.isNotEmpty());assertTrue(requests.all{it==listOf("BOOK")})
  compose.onNodeWithTag("boundary-commentaries:EDITION:Gen.1.1:start").performClick()
  compose.onNodeWithText("Neutral book article").assertIsDisplayed()
  compose.onAllNodesWithText("Published after article").assertCountEquals(0)
  // Only the explicitly placed chapter article is labelled before; the neutral book article is not.
  compose.onAllNodesWithText("Перед текстом").assertCountEquals(1)
  compose.onAllNodesWithText("Verse-specific article").assertCountEquals(0)
  compose.onAllNodesWithText("Читать полностью").onFirst().performClick()
  compose.onNode(hasText("Full neutral body.",substring=true)).assertTextEquals(studyReadingText(entries.first().body))
  compose.onNodeWithText("Закрыть").performClick()
  compose.onNodeWithTag("continuous-reader").performScrollToNode(hasTestTag("boundary-commentaries:EDITION:Gen.1.3:after"))
  compose.onNodeWithTag("boundary-commentaries:EDITION:Gen.1.3:after").performClick()
  compose.onNodeWithText("Actual book after body").assertIsDisplayed()
  compose.onAllNodesWithText("Neutral book article").assertCountEquals(0)
  assertEquals(listOf(4L),runBlocking{InlineCommentaryPages.entries(source,chapter,chapter.verses.first(),listOf("BOOK"))}.map{it.id})
 }
 @Test fun cleanAndDisabledCommentaryLinksDoNotRequestOrRenderGeneralMaterials(){
  val gate=CompletableDeferred<Unit>().apply{complete(Unit)};val requests=CopyOnWriteArrayList<List<String>>();val source=source(gate,requests)
  val display=mutableStateOf(ReaderPreferences(clean=true))
  compose.setContent{BibleDesktopTheme{CompositionLocalProvider(LocalReaderPreferences provides display.value){ContinuousChapterContent("ru",chapter,source,19f,emptySet(),{_,_->},{_,_->},{_,_->},null,1,Modifier.fillMaxSize(),null)}}}
  compose.onNodeWithText("Bible text 1").assertIsDisplayed();compose.runOnIdle{assertTrue(requests.isEmpty());display.value=ReaderPreferences(commentaryLinks=false)}
  compose.waitForIdle();assertTrue(requests.isEmpty())
  compose.onAllNodes(hasTestTag("boundary-commentaries:EDITION:Gen.1.1:start")).assertCountEquals(0)
 }
 @Test fun interleavedComparisonKeepsBoundaryMaterialsAttachedToActualEdition(){
  val gate=CompletableDeferred<Unit>().apply{complete(Unit)};val requests=CopyOnWriteArrayList<List<String>>();val source=source(gate,requests)
  val other=chapter.copy(translation=chapter.translation.copy(code="OTHER",name="Other actual edition"))
  compose.setContent{BibleDesktopTheme{ComparisonRows("ru",chapter,other,19f,Modifier.fillMaxSize(),1,source)}}
  compose.waitUntil(10_000){compose.onAllNodesWithTag("boundary-commentaries:OTHER:Gen.1.1:start").fetchSemanticsNodes().isNotEmpty()}
  compose.onNodeWithTag("boundary-commentaries:EDITION:Gen.1.1:start").assertExists()
  compose.onNodeWithTag("boundary-commentaries:OTHER:Gen.1.1:start").performClick()
  compose.onNodeWithText("Neutral book article").assertIsDisplayed()
  compose.onAllNodesWithText("Published after article").assertCountEquals(0)
  assertTrue(requests.all{it==listOf("BOOK")})
 }
 @Test fun temporaryTransportFailureRetriesInBackgroundWithoutBlockingBibleOrAddingRetryButton(){
  val requests=CopyOnWriteArrayList<List<String>>();val ready=CompletableDeferred<Unit>().apply{complete(Unit)}
  val backing=source(ready,requests);val attempts=AtomicInteger()
  val client=object:BibleContentSource by backing{
   override suspend fun getCommentaries(book:String,chapter:Int?,modules:List<String>,offset:Int):CommentaryPage{
    if(attempts.incrementAndGet()==1)throw IOException("Temporary transport failure")
    return backing.getCommentaries(book,chapter,modules,offset)
   }
  }
  compose.setContent{BibleDesktopTheme{Column{Text("Bible text stays available");BoundaryCommentaries("ru",client,chapter,chapter.verses.first(),CommentaryBoundary(1,false))}}}
  compose.waitUntil(3_000){attempts.get()==1}
  compose.onNodeWithText("Bible text stays available").assertIsDisplayed()
  compose.onAllNodes(hasText("Повторить",substring=true)).assertCountEquals(0)
  compose.waitUntil(10_000){compose.onAllNodesWithTag("boundary-commentaries:EDITION:Gen.1.1:start").fetchSemanticsNodes().isNotEmpty()}
  assertTrue(attempts.get()>1)
  compose.onNodeWithText("Bible text stays available").assertIsDisplayed()
  compose.onAllNodes(hasText("Повторить",substring=true)).assertCountEquals(0)
  compose.onNodeWithTag("boundary-commentaries:EDITION:Gen.1.1:start").performClick()
  compose.onNodeWithText("Chapter introduction").assertIsDisplayed()
 }
}
