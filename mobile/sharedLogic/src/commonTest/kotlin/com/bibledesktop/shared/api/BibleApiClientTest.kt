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

    @Test
    fun decodesBooksCatalog() = runBlocking {
        val engine = MockEngine { request ->
            assertEquals("/api/translations/RST/books", request.url.encodedPath)
            respond(
                content = """{"data":{"translation":{"code":"RST","name":"Synodal","language":{"code":"ru","name":"Russian"}},"books":[{"slug":"genesis","name":"Бытие","order":1,"chapters_count":50,"canonical_book":{"osis_code":"Gen","testament":"old"}}]}}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = BibleApiClient(
            client = HttpClient(engine) { configureBibleApiClient() },
            baseUrl = "https://example.test/api",
        )

        val book = client.getBooks("RST").single()

        assertEquals("Бытие", book.name)
        assertEquals(50, book.chaptersCount)
        assertEquals("old", book.canonicalBook?.testament)
        client.close()
    }

    @Test
    fun decodesBibleChapter() = runBlocking {
        val engine = MockEngine { request ->
            assertEquals("/api/translations/RST/books/john/chapters/3", request.url.encodedPath)
            respond(
                content = """{"data":{"translation":{"code":"RST","name":"Synodal","language":{"code":"ru","name":"Russian"}},"book":{"slug":"john","name":"Иоанна","chapters_count":21},"chapter":{"number":3,"verses_count":36},"verses":[{"id":16,"number":16,"osis_ref":"John.3.16","text":"text","plain_text":"plain text"}]}}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = BibleApiClient(
            client = HttpClient(engine) { configureBibleApiClient() },
            baseUrl = "https://example.test/api",
        )

        val chapter = client.getChapter("RST", "john", 3)

        assertEquals("Иоанна", chapter.book.name)
        assertEquals(3, chapter.chapter.number)
        assertEquals("John.3.16", chapter.verses.single().osisRef)
        assertEquals("plain text", chapter.verses.single().plainText)
        client.close()
    }

    @Test
    fun filtersPrayerResponseByRequestedLanguage() = runBlocking {
        val engine = MockEngine { request ->
            assertEquals("de", request.url.parameters["language"])
            respond(
                content = """{"data":[{"id":1,"language_code":"ru","category":"common","title":"Отче наш","excerpt":"..."},{"id":2,"language_code":"de","category":"common","title":"Vaterunser","excerpt":"..."}]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = BibleApiClient(
            client = HttpClient(engine) { configureBibleApiClient() },
            baseUrl = "https://example.test/api",
        )

        val prayers = client.getPrayers("de")

        assertEquals(listOf("Vaterunser"), prayers.map(PrayerSummary::title))
        client.close()
    }

    @Test
    fun decodesCalendarDayAndPassesParameters() = runBlocking {
        val engine = MockEngine { request ->
            assertEquals("2026-09-18", request.url.parameters["date"])
            assertEquals("de", request.url.parameters["lang"])
            assertEquals("typikon-strict", request.url.parameters["profile"])
            respond(
                content = """{"data":{"date":"2026-09-18","old_style_date":"2026-09-05","pascha_date":"2026-04-12","liturgical_period":"Freitag","source":"engine","events":[{"id":"1","name":"Gedenktag","is_icon_commemoration":false,"is_fasting":false}],"fasting_events":[{"id":"food","name":"Fasten","is_fasting":true}],"readings":[{"id":"r1","title":"Eph 1:7-17","display_ref":"Eph 1:7-17","passage_ref":"Eph.1.7"}]}}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = BibleApiClient(
            client = HttpClient(engine) { configureBibleApiClient() },
            baseUrl = "https://example.test/api",
        )

        val day = client.getCalendarDay("2026-09-18", "de")

        assertEquals("Freitag", day.liturgicalPeriod)
        assertEquals("Gedenktag", day.events.single().name)
        assertEquals("Eph.1.7", day.readings.single().passageRef)
        client.close()
    }
}
