package com.bibledesktop.myapp

import androidx.activity.ComponentActivity
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.bibledesktop.myapp.ui.daily.*
import com.bibledesktop.myapp.ui.theme.BibleDesktopTheme
import com.bibledesktop.shared.api.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class CalendarReadingDialogTest {
 @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
 private val translation=TranslationSummary(code="calendar-fixture",name="Fixture",language=LanguageSummary("ru","Русский"),canonCode="fixture")
 private val john=BibleBook("actual-john","John",chaptersCount=2,canonicalBook=CanonicalBookSummary("John","NT"))
 private val luke=BibleBook("actual-luke","Luke",chaptersCount=1,canonicalBook=CanonicalBookSummary("Luke","NT"))
 private fun chapter(book:BibleBook,number:Int,refs:List<String>)=BibleChapter(translation,book,ChapterSummary(number,refs.size),refs.mapIndexed{index,ref->BibleVerse((number*100+index+1).toLong(),index+1,ref,"Actual $ref","Actual $ref")})
 private val chapters=listOf(chapter(john,1,listOf("John.3.1","John.3.2")),chapter(john,2,listOf("John.3.3","John.4.1")),chapter(luke,1,listOf("Luke.1.1","Luke.1.2")))
 private val reading=CalendarReading("multi","Readings","John 3:2–4:1; Luke 1:1–2","John.3.2",CalendarNormalizedReading(1,"parsed",listOf(
  CalendarReadingPassage("John",CalendarReadingPoint(3,2),CalendarReadingPoint(4,1)),CalendarReadingPassage("Luke",CalendarReadingPoint(1,1),CalendarReadingPoint(1,2)))))
 private val source=object:BibleContentSource by BibleApiClient(){
  override suspend fun getTranslations(language:String?)=listOf(translation)
  override suspend fun getBooks(translationCode:String)=listOf(john,luke)
  override suspend fun getChapter(translationCode:String,bookSlug:String,chapterNumber:Int)=chapters.single{it.book.slug==bookSlug&&it.chapter.number==chapterNumber}
  override suspend fun getVerseLocations(translationCode:String,osis:List<String>)=chapters.flatMap{chapter->chapter.verses.filter{it.osisRef in osis}.map{VerseLocation(it.id,it.osisRef,chapter.book.slug,chapter.chapter.number,it.number)}}
  override suspend fun getCalendarMonth(year:Int,month:Int,language:String)=listOf(CalendarGridDay(LocalDate.now().toString(),LocalDate.now().minusDays(13).toString(),4,CalendarDayStyle("ordinary","#000000",400),"Source fasting description","#dcebc9"))
  override suspend fun getCalendarDay(date:String,language:String,profile:String)=CalendarDay(date,LocalDate.parse(date).minusDays(13).toString(),"2026-04-12","Period","Fixture",readings=listOf(reading))
 }
 @Test fun allNormalizedPartsKeepActualSplitModuleChaptersAndOrderedVerses()=runBlocking {
  val rows=loadCalendarReading(source,translation,reading,pause={})
  assertEquals(listOf("John.3.2","John.3.3","John.4.1","Luke.1.1","Luke.1.2"),rows.map{it.verse.osisRef})
  assertEquals(listOf(1,2,2,1,1),rows.map{it.chapter.chapter.number})
  Unit
 }
 @Test fun wholeCanonicalChapterSpanningModuleChaptersIncludesBothAndUnparsedSourceFailsClosed()=runBlocking {
  val whole=reading.copy(reading=CalendarNormalizedReading(1,"parsed",listOf(CalendarReadingPassage("John",CalendarReadingPoint(3),CalendarReadingPoint(3)))))
  assertEquals(listOf("John.3.1","John.3.2","John.3.3"),loadCalendarReading(source,translation,whole,pause={}).map{it.verse.osisRef})
  assertTrue(runCatching{loadCalendarReading(source,translation,reading.copy(reading=reading.reading!!.copy(parseStatus="unparsed")),pause={})}.isFailure)
  assertTrue(runCatching{normalizedCalendarPassages(reading.copy(reading=null,passageRef="Ин. 3:2–4:1"))}.isFailure)
  Unit
 }
 @Test fun popupClosesAndBackReturnsToSameSelectedDayAndCalendarScroll(){
  var scroll:ScrollState?=null
  compose.setContent{BibleDesktopTheme{val state=rememberScrollState();scroll=state;Column(Modifier.fillMaxSize().verticalScroll(state)){Spacer(Modifier.height(200.dp));CalendarOverview("ru",source,detailed=true)}}}
  compose.waitUntil(10_000){compose.onAllNodesWithTag("calendar-reading-open-multi").fetchSemanticsNodes().isNotEmpty()}
  val previous=LocalDate.now().minusDays(1).toString()
  compose.onNode(hasContentDescription(previous,substring=true)).performScrollTo().performClick()
  compose.waitUntil(10_000){compose.onAllNodesWithTag("calendar-reading-open-multi").fetchSemanticsNodes().isNotEmpty()}
  compose.onNodeWithTag("calendar-reading-open-multi").performScrollTo()
  fun selectedDateText()=compose.onNodeWithTag("calendar-selected-date").onChildren().onFirst().fetchSemanticsNode().config[SemanticsProperties.Text].joinToString()
  val date=selectedDateText();val offset=scroll!!.value
  compose.onNodeWithTag("calendar-reading-open-multi").performClick()
  compose.waitUntil(10_000){compose.onAllNodesWithTag("calendar-reading-rows").fetchSemanticsNodes().isNotEmpty()}
  assertEquals(compose.activity.window.decorView.width.toFloat(),compose.onNodeWithTag("calendar-reading-dialog").fetchSemanticsNode().boundsInRoot.width,2f)
  compose.onNodeWithTag("calendar-reading-rows").performScrollToNode(hasTestTag("calendar-reading-Luke.1.2"))
  compose.onNodeWithTag("calendar-reading-Luke.1.2").assertIsDisplayed()
  compose.onNodeWithTag("calendar-reading-close").performClick()
  compose.onNodeWithTag("calendar-reading-dialog").assertDoesNotExist()
  assertEquals(date,selectedDateText());assertEquals(offset,scroll!!.value)
  compose.onNodeWithTag("calendar-reading-open-multi").performClick()
  compose.runOnIdle{compose.activity.onBackPressedDispatcher.onBackPressed()}
  compose.onNodeWithTag("calendar-reading-dialog").assertDoesNotExist();assertEquals(offset,scroll!!.value)
 }
 @Test fun fastingIconUsesKnownPublishedColorAndNeverLocalizedLabelGuess(){
  assertTrue(calendarHasFast("#DCEBC9"));assertFalse(calendarHasFast("#ffffff"));assertFalse(calendarHasFast("#fff3bf"));assertFalse(calendarHasFast("#dedee5"));assertFalse(calendarHasFast("unknown"))
  compose.setContent{BibleDesktopTheme{Column(Modifier.verticalScroll(rememberScrollState())){CalendarOverview("ru",source)}}}
  val tag="calendar-fast-${LocalDate.now()}"
  compose.waitUntil(10_000){compose.onAllNodesWithTag(tag,useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
  compose.onNodeWithTag(tag,useUnmergedTree=true).assertContentDescriptionEquals("Source fasting description")
 }
}
