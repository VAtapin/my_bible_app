package com.bibledesktop.myapp

import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.bibledesktop.myapp.data.*
import com.bibledesktop.myapp.ui.bible.*
import com.bibledesktop.myapp.ui.more.BibleLibraryScreen
import com.bibledesktop.myapp.ui.theme.BibleDesktopTheme
import com.bibledesktop.shared.api.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.UUID

class BibleCatalogTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val root = File(context.cacheDir, "catalog-test-${UUID.randomUUID()}")
    private val wrapped = object : ContextWrapper(context) { override fun getNoBackupFilesDir() = root }
    private val api = BibleApiClient()
    private val editions = listOf(
        TranslationSummary("RU", "Русская Библия", language = LanguageSummary("ru", "Русский")),
        TranslationSummary("DE", "Deutsche Bibel", language = LanguageSummary("de", "Deutsch")),
        TranslationSummary("AM", "Amharic", language = LanguageSummary("am", "AM")),
        TranslationSummary("NEW", "New language", language = LanguageSummary("unseen", "Unseen language")))
    @Before fun before() { check(context.packageName.endsWith(".debug")); check(root.mkdirs()) }
    @After fun after() { api.close(); root.walkBottomUp().forEach { check(it.delete()) } }

    @Test fun groupsLanguagesAndSearchAreIndependentAndUnknownLanguagesStayVisible() {
        assertEquals("slavic", translationGroup(editions[0]))
        assertEquals("afro", translationGroup(editions[2]))
        assertEquals("other", translationGroup(editions[3]))
        assertEquals(listOf(editions[1]), matchingTranslations(editions, "deutsche deutsch", "germanic", "de"))
        assertEquals(emptyList<TranslationSummary>(), matchingTranslations(editions, "", "slavic", "de"))
        assertEquals(listOf(editions[3]), matchingTranslations(editions, "unseen", "other", ""))
    }
    @Test fun readingNeverFetchesOrRefreshesBibleTextEvenWhenOnline() = runBlocking {
        var calls = 0
        val remote = object : BibleContentSource by api {
            override suspend fun getChapter(translationCode: String, bookSlug: String, chapterNumber: Int): BibleChapter { calls++; error("Network forbidden") }
            override suspend fun getBooks(translationCode: String): List<BibleBook> { calls++; error("Network forbidden") }
        }
        val store = OfflineStore(wrapped)
        val book = BibleBook("john", "Иоанна", chaptersCount = 1)
        val chapter = BibleChapter(editions[0], book, ChapterSummary(1, 1), listOf(BibleVerse(1, 1, "John.1.1", "text", "text")))
        val repository = OfflineContentRepository(remote, store, canRefresh = { true })
        assertTrue(runCatching { repository.getBooks("RU") }.exceptionOrNull() is BibleNotInstalled)
        assertTrue(runCatching { repository.getChapter("RU", "john", 1) }.exceptionOrNull() is BibleNotInstalled)
        store.write("books:RU", ListSerializer(BibleBook.serializer()), listOf(book))
        store.write(chapterKey("RU", "john", 1), BibleChapter.serializer(), chapter)
        assertEquals(chapter, repository.getChapter("RU", "john", 1))
        assertEquals(listOf(book), repository.getBooks("RU"))
        assertTrue(runCatching { repository.getChapter("DE", "john", 1) }.isFailure)
        assertEquals(0, calls)
        repository.close()
    }
    @Test fun installedListSurvivesRestartAndDoesNotDependOnRemoteCatalogue() = runBlocking {
        val store = OfflineStore(wrapped)
        val book = BibleBook("one", "One", chaptersCount = 3)
        store.write(biblePackageKey("RU"), BiblePackage.serializer(), BiblePackage(editions[0], listOf(book), done = 3, complete = true))
        store.write(biblePackageKey("DE"), BiblePackage.serializer(), BiblePackage(editions[1], listOf(book), done = 1))
        store.write(biblePackageKey("AM"), BiblePackage.serializer(), BiblePackage(editions[2], listOf(book), done = 2, unavailable = listOf("One 3")))
        val reopened = OfflineContentRepository(api, OfflineStore(wrapped))
        assertEquals(setOf("RU", "AM"), reopened.installedTranslations().map { it.code }.toSet())
        assertFalse(store.biblePackages().single { it.translation.code == "AM" }.complete)
    }
    @Test fun emptyReaderOffersInstallationInsteadOfInfiniteLoadingOrRemoteRead() {
        var opened = false
        compose.setContent { BibleDesktopTheme { BibleReader("ru", emptyList(), api, {}, onDownloads = { opened = true }) } }
        compose.onNodeWithTag("reader-install").performClick()
        assertTrue(opened)
    }
    @Test fun catalogHasSearchableGroupThenLanguageFiltersAndNoFixedLanguageChips() {
        val fixture = object : BibleContentSource by api { override suspend fun getTranslations(language: String?) = editions }
        compose.setContent { CompositionLocalProvider(LocalContext provides wrapped) { BibleDesktopTheme { BibleLibraryScreen("ru", fixture, onBack = {}) } } }
        compose.onNodeWithTag("library-catalog").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("install-RU").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("catalog-group").performClick()
        compose.onNodeWithTag("filter-slavic").performClick()
        compose.onNodeWithTag("catalog-language").performClick()
        compose.onNodeWithTag("filter-search").performTextInput("Русский")
        compose.onNodeWithTag("filter-ru").performClick()
        compose.onNodeWithTag("install-RU").assertExists()
        compose.onNodeWithTag("install-DE").assertDoesNotExist()
        compose.onNodeWithTag("library-installed").performClick()
        compose.onNodeWithTag("library-add").assertExists()
    }
    @Test fun fullRealApiCatalogueIsNotTheEightEditionDefaultList() = runBlocking {
        val catalog = api.getTranslations()
        assertTrue(catalog.size > 100)
        assertTrue(catalog.map { it.language.code }.distinct().size > 100)
        assertTrue(catalog.any { it.code.startsWith("BQ_MYBIBLE_") })
    }
    @Test fun newImportedEditionInstallsEveryAvailableChapterAndReadsAfterRestartOffline() = runBlocking {
        val code = "BQ_MYBIBLE_TZOSA_AFABB3AC35BC"
        val store = OfflineStore(wrapped)
        assertTrue(BibleDownloadEngine(api, store).download(code) { pack ->
            if (pack.done % 50 == 0) android.util.Log.i("BibleCatalogTest", "New edition ${pack.done}/${pack.total}")
        })
        val pack = store.biblePackages().single()
        assertEquals(code, pack.translation.code)
        assertEquals(27, pack.books.size)
        assertEquals(260, pack.total)
        assertTrue(pack.isInstalled)
        val noNetwork = object : BibleContentSource by api {
            override suspend fun getBooks(translationCode: String): List<BibleBook> = error("Network forbidden")
            override suspend fun getChapter(translationCode: String, bookSlug: String, chapterNumber: Int): BibleChapter = error("Network forbidden")
        }
        val reopened = OfflineContentRepository(noNetwork, OfflineStore(wrapped), canRefresh = { true })
        var saved = 0
        for (book in reopened.getBooks(code)) for (number in 1..book.chaptersCount) {
            if ("${book.name} $number" !in pack.unavailable) {
                assertTrue(reopened.getChapter(code, book.slug, number).verses.isNotEmpty()); saved++
            }
        }
        assertEquals(pack.done, saved)
        assertEquals(code, reopened.installedTranslations().single().code)
    }
}
