package com.bibledesktop.shared.api
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class DictionaryApiTest {
    @Test fun readsVersionedSourceAndPaginatedArticles() = runBlocking {
        val key = "a".repeat(40)
        val api = DictionaryApi(HttpClient(MockEngine { request ->
            val body = when {
                request.url.encodedPath.endsWith("dictionaries") -> """{"data":[{"code":"MAPS","name":"Atlas","kind":"atlas","content_version":"v1","entries_count":372,"media_count":20,"word_forms_count":0}]}"""
                request.url.encodedPath.endsWith("entries") -> { assertEquals("30", request.url.parameters["offset"]); assertEquals("30", request.url.parameters["limit"]); """{"data":[{"id":1,"key":"$key","topic":"Jerusalem"}],"total":372}""" }
                else -> """{"data":{"id":1,"key":"$key","topic":"Jerusalem","body":"Text","media":[{"id":2,"fragment_id":"map","url":"/api/dictionaries/MAPS/media/2"}],"references":[{"book_slug":"john","chapter_number":3,"verse_from":1,"verse_to":5}],"links":[]}}"""
            }
            respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureBibleApiClient() }, "https://example.test/api")
        try { assertEquals("v1", api.modules().single().version); assertEquals(372, api.entries("MAPS", "", 30).total); val article = api.article("MAPS", key); assertEquals(5, article.references.single().last); assertEquals("https://example.test/api/dictionaries/MAPS/media/2", api.imageUrl("MAPS", article.media.single())) } finally { api.close() }
    }
    @Test fun rejectsImageOutsideSource() {
        val api = DictionaryApi(HttpClient(MockEngine { respond("{}") }) { configureBibleApiClient() }, "https://example.test/api")
        try { assertFailsWith<IllegalArgumentException> { api.imageUrl("MAPS", DictionaryMedia(2, "map", "https://evil.test/image")) }; assertFailsWith<IllegalArgumentException> { api.imageUrl("MAPS", DictionaryMedia(2, "map", "/api/dictionaries/OTHER/media/2")) } } finally { api.close() }
    }
}
