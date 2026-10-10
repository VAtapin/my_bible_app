package com.bibledesktop.myapp

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.height
import com.bibledesktop.myapp.ui.study.*
import com.bibledesktop.myapp.ui.theme.BibleDesktopTheme
import com.bibledesktop.shared.api.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import org.junit.After
import androidx.test.platform.app.InstrumentationRegistry
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

class InlineReferenceRetryTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private var priorReferences:ReferenceDisplaySettings?=null
    @Before fun isolateSources(){val store=ReferenceDisplayStore(InstrumentationRegistry.getInstrumentation().targetContext);priorReferences=store.load();store.save(ReferenceDisplaySettings(list=true))}
    @After fun restoreSources(){priorReferences?.let{ReferenceDisplayStore(InstrumentationRegistry.getInstrumentation().targetContext).save(it)}}
    private val verse=BibleVerse(501,16,"John.3.16","Text","Text")
    private val chapter=BibleChapter(TranslationSummary(code="fixture",name="Fixture",language=LanguageSummary("en","English")),BibleBook("john","John",chaptersCount=21),ChapterSummary(3,1),listOf(verse))
    @Test fun failureHasNoInlineErrorButtonAndDelayedSuccessShowsCompactAvailablePassage(){
        val calls=AtomicInteger();val books=AtomicInteger();val shouldFail=java.util.concurrent.atomic.AtomicBoolean(true);
        val source=object:BibleContentSource by BibleApiClient(){
            override suspend fun getCrossReferences(verseId:Long,translationCode:String):CrossReferences{
                calls.incrementAndGet();if(shouldFail.get())throw IOException("Offline")
                return CrossReferences(StudyVerse(verseId,verse.osisRef),translationCode,listOf(CrossReference(1,ReferenceTarget(502,"John.3.17","John 3:17","john",3,17),source="Fixture")))
            }
            override suspend fun getBooks(translationCode:String):List<BibleBook>{books.incrementAndGet();return listOf(chapter.book)}
            override suspend fun getVerseLocations(translationCode:String,osis:List<String>)=listOf(VerseLocation(502,"John.3.17","john",3,17))
            override suspend fun getChapter(translationCode:String,bookSlug:String,chapterNumber:Int)=chapter.copy(verses=listOf(BibleVerse(502,17,"John.3.17","Available target text","Available target text")))

        }
        compose.setContent{BibleDesktopTheme{CompositionLocalProvider(LocalVerseStudyClient provides source,LocalInlineReferenceLoader provides InlineReferenceLoader(startPauseMs=0,retryPauses=listOf(30))){Column{InlineVerseReferences("ru",chapter,verse){}}}}}
        compose.waitUntil(10_000){calls.get()>=2}
        compose.onAllNodes(hasText("Повторить",substring=true)).assertCountEquals(0)
        compose.onAllNodes(hasText("↗",substring=true)).assertCountEquals(0)
        shouldFail.set(false)
        compose.waitUntil(10_000){compose.onAllNodesWithTag("inline-reference-John.3.17").fetchSemanticsNodes().isNotEmpty()}
        compose.onAllNodes(hasText("↗",substring=true)).assertCountEquals(0)
        assertEquals(0,books.get())
        val bounds=compose.onNodeWithTag("inline-reference-John.3.17").getUnclippedBoundsInRoot()
        assertTrue("Textual links must not inherit 48dp button rows",bounds.height.value<30)
        compose.onNodeWithTag("inline-reference-John.3.17").performClick()
        compose.waitUntil(10_000){compose.onAllNodesWithText("Available target text").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Available target text").assertIsDisplayed()
    }
    @Test fun emptySuccessfulResultAddsNeitherZeroButtonNorBackgroundBookRequests(){
        val calls=AtomicInteger();val books=AtomicInteger()
        val source=object:BibleContentSource by BibleApiClient(){
            override suspend fun getCrossReferences(verseId:Long,translationCode:String):CrossReferences{calls.incrementAndGet();return CrossReferences(StudyVerse(verseId,verse.osisRef),translationCode)}
            override suspend fun getBooks(translationCode:String):List<BibleBook>{books.incrementAndGet();return emptyList()}
        }
        compose.setContent{BibleDesktopTheme{CompositionLocalProvider(LocalVerseStudyClient provides source){InlineVerseReferences("ru",chapter,verse){}}}}
        compose.waitUntil(10_000){calls.get()>0};compose.waitForIdle()
        compose.onAllNodes(hasText("↗",substring=true)).assertCountEquals(0)
        runBlocking{delay(70)}
        assertEquals(1,calls.get());assertEquals(0,books.get())
    }
    @Test fun removingVerseCancelsPendingRetryAndNeverUpdatesAnotherVerse(){
        val calls=AtomicInteger();var shown by mutableStateOf(true)
        val source=object:BibleContentSource by BibleApiClient(){
            override suspend fun getCrossReferences(verseId:Long,translationCode:String):CrossReferences{calls.incrementAndGet();throw IOException("Offline")}
        }
        compose.setContent{BibleDesktopTheme{CompositionLocalProvider(LocalVerseStudyClient provides source,LocalInlineReferenceLoader provides InlineReferenceLoader(startPauseMs=0,retryPauses=listOf(200))){if(shown)InlineVerseReferences("ru",chapter,verse){}}}}
        compose.waitUntil(10_000){calls.get()>0}
        compose.runOnIdle{shown=false}
        compose.waitForIdle();val stopped=calls.get()
        runBlocking{delay(350)}
        assertEquals(stopped,calls.get())
    }
    @Test fun defaultTextOnlyModeDoesNotLoadReferencesOrAddRows() {
        ReferenceDisplayStore(InstrumentationRegistry.getInstrumentation().targetContext).save(ReferenceDisplaySettings(list=false))
        val calls=AtomicInteger()
        val source=object:BibleContentSource by BibleApiClient(){
            override suspend fun getCrossReferences(verseId:Long,translationCode:String):CrossReferences{calls.incrementAndGet();error("Text-only mode must not request references")}
        }
        compose.setContent{BibleDesktopTheme{CompositionLocalProvider(LocalVerseStudyClient provides source){InlineVerseReferences("ru",chapter,verse){}}}}
        compose.waitForIdle()
        compose.onAllNodes(hasText("↗",substring=true)).assertCountEquals(0)
        assertEquals(0,calls.get())
    }
    @Test fun backoffUsesPausesAndCancellationDoesNotConsumeRetry()=runBlocking {
        val pauses=mutableListOf<Long>();var attempts=0
        val result=InlineReferenceLoader(startPauseMs=0).load(attempt={attempts++;if(attempts<=5)throw IOException("Offline");"Actual source"},waitForRetry={pauses+=it})
        assertEquals("Actual source",result);assertEquals(listOf(3_000L,9_000L,30_000L,60_000L,60_000L),pauses)
        var cancelledAttempts=0
        val failure=runCatching{InlineReferenceLoader(startPauseMs=0).load(attempt={cancelledAttempts++;throw CancellationException("Gone")},waitForRetry={error("Cancellation must not retry")})}.exceptionOrNull()
        assertTrue(failure is CancellationException);assertEquals(1,cancelledAttempts)
        Unit
    }
    @Test fun queueLimitsSimultaneousRequestsAndCancelledWaiterNeverStarts()=runBlocking {
        val loader=InlineReferenceLoader(maxConcurrent=2,startPauseMs=0)
        val release=CompletableDeferred<Unit>();val started=AtomicInteger();val active=AtomicInteger();val maximum=AtomicInteger()
        suspend fun attempt():String {
            started.incrementAndGet();val count=active.incrementAndGet();maximum.updateAndGet{maxOf(it,count)}
            return try{release.await();"source"}finally{active.decrementAndGet()}
        }
        val running=(1..2).map{async(Dispatchers.Default){loader.load(attempt=::attempt)}}
        withTimeout(3_000){while(started.get()<2)delay(5)}
        val queued=async(start=CoroutineStart.UNDISPATCHED){loader.load(attempt=::attempt)}
        val cancelled=async(start=CoroutineStart.UNDISPATCHED){loader.load(attempt=::attempt)}
        assertEquals(2,started.get());cancelled.cancelAndJoin();release.complete(Unit)
        (running+queued).awaitAll()
        assertEquals(3,started.get());assertEquals(2,maximum.get())
        Unit
    }
}
