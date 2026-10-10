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
    private lateinit var temporaryChapter:BibleChapter
    private var showing by mutableStateOf(true)
    private fun mount() = runBlocking {
        val store = OfflineStore(File(context.cacheDir, "windows-test-${UUID.randomUUID()}"))
        BundledBible.install(context, store)
        val noNetwork = object : BibleContentSource by api {
            override suspend fun getChapter(translationCode: String, bookSlug: String, chapterNumber: Int): BibleChapter = error("Unexpected network")
        }
        val client = OfflineContentRepository(noNetwork, store, { false })
        val chapter = client.getChapter(BundledBible.code, "acts", 5)
        temporaryChapter=client.getChapter(BundledBible.code,"acts",8)
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

    @Test fun translationCommandResolvesActiveVerseAcrossDifferentModuleChapterBoundaries() {
        val first = TranslationSummary("SOURCE", "Source edition", language = LanguageSummary("en", "English"))
        val other = first.copy(code = "TARGET", name = "Target edition")
        val text = "Actual fixture verse body. ".repeat(100)
        val source = BibleChapter(first, BibleBook("source-gen", "Genesis", chaptersCount = 1), ChapterSummary(1, 2),
            listOf(BibleVerse(11, 1, "Gen.1.1", text, text), BibleVerse(12, 2, "Gen.1.2", text, text)))
        val target = BibleChapter(other, BibleBook("target-gen", "Genesis", chaptersCount = 8), ChapterSummary(8, 1),
            listOf(BibleVerse(22, 2, "Gen.1.2", text, text)))
        val requests = mutableListOf<List<String>>()
        val chapterRequests = mutableListOf<Int>()
        val client = object : BibleContentSource by api {
            override suspend fun getVerseLocations(translationCode: String, osis: List<String>): List<VerseLocation> {
                assertEquals("TARGET", translationCode)
                requests += osis
                return osis.map { ref -> if (ref == "Gen.1.2") VerseLocation(22, ref, "target-gen", 8, 2)
                    else VerseLocation(21, ref, "target-gen", 6, 1) }
            }
            override suspend fun getChapter(translationCode: String, bookSlug: String, chapterNumber: Int): BibleChapter {
                assertEquals("TARGET", translationCode); assertEquals("target-gen", bookSlug)
                chapterRequests += chapterNumber
                return when (chapterNumber) {
                    8 -> target
                    6 -> target.copy(chapter = ChapterSummary(6, 1), verses = listOf(BibleVerse(21, 1, "Gen.1.1", text, text)))
                    else -> error("No neighboring fixture chapter")
                }
            }
        }
        preferences.edit().putBoolean("sync", false).putBoolean("open1", false).commit()
        compose.setContent { BibleDesktopTheme { ReaderWindows("ru", source, first.code, listOf(first, other), client,
            19f, 2, Modifier.fillMaxSize(), emptySet(), { _, _ -> }, { _, _ -> }, { _, _ -> }, null,
            { _, _, _, _ -> }, { commands = it }, preferencesName) } }
        compose.waitUntil(10_000) { commands != null && JSONArray(preferences.getString("places", "[]")).length() == 2 && place(0).getInt("verse") == 2 }
        compose.runOnIdle { commands!!.translation(other.code) }
        compose.waitUntil(10_000) { place(0).getString("code") == other.code && place(0).getInt("chapter") == 8 }
        assertEquals("target-gen", place(0).getString("book"))
        assertEquals(2, place(0).getInt("verse"))
        assertEquals(listOf(listOf("Gen.1.2")), requests)
        assertEquals(listOf(8), chapterRequests)
        compose.runOnIdle { assertEquals(target, commands!!.source()) }
    }

    @Test fun resolverRejectsWrongChapterOrVerseIdentityEvenWhenStoredReferenceMatches() = runBlocking {
        val translation = TranslationSummary("SOURCE", "Source", language = LanguageSummary("en", "English"))
        val source = BibleChapter(translation, BibleBook("gen", "Genesis", chaptersCount = 1), ChapterSummary(1, 1),
            listOf(BibleVerse(1, 2, "Gen.1.2", "Source", "Source")))
        val correct = source.copy(translation = translation.copy(code = "TARGET"), book = source.book.copy(slug = "target-gen", chaptersCount = 8),
            chapter = ChapterSummary(8, 1), verses = listOf(source.verses.single().copy(id = 22)))
        val invalid = listOf(correct.copy(translation = translation), correct.copy(book = source.book),
            correct.copy(chapter = ChapterSummary(6, 1)), correct.copy(verses = listOf(correct.verses.single().copy(id = 23))),
            correct.copy(verses = listOf(correct.verses.single().copy(number = 1))),
            correct.copy(verses = listOf(correct.verses.single().copy(osisRef = "Gen.1.1"))),
            correct.copy(verses = listOf(correct.verses.single().copy(plainText = " "))))
        for (chapter in invalid) {
            val client = object : BibleContentSource by api {
                override suspend fun getVerseLocations(translationCode: String, osis: List<String>) = listOf(VerseLocation(22, "Gen.1.2", "target-gen", 8, 2))
                override suspend fun getChapter(translationCode: String, bookSlug: String, chapterNumber: Int) = chapter
            }
            assertTrue("Wrong published identity must be rejected: $chapter", runCatching { resolveWindowVerse(source, "TARGET", "Gen.1.2", client) }.isFailure)
        }
    }

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
    @Test fun temporaryAssignmentPreservesOriginalPlacesSyncActiveClosedPaneAndDivider() {
        mount()
        compose.onNodeWithTag("windows-sync").performClick()
        go(1,"6:3")
        compose.waitUntil(15000){place(1).getInt("chapter")==6&&place(1).getInt("verse")==3}
        compose.onNodeWithTag("windows-slider").performSemanticsAction(SemanticsActions.SetProgress){it(.65f)}
        compose.onNodeWithTag("windows-close-0").performClick()
        compose.waitForIdle()
        val original=preferences.getString("places",null)
        val active=preferences.getInt("active",-1)
        compose.runOnIdle{commands!!.preview(temporaryChapter,1,0)}
        compose.onNodeWithTag("windows-preview-return").assertExists()
        compose.runOnIdle{assertEquals(8,commands!!.source()!!.chapter.number)}
        compose.onNodeWithTag("comparison-pane-0").assertExists()
        compose.onNodeWithTag("windows-sync").assertIsNotEnabled()
        compose.waitForIdle()
        assertEquals(original,preferences.getString("places",null))
        assertFalse(preferences.getBoolean("open0",true))
        compose.onNodeWithTag("windows-preview-return").performClick()
        compose.waitUntil(15000){compose.onAllNodesWithTag("windows-preview-return").fetchSemanticsNodes().isEmpty()}
        compose.onNodeWithTag("comparison-pane-0").assertDoesNotExist()
        assertEquals(original,preferences.getString("places",null))
        assertEquals(active,preferences.getInt("active",-1))
        compose.runOnIdle{assertEquals(6,commands!!.source()!!.chapter.number)}
        assertFalse(preferences.getBoolean("sync",true))
        assertEquals(.65f,preferences.getFloat("ratio",0f),.01f)
    }

}
