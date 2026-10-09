package com.bibledesktop.myapp

import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import com.bibledesktop.myapp.data.*
import com.bibledesktop.shared.api.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.UUID
import java.util.zip.GZIPInputStream
import kotlinx.serialization.json.Json

/** Uses the real APK asset and isolated app storage; no API or existing user data. */
class BundledBibleTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val root = File(context.cacheDir, "bundled-bible-test-${UUID.randomUUID()}")
    private val wrapped = object : ContextWrapper(context) { override fun getNoBackupFilesDir() = root }
    @Before fun before() { check(context.packageName.endsWith(".debug")); check(root.mkdirs()) }
    @After fun after() { root.walkBottomUp().forEach { check(it.delete()) } }

    @Test fun interruptedCopyResumesWithoutOverwritingCommittedChapters() = runBlocking {
        val first = GZIPInputStream(context.assets.open("bibles/synodal.bundle")).bufferedReader(Charsets.UTF_8).use { reader ->
            reader.readLine()
            Json { ignoreUnknownKeys = true }.decodeFromString(BibleChapter.serializer(), requireNotNull(reader.readLine()))
        }
        val store = OfflineStore(wrapped)
        val key = chapterKey(BundledBible.code, first.book.slug, first.chapter.number)
        store.write(key, BibleChapter.serializer(), first)
        val savedAt = store.savedAt(key)
        assertTrue(store.biblePackages().isEmpty())
        BundledBible.install(wrapped)
        assertTrue(store.biblePackages().single().isInstalled)
        assertEquals(savedAt, store.savedAt(key))
        assertEquals(first, store.read(key, BibleChapter.serializer()))
    }

    @Test fun firstLaunchAndEveryBundledChapterWorkWithNetworkForbidden() = runBlocking {
        var requests = 0
        val remote = object : BibleContentSource by BibleApiClient() {
            override suspend fun getTranslations(language: String?): List<TranslationSummary> { requests++; error("Network forbidden") }
            override suspend fun getBooks(translationCode: String): List<BibleBook> { requests++; error("Network forbidden") }
            override suspend fun getChapter(translationCode: String, bookSlug: String, chapterNumber: Int): BibleChapter { requests++; error("Network forbidden") }
        }
        val store = OfflineStore(wrapped)
        val repository = OfflineContentRepository(remote, store, installBuiltIn = { BundledBible.install(wrapped) })
        try {
            assertEquals(BundledBible.code, repository.installedTranslations().single().code)
            assertEquals(BundledBible.code, repository.getTranslations().single().code)
            val pack = store.biblePackages().single()
            assertEquals(66, pack.books.size)
            assertTrue(pack.isInstalled)
            assertEquals(pack.total, pack.done + pack.unavailable.size)
            var chapters = 0
            for (book in repository.getBooks(BundledBible.code)) for (number in 1..book.chaptersCount) {
                if ("${book.name} $number" in pack.unavailable) {
                    assertTrue(runCatching { repository.getChapter(BundledBible.code, book.slug, number) }.exceptionOrNull() is BibleNotInstalled)
                } else {
                    val chapter = repository.getChapter(BundledBible.code, book.slug, number)
                    assertEquals(BundledBible.code, chapter.translation.code)
                    assertTrue(chapter.verses.any { it.plainText.isNotBlank() }); chapters++
                }
            }
            assertEquals(pack.done, chapters); assertEquals(0, requests)
            val firstBook = pack.books.first()
            val saved = repository.getChapter(BundledBible.code, firstBook.slug, 1)
            val savedAt = store.savedAt(chapterKey(BundledBible.code, firstBook.slug, 1))
            // A subsequent install must not overwrite downloaded texts or their timestamps.
            BundledBible.install(wrapped)
            assertEquals(savedAt, store.savedAt(chapterKey(BundledBible.code, firstBook.slug, 1)))
            val reopened = OfflineContentRepository(remote, OfflineStore(wrapped), installBuiltIn = { BundledBible.install(wrapped) })
            assertEquals(saved, reopened.getChapter(BundledBible.code, firstBook.slug, 1))
        } finally { repository.close() }
    }
}
