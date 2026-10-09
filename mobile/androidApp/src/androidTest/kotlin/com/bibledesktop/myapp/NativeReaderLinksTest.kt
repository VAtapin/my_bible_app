package com.bibledesktop.myapp

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bibledesktop.myapp.data.ReaderLink
import com.bibledesktop.myapp.data.OfflineStore
import com.bibledesktop.shared.api.isInstalled
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/** Real cold VIEW launch/API and warm Android intent delivery, only in the debug package. */
@RunWith(AndroidJUnit4::class)
class NativeReaderLinksTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val preferences get() = context.getSharedPreferences("bible-desktop-native-profile", Context.MODE_PRIVATE)
    private fun intent(book: String = "john", chapter: Int = 3, verse: Int = 16) = Intent(
        Intent.ACTION_VIEW, Uri.parse(ReaderLink("BQ_RUSSIAN_RST_STRONG", book, chapter, verse).onlineUrl()),
        context, MainActivity::class.java,
    )
    private fun withProfile(test: () -> Unit) {
        check(context.packageName == "com.bibledesktop.myapp.debug")
        org.junit.Assume.assumeTrue("Install the Russian edition before link integration tests", kotlinx.coroutines.runBlocking {
            OfflineStore(context).biblePackages().any { it.translation.code == "BQ_RUSSIAN_RST_STRONG" && it.isInstalled }
        })
        val keys = listOf("setupComplete", "uiLanguage", "lastTranslation", "lastBookSlug", "lastChapter", "lastVerse")
        val original = keys.associateWith { preferences.all[it] }
        check(preferences.edit().putBoolean("setupComplete", false).putString("uiLanguage", "ru")
            .putString("lastTranslation", "BQ_RUSSIAN_RST_STRONG").putString("lastBookSlug", "genesis")
            .putInt("lastChapter", 18).putInt("lastVerse", 2).commit())
        try { test() } finally {
            val editor = preferences.edit()
            original.forEach { (key, value) -> when(value) {
                is String -> editor.putString(key, value)
                is Int -> editor.putInt(key, value)
                is Boolean -> editor.putBoolean(key, value)
                null -> editor.remove(key)
            } }
            check(editor.commit())
        }
    }

    @Test fun coldLinkBeforeSetupHighlightsExactVerseAndReturnsToWelcome() = withProfile {
        ActivityScenario.launch<MainActivity>(intent()).use {
            waitForVerse(16)
            compose.onNodeWithTag("verse-16").assertIsSelected().assertIsDisplayed()
            assertEquals("john", preferences.getString("lastBookSlug", null))
            assertEquals(16, preferences.getInt("lastVerse", 0))
            assertFalse(preferences.getBoolean("setupComplete", false))
            compose.onNodeWithContentDescription("На главную").performClick()
            compose.onNodeWithText("Добро пожаловать").performScrollTo().assertIsDisplayed()
        }
    }

    @Test fun linkedReaderKeepsNewChapterAcrossRecreation() = withProfile {
        ActivityScenario.launch<MainActivity>(intent()).use { scenario ->
            waitForVerse(16)
            compose.onNodeWithTag("verse-16").assertIsSelected()
            compose.onNodeWithContentDescription("Следующая глава").performClick()
            compose.waitUntil(30_000) { preferences.getInt("lastChapter", 0) == 4 }
            waitForVerse(1)
            scenario.recreate()
            waitForVerse(1)
            compose.onNodeWithTag("reader-choose-chapter").assertTextContains("Глава 4", substring = true)
            assertEquals(4, preferences.getInt("lastChapter", 0))
        }
    }

    @Test fun warmNewLinkUpdatesExistingReaderWithoutAnotherSetup() = withProfile {
        val initial = intent()
        ActivityScenario.launch<MainActivity>(initial).use { scenario ->
            waitForVerse(16)
            var activity: MainActivity? = null
            scenario.onActivity { activity = it; it.startActivity(intent("luke", 4, 16).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)) }
            try {
                compose.waitUntil(30_000) { preferences.getString("lastBookSlug", null) == "luke" }
                waitForVerse(16)
                compose.onNodeWithTag("verse-16").assertIsSelected().assertIsDisplayed()
                assertEquals(4, preferences.getInt("lastChapter", 0))
            } finally {
                // ActivityScenario filters lifecycle events by the original VIEW intent.
                // After testing real onNewIntent delivery, restore its harness identity for close().
                instrumentation.runOnMainSync { activity?.intent = initial }
            }
        }
    }

    @Test fun unavailableVerseLeavesOldReadingPositionUnchanged() = withProfile {
        ActivityScenario.launch<MainActivity>(intent(verse = 999)).use {
            val russian = Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag("ru")) }
            val error = context.createConfigurationContext(russian).getString(R.string.reader_link_unavailable)
            compose.waitUntil(30_000) { compose.onAllNodes(hasText(error)).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(error).assertIsDisplayed()
            assertEquals("genesis", preferences.getString("lastBookSlug", null))
            assertEquals(18, preferences.getInt("lastChapter", 0))
            assertEquals(2, preferences.getInt("lastVerse", 0))
        }
    }

    private fun waitForVerse(number: Int) {
        compose.waitUntil(30_000) { compose.onAllNodesWithTag("verse-$number").fetchSemanticsNodes().isNotEmpty() }
    }
}
