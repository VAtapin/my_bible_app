package com.bibledesktop.myapp

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.bibledesktop.myapp.ui.study.VerseStudyDialog
import com.bibledesktop.myapp.ui.theme.BibleDesktopTheme
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
        val chapter = runBlocking {
            // Import changes catalogue order; not every new module has tokens for this verse.
            val translation = client.getTranslations("ru").first { it.code == "BQ_RUSSIAN_RST_STRONG" }
            val book = client.getBooks(translation.code).first { it.canonicalBook?.osisCode == "John" }
            client.getChapter(translation.code, book.slug, 3)
        }
        val verse = chapter.verses.first { it.number == 16 }
        val expected = runBlocking { client.getCrossReferences(verse.id, chapter.translation.code).references.first().target }
        var opened: ReferenceTarget? = null
        compose.setContent { BibleDesktopTheme { VerseStudyDialog("ru", chapter, verse, client, onOpen = { opened = it }, onClose = {}) } }
        compose.waitUntil(30_000) { compose.onAllNodesWithTag("reference-${expected.osisRef}").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("reference-${expected.osisRef}").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(expected.osisRef, opened?.osisRef) }
        capture("native-study")
        compose.waitUntil(30_000) { compose.onAllNodesWithText("G25").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("G25").performScrollTo().performClick()
        compose.waitUntil(30_000) { compose.onAllNodes(hasText("ἀγαπάω", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(hasText("ἀγαπάω", substring = true))[0].performScrollTo().assertIsDisplayed()
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
