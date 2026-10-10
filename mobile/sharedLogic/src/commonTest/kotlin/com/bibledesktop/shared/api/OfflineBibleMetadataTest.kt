package com.bibledesktop.shared.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class OfflineBibleMetadataTest {
    private suspend fun catalog(extra: String): Result<TranslationSummary> {
        val engine = MockEngine {
            respond("""{"data":[{"code":"TEST","name":"Edition","language":{"code":"en","name":"English"}$extra}]}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val client = BibleApiClient(HttpClient(engine) { configureBibleApiClient() }, "https://example.test/api")
        return try { runCatching { client.getTranslations().single() } } finally { client.close() }
    }
    @Test fun oldCatalogDoesNotInventSizeOrRevision() = runBlocking {
        val old = catalog("").getOrThrow()
        assertNull(old.offlineSizeEstimateBytes); assertNull(old.contentRevision)
    }
    @Test fun publishedEstimateAndRevisionRemainOptional() = runBlocking {
        val edition = catalog(""", "offline_size_estimate_bytes":12345,"content_revision":"actual-published-revision"""").getOrThrow()
        assertEquals(12345L, edition.offlineSizeEstimateBytes)
        assertEquals("actual-published-revision", edition.contentRevision)
    }
    @Test fun rejectsMisleadingSizesAndRevision() = runBlocking {
        for (extra in listOf(""", "offline_size_estimate_bytes":-1""", """, "offline_size_estimate_bytes":0""",
            """, "offline_size_estimate_bytes":9007199254740992""", """, "content_revision":" """")) {
            assertTrue(catalog(extra).isFailure, extra)
        }
    }
}
