package com.bibledesktop.shared.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BibleApiClientTest {
    @Test
    fun decodesTranslationsAndPassesLanguageFilter() = runBlocking {
        val engine = MockEngine { request ->
            assertEquals("de", request.url.parameters["language"])
            respond(
                content = """{"data":[{"code":"ELB","name":"Elberfelder","language":{"code":"de","name":"Deutsch"},"is_default":true}]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = BibleApiClient(
            client = HttpClient(engine) { configureBibleApiClient() },
            baseUrl = "https://example.test/api",
        )

        val translations = client.getTranslations("de")

        assertEquals(1, translations.size)
        assertEquals("Elberfelder", translations.single().name)
        assertEquals("de", translations.single().language.code)
        assertTrue(translations.single().isDefault)
        client.close()
    }
}
