package com.bibledesktop.myapp

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.bibledesktop.myapp.data.*
import com.bibledesktop.myapp.ui.bible.*
import com.bibledesktop.myapp.ui.theme.BibleDesktopTheme
import com.bibledesktop.shared.api.*
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.UUID

/** Real installed Acts, isolated window preferences; any network chapter request fails. */
class ReaderWindowsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferencesName = "reader-windows-test-${UUID.randomUUID()}"
    private val preferences get() = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    private val api = BibleApiClient()
    private var commands: WindowCommands? = null
    private var showing by mutableStateOf(true)
    private fun mount() = runBlocking {
        val store = OfflineStore(File(context.cacheDir, "windows-test-${UUID.randomUUID()}"))
        BundledBible.install(context, store)
        val noNetwork = object : BibleContentSource by api {
            override suspend fun getChapter(translationCode: String, bookSlug: String, chapterNumber: Int): BibleChapter = error("Unexpected network")
        }
        val client = OfflineContentRepository(noNetwork, store, { false })
        val chapter = client.getChapter(BundledBible.code, "acts", 5)
        compose.setContent { BibleDesktopTheme { if (showing) ReaderWindows("ru", chapter, chapter.translation.code, listOf(chapter.translation),
            client, 19f, 1, Modifier.fillMaxSize(), emptySet(), { _, _ -> }, { _, _ -> }, { _, _ -> }, null,
            { _, _, _, _ -> }, { commands = it }, preferencesName) } }
        compose.waitUntil(20_000) { JSONArray(preferences.getString("places", "[]")).length() == 2 && commands != null }
    }
    private fun place(id: Int) = JSONArray(preferences.getString("places", "[]")).getJSONObject(id)
    private fun go(id: Int, target: String) {
        compose.onNodeWithTag("windows-digital-$id").performClick()
        compose.onNodeWithTag("windows-position").performTextReplacement(target)
        compose.onNodeWithText("Перейти", useUnmergedTree = true).performClick()
    }
    @After fun close() { api.close() }

    @Test fun independentNavigationActiveCommandsCloseSwapDividerAndRestore() {
        mount()
        compose.onNodeWithTag("windows-sync").performClick()
        go(1, "6:3")
        compose.waitUntil(15_000) { place(1).getInt("chapter") == 6 && place(1).getInt("verse") == 3 }
        assertEquals(5, place(0).getInt("chapter"))
        compose.runOnIdle { commands!!.move(1) }
        compose.waitUntil(15_000) { place(1).getInt("chapter") == 7 }
        assertEquals(5, place(0).getInt("chapter"))
        compose.onNodeWithTag("windows-slider").performSemanticsAction(SemanticsActions.SetProgress) { assertTrue(it(.65f)) }
        assertEquals(.65f, preferences.getFloat("ratio", 0f), .01f)
        compose.onNodeWithTag("windows-close-1").performClick()
        compose.onNodeWithTag("comparison-pane-1").assertDoesNotExist()
        assertEquals(7, place(1).getInt("chapter"))
        compose.onNodeWithTag("windows-reopen-1").performClick()
        compose.onNodeWithTag("comparison-pane-1").assertExists()
        compose.onNodeWithTag("windows-swap").performClick()
        compose.waitUntil(10_000) { place(0).getInt("chapter") == 7 && place(1).getInt("chapter") == 5 }
        compose.runOnIdle { showing = false }
        compose.waitForIdle()
        compose.runOnIdle { showing = true }
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("comparison-list-1").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(7, place(0).getInt("chapter")); assertEquals(5, place(1).getInt("chapter"))
        assertFalse(preferences.getBoolean("sync", true))
        assertEquals(.65f, preferences.getFloat("ratio", 0f), .01f)
    }

    @Test fun synchronizationFollowsCanonicalVerseAndMissingTargetKeepsPlace() {
        mount()
        go(0, "6:3")
        compose.waitUntil(15_000) { place(0).getInt("chapter") == 6 && place(1).getInt("chapter") == 6 && place(1).getInt("verse") == 3 }
        go(0, "6:999")
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Не удалось открыть место. Повторите переход.").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(6, place(0).getInt("chapter")); assertEquals(3, place(0).getInt("verse"))
        assertEquals(6, place(1).getInt("chapter")); assertEquals(3, place(1).getInt("verse"))
    }
}
