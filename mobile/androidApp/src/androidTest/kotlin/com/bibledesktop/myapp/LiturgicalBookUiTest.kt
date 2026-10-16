package com.bibledesktop.myapp

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.bibledesktop.myapp.ui.daily.LiturgicalBookLibrary
import com.bibledesktop.myapp.ui.daily.PrayersScreen
import com.bibledesktop.myapp.ui.daily.LiturgicalReader
import com.bibledesktop.myapp.ui.theme.BibleDesktopTheme
import com.bibledesktop.myapp.data.NativeLiturgicalCache
import com.bibledesktop.myapp.data.OfflineStore
import com.bibledesktop.shared.api.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.UUID

/** Optional full-corpus acceptance: supply an actual API export to the debug app's private files. */
class LiturgicalBookUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val json=Json{ignoreUnknownKeys=true}
    private lateinit var version:LiturgicalWorkVersion
    private lateinit var summary:LiturgicalWorkSummary
    private lateinit var prayerWorks:List<LiturgicalWorkSummary>
    private lateinit var catalog:PrayerCatalog
    private lateinit var source:BibleContentSource
    @Before fun loadActualCorpus(){
        val file=File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir,"prayerbook-verification.json")
        Assume.assumeTrue("Provide isolated actual API export; see mobile/README.md",file.exists())
        val data=json.parseToJsonElement(file.readText()).jsonObject
        prayerWorks=json.decodeFromJsonElement(data.getValue("prayerWorks"));catalog=json.decodeFromJsonElement<PrayerCatalog>(data.getValue("prayers")).validatePrayerCatalog()
        version=json.decodeFromJsonElement(data.getValue("version"));summary=json.decodeFromJsonElement(data.getValue("summary"))
        version.validateLiturgicalVersion("de",summary.editions.single().code)
        assertEquals(1391,version.blocks.size);assertEquals(19,version.bookSections.size)
        source=object:BibleContentSource by BibleApiClient(){
            override suspend fun getLiturgicalWorks(collection:String)=when(collection){
                "prayer-books"->listOf(summary)
                "prayers"->prayerWorks
                ""->prayerWorks+summary
                else->error("Unexpected collection: $collection")
            }
            override suspend fun getPrayerCatalog(language:String)=catalog.also{require(language=="ru")}
            override suspend fun getLiturgicalWork(slug:String)=summary.also{require(slug==summary.slug)}
            override suspend fun getLiturgicalVersion(slug:String,language:String,edition:String?)=version.also{require(slug==summary.slug);it.validateLiturgicalVersion(language,edition)}
        }
    }
    private fun await(text:String){compose.waitUntil(20_000){compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}}
    @Test fun nativeLibraryOpensWholeGermanBookAndSectionJumpShowsExactSourceText(){
        compose.setContent{BibleDesktopTheme{LiturgicalBookLibrary("ru",source,{})}}
        await(summary.title);compose.onNodeWithText(summary.title).performClick();await("Оглавление")
        compose.onNodeWithText("Оглавление").performClick()
        val first=version.bookSections.first()
        await(first.title);compose.onNode(hasText(first.title) and hasClickAction()).performClick()
        val text=version.blocks.first{it.id==first.startBlockId}.text
        await(text);compose.onNodeWithText(text).assertIsDisplayed()
        assertEquals("de",version.language)
        compose.waitForIdle()
        val screenshot=compose.onRoot().captureToImage().asAndroidBitmap()
        File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir,"prayerbook-reading.png").outputStream().use{screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
        screenshot.recycle()
    }
    @Test fun actualGermanOnlyPrayersAppearWithoutSlavonicPairsAndInternalBookDoesNotBreakCatalogue(){
        compose.setContent{BibleDesktopTheme{PrayersScreen("ru",source,{})}}
        compose.waitUntil(20_000){compose.onAllNodesWithTag("prayer-edition-de").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("prayer-edition-de").performClick()
        compose.waitUntil(20_000){compose.onAllNodes(hasTestTag("prayer-source-count") and hasText(": 101",substring=true)).fetchSemanticsNodes().isNotEmpty()}
        val unit=prayerWorks.first{it.availableLanguages==listOf("de")}
        compose.onNodeWithTag("daily-content-list").performScrollToNode(hasText(unit.title))
        compose.onNodeWithText(unit.title).assertIsDisplayed()
        compose.onNodeWithTag("daily-content-list").performScrollToIndex(0)
        compose.onNodeWithText("Молитвословы (книги)").performClick()
        await(summary.title)
        compose.onNodeWithContentDescription("Назад").performClick()
        compose.onNodeWithTag("prayer-edition-de").assertIsSelected()
        compose.onNode(hasTestTag("prayer-source-count") and hasText(": 101",substring=true)).assertExists()
    }
    @Test fun sameLanguageEditionSelectorRequestsExactEditionAndOfflineCacheRetainsWholeBook(){
        var requested:String?=null
        val historical=summary.editions.single().copy(code="test-historic",title="Test historical edition")
        val meta=summary.copy(editions=summary.editions+historical)
        val selectable=object:BibleContentSource by source{
            override suspend fun getLiturgicalWork(slug:String)=meta
            override suspend fun getLiturgicalVersion(slug:String,language:String,edition:String?):LiturgicalWorkVersion{
                requested=edition
                return version.copy(edition=edition!!,editionTitle=if(edition==historical.code)historical.title else version.editionTitle)
            }
        }
        compose.setContent{BibleDesktopTheme{LiturgicalReader("ru",selectable,summary.slug,"de",{},{})}}
        await("Издание");compose.onNodeWithText("Издание").performClick()
        compose.onNodeWithText(historical.title).performClick();await(historical.title)
        assertEquals(historical.code,requested)
        runBlocking{
            val root=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"book-test-${UUID.randomUUID()}")
            var online=true
            val cache=NativeLiturgicalCache(source,OfflineStore(root),{online})
            assertEquals(prayerWorks+summary,cache.works(""))
            cache.works("prayer-books");cache.version(summary.slug,"de",version.edition)
            online=false
            val reopened=NativeLiturgicalCache(object:BibleContentSource by source{override suspend fun getLiturgicalVersion(slug:String,language:String,edition:String?):LiturgicalWorkVersion=error("Must use stored body")},OfflineStore(root),{false})
            assertEquals(version,reopened.version(summary.slug,"de",version.edition))
            assertEquals(prayerWorks+summary,reopened.works(""))
            assertEquals(listOf(summary),reopened.works("prayer-books"))
        }
    }
}
