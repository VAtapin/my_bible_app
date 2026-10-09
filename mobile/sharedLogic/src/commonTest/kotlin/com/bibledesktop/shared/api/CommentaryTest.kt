package com.bibledesktop.shared.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class CommentaryTest {
    @Test fun intervalCoverageIncludesIntroductionsAndCrossChapterRanges() {
        val range = CommentaryEntry(1, body = "Text", chapterFrom = 3, verseFrom = 16, chapterTo = 4, verseTo = 2, moduleCode = "S", moduleName = "Source")
        assertTrue(commentaryOverlaps(range, 3, 15, 16)); assertFalse(commentaryOverlaps(range, 3, 1, 15))
        assertTrue(commentaryOverlaps(range, 4, 2, 5)); assertFalse(commentaryOverlaps(range, 4, 3, 5))
        assertTrue(commentaryOverlaps(range.copy(chapterFrom = 0), 2, 1, 1))
        assertTrue(commentaryOverlaps(range.copy(chapterTo = null, verseTo = null, verseFrom = 0), 3, 1, 2))
        assertFalse(commentaryOverlaps(range.copy(chapterTo = null, verseTo = null), 3, 17, 17))
    }
    @Test fun publicBooksKeepTotalsAndSectionIdentity() = runBlocking {
        val engine = MockEngine { request ->
            val body = if (request.url.encodedPath.endsWith("sections/40")) """{"data":{"id":40,"title":"Section","author":"Author","body":"Text","chapter_from":3,"verse_from":1,"chapter_to":4,"verse_to":2,"book_osis_code":"John"}}"""
            else { assertEquals("20", request.url.parameters["offset"]); assertEquals("A & B", request.url.parameters["q"])
                """{"data":[{"id":1,"slug":"source:book","title":"Book","module_code":"SOURCE"}],"total":107}""" }
            respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val api = BibleApiClient(HttpClient(engine) { configureBibleApiClient() }, "https://example.test/api")
        try {
            assertEquals(107, api.getStudyBooks("A & B", 20).total)
            assertEquals(4, api.getBookSection(1, 40).chapterTo)
        } finally { api.close() }
    }
}
