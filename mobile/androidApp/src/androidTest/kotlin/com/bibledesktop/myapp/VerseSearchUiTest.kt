package com.bibledesktop.myapp
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.bibledesktop.myapp.data.*
import com.bibledesktop.myapp.ui.bible.BibleSearchScreen
import com.bibledesktop.myapp.ui.theme.BibleDesktopTheme
import com.bibledesktop.shared.api.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.UUID
class VerseSearchUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun realOfflineSearchOpensExactVerseAndRestoresResults() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val directory=File(context.cacheDir,"search-ui-${UUID.randomUUID()}")
        val store=OfflineStore(File(directory,"content"))
        runBlocking { BundledBible.install(context,store) }
        runBlocking { LocalBibleSearch(store,File(directory,"index.sqlite")).prepare(BundledBible.code) }
        val api=BibleApiClient()
        val noNetwork=object: BibleContentSource by api {
            override suspend fun getChapter(translationCode:String,bookSlug:String,chapterNumber:Int):BibleChapter=error("Network forbidden")
        }
        val source=OfflineContentRepository(noNetwork,store,{false})
        val prefs="search-ui-${UUID.randomUUID()}"
        val visible=mutableStateOf(true)
        var opened:VerseSearchHit?=null
        compose.setContent { BibleDesktopTheme { if(visible.value)BibleSearchScreen("ru",source,BundledBible.code,{}, {opened=it},prefs,File(directory,"index.sqlite")) } }
        compose.onNodeWithTag("verse-search-query").performTextInput("Бог")
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Библия. Синодальный текст c номерами Стронга").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("verse-search-submit").performScrollTo().performClick()
        compose.waitUntil(45_000) { compose.onAllNodesWithTag("verse-search-count").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("search-hit-Gen.1.1").performScrollTo().performClick()
        assertEquals("Gen.1.1",opened?.reference)
        assertEquals(BundledBible.code,opened?.translation)
        compose.runOnIdle{visible.value=false};compose.waitForIdle();compose.runOnIdle{visible.value=true}
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("verse-search-count").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("verse-search-query").assertTextContains("Бог")
        compose.onNodeWithTag("search-hit-Gen.1.1").performScrollTo().assertIsDisplayed()
        source.close()
    }
    @Test fun editingPendingRestoredQueryDoesNotStartSearchWhenBackgroundPreparationFinishes(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val directory=File(context.cacheDir,"search-pending-ui-${UUID.randomUUID()}")
        val store=OfflineStore(File(directory,"content"))
        val file=File(directory,"index.sqlite")
        val edition=TranslationSummary("PENDING","Pending edition",language=LanguageSummary("ru","Русский"))
        val book=BibleBook("genesis","Бытие",chaptersCount=1,canonicalBook=CanonicalBookSummary("Gen","old"))
        val chapter=BibleChapter(edition,book,ChapterSummary(1,1),listOf(BibleVerse(1,1,"Gen.1.1","Новый текст","Новый текст")))
        runBlocking{
            store.write(chapterKey(edition.code,book.slug,1),BibleChapter.serializer(),chapter)
            store.write("books:${edition.code}",ListSerializer(BibleBook.serializer()),listOf(book))
            store.write(biblePackageKey(edition.code),BiblePackage.serializer(),BiblePackage(edition,listOf(book),done=1,complete=true))
        }
        val api=BibleApiClient()
        val source=OfflineContentRepository(api,store,{false})
        try{
            compose.setContent{BibleDesktopTheme{BibleSearchScreen("ru",source,edition.code,{}, {},"pending-${UUID.randomUUID()}",file,initialQuery="Старый")}}
            compose.waitUntil(10_000){compose.onAllNodesWithText(edition.name).fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithTag("verse-search-preparing").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("verse-search-submit").assertIsNotEnabled()
            assertFalse(file.exists())
            compose.onNodeWithTag("verse-search-query").performTextReplacement("Новый")
            runBlocking{assertTrue(LocalBibleSearch(store,file).prepare(edition.code))}
            compose.waitUntil(10_000){compose.onAllNodesWithTag("verse-search-preparing").fetchSemanticsNodes().isEmpty()}
            compose.onNodeWithTag("verse-search-count").assertDoesNotExist()
            compose.onNodeWithTag("verse-search-submit").performScrollTo().performClick()
            compose.waitUntil(10_000){compose.onAllNodesWithTag("verse-search-count").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithTag("search-hit-Gen.1.1").performScrollTo().assertIsDisplayed()
        }finally{source.close()}
    }
}
