package com.bibledesktop.myapp

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.test.platform.app.InstrumentationRegistry
import com.bibledesktop.myapp.ui.study.SlavonicDictionaryScreen
import com.bibledesktop.myapp.ui.study.DictionariesScreen
import com.bibledesktop.myapp.ui.more.MoreScreen
import com.bibledesktop.myapp.ui.theme.BibleDesktopTheme
import com.bibledesktop.shared.api.DictionaryApi
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.UUID

/** Read-only real production API, isolated test favorites, no user profile deletion. */
class SlavonicDictionaryUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val api = DictionaryApi()
    private val preferences = "slavonic-ui-${UUID.randomUUID()}"
    private val abie = "b877ca545a7c1eeefed0914bbc87424ebe4cac55"
    @After fun close() { api.close() }
    private fun ready() { compose.waitUntil(30_000) { compose.onAllNodesWithTag("slavonic-count").fetchSemanticsNodes().isNotEmpty() && compose.onAllNodesWithTag("slavonic-loading").fetchSemanticsNodes().isEmpty() } }

    @Test fun directEntryFromMoreOpensNativeCatalog() {
        val open = mutableStateOf(false)
        compose.setContent { BibleDesktopTheme {
            if (open.value) SlavonicDictionaryScreen("ru", { open.value = false }, api, preferences)
            else MoreScreen("ru", {}, {}, {}, {}, {}, {}, onSlavonicDictionary = { open.value = true })
        } }
        compose.onNodeWithText("Церковнославянский словарь").performScrollTo().performClick()
        ready(); compose.onNodeWithTag("slavonic-count").assertTextEquals("Слов: 22908")
    }

    @Test fun selectedArticleFromExistingDictionaryLibraryOpensTheNativeLexicalView() {
        compose.setContent { BibleDesktopTheme {
            DictionariesScreen("ru", {}, initialModule = "AZBYKA_CU_FULL", initialEntry = abie, initialQuery = "абие")
        } }
        try { compose.waitUntil(30_000) { compose.onAllNodesWithTag("slavonic-definition").fetchSemanticsNodes().isNotEmpty() } }
        catch (error: Exception) { android.util.Log.e("SlavonicTest", compose.onRoot(useUnmergedTree = true).printToString()); throw error }
        compose.onNodeWithTag("slavonic-definition").assertTextEquals("Скоро, тотчас, вдруг; кстати, благоприятно")
        compose.onNodeWithTag("slavonic-close").performClick(); ready()
        compose.onNodeWithTag("slavonic-count").assertTextEquals("Слов: 1")
    }

    @Test fun actualCatalogPaginatesAndFullArticleReturnsToSameList() {
        compose.setContent { BibleDesktopTheme { SlavonicDictionaryScreen("ru", {}, api, preferences) } }
        ready()
        compose.onNodeWithTag("slavonic-count").assertTextEquals("Слов: 22908")
        compose.onNodeWithTag("slavonic-letter-1").performClick()
        ready(); compose.onNodeWithTag("slavonic-count").assertTextEquals("Слов: 466")
        compose.onNodeWithTag("slavonic-list").performScrollToNode(hasTestTag("slavonic-more"))
        compose.onNodeWithTag("slavonic-more").performClick()
        compose.waitUntil(30_000) { compose.onAllNodes(hasTestTag("slavonic-list") and hasStateDescription("Слов: 60 / 466")).fetchSemanticsNodes().isNotEmpty() }
        // This actual word is on the second page; absence would expose a pagination failure.
        val second = runBlocking { api.slavonicSearch(letter = 1, offset = 30).data.first() }
        compose.onNodeWithTag("slavonic-list").performScrollToNode(hasTestTag("slavonic-word-${second.key}"))
        compose.onNodeWithTag("slavonic-word-${second.key}").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("slavonic-definition").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("slavonic-definition").assertTextEquals(second.definition)
        compose.onNodeWithTag("slavonic-close").performClick()
        compose.onNodeWithTag("slavonic-word-${second.key}").assertIsDisplayed()
    }

    @Test fun actualWordMeaningAndFavoritesRemainSeparateFromCatalog() {
        compose.setContent { BibleDesktopTheme { SlavonicDictionaryScreen("ru", {}, api, preferences) } }
        ready(); compose.onNodeWithTag("slavonic-tab-1").performClick()
        compose.onNodeWithTag("slavonic-query").performTextReplacement("абие")
        compose.onNodeWithTag("slavonic-search").performClick(); ready()
        compose.onNodeWithTag("slavonic-count").assertTextEquals("Слов: 1")
        compose.onNodeWithTag("slavonic-word-$abie").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("slavonic-definition").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("slavonic-save").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("★ Убрать из избранного").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("slavonic-close").performClick(); compose.onNodeWithTag("slavonic-tab-2").performClick()
        compose.onNodeWithText("А҆́бїе").assertIsDisplayed()
        compose.onNodeWithTag("slavonic-tab-1").performClick()
        compose.onNodeWithTag("slavonic-query").performTextReplacement("святая вода")
        compose.onNodeWithTag("slavonic-meaning").performClick(); ready()
        compose.onNodeWithTag("slavonic-count").assertTextEquals("Слов: 1")
        compose.onNodeWithText("А̑гїа́сма").assertIsDisplayed()
        compose.onNodeWithTag("slavonic-tab-0").performClick(); ready()
        compose.onNodeWithTag("slavonic-count").assertTextEquals("Слов: 22908")
        compose.onNodeWithTag("slavonic-query").assertDoesNotExist()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val image = instrumentation.uiAutomation.takeScreenshot()
        File(instrumentation.targetContext.getExternalFilesDir(null), "slavonic-catalog.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
    }

    @Test fun sectionAndArticleRestoreWithoutLosingSelectedLetter() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { BibleDesktopTheme { SlavonicDictionaryScreen("ru", {}, api, preferences) } }
        ready(); compose.onNodeWithTag("slavonic-letter-36").performClick(); ready()
        val first = runBlocking { api.slavonicSearch(letter = 36).data.first() }
        compose.onNodeWithTag("slavonic-count").assertTextEquals("Слов: 16")
        compose.onNodeWithTag("slavonic-list").performScrollToNode(hasTestTag("slavonic-word-${first.key}"))
        compose.onNodeWithTag("slavonic-word-${first.key}").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("slavonic-definition").fetchSemanticsNodes().isNotEmpty() }
        restoration.emulateSavedInstanceStateRestore()
        compose.waitUntil(30_000) { compose.onAllNodesWithTag("slavonic-definition").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("slavonic-definition").assertTextEquals(first.definition)
        compose.onNodeWithTag("slavonic-close").performClick(); ready()
        compose.onNodeWithTag("slavonic-count").assertTextEquals("Слов: 16")
    }
}
