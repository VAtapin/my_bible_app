package com.bibledesktop.shared.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class PrayerCatalogTest {
    private val metadata = """"canonical_slug":"prayer-lords","liturgical_work_id":901,"group":"short","groups":["short","occasions"],"available_languages":["cu-civil"],"completeness":"complete","review_status":"source-verified","content_revision":"${"a".repeat(64)}","catalog_visible":true"""
    private val summary = """{"id":17,"language_code":"cu-civil","category":"common","title":"Отче наш","short_title":null,"intro":null,"excerpt":null,$metadata}"""
    private val detail = """{"id":17,"language_code":"cu-civil","category":"common","title":"Отче наш","intro":null,"body":"<p>Actual whole beginning</p><p>Actual whole ending</p>","plain_text":"Actual whole beginning\n\nActual whole ending","sections":[],$metadata}"""
    private val catalog = """{"data":[$summary],"catalog_version":2,"groups":{"short":"Короткие молитвы","rules":"Молитвенные правила","occasions":"На разные случаи","initial":"Начальные молитвы"},"external_sources":[{"language":"de","title":"German source","url":"https://orthodoxia.de/gebete/gebetbuch","availability":"external-only","offline_available":false}]}"""

    @Test fun v2EnvelopeKeepsActualLanguageNullableIntroAndLegacyIdentity() = runBlocking {
        var payload = catalog
        val api = BibleApiClient(HttpClient(MockEngine { request ->
            assertEquals("ru", request.url.parameters["language"])
            respond(payload, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureBibleApiClient() }, "https://example.test/api")
        try {
            val value = api.getPrayerCatalog("ru")
            assertEquals(2, value.catalogVersion)
            assertEquals("cu-civil", value.data.single().languageCode)
            assertEquals(17L, value.data.single().id)
            assertEquals(901L, value.data.single().liturgicalWorkId)
            assertNull(value.data.single().excerpt)
            assertNull(value.data.single().intro)
            assertEquals(setOf("short", "rules", "occasions", "initial"), value.groups.keys)
            assertFalse(value.externalSources.single().offlineAvailable)
            assertEquals(value.data, api.getPrayers("ru"))
            payload = """{"data":[],"catalog_version":2,"groups":[],"external_sources":[]}"""
            assertTrue(runCatching { api.getPrayerCatalog("ru") }.isFailure)
            payload = catalog.replace("\"offline_available\":false", "\"offline_available\":true")
            assertTrue(runCatching { api.getPrayerCatalog("ru") }.isFailure)
            payload = catalog.replace("\"content_revision\":\"${"a".repeat(64)}\",", "")
            assertTrue(runCatching { api.getPrayerCatalog("ru") }.isFailure)
            payload = """{"data":[{"id":1,"language_code":"ru","category":"common","title":"Legacy","excerpt":"Old source"},{"id":2,"language_code":"cu-civil","category":"common","title":"Legacy civil","excerpt":"Actual source"}]}"""
            assertNull(api.getPrayerCatalog("ru").catalogVersion)
            assertEquals(listOf(1L), api.getPrayers("ru").map { it.id })
        } finally { api.close() }
    }

    @Test fun explicitActualEditionAndAccessFailuresNeverFallbackOrRelabel() = runBlocking {
        var status = HttpStatusCode.OK
        val api = BibleApiClient(HttpClient(MockEngine { request ->
            request.url.parameters["language"]?.let { assertTrue(it in listOf("de", "cu-civil")) }
            respond("""{"data":$detail,"catalog_version":2}""", status, headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureBibleApiClient() }, "https://example.test/api")
        try {
            assertEquals("cu-civil", api.getPrayer(17).languageCode)
            val actualDetail = api.getPrayer(17)
            assertTrue(runCatching { api.getPrayer(18) }.exceptionOrNull() is IllegalArgumentException)
            assertTrue(runCatching { api.getPrayer(18, "cu-civil") }.exceptionOrNull() is IllegalArgumentException)
            assertEquals("Actual whole beginning\n\nActual whole ending", actualDetail.plainText)
            assertTrue(runCatching { actualDetail.copy(plainText = null).validatePrayerDetail(2) }.isFailure)
            val wrongLanguage = runCatching { api.getPrayer(17, "de") }.exceptionOrNull()
            assertNotNull(wrongLanguage)
            assertTrue(wrongLanguage is IllegalArgumentException)
            assertFalse(isPrayerTransportFailure(wrongLanguage))
            assertFalse(isPrayerUnavailableFailure(wrongLanguage))
            for (failureStatus in listOf(HttpStatusCode.Forbidden, HttpStatusCode.NotFound, HttpStatusCode.Conflict)) {
                status = failureStatus
                val failure = runCatching { api.getPrayer(17, "de") }.exceptionOrNull()
                assertNotNull(failure)
                assertFalse(isPrayerTransportFailure(failure))
                assertEquals(failureStatus.value in setOf(404, 409), isPrayerUnavailableFailure(failure))
            }
        } finally { api.close() }
    }

    @Test fun nonHttpFailuresNeverRevokePreviouslyPublishedPrayerRequests() {
        for (failure in listOf(
            io.ktor.utils.io.errors.IOException("Offline"),
            io.ktor.client.network.sockets.SocketTimeoutException("Timeout"),
            kotlinx.coroutines.CancellationException("Cancelled"),
            kotlinx.serialization.SerializationException("Invalid payload"),
            IllegalArgumentException("404 is merely text in a local contract error"),
        )) assertFalse(isPrayerUnavailableFailure(failure))
    }

    @Test fun backendAliasAndWholeEditionHashAreDistinctFromAggregateWorkRevision() = runBlocking {
        val aggregate = "${"a".repeat(64)}:${"b".repeat(64)}"
        val api = BibleApiClient(HttpClient(MockEngine { request ->
            val payload = if (!request.url.encodedPath.contains("/versions/")) {
                """{"data":{"id":901,"slug":"prayer-lords","title":"Отче наш","collections":["prayers"],"available_languages":["cu-civil"],"editions":[{"code":"ACTUAL","title":"Source","language":"cu-civil","orthography":"civil-accented","reader_profile":"prayer"}],"prayer_group":"short","prayer_groups":["short","occasions"],"intro":null,"completeness":"complete","legacy_slugs":["prayer-17","prayer-18"],"content_revision":"$aggregate"}}"""
            } else {
                """{"data":{"slug":"prayer-lords","title":"Отче наш","language":"cu-civil","edition":"ACTUAL","edition_title":"Source","orthography":"civil-accented","reader_profile":"prayer","blocks":[{"id":"b1","kind":"text","text":"Actual complete beginning"},{"id":"b2","kind":"text","text":"Actual complete ending"}],"credit":"Real source","source_url":"https://example.test/source","content_hash":"${"a".repeat(64)}","review_status":"prayer-reviewed","completeness":"complete"}}"""
            }
            respond(payload, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureBibleApiClient() }, "https://example.test/api")
        try {
            val work = api.getLiturgicalWork("prayer-17")
            assertEquals("prayer-lords", work.slug)
            assertEquals(listOf("prayer-17", "prayer-18"), work.legacySlugs)
            assertTrue(runCatching { work.copy(prayerGroups = emptyList()).validateLiturgicalWork() }.isFailure)
            val version = api.getLiturgicalVersion("prayer-17", "cu-civil", "ACTUAL")
            assertEquals(2, version.blocks.size)
            assertNotEquals(work.contentRevision, version.contentHash)
            assertTrue(runCatching { version.copy(reviewStatus = "source-imported").validateLiturgicalVersion("cu-civil", requireReviewed = true) }.isFailure)
            assertTrue(runCatching { version.copy(orthography = "traditional").validateLiturgicalVersion("cu-civil", requireReviewed = true) }.isFailure)
            assertTrue(runCatching { api.getLiturgicalVersion("prayer-17", "de") }.isFailure)
            assertTrue(runCatching { api.getLiturgicalVersion("prayer-17", "cu-civil", "WRONG") }.isFailure)
            assertTrue(runCatching { api.getLiturgicalWork("prayer-unrelated") }.isFailure)
            assertTrue(runCatching { api.getLiturgicalVersion("prayer-unrelated", "cu-civil") }.isFailure)
        } finally { api.close() }
    }
}
