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
    @Test fun decodesStudyAndUsesRealVerseIdentityAndTranslation() = runBlocking {
        val engine = MockEngine { request ->
            val path = request.url.encodedPath
            val body = when {
                path.endsWith("cross-references") -> {
                    assertEquals("/api/verses/42/cross-references", path)
                    assertEquals("RST", request.url.parameters["translation"])
                    """{"data":{"verse":{"id":42,"osis_ref":"John.3.16"},"translation_code":"RST","references":[{"id":1,"target":{"verse_id":99,"osis_ref":"1John.4.10","reference":"1 Иоанна 4:10","book_slug":"1john","chapter_number":4,"verse_number":10,"text":null}}]}}"""
                }
                path.endsWith("strong-tokens") -> {
                    assertEquals("RST", request.url.parameters["translation"])
                    """{"data":{"verse":{"id":42,"osis_ref":"John.3.16"},"tokens":[{"strong_number":"G25","entry":{"word":null}}]}}"""
                }
                else -> {
                    assertEquals("/api/strong/G25", path)
                    assertEquals("42", request.url.parameters["verse"])
                    """{"data":{"number":"G25","word":"ἀγαπάω","content":"<p>любить</p>","lexicon":{"name":"Dictionary","language":"ru"}}}"""
                }
            }
            respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val client = BibleApiClient(HttpClient(engine) { configureBibleApiClient() }, "https://example.test/api")
        try {
            val reference = client.getCrossReferences(42, "RST").references.single().target
            assertEquals("1John.4.10", reference.osisRef)
            assertEquals(null, reference.text)
            assertEquals("G25", client.getStrongTokens(42, "RST").tokens.single().number)
            assertEquals("ru", client.getStrongEntry("G25", 42).lexicon.language)
            assertTrue(runCatching { client.getStrongEntry("../invalid", 42) }.isFailure)
        } finally { client.close() }
    }

    @Test fun rejectsStudyForAnotherVerseOrTranslation() = runBlocking {
        for ((id, code) in listOf(43 to "RST", 42 to "OTHER")) {
            val engine = MockEngine { respond("""{"data":{"verse":{"id":$id,"osis_ref":"John.3.16"},"translation_code":"$code","references":[]}}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json")) }
            val client = BibleApiClient(HttpClient(engine) { configureBibleApiClient() }, "https://example.test/api")
            try { assertTrue(runCatching { client.getCrossReferences(42, "RST") }.isFailure) }
            finally { client.close() }
        }
    }
    @Test fun rateLimitsHonorRetryAfterAndAccessFailuresAreNotRetried() = runBlocking {
        for (status in listOf(HttpStatusCode.TooManyRequests, HttpStatusCode.Forbidden, HttpStatusCode.ServiceUnavailable)) {
            val engine = MockEngine { respond("error", status, headersOf(HttpHeaders.RetryAfter, "120")) }
            val client = BibleApiClient(HttpClient(engine) { configureBibleApiClient() }, "https://example.test/api")
            try {
                val error = runCatching { client.getTranslations() }.exceptionOrNull()!!
                assertEquals(status != HttpStatusCode.Forbidden, isRetryableBibleFailure(error))
                assertEquals(120_000L, bibleRetryDelayMillis(error))
            } finally { client.close() }
        }
    }

    @Test fun decodesCalendarIconsMarksAndService() = runBlocking {
        val engine = MockEngine { request ->
            val body = if (request.url.encodedPath.endsWith("service")) {
                assertEquals("sixth-hour", request.url.parameters["office"])
                assertEquals("full", request.url.parameters["expansion"])
                assertEquals("typikon-strict", request.url.parameters["profile"])
                assertEquals("ru", request.url.parameters["lang"])
                """{"data":{"date":"2026-10-08","textLanguage":"cu","assignments":[{"title":"Psalm","slot":"psalm","text":"text"}],"expansions":[{"id":"x","title":"Troparion","text":"text"}],"properCoverage":{"message":"Reference only"}}}"""
            } else """{"data":{"date":"2026-10-08","old_style_date":"2026-09-25","pascha_date":"2026-04-12","liturgical_period":"Thursday","source":"engine","events":[{"id":"1","name":"Saint","type_code":4,"typikon_mark":{"label":"Polyeleos","image_url":"/assets/typikon/polyeleos.svg"}}],"icons":[{"id":3054,"title":"Saint","image_url":"https://bible-desktop.com/storage/calendar-icons/image.png","imagePreviewUrl":"https://bible-desktop.com/api/calendar/icons/3054/images/7023?preview=1","images":[{"url":"original","previewUrl":"preview"}],"local_caching_allowed":true}],"memorial_markers":[{"label":"Memorial","image_url":"/assets/markers/minimal-dark/memorial.png"}]}}"""
            respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val client = BibleApiClient(HttpClient(engine) { configureBibleApiClient() }, "https://example.test/api")
        try {
            val day = client.getCalendarDay("2026-10-08", "en")
            assertEquals(4, day.events.single().typeCode)
            assertEquals("Polyeleos", day.events.single().typikonMark?.label)
            assertTrue(day.icons.single().localCachingAllowed)
            assertEquals("preview", day.icons.single().images.single().previewUrl)
            assertEquals("Memorial", day.memorialMarkers.single().label)
            val service = client.getCalendarService("2026-10-08", "en")
            assertEquals("cu", service.textLanguage)
            assertEquals("Psalm", service.assignments.single().title)
            assertEquals("Troparion", service.expansions.single().title)
        } finally { client.close() }
    }

    @Test fun decodesMonthAndUsesExplicitEnglishCorpusFallback() = runBlocking {
        val engine = MockEngine { request ->
            assertEquals("/api/calendar/month", request.url.encodedPath)
            assertEquals("2026", request.url.parameters["year"])
            assertEquals("10", request.url.parameters["month"])
            assertEquals("ru", request.url.parameters["lang"])
            respond("""{"data":[{"date":"2026-10-08","oldStyleDate":"2026-09-25","weekday":4,"dayStyle":{"rank":"vigil","color":"#333333","fontWeight":700},"foodLabel":"поста нет","fastingColor":"#333333","events":[{"id":"1","title":"Saint","typeCode":4,"category":"commemoration","typikonMark":{"label":"Vigil","svgSource":"/assets/typikon/vigil.svg"}}]}]}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val client = BibleApiClient(HttpClient(engine) { configureBibleApiClient() }, "https://example.test/api")
        try {
            val day = client.getCalendarMonth(2026, 10, "en").single()
            assertEquals(700, day.dayStyle.fontWeight)
            assertEquals("Vigil", day.events.single().typikonMark?.label)
        } finally { client.close() }
    }

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
