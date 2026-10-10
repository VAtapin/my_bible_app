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

    @Test fun studySurvivesRepositoryRestartAndKeepsTranslationBoundaries() = runBlocking {
        val source = FixtureSource()
        val repository = OfflineContentRepository(source, OfflineStore(root))
        val refs = repository.getCrossReferences(42, "RU")
        val tokens = repository.getStrongTokens(42, "RU")
        val word = repository.getStrongEntry("G25", 42)
        val calls = source.calls
        source.offline = true
        val reopened = OfflineContentRepository(source, OfflineStore(root))
        assertEquals(refs, reopened.getCrossReferences(42, "RU"))
        assertEquals(tokens, reopened.getStrongTokens(42, "RU"))
        assertEquals(word, reopened.getStrongEntry("G25", 42))
        assertEquals(calls, source.calls)
        assertTrue(runCatching { reopened.getCrossReferences(42, "EN") }.isFailure)
        assertTrue(runCatching { reopened.getStrongTokens(43, "RU") }.isFailure)
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
        val chapter = online.getChapter("RU", "john", 3)
        store.write(chapterKey("RU", "john", 3), BibleChapter.serializer(), chapter)
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

    @Test fun calendarWorkerEngineCompletesAndResumesOnlyAuditedAutomaticSnapshots() = runBlocking {
        val store=OfflineStore(root);val source=AutomaticCalendarFixtureSource()
        val repository=OfflineContentRepository(source,store,{false})
        try{
            val engine=CalendarDownloadEngine(repository,pause={},savePreview=::saveCalendarFixturePreview)
            val stopped=runCatching{engine.download("2026-09-09","ru","test"){done,_->if(done==29)throw kotlinx.coroutines.CancellationException("Interrupted")}}.exceptionOrNull()
            assertTrue(stopped is kotlinx.coroutines.CancellationException)
            assertEquals(29,store.read(calendarWindowCheckpoint("test","2026-09-09","ru"),ListSerializer(String.serializer()))!!.size)
            source.serviceCalls.clear()
            assertEquals(30,CalendarDownloadEngine(repository,pause={},savePreview=::saveCalendarFixturePreview).download("2026-09-09","ru","test"){_,_->}.done)
            assertEquals(listOf("2026-10-08" to "ru"),source.serviceCalls)
            source.offline=true
            val day=repository.getCalendarDay("2026-10-08","ru")
            val plan=repository.getAutomaticCalendarService(day.date,"ru")
            assertEquals("2026-10-08",plan.date);assertEquals(AutomaticCalendarPolicy,plan.textPolicy)
            assertTrue(plan.assignments.isNotEmpty());assertTrue(plan.assignments.none{it.selection=="missing"})
            day.icons.filter{it.localCachingAllowed}.forEach{assertNotNull(store.image(it.imagePreviewUrl!!))}
        }finally{repository.close()}
    }

    @Test fun thirtyDayAutomaticSnapshotIsFullyReadableAfterRepositoryRestartWithNoNetwork() = runBlocking {
        val store=OfflineStore(root);val source=AutomaticCalendarFixtureSource()
        val online=OfflineContentRepository(source,store,{false})
        val result=CalendarDownloadEngine(online,pause={},savePreview=::saveCalendarFixturePreview).download("2026-10-08","ru","full-test"){_,_->}
        assertEquals(30,result.done);assertTrue(result.missingServices.isEmpty())
        source.offline=true
        val repository=OfflineContentRepository(source,OfflineStore(root),{false})
        for (offset in 0..29) {
            val iso = java.time.LocalDate.parse("2026-10-08").plusDays(offset.toLong()).toString()
            val day = repository.getCalendarDay(iso, "ru")
            assertEquals(iso, day.date)
            val service=repository.getAutomaticCalendarService(iso,"ru")
            assertEquals(iso,service.date);assertEquals("ru",service.calendarLanguage);assertEquals(AutomaticCalendarPolicy,service.textPolicy)
            day.icons.filter { it.localCachingAllowed }.forEach { assertNotNull(store.image(it.imagePreviewUrl!!)) }
        }
        assertEquals(31, repository.getCalendarMonth(2026, 10, "ru").size)
        assertEquals(30, repository.getCalendarMonth(2026, 11, "ru").size)
        repository.close();online.close()
    }
}

/** Isolated content/cache fixtures; no production calendar service requests. */
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
    override suspend fun getCrossReferences(verseId: Long, translationCode: String): CrossReferences {
        request(); return CrossReferences(StudyVerse(verseId, "John.3.16"), translationCode)
    }
    override suspend fun getStrongTokens(verseId: Long, translationCode: String): StrongTokens {
        request(); return StrongTokens(StudyVerse(verseId, "John.3.16"), listOf(StrongToken("G25")))
    }
    override suspend fun getStrongEntry(number: String, verseId: Long): StrongEntry {
        request(); return StrongEntry(number, content = "Definition", lexicon = StrongLexicon("Dictionary", "ru"))
    }
    override suspend fun getCalendarMonth(year: Int, month: Int, language: String): List<CalendarGridDay> { request(); return emptyList() }
    override fun close() = Unit
}
