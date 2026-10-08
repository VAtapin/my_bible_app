package com.bibledesktop.myapp

import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.bibledesktop.myapp.data.*
import com.bibledesktop.shared.api.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.io.IOException
import java.util.UUID

class OfflineStoreTest {
    private val target = InstrumentationRegistry.getInstrumentation().targetContext
    private val root = File(target.cacheDir, "native-offline-test-${UUID.randomUUID()}")
    @Before fun initialize() { check(target.packageName == "com.bibledesktop.myapp.debug"); check(root.mkdirs()) }
    @After fun cleanup() { root.walkBottomUp().forEach { check(it.delete()) } }

    @Test fun atomicContentAndImagesSurviveNewStoreInstance() = runBlocking {
        val store = OfflineStore(root)
        val serializer = ListSerializer(String.serializer())
        store.write("test", serializer, listOf("ru", "uk"))
        store.saveImage("preview", byteArrayOf(1, 2, 3))
        val reopened = OfflineStore(root)
        assertEquals(listOf("ru", "uk"), reopened.read("test", serializer))
        assertArrayEquals(byteArrayOf(1, 2, 3), reopened.image("preview")!!.readBytes())
        assertNull(reopened.read("unknown", serializer))
        assertTrue(runCatching { store.saveImage("too-large", ByteArray(384 * 1024 + 1)) }.isFailure)
        assertNull(reopened.image("too-large"))
    }

    @Test fun unknownSchemaIsNotOverwritten() = runBlocking {
        val store = OfflineStore(root)
        store.write("test", String.serializer(), "saved")
        val file = root.listFiles()!!.single()
        val future = """{"schemaVersion":2,"data":"future"}"""
        file.writeText(future)
        assertNull(store.read("test", String.serializer()))
        assertTrue(runCatching { store.write("test", String.serializer(), "overwrite") }.isFailure)
        assertEquals(future, file.readText())
    }

    @Test fun cancelledWorkerDoesNotMarkAPartialPackAsComplete() = runBlocking {
        val context = object : ContextWrapper(target) {
            override fun getNoBackupFilesDir(): File = root
            override fun getApplicationContext(): android.content.Context = this
        }
        val serializer = ListSerializer(String.serializer())
        OfflineStore(context).write("pack:cancel-test:2026-10-08:ru", serializer, listOf("2026-10-08"))
        val worker = TestListenableWorkerBuilder<CalendarDownloadWorker>(context)
            .setInputData(workDataOf("start" to "2026-10-08", "language" to "ru", "pack" to "cancel-test")).build()
        val error = runCatching { kotlinx.coroutines.withTimeout(100) { worker.doWork() } }.exceptionOrNull()
        assertTrue(error is kotlinx.coroutines.CancellationException)
        assertEquals(listOf("2026-10-08"), OfflineStore(context).read("pack:cancel-test:2026-10-08:ru", serializer))
    }

    @Test fun cachedDayServiceAndPrayerAreReadWithoutAnyNetworkCalls() = runBlocking {
        val store = OfflineStore(root)
        val online = FixtureSource()
        val repository = OfflineContentRepository(online, store)
        val day = repository.getCalendarDay("2026-10-08", "en")
        val prayer = repository.getPrayer(1)
        val service = repository.getCalendarService("2026-10-08", "cu-civil")
        val chapter = repository.getChapter("RU", "john", 3)
        val calls = online.calls
        online.offline = true
        val reopened = OfflineContentRepository(online, OfflineStore(root))
        assertEquals(day, reopened.getCalendarDay("2026-10-08", "ru"))
        assertEquals(prayer, reopened.getPrayer(1))
        assertEquals(service, reopened.getCalendarService("2026-10-08", "cu-civil"))
        assertEquals(chapter, reopened.getChapter("RU", "john", 3))
        assertEquals(calls, online.calls)
        assertTrue(runCatching { reopened.getCalendarDay("2026-10-09", "ru") }.isFailure)
        assertTrue(runCatching { reopened.getCalendarDay("2026-10-08", "uk") }.isFailure)
        assertTrue(runCatching { reopened.getChapter("EN", "john", 3) }.isFailure)
    }

    @Test fun realCalendarWorkerCompletesAndCanResumeFromDurableCheckpoint() = runBlocking {
        val context = object : ContextWrapper(target) {
            override fun getNoBackupFilesDir(): File = root
            override fun getApplicationContext(): android.content.Context = this
        }
        val data = workDataOf("start" to "2026-09-09", "language" to "ru", "pack" to "test")
        val store = OfflineStore(context)
        // A prior attempt already committed 29 complete days. The last day must use the real API.
        val dates = (0..28).map { java.time.LocalDate.parse("2026-09-09").plusDays(it.toLong()).toString() }
        store.write("pack:test:2026-09-09:ru", ListSerializer(String.serializer()), dates)
        val worker = TestListenableWorkerBuilder<CalendarDownloadWorker>(context).setInputData(data).build()
        val result = worker.doWork()
        assertEquals(androidx.work.ListenableWorker.Result.success(workDataOf("done" to 30, "total" to 30, "start" to "2026-09-09", "language" to "ru")), result)
        assertEquals(30, store.read("pack:test:2026-09-09:ru", ListSerializer(String.serializer()))!!.size)
        val repository = OfflineContentRepository(FixtureSource().apply { offline = true }, store)
        val day = repository.getCalendarDay("2026-10-08", "ru")
        assertEquals("2026-10-08", day.date)
        assertTrue(repository.getCalendarService(day.date, "cu-civil").assignments.isNotEmpty())
        val allowed = day.icons.filter { it.localCachingAllowed }
        assertTrue(allowed.isNotEmpty())
        allowed.forEach { assertNotNull(store.image(it.imagePreviewUrl!!)) }
    }

    @Test fun realThirtyDayPackIsFullyReadableAfterRepositoryRestartWithNoNetwork() = runBlocking {
        val context = object : ContextWrapper(target) {
            override fun getNoBackupFilesDir(): File = root
            override fun getApplicationContext(): android.content.Context = this
        }
        val worker = TestListenableWorkerBuilder<CalendarDownloadWorker>(context)
            .setInputData(workDataOf("start" to "2026-10-08", "language" to "ru", "pack" to "full-test")).build()
        var result = worker.doWork()
        // Real public API rate limits are respected, not disabled for the integration test.
        if (result == androidx.work.ListenableWorker.Result.retry()) {
            kotlinx.coroutines.delay(65_000)
            result = TestListenableWorkerBuilder<CalendarDownloadWorker>(context)
                .setInputData(workDataOf("start" to "2026-10-08", "language" to "ru", "pack" to "full-test")).build().doWork()
        }
        assertEquals(androidx.work.ListenableWorker.Result.success(workDataOf("done" to 30, "total" to 30, "start" to "2026-10-08", "language" to "ru")), result)
        val store = OfflineStore(context)
        val repository = OfflineContentRepository(FixtureSource().apply { offline = true }, store)
        for (offset in 0..29) {
            val iso = java.time.LocalDate.parse("2026-10-08").plusDays(offset.toLong()).toString()
            val day = repository.getCalendarDay(iso, "ru")
            assertEquals(iso, day.date)
            assertEquals(iso, repository.getCalendarService(iso, "cu-civil").date)
            day.icons.filter { it.localCachingAllowed }.forEach { assertNotNull(store.image(it.imagePreviewUrl!!)) }
        }
        assertEquals(31, repository.getCalendarMonth(2026, 10, "ru").size)
        assertEquals(30, repository.getCalendarMonth(2026, 11, "ru").size)
    }
}

/** Isolated storage tests only; the worker test above uses the real production GET API. */
private class FixtureSource : BibleContentSource {
    var calls = 0
    var offline = false
    private fun request() { calls++; if (offline) throw IOException("Offline") }
    override suspend fun getCalendarDay(date: String, language: String, profile: String): CalendarDay {
        request(); return CalendarDay(date, "2026-09-25", "2026-04-12", "Thursday", "fixture")
    }
    override suspend fun getPrayer(id: Long): PrayerDetail {
        request(); return PrayerDetail(id, "cu-civil", "common", title = "Prayer", body = "Text")
    }
    override suspend fun getCalendarService(date: String, language: String): CalendarServicePlan {
        request(); return CalendarServicePlan(date, language)
    }
    override suspend fun getTranslations(language: String?): List<TranslationSummary> { request(); return emptyList() }
    override suspend fun getBooks(translationCode: String): List<BibleBook> { request(); return emptyList() }
    override suspend fun getChapter(translationCode: String, bookSlug: String, chapterNumber: Int): BibleChapter {
        request(); return BibleChapter(TranslationSummary(translationCode, "Translation", language = LanguageSummary("ru", "Русский")),
            BibleBook(bookSlug, "John", chaptersCount = 21), ChapterSummary(chapterNumber, 1), listOf(BibleVerse(1, 1, "John.3.1", "Text", "Text")))
    }
    override suspend fun getPrayers(language: String): List<PrayerSummary> { request(); return emptyList() }
    override suspend fun getCalendarMonth(year: Int, month: Int, language: String): List<CalendarGridDay> { request(); return emptyList() }
    override fun close() = Unit
}
