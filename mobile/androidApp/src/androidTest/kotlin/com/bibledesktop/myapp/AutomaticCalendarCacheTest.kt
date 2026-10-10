package com.bibledesktop.myapp

import androidx.test.platform.app.InstrumentationRegistry
import com.bibledesktop.myapp.data.OfflineContentRepository
import com.bibledesktop.myapp.data.OfflineStore
import com.bibledesktop.shared.api.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import java.io.File
import java.io.IOException
import java.util.UUID

class AutomaticCalendarCacheTest {
    private val target = InstrumentationRegistry.getInstrumentation().targetContext
    private val root = File(target.cacheDir, "calendar-policy-test-${UUID.randomUUID()}")
    private val date = "2026-10-10"
    @Before fun initialize() { check(target.packageName == "com.bibledesktop.myapp.debug"); check(root.mkdirs()) }
    @After fun cleanup() { root.walkBottomUp().forEach { check(it.delete()) } }

    private fun plan(language: String): AutomaticCalendarServicePlan {
        val actual = CalendarTextEdition(71, "cu", "traditional", "REAL_CU", "liturgical-corpus")
        val text = AutomaticCalendarText("Actual source title", "Ѡтче нашъ", "work:prayer", "prayer", "cu", "traditional",
            "REAL_CU", "liturgical-corpus", 71, "a".repeat(64), listOf(actual),
            if (language in listOf("ru", "uk", "cu")) "preferred" else "fallback", null,
            textId = "calendar-31", slot = "troparion", insert = true)
        return AutomaticCalendarServicePlan(2, AutomaticCalendarPolicy, language, date, "mixed", listOf(text), emptyList())
    }

    private class Source(private val delegate: BibleContentSource = BibleApiClient()) : BibleContentSource by delegate {
        var calls = 0
        var error: Exception? = null
        var response: AutomaticCalendarServicePlan? = null
        override suspend fun getAutomaticCalendarService(date: String, calendarLanguage: String): AutomaticCalendarServicePlan {
            calls++
            error?.let { throw it }
            return checkNotNull(response)
        }
        override suspend fun getCalendarService(date: String, language: String): CalendarServicePlan = kotlin.error("Legacy service must never be requested")
    }

    @Test fun originalLanguageAndActualScriptSurviveRepositoryRestartWithoutNetwork() = runBlocking {
        val source = Source().apply { response = plan("en") }
        val repository = OfflineContentRepository(source, OfflineStore(root), { true })
        try {
            assertEquals(plan("en"), repository.getAutomaticCalendarService(date, "en"))
            source.error = IOException("No network")
            val reopened = OfflineContentRepository(source, OfflineStore(root), { false })
            assertEquals("traditional", reopened.getAutomaticCalendarService(date, "en").assignments.single().orthography)
            assertEquals(1, source.calls)
            assertTrue(runCatching { reopened.getAutomaticCalendarService(date, "ru") }.isFailure)
            assertEquals(2, source.calls)
        } finally { repository.close() }
    }

    @Test fun invalidCachedContractCannotRenderButValidRemoteRepairsIt() = runBlocking {
        val store = OfflineStore(root)
        val wrong = plan("en").copy(assignments = listOf(plan("en").assignments.single().copy(orthography = "civil")))
        store.write(automaticCalendarServiceKey(date, "en"), AutomaticCalendarServicePlan.serializer(), wrong)
        val source = Source().apply { response = plan("en"); error = IOException("No network") }
        val offline = OfflineContentRepository(source, store, { false })
        try {
            assertTrue(runCatching { offline.getAutomaticCalendarService(date, "en") }.exceptionOrNull() is IOException)
            source.error = null
            val repaired = offline.getAutomaticCalendarService(date, "en")
            assertEquals("traditional", repaired.assignments.single().orthography)
            source.error = IOException("No network")
            assertEquals(repaired, OfflineContentRepository(source, OfflineStore(root), { false }).getAutomaticCalendarService(date, "en"))
            assertEquals(2, source.calls)
        } finally { offline.close() }
    }

    @Test fun contractAccessAndCancellationFailuresNeverFallbackToCachedText() = runBlocking {
        val source = Source().apply { response = plan("de") }
        val repository = OfflineContentRepository(source, OfflineStore(root), { true })
        try {
            repository.getAutomaticCalendarService(date, "de")
            source.response = plan("de").copy(textPolicy = "old-policy")
            assertTrue(runCatching { repository.getAutomaticCalendarService(date, "de") }.exceptionOrNull() is IllegalArgumentException)
            source.error = SecurityException("Access rejected")
            assertTrue(runCatching { repository.getAutomaticCalendarService(date, "de") }.exceptionOrNull() is SecurityException)
            source.error = CancellationException("Cancelled")
            assertTrue(runCatching { repository.getAutomaticCalendarService(date, "de") }.exceptionOrNull() is CancellationException)
            source.error = IOException("Transport interrupted")
            assertEquals(plan("de"), repository.getAutomaticCalendarService(date, "de"))
        } finally { repository.close() }
    }

    @Test fun legacyCacheIsNeverAnAutomaticPolicyAliasAndExplicitRefreshIsDurable() = runBlocking {
        val store = OfflineStore(root)
        store.write("service:$date:ru", CalendarServicePlan.serializer(), CalendarServicePlan(date, "ru"))
        val source = Source().apply { response = plan("en"); error = IOException("No network") }
        val repository = OfflineContentRepository(source, store, { false })
        try {
            assertTrue(runCatching { repository.getAutomaticCalendarService(date, "en") }.isFailure)
            source.error = null
            assertEquals(plan("en"), repository.refreshAutomaticService(date, "en"))
            assertEquals(plan("en"), OfflineStore(root).read(automaticCalendarServiceKey(date, "en"), AutomaticCalendarServicePlan.serializer()))
            assertNotNull(store.read("service:$date:ru", CalendarServicePlan.serializer()))
        } finally { repository.close() }
    }
}
