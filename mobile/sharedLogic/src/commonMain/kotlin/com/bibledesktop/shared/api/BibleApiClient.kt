package com.bibledesktop.shared.api

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

private const val DefaultApiBaseUrl = "https://bible-desktop.com/api"

class BibleApiClient internal constructor(
    private val client: HttpClient,
    private val baseUrl: String = DefaultApiBaseUrl,
) {
    constructor() : this(createPlatformHttpClient())

    suspend fun getTranslations(language: String? = null): List<TranslationSummary> {
        val response = client.get("$baseUrl/translations") {
            language?.takeIf(String::isNotBlank)?.let { parameter("language", it) }
        }

        return response.body<ApiEnvelope<List<TranslationSummary>>>().data
    }

    suspend fun getBooks(translationCode: String): List<BibleBook> {
        val response = client.get("$baseUrl/translations/$translationCode/books")
        return response.body<ApiEnvelope<BibleBooksPayload>>().data.books
    }

    suspend fun getChapter(
        translationCode: String,
        bookSlug: String,
        chapterNumber: Int,
    ): BibleChapter {
        val response = client.get(
            "$baseUrl/translations/$translationCode/books/$bookSlug/chapters/$chapterNumber",
        )
        return response.body<ApiEnvelope<BibleChapter>>().data
    }

    suspend fun getPrayers(language: String): List<PrayerSummary> {
        val response = client.get("$baseUrl/prayers") {
            parameter("language", language)
        }
        return response.body<ApiEnvelope<List<PrayerSummary>>>().data
            .filter { it.languageCode == language }
    }

    suspend fun getPrayer(id: Long): PrayerDetail {
        val response = client.get("$baseUrl/prayers/$id")
        return response.body<ApiEnvelope<PrayerDetail>>().data
    }

    suspend fun getCalendarDay(
        date: String,
        language: String,
        profile: String = "typikon-strict",
    ): CalendarDay {
        val response = client.get("$baseUrl/calendar/day") {
            parameter("date", date)
            parameter("lang", calendarContentLanguage(language))
            parameter("profile", profile)
        }
        return response.body<ApiEnvelope<CalendarDay>>().data
    }

    suspend fun getCalendarMonth(year: Int, month: Int, language: String): List<CalendarGridDay> {
        require(year in 1900..2100 && month in 1..12)
        val response = client.get("$baseUrl/calendar/month") {
            parameter("year", year)
            parameter("month", month)
            parameter("lang", calendarContentLanguage(language))
            parameter("profile", "typikon-strict")
        }
        return response.body<ApiEnvelope<List<CalendarGridDay>>>().data
    }

    fun close() {
        client.close()
    }
}

/** English UI must not pretend that the calendar corpus has been translated. */
fun calendarContentLanguage(language: String): String = if (language == "en") "ru" else language

internal fun HttpClientConfig<*>.configureBibleApiClient() {
    expectSuccess = true

    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            },
        )
    }

    install(HttpTimeout) {
        requestTimeoutMillis = 15_000
        connectTimeoutMillis = 10_000
        socketTimeoutMillis = 15_000
    }
}

internal expect fun createPlatformHttpClient(): HttpClient
