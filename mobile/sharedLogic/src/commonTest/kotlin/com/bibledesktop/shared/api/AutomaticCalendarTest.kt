package com.bibledesktop.shared.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.*

class AutomaticCalendarTest {
    private val date = "2026-10-10"
    private fun edition(language: String, id: Long) = CalendarTextEdition(
        id, language, if (language == "cu") "traditional" else "civil", "real-$id", "liturgical-corpus",
    )
    private fun item(language: String, available: List<CalendarTextEdition>, preferred: Boolean) = AutomaticCalendarText(
        title = "Same source title", text = "Actual $language body", familyId = "work:real-work",
        workSlug = "real-work", language = language, orthography = available.first { it.language == language }.orthography,
        edition = available.first { it.language == language }.edition, sourceKind = "liturgical-corpus",
        selectedTextId = available.first { it.language == language }.selectedTextId,
        contentHash = "a".repeat(64), availableEditions = available,
        selection = if (preferred) "preferred" else "fallback", missingReason = null,
        textId = "calendar-assignment", slot = "troparion", insert = true,
    )
    private fun plan(language: String, texts: List<AutomaticCalendarText>) = AutomaticCalendarServicePlan(
        2, AutomaticCalendarPolicy, language, date, "mixed", texts, emptyList(),
    )

    @Test fun priorityIsPerTextAndKeepsActualScriptAndEditionIdentity() {
        assertEquals(listOf("cu", "cu-civil", "ru"), calendarTextPriority("ru"))
        assertEquals(listOf("cu", "cu-civil", "uk", "ru"), calendarTextPriority("uk"))
        assertEquals(listOf("de", "cu", "cu-civil", "ru"), calendarTextPriority("de"))
        assertEquals(listOf("en", "cu", "cu-civil", "ru"), calendarTextPriority("en"))
        val cu = edition("cu", 1)
        val civil = edition("cu-civil", 2)
        val ru = edition("ru", 3)
        val mixed = plan("uk", listOf(
            item("cu", listOf(cu, civil, ru), true),
            item("cu-civil", listOf(civil, ru), false).copy(familyId = "work:second-work", workSlug = "second-work"),
            item("ru", listOf(ru), false).copy(familyId = "work:third-work", workSlug = "third-work"),
        ))
        assertEquals(listOf("cu", "cu-civil", "ru"), mixed.validateAutomaticCalendar(date, "uk").assignments.map { it.language })
        assertEquals(listOf(1L, 2L, 3L), mixed.assignments.map { it.selectedTextId })
        assertFailsWith<IllegalArgumentException> { plan("uk", listOf(item("ru", listOf(cu, ru), false))).validateAutomaticCalendar(date, "uk") }
        assertFailsWith<IllegalArgumentException> { plan("ru", listOf(item("cu", listOf(cu), true).copy(orthography = "civil"))).validateAutomaticCalendar(date, "ru") }
        assertFailsWith<IllegalArgumentException> { mixed.copy(calendarLanguage = "ru").validateAutomaticCalendar(date, "uk") }
    }

    @Test fun missingOneTextDoesNotInventItsEditionOrReplaceAnotherText() {
        val missing = AutomaticCalendarText("Same source title", "", "work:missing-work", "missing-work", null, null, null, null, null, null,
            emptyList(), "missing", "no-published-edition", textId = "calendar-missing", slot = "kontakion")
        val value = plan("de", listOf(item("de", listOf(edition("de", 12)), true), missing))
        assertEquals("", value.validateAutomaticCalendar(date, "de").assignments.last().text)
        assertFailsWith<IllegalArgumentException> { value.copy(assignments = listOf(missing.copy(text = "Wrong inherited body"))).validateAutomaticCalendar(date, "de") }
        assertFailsWith<IllegalArgumentException> { value.copy(assignments = listOf(value.assignments.first().copy(familyId = "work:another-work"))).validateAutomaticCalendar(date, "de") }
        assertNotEquals(automaticCalendarServiceKey(date, "en"), automaticCalendarServiceKey(date, "ru"))
    }

    @Test fun clientUsesOriginalCalendarLanguagePolicyAndRejectsLegacyOrContractFailures() = runBlocking {
        assertTrue(isCalendarTransportFailure(io.ktor.utils.io.errors.IOException("Transport interrupted")))
        assertTrue(isCalendarTransportFailure(io.ktor.client.network.sockets.SocketTimeoutException("Timed out")))
        assertFalse(isCalendarTransportFailure(kotlinx.coroutines.CancellationException("Cancelled")))
        assertFalse(isCalendarTransportFailure(IllegalArgumentException("Invalid contract")))
        assertFalse(isCalendarTransportFailure(kotlinx.serialization.SerializationException("Invalid response")))
        val value = plan("en", listOf(item("cu", listOf(edition("cu", 1)), false)))
        var payload = """{"data":${Json.encodeToString(AutomaticCalendarServicePlan.serializer(), value)}}"""
        var status = HttpStatusCode.OK
        val client = BibleApiClient(HttpClient(MockEngine { request ->
            assertEquals("en", request.url.parameters["calendar_lang"])
            assertEquals(AutomaticCalendarPolicy, request.url.parameters["text_policy"])
            assertNull(request.url.parameters["lang"])
            respond(payload, status, headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureBibleApiClient() }, "https://example.test/api")
        try {
            assertEquals(value, client.getAutomaticCalendarService(date, "en"))
            payload = """{"data":{"schemaVersion":1,"date":"$date","textLanguage":"cu-civil","assignments":[],"expansions":[]}}"""
            assertTrue(runCatching { client.getAutomaticCalendarService(date, "en") }.isFailure)
            payload = """{"data":${Json.encodeToString(AutomaticCalendarServicePlan.serializer(), value.copy(textPolicy = "other-policy"))}}"""
            assertTrue(runCatching { client.getAutomaticCalendarService(date, "en") }.isFailure)
            status = HttpStatusCode.Forbidden
            val accessFailure = runCatching { client.getAutomaticCalendarService(date, "en") }.exceptionOrNull()
            assertNotNull(accessFailure)
            assertFalse(isCalendarTransportFailure(accessFailure))
        } finally { client.close() }
    }
}
