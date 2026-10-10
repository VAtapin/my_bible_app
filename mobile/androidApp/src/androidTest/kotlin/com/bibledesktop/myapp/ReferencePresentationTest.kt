package com.bibledesktop.myapp

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.bibledesktop.myapp.ui.study.*
import com.bibledesktop.myapp.ui.theme.BibleDesktopTheme
import com.bibledesktop.shared.api.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ReferencePresentationTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val edition=TranslationSummary("RST","Синодальный",language=LanguageSummary("ru","Русский"))
    private val book=BibleBook("actual-genesis","Бытие",chaptersCount=50)
    private val status=ReferenceVersification("verified","source","edition","reviewed-v1")
    private fun target(number:Int=7,chapter:Int=16,verified:Boolean=true)=ReferenceTarget(1,"Gen.$chapter.$number","Бытие $chapter:$number","legacy-genesis",chapter,number,"UNTRUSTED CACHED BODY",if(verified)status else ReferenceVersification())
    private fun chapter(number:Int=16)=BibleChapter(edition,book,ChapterSummary(number,1),listOf(BibleVerse(1607,7,"Gen.16.7","Ангел Господень нашел ее у источника воды в пустыне","Ангел Господень нашел ее у источника воды в пустыне")))

    @Test fun unavailableCardsAreAbsentWhileAvailableLabelAndOpenAreBothActions() {
        val unknown=ReferenceGroup("legacy_quote","raw",listOf(target(verified=false)))
        val confirmed=ReferenceGroup("legacy_quote","raw",listOf(target(chapter=19,number=27).copy(text=null)))
        var opened=0
        compose.setContent { BibleDesktopTheme { Column {
            ReferencePassageCard("ru",chapter(),unknown){error("Unknown card must not open")}
            ReferencePassageCard("ru",chapter(),confirmed,mapOf("Gen.19.27" to "Авраам встал рано утром")){opened++}
        } } }
        compose.onNodeWithText("Бытие 16:7").assertDoesNotExist()
        compose.onNodeWithText("UNTRUSTED CACHED BODY",substring=true).assertDoesNotExist()
        compose.onNodeWithText("legacy_quote",substring=true).assertDoesNotExist()
        compose.onNodeWithText("27 —").assertDoesNotExist()
        compose.onNodeWithTag("reference-label-Gen.19.27").performClick()
        compose.onNodeWithTag("reference-Gen.19.27").performClick()
        compose.runOnIdle { assertEquals(2,opened) }
        assertEquals("Бытие 19:27 · Синодальный",referenceCopyText(confirmed,edition.name))
    }

    @Test fun exactLocationUsesCurrentEditionIdAndChapterAndNeverTrustsStoredCoordinates()=runBlocking {
        val api=BibleApiClient()
        val batches=mutableListOf<List<String>>();val chapters=mutableListOf<Pair<String,Int>>()
        val source=object:BibleContentSource by api {
            override suspend fun getVerseLocations(code:String,references:List<String>):List<VerseLocation> {
                assertEquals("RST",code);batches+=references
                return listOf(VerseLocation(1607,"Gen.16.7",book.slug,16,7),VerseLocation(1927,"Gen.19.27",book.slug,19,27))
            }
            override suspend fun getChapter(code:String,slug:String,number:Int):BibleChapter {
                chapters+=slug to number
                return if(number==16)chapter() else chapter(19).copy(verses=listOf(BibleVerse(1927,27,"Gen.19.27","Авраам встал рано утром","Авраам встал рано утром")))
            }
        }
        try {
            val targets=listOf(target(verified=false).copy(bookSlug="wrong",chapterNumber=1,verseId=999),target(27,19,verified=false))
            val result=resolveReferenceCardText(source,"RST",targets)
            assertEquals(listOf(listOf("Gen.16.7","Gen.19.27")),batches)
            assertEquals(listOf(book.slug to 16,book.slug to 19),chapters)
            assertEquals(chapter().verses.single().plainText,result["Gen.16.7"])
            assertEquals("Авраам встал рано утром",result["Gen.19.27"])
            assertTrue(targets.all { !it.versification.verified })
            assertFalse(referenceCopyText(ReferenceGroup("legacy_quote","raw",targets),edition.name,result).contains("UNTRUSTED"))
        } finally { api.close() }
        Unit
    }

    @Test fun unknownSemanticWithExactAvailableTextOpensExistingPassageReader() {
        val api=BibleApiClient();val calls=mutableListOf<String>()
        val source=object:BibleContentSource by api {
            override suspend fun getVerseLocations(code:String,references:List<String>):List<VerseLocation> {
                calls+=references
                return listOf(VerseLocation(1607,"Gen.16.7",book.slug,16,7))
            }
            override suspend fun getChapter(code:String,slug:String,number:Int):BibleChapter=chapter()
            override suspend fun getBooks(code:String):List<BibleBook> = listOf(book)
        }
        var opened by mutableStateOf(false)
        try {
            compose.setContent { BibleDesktopTheme {
                ReferencePassageCards("ru",chapter(),listOf(ReferenceGroup("legacy_quote","raw",listOf(target(verified=false)))),source){opened=true}
                if(opened)TemporaryStudyPassage("ru","RST",listOf(target(verified=false)),source){opened=false}
            } }
            compose.waitUntil(10_000){compose.onAllNodesWithTag("reference-label-Gen.16.7").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithTag("reference-label-Gen.16.7").performClick()
            compose.waitUntil(10_000){compose.onAllNodesWithTag("verse-7").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithTag("verse-7").assertIsDisplayed()
            compose.runOnIdle { assertEquals(listOf("Gen.16.7","Gen.16.7"),calls) }
            compose.onNodeWithText("Нумерация",substring=true).assertDoesNotExist()
        } finally { api.close() }
    }

    @Test fun mismatchedActualVerseIdNeverMakesUnknownCachedTextAvailable()=runBlocking {
        val api=BibleApiClient()
        val item=target(verified=false)
        val source=object:BibleContentSource by api {
            override suspend fun getVerseLocations(code:String,references:List<String>)=listOf(VerseLocation(999,"Gen.16.7",book.slug,16,7))
            override suspend fun getChapter(code:String,slug:String,number:Int)=chapter()
        }
        try {
            val result=resolveReferenceCardText(source,"RST",listOf(item))
            assertTrue(result.isEmpty())
            assertFalse(referenceGroupAvailable(ReferenceGroup("legacy_quote","raw",listOf(item)),result))
            assertFalse(item.versification.verified)
            assertFalse(referenceCopyText(ReferenceGroup("legacy_quote","raw",listOf(item)),edition.name,result).contains("UNTRUSTED"))
        } finally { api.close() }
        Unit
    }
}
