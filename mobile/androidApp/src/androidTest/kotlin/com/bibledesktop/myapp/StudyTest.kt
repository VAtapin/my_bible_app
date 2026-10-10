package com.bibledesktop.myapp

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.bibledesktop.myapp.ui.study.VerseStudyDialog
import com.bibledesktop.myapp.ui.study.LocalTemporaryWindowAssignment
import com.bibledesktop.myapp.ui.theme.BibleDesktopTheme
import com.bibledesktop.myapp.data.BundledBible
import com.bibledesktop.myapp.data.OfflineStore
import com.bibledesktop.myapp.data.OfflineContentRepository
import com.bibledesktop.shared.api.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

/** Read-only integration with real BibleDesktop study data. */
class StudyTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val client = BibleApiClient()
    @After fun close() { client.close() }

    @Test fun realParallelPlacesAndStrongDictionaryLoadAndNavigate() {
        val store = OfflineStore(InstrumentationRegistry.getInstrumentation().targetContext)
        runBlocking { BundledBible.install(InstrumentationRegistry.getInstrumentation().targetContext, store) }
        // Follow the application's installed-first resolver; public study requests remain real HTTP.
        val client = OfflineContentRepository(this.client, store)
        val chapter = runBlocking {
            // Import changes catalogue order; not every new module has tokens for this verse.
            val translation = client.getTranslations("ru").first { it.code == "BQ_RUSSIAN_RST_STRONG" }
            val book = client.getBooks(translation.code).first { it.canonicalBook?.osisCode == "John" }
            client.getChapter(translation.code, book.slug, 3)
        }
        val verse = chapter.verses.first { it.number == 16 }
        val expected = runBlocking { client.getCrossReferences(verse.id, chapter.translation.code).references.first().target }
        val resolved = runBlocking {
            val location = client.getVerseLocations(chapter.translation.code, listOf(expected.osisRef)).single()
            val actual = client.getChapter(chapter.translation.code, location.book, location.chapter)
            assertEquals(chapter.translation.code, actual.translation.code)
            assertEquals(location.book, actual.book.slug)
            assertEquals(location.chapter, actual.chapter.number)
            actual to actual.verses.single { it.id == location.verseId && it.number == location.verse && it.osisRef == expected.osisRef && it.plainText.isNotBlank() }
        }
        val expectedEntry = runBlocking { client.getStrongEntry("G25", verse.id) }
        assertEquals("G25", expectedEntry.number)
        assertTrue(!expectedEntry.content.isNullOrBlank())
        var opened:Pair<BibleChapter,Int>? = null
        compose.setContent { BibleDesktopTheme { CompositionLocalProvider(LocalTemporaryWindowAssignment provides {actual,number,window->assertEquals(0,window);opened=actual to number}){VerseStudyDialog("ru", chapter, verse, client, onOpen = { error("Dialog must open its temporary passage first") }, onClose = {})} } }
        compose.waitUntil(30_000) { compose.onAllNodesWithTag("reference-${expected.osisRef}").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("reference-${expected.osisRef}").performScrollTo().performClick()
        compose.waitUntil(30_000){compose.onAllNodes(hasTestTag("verse-${resolved.second.number}") and isSelected() and hasAnyAncestor(isDialog())).fetchSemanticsNodes().isNotEmpty()}
        compose.onNode(hasTestTag("verse-${resolved.second.number}") and isSelected() and hasAnyAncestor(isDialog())).assertIsDisplayed()
        compose.onNode(hasText(resolved.second.plainText, substring=true) and hasAnyAncestor(isDialog())).assertIsDisplayed()
        compose.onNode(hasText("Окно 1") and isSelectable()).performClick()
        compose.onNode(hasText("Связанный отрывок") and hasClickAction() and isEnabled()).performClick()
        compose.runOnIdle {val actual=opened;assertNotNull(actual);assertEquals(chapter.translation.code,actual!!.first.translation.code);assertEquals(resolved.first.book.slug,actual.first.book.slug);assertEquals(resolved.first.chapter.number,actual.first.chapter.number);val selected=actual.first.verses.single{it.number==actual.second};assertEquals(resolved.second.id,selected.id);assertEquals(expected.osisRef,selected.osisRef)}
        capture("native-study")
        compose.waitUntil(30_000) { compose.onAllNodesWithText("G25").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("G25").performScrollTo().performClick()
        compose.waitUntil(30_000) { compose.onAllNodes(hasText("ἀγαπάω", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(hasText("ἀγαπάω", substring = true))[0].performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(com.bibledesktop.myapp.ui.reading.readingText(expectedEntry.content!!)).performScrollTo().assertIsDisplayed()
        capture("native-strong")
        compose.onNodeWithText("Закрыть").performScrollTo().assertIsDisplayed()
    }
    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "com.bibledesktop.myapp.debug")
        context.getExternalFilesDir(null)!!.resolve("$name.png").outputStream().use {
            compose.waitForIdle()
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()!!.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
