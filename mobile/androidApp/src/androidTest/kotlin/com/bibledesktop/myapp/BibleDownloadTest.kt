package com.bibledesktop.myapp

import androidx.test.platform.app.InstrumentationRegistry
import android.content.ContextWrapper
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.bibledesktop.myapp.data.*
import com.bibledesktop.shared.api.*
import kotlinx.coroutines.*
import kotlinx.serialization.builtins.ListSerializer
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.io.IOException
import java.util.UUID

class BibleDownloadTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val root = File(context.cacheDir, "bible-download-test-${UUID.randomUUID()}")
    @Before fun before() { check(context.packageName == "com.bibledesktop.myapp.debug"); check(root.mkdirs()) }
    @After fun after() { root.walkBottomUp().forEach { check(it.delete()) } }

    private class Fixture : BibleContentSource by BibleApiClient() {
        val translations = listOf(TranslationSummary("TEST", "Test", language = LanguageSummary("ru", "Russian")))
        val books = listOf(BibleBook("one", "One", chaptersCount = 2, canonicalBook = CanonicalBookSummary("Gen", "old")),
            BibleBook("two", "Two", chaptersCount = 1, canonicalBook = CanonicalBookSummary("Rev", "new")))
        var calls = 0
        var failAt = 0
        var wrong = false
        var empty = false
        var blankVerse = false
        override suspend fun getTranslations(language: String?) = translations
        override suspend fun getBooks(translationCode: String) = books
        override suspend fun getChapter(translationCode: String, bookSlug: String, chapterNumber: Int): BibleChapter {
            calls++
            if (calls == failAt) throw IOException("fixture interruption")
            val book = books.first { it.slug == bookSlug }
            if (empty && chapterNumber == 2) return BibleChapter(translations.single(), book, ChapterSummary(chapterNumber, 0), emptyList())
            val verses = listOf(BibleVerse(1, 1, "${book.canonicalBook!!.osisCode}.$chapterNumber.1", "text", "text")) +
                if (blankVerse) listOf(BibleVerse(2, 2, "${book.canonicalBook!!.osisCode}.$chapterNumber.2", "", "")) else emptyList()
            return BibleChapter(translations.single().copy(code = if (wrong) "OTHER" else translationCode), book,
                ChapterSummary(chapterNumber, verses.size), verses)
        }
    }
    @Test fun fullCatalogDownloadSurvivesRestartAndNeedsNoNetworkForEveryChapter() = runBlocking {
        val fixture = Fixture()
        try {
            assertTrue(BibleDownloadEngine(fixture, OfflineStore(root), pause = {}).download("TEST") {})
            val pack = OfflineStore(root).read(biblePackageKey("TEST"), BiblePackage.serializer())!!
            assertTrue(pack.complete); assertEquals(3, pack.done); assertTrue(pack.bytes > 0)
            val offline = object : BibleContentSource by fixture {
                override suspend fun getTranslations(language: String?): List<TranslationSummary> = error("Network forbidden")
                override suspend fun getBooks(translationCode: String): List<BibleBook> = error("Network forbidden")
                override suspend fun getChapter(translationCode: String, bookSlug: String, chapterNumber: Int): BibleChapter = error("Network forbidden")
            }
            val repository = OfflineContentRepository(offline, OfflineStore(root))
            assertEquals(fixture.translations, repository.getTranslations())
            for (book in repository.getBooks("TEST")) for (number in 1..book.chaptersCount)
                assertEquals(number, repository.getChapter("TEST", book.slug, number).chapter.number)
            assertEquals(3, fixture.calls)
        } finally { fixture.close() }
    }
    @Test fun interruptionResumesWithoutRedownloadingAndAuditsMissingFiles() = runBlocking {
        val fixture = Fixture()
        try {
            fixture.failAt = 2
            assertTrue(runCatching { BibleDownloadEngine(fixture, OfflineStore(root), pause = {}).download("TEST") {} }.isFailure)
            val partial = OfflineStore(root).read(biblePackageKey("TEST"), BiblePackage.serializer())!!
            assertEquals(1, partial.done); assertFalse(partial.complete)
            fixture.failAt = 0
            assertTrue(BibleDownloadEngine(fixture, OfflineStore(root), pause = {}).download("TEST") {})
            assertEquals(4, fixture.calls) // One committed chapter was reused.
            assertTrue(BibleDownloadEngine(fixture, OfflineStore(root), pause = {}).download("TEST") {})
            assertEquals(4, fixture.calls)
            val damaged = fixture.getChapter("TEST", "one", 1).copy(translation = fixture.translations.single().copy(code = "WRONG"))
            OfflineStore(root).write(chapterKey("TEST", "one", 1), BibleChapter.serializer(), damaged)
            val beforeRepair = fixture.calls
            assertTrue(BibleDownloadEngine(fixture, OfflineStore(root), pause = {}).download("TEST") {})
            assertEquals(beforeRepair + 1, fixture.calls)
            assertEquals("TEST", OfflineStore(root).read(chapterKey("TEST", "one", 1), BibleChapter.serializer())!!.translation.code)
        } finally { fixture.close() }
    }
    @Test fun realWorkerAuditsCompletedPackageWithoutFetchingNetwork() = runBlocking {
        val fixture = Fixture()
        try {
            val wrapped = object : ContextWrapper(context) {
                override fun getNoBackupFilesDir(): File = root
                override fun getApplicationContext(): android.content.Context = this
            }
            assertTrue(BibleDownloadEngine(fixture, OfflineStore(wrapped), pause = {}).download("TEST") {})
            val worker = TestListenableWorkerBuilder<BibleDownloadWorker>(wrapped).setInputData(workDataOf("code" to "TEST")).build()
            assertEquals(androidx.work.ListenableWorker.Result.success(), worker.doWork())
            assertTrue(OfflineStore(wrapped).read(biblePackageKey("TEST"), BiblePackage.serializer())!!.complete)
        } finally { fixture.close() }
    }
    @Test fun wrongTranslationAndCancellationCannotClaimCompletion() = runBlocking {
        val fixture = Fixture()
        try {
            fixture.wrong = true
            assertTrue(runCatching { BibleDownloadEngine(fixture, OfflineStore(root), pause = {}).download("TEST") {} }.isFailure)
            assertFalse(OfflineStore(root).read(biblePackageKey("TEST"), BiblePackage.serializer())!!.complete)
            fixture.wrong = false
            assertFalse(BibleDownloadEngine(fixture, OfflineStore(root), pause = {}, shouldYield = { true }).download("TEST") {})
            val error = runCatching { BibleDownloadEngine(fixture, OfflineStore(root), pause = { throw CancellationException() }).download("TEST") {} }.exceptionOrNull()
            assertTrue(error is CancellationException)
            assertEquals(0, OfflineStore(root).read(biblePackageKey("TEST"), BiblePackage.serializer())!!.done)
        } finally { fixture.close() }
    }
    @Test fun emptySourceChapterDoesNotBlockOthersOrClaimACompleteBible() = runBlocking {
        val fixture = Fixture()
        try {
            fixture.empty = true
            assertTrue(BibleDownloadEngine(fixture, OfflineStore(root), pause = {}).download("TEST") {})
            val pack = OfflineStore(root).read(biblePackageKey("TEST"), BiblePackage.serializer())!!
            assertEquals(2, pack.done); assertFalse(pack.complete); assertEquals(listOf("One 2"), pack.unavailable)
            fixture.empty = false
            assertTrue(BibleDownloadEngine(fixture, OfflineStore(root), pause = {}).download("TEST") {})
            assertTrue(OfflineStore(root).read(biblePackageKey("TEST"), BiblePackage.serializer())!!.complete)
        } finally { fixture.close() }
    }
    /** Every advertised chapter is requested; API-empty chapters remain explicitly unavailable. */
    @Test fun blankSourceVersesStayExplicitAndCanBeRechecked() = runBlocking {
        val fixture = Fixture()
        try {
            fixture.blankVerse = true
            assertTrue(BibleDownloadEngine(fixture, OfflineStore(root), pause = {}).download("TEST") {})
            val pack = OfflineStore(root).read(biblePackageKey("TEST"), BiblePackage.serializer())!!
            assertTrue(pack.isInstalled); assertFalse(pack.complete); assertEquals(3, pack.done)
            assertEquals(3, pack.missingVerses.size)
            assertTrue(OfflineStore(root).read(chapterKey("TEST", "one", 1), BibleChapter.serializer())!!.verses[1].plainText.isBlank())
            fixture.blankVerse = false
            assertTrue(BibleDownloadEngine(fixture, OfflineStore(root), pause = {}).download("TEST") {})
            assertTrue(OfflineStore(root).read(biblePackageKey("TEST"), BiblePackage.serializer())!!.complete)
        } finally { fixture.close() }
    }
    @Test fun realEntireRussianCatalogIsSavedAndMissingChaptersAreExplicit() = runBlocking {
        val source = BibleApiClient()
        try {
            var last = -1
            assertTrue(BibleDownloadEngine(source, OfflineStore(root)).download("BQ_RUSSIAN_RST_STRONG") { pack ->
                if (pack.done / 100 != last) { last = pack.done / 100; android.util.Log.i("BibleFullTest", "${pack.done}/${pack.total}") }
            })
            val pack = OfflineStore(root).read(biblePackageKey("BQ_RUSSIAN_RST_STRONG"), BiblePackage.serializer())!!
            assertFalse(pack.complete); assertEquals(66, pack.books.size); assertEquals(1191, pack.total); assertEquals(1189, pack.done)
            assertEquals(setOf("Даниил 13", "Даниил 14"), pack.unavailable.toSet())
            val offline = object : BibleContentSource by source {
                override suspend fun getChapter(translationCode: String, bookSlug: String, chapterNumber: Int): BibleChapter = error("Network forbidden")
                override suspend fun getBooks(translationCode: String): List<BibleBook> = error("Network forbidden")
                override suspend fun getTranslations(language: String?): List<TranslationSummary> = error("Network forbidden")
            }
            val repository = OfflineContentRepository(offline, OfflineStore(root))
            assertTrue(repository.getTranslations().any { it.code == pack.translation.code })
            var read = 0
            for (book in repository.getBooks(pack.translation.code)) for (number in 1..book.chaptersCount) {
                if ("${book.name} $number" in pack.unavailable) assertTrue(runCatching { repository.getChapter(pack.translation.code, book.slug, number) }.isFailure)
                else { assertTrue(repository.getChapter(pack.translation.code, book.slug, number).verses.isNotEmpty()); read++ }
            }
            assertEquals(pack.done, read)
        } finally { source.close() }
    }
}
