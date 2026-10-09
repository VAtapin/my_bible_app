package com.bibledesktop.myapp

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.bibledesktop.myapp.data.*
import com.bibledesktop.myapp.ui.bible.ChapterReadingContent
import com.bibledesktop.myapp.ui.theme.BibleDesktopTheme
import com.bibledesktop.shared.api.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/** Real bundled Acts, isolated durable storage, no network permitted. */
class ContinuousReadingTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val api = BibleApiClient()
    private val loaded = ConcurrentHashMap.newKeySet<Int>()
    private val gate = CompletableDeferred<Unit>()
    private var source: BibleContentSource? = null
    private fun reading(blockPrevious: Boolean = false): Pair<BibleChapter, BibleContentSource> = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = OfflineStore(File(context.cacheDir, "continuous-test-${UUID.randomUUID()}"))
        BundledBible.install(context, store)
        val noNetwork = object : BibleContentSource by api {
            override suspend fun getChapter(translationCode: String, bookSlug: String, chapterNumber: Int): BibleChapter = error("Unexpected network")
        }
        val local = OfflineContentRepository(noNetwork, store, { false })
        val chapter = local.getChapter(BundledBible.code, "acts", 5)
        val traced = object : BibleContentSource by local {
            override suspend fun getChapter(translationCode: String, bookSlug: String, chapterNumber: Int): BibleChapter {
                if (blockPrevious && chapterNumber == 4) gate.await()
                return local.getChapter(translationCode, bookSlug, chapterNumber).also { loaded.add(chapterNumber) }
            }
        }
        source = traced
        chapter to traced
    }
    @After fun close() { gate.complete(Unit); source?.close(); api.close() }
    @Test fun actsFiveContinuesIntoSixByScrollingWithoutChapterButton() {
        val (chapter, client) = reading()
        var visibleChapter = 0
        compose.setContent { BibleDesktopTheme { Column(Modifier.fillMaxSize()) {
            ChapterReadingContent("ru", chapter, 19f, emptySet(), { _, _ -> }, { _, _ -> }, { _, _ -> },
                modifier = Modifier.weight(1f), initialVerse = chapter.verses.last().number, client = client,
                onVisiblePlace = { value, _, _, _ -> visibleChapter = value.chapter.number })
        } } }
        compose.waitUntil(20_000) { 6 in loaded }
        compose.onNodeWithTag("continuous-reader").performScrollToNode(hasTestTag("stream-chapter-6"))
        compose.onNodeWithTag("continuous-reader").performTouchInput { swipeUp() }
        compose.waitUntil(10_000) { visibleChapter >= 6 }
        assertTrue(visibleChapter >= 6)
    }
    @Test fun precedingChapterLoadsWithoutMovingTheVisibleAnchor() {
        val (chapter, client) = reading(blockPrevious = true)
        compose.setContent { BibleDesktopTheme { ChapterReadingContent("ru", chapter, 19f, emptySet(), { _, _ -> }, { _, _ -> }, { _, _ -> }, client = client) } }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("stream-chapter-5").fetchSemanticsNodes().isNotEmpty() }
        val before = compose.onNodeWithTag("stream-chapter-5").fetchSemanticsNode().boundsInRoot.top
        gate.complete(Unit)
        compose.waitUntil(20_000) { 4 in loaded }
        compose.waitForIdle()
        val after = compose.onNodeWithTag("stream-chapter-5").fetchSemanticsNode().boundsInRoot.top
        assertTrue("Visible anchor moved: $before -> $after", abs(after - before) < 2f)
    }
}
