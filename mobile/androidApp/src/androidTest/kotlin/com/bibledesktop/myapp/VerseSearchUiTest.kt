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
}
