package com.bibledesktop.myapp

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.bibledesktop.myapp.data.OfflineContentRepository
import com.bibledesktop.myapp.data.OfflineStore
import com.bibledesktop.myapp.ui.study.BooksScreen
import com.bibledesktop.myapp.ui.study.CommentaryPanel
import com.bibledesktop.myapp.ui.study.studyReadingText
import com.bibledesktop.myapp.ui.theme.BibleDesktopTheme
import com.bibledesktop.shared.api.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.UUID

/** Public production content, with isolated test storage and preferences. No user data is removed. */
class StudyBooksTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val api = BibleApiClient()
    @After fun close() { api.close() }

    @Test fun realBookOpensAndRestoresItsSection() {
        val book = runBlocking { api.getStudyBooks().data.first() }
        val section = runBlocking { api.getBookContents(book.id).sections.first() }
        val visible = mutableStateOf(true)
        val preferencesName = "study-ui-test-${UUID.randomUUID()}"
        compose.setContent { BibleDesktopTheme { if (visible.value) BooksScreen("ru", api, {}, preferencesName) } }
        compose.waitUntil(30_000) { compose.onAllNodesWithTag("study-book-${book.id}").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("study-book-${book.id}").performScrollTo().performClick()
        compose.waitUntil(30_000) { compose.onAllNodesWithTag("study-section-${section.id}").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("study-section-${section.id}").performScrollTo().performClick()
        compose.waitUntil(30_000) { compose.onAllNodesWithTag("study-book-body").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("study-book-body").assertIsDisplayed()
        compose.runOnIdle { visible.value = false }; compose.waitForIdle()
        compose.runOnIdle { visible.value = true }
        compose.waitUntil(30_000) { compose.onAllNodesWithTag("study-book-body").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("study-book-body").assertIsDisplayed()
    }

    @Test fun openedBooksAndCommentariesSurviveRepositoryRestartWithoutNetwork() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = OfflineStore(File(context.cacheDir, "study-offline-test-${UUID.randomUUID()}"))
        val online = OfflineContentRepository(api, store, { true })
        val catalog = online.getStudyBooks()
        val book = catalog.data.first()
        val contents = online.getBookContents(book.id)
        val article = online.getBookSection(book.id, contents.sections.first().id)
        val modules = online.getCommentaryModules()
        val comments = online.getCommentaries("john", 3, listOf("OPTINA_COMMENTARIES"))
        val noNetwork = object : BibleContentSource by api {
            override suspend fun getStudyBooks(query: String, offset: Int): StudyBookPage = error("Unexpected network")
            override suspend fun getBookContents(book: Long, offset: Int): BookContents = error("Unexpected network")
            override suspend fun getBookSection(book: Long, section: Long): StudySection = error("Unexpected network")
            override suspend fun getCommentaryModules(): List<CommentaryModule> = error("Unexpected network")
            override suspend fun getCommentaries(book: String, chapter: Int?, modules: List<String>, offset: Int): CommentaryPage = error("Unexpected network")
        }
        val offline = OfflineContentRepository(noNetwork, store, { false })
        assertEquals(catalog, offline.getStudyBooks())
        assertEquals(contents, offline.getBookContents(book.id))
        assertEquals(article, offline.getBookSection(book.id, article.id))
        assertEquals(modules, offline.getCommentaryModules())
        assertEquals(comments, offline.getCommentaries("john", 3, listOf("OPTINA_COMMENTARIES")))
    }

    @Test fun realRangeCommentaryIsShownOnceAndExpandsInFull() {
        val chapter = runBlocking { api.getChapter("BQ_RUSSIAN_RST_STRONG", "john", 3) }
        val expected = runBlocking { api.getCommentaries("john", 3, listOf("OPTINA_COMMENTARIES")).entries.first() }
        assertTrue(expected.verseFrom <= 2 && (expected.verseTo ?: 0) >= 3)
        val preferencesName = "study-range-test-${UUID.randomUUID()}"
        InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences(preferencesName, android.content.Context.MODE_PRIVATE)
            .edit().putStringSet("sources", setOf("OPTINA_COMMENTARIES")).commit()
        compose.setContent { BibleDesktopTheme { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            CommentaryPanel("ru", chapter, chapter.verses.first(), api, preferencesName = preferencesName)
        } } }
        compose.waitUntil(30_000) { compose.onAllNodesWithTag("commentary-${expected.id}").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("К диапазону стихов").performScrollTo().performClick()
        compose.onNode(hasSetTextAction() and hasText("Первый стих")).performScrollTo().performTextReplacement("2")
        compose.onNode(hasSetTextAction() and hasText("Последний стих")).performTextReplacement("3")
        compose.waitUntil(30_000) { compose.onAllNodesWithTag("commentary-${expected.id}").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithTag("commentary-${expected.id}").assertCountEquals(1)
        compose.onNode(hasText("Читать полностью") and hasAnyAncestor(hasTestTag("commentary-${expected.id}"))).performScrollTo().performClick()
        compose.onNodeWithText(studyReadingText(expected.body)).assertExists()
    }
}
