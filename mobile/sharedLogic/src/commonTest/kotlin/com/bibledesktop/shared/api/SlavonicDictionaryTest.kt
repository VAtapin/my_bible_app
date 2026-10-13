package com.bibledesktop.shared.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class SlavonicDictionaryTest {
    private val key = "a".repeat(40)
    private val hash = "b".repeat(64)
    private fun word(format: String = "plain-text", link: String = "https://azbyka.ru/otechnik/Spravochniki/polnyj-pravoslavnyj-tserkovnoslavjanskij-slovar/1") =
        """{"key":"$key","headword":"А҆́бїе","civil":"Абие","definition":"Скоро, тотчас.\nПолная строка.","body_format":"$format","source_url":"$link","source_chapter":1,"content_hash":"$hash"}"""
    private fun page(offset: Int, body: String = word()) = """{"module_code":"AZBYKA_CU_FULL","content_version":"$hash","data":[$body],"offset":$offset,"limit":30,"total":61}"""

    @Test fun catalogWordAndMeaningUseSourceSectionsAndPagination() = runBlocking {
        val requests = mutableListOf<io.ktor.http.Url>()
        val api = DictionaryApi(HttpClient(MockEngine { request ->
            requests += request.url
            respond(page(request.url.parameters["offset"]!!.toInt()), headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureBibleApiClient() }, "https://example.test/api")
        try {
            assertEquals(61, api.slavonicSearch(letter = 1).total)
            assertEquals("", requests[0].parameters["q"])
            assertEquals("word", requests[0].parameters["mode"])
            val value = api.slavonicSearch("святая вода", true, offset = 30).data.single()
            assertEquals("Скоро, тотчас.\nПолная строка.", value.definition)
            assertEquals("meaning", requests[1].parameters["mode"])
            assertEquals("30", requests[1].parameters["offset"])
            assertEquals("святая вода", requests[1].parameters["q"])
        } finally { api.close() }
    }
    @Test fun fullArticleAndOriginalAlphabetFollowTheActualContract() = runBlocking {
        val api = DictionaryApi(HttpClient(MockEngine { request ->
            val body = if (request.url.encodedPath.endsWith("alphabet"))
                """{"module_code":"AZBYKA_CU_FULL","data":[{"chapter":36,"label":"Ѵ","count":16}]}"""
            else """{"data":{"key":"$key","module_code":"AZBYKA_CU_FULL","lexical":${word()}}}"""
            respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureBibleApiClient() }, "https://example.test/api")
        try {
            assertEquals(SlavonicLetter(36, "Ѵ", 16), api.slavonicAlphabet().single())
            assertEquals(key, api.slavonicArticle(key).key)
            assertTrue(api.slavonicArticle(key).definition.contains("\nПолная строка."))
        } finally { api.close() }
    }
    @Test fun invalidTextFormatAndSourceLinksAreRejected() = runBlocking {
        for (body in listOf(word("html"), word(link = "https://other.test/1"))) {
            val api = DictionaryApi(HttpClient(MockEngine {
                respond(page(0, body), headers = headersOf(HttpHeaders.ContentType, "application/json"))
            }) { configureBibleApiClient() })
            try { assertFailsWith<IllegalArgumentException> { api.slavonicSearch() } } finally { api.close() }
        }
    }
    @Test fun rateLimitPreventsNewRequestsUntilRetryAfter() = runBlocking {
        var calls = 0
        val api = DictionaryApi(HttpClient(MockEngine {
            calls++; respond("{}", HttpStatusCode.TooManyRequests,
                headersOf(HttpHeaders.ContentType to listOf("application/json"), HttpHeaders.RetryAfter to listOf("60")))
        }) { configureBibleApiClient() })
        try {
            assertEquals(60_000, assertFailsWith<SlavonicRateLimitException> { api.slavonicSearch() }.retryMillis)
            assertFailsWith<SlavonicRateLimitException> { api.slavonicAlphabet() }
            assertEquals(1, calls)
        } finally { api.close() }
    }
}
