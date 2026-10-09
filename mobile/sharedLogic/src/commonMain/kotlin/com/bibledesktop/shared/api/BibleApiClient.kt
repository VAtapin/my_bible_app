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
) : BibleContentSource {
    constructor() : this(createPlatformHttpClient())

    override suspend fun getStudyBooks(query: String, offset: Int): StudyBookPage {
        require(offset >= 0 && query.length <= 100)
        return client.get("$baseUrl/books") { parameter("q", query); parameter("offset", offset); parameter("limit", 20) }
            .body<StudyBookPage>().also { require(it.total >= 0 && it.data.all { book -> book.id > 0 }) }
    }
    override suspend fun getBookContents(book: Long, offset: Int): BookContents {
        require(book > 0 && offset >= 0)
        return client.get("$baseUrl/books/$book") { parameter("offset", offset); parameter("limit", 20) }
            .body<ApiEnvelope<BookContents>>().data.also { require(it.book.id == book && it.total >= 0 && it.sections.all { section -> section.id > 0 }) }
    }
    override suspend fun getBookSection(book: Long, section: Long): StudySection {
        require(book > 0 && section > 0)
        return client.get("$baseUrl/books/$book/sections/$section").body<ApiEnvelope<StudySection>>().data
            .also { require(it.id == section && it.body != null) }
    }
    override suspend fun getCommentaryModules(): List<CommentaryModule> = client.get("$baseUrl/commentary-modules")
        .body<ApiEnvelope<List<CommentaryModule>>>().data
    override suspend fun getCanonicalSlug(canon: String, osis: String): String {
        require(Regex("[A-Za-z0-9_-]+").matches(canon))
        return client.get("$baseUrl/canons/$canon/books").body<ApiEnvelope<CanonBooks>>().data.books
            .firstOrNull { it.osisCode == osis }?.slug ?: error("Canonical book unavailable")
    }
    override suspend fun getCommentaries(book: String, chapter: Int?, modules: List<String>, offset: Int): CommentaryPage {
        require(Regex("[A-Za-z0-9_-]+").matches(book) && (chapter == null || chapter > 0) && offset >= 0 && modules.size in 1..30)
        val path = if (chapter == null) "$baseUrl/bible/books/$book/commentaries" else "$baseUrl/bible/books/$book/chapters/$chapter/commentaries"
        return client.get(path) { parameter("modules", modules.distinct().sorted().joinToString(",")); parameter("offset", offset); parameter("limit", 10) }
            .body<ApiEnvelope<CommentaryPage>>().data.also { require(it.book == book && it.chapter == chapter && it.total >= 0) }
    }

    override suspend fun getTranslations(language: String?): List<TranslationSummary> {
        val response = client.get("$baseUrl/translations") {
            parameter("catalog", "available") // Public editions, including those hidden from the short default list.
            language?.takeIf(String::isNotBlank)?.let { parameter("language", it) }
        }

        return response.body<ApiEnvelope<List<TranslationSummary>>>().data
    }

    override suspend fun getBooks(translationCode: String): List<BibleBook> {
        val response = client.get("$baseUrl/translations/$translationCode/books")
        return response.body<ApiEnvelope<BibleBooksPayload>>().data.books
    }

    override suspend fun getChapter(
        translationCode: String,
        bookSlug: String,
        chapterNumber: Int,
    ): BibleChapter {
        val response = client.get(
            "$baseUrl/translations/$translationCode/books/$bookSlug/chapters/$chapterNumber",
        )
        return response.body<ApiEnvelope<BibleChapter>>().data
    }

    override suspend fun getCrossReferences(verseId: Long, translationCode: String): CrossReferences {
        require(verseId > 0 && translationCode.isNotBlank())
        return client.get("$baseUrl/verses/$verseId/cross-references") {
            parameter("translation", translationCode)
        }.body<ApiEnvelope<CrossReferences>>().data.also {
            require(it.verse.id == verseId && it.translationCode == translationCode)
        }
    }

    override suspend fun getStrongTokens(verseId: Long, translationCode: String): StrongTokens {
        require(verseId > 0 && translationCode.isNotBlank())
        return client.get("$baseUrl/verses/$verseId/strong-tokens") {
            parameter("translation", translationCode)
        }.body<ApiEnvelope<StrongTokens>>().data.also { require(it.verse.id == verseId) }
    }

    override suspend fun getStrongEntry(number: String, verseId: Long): StrongEntry {
        require(Regex("[GH]?[0-9]{1,5}").matches(number) && verseId > 0)
        return client.get("$baseUrl/strong/$number") { parameter("verse", verseId) }
            .body<ApiEnvelope<StrongEntry>>().data
    }

    override suspend fun getPrayers(language: String): List<PrayerSummary> {
        val response = client.get("$baseUrl/prayers") {
            parameter("language", language)
        }
        return response.body<ApiEnvelope<List<PrayerSummary>>>().data
            .filter { it.languageCode == language }
    }

    override suspend fun getPrayer(id: Long): PrayerDetail {
        val response = client.get("$baseUrl/prayers/$id")
        return response.body<ApiEnvelope<PrayerDetail>>().data
    }

    override suspend fun getCalendarDay(
        date: String,
        language: String,
        profile: String,
    ): CalendarDay {
        val response = client.get("$baseUrl/calendar/day") {
            parameter("date", date)
            parameter("lang", calendarContentLanguage(language))
            parameter("profile", profile)
        }
        return response.body<ApiEnvelope<CalendarDay>>().data
    }

    override suspend fun getCalendarMonth(year: Int, month: Int, language: String): List<CalendarGridDay> {
        require(year in 1900..2100 && month in 1..12)
        val response = client.get("$baseUrl/calendar/month") {
            parameter("year", year)
            parameter("month", month)
            parameter("lang", calendarContentLanguage(language))
            parameter("profile", "typikon-strict")
        }
        return response.body<ApiEnvelope<List<CalendarGridDay>>>().data
    }

    override fun close() {
        client.close()
    }

    override suspend fun getCalendarService(date: String, language: String): CalendarServicePlan {
        val response = client.get("$baseUrl/calendar/service") {
            parameter("date", date)
            parameter("lang", calendarContentLanguage(language))
            parameter("office", "sixth-hour")
            parameter("expansion", "full")
            parameter("profile", "typikon-strict")
        }
        return response.body<ApiEnvelope<CalendarServicePlan>>().data.also { require(it.date == date) }
    }
}

/** English UI must not pretend that the calendar corpus has been translated. */
fun calendarContentLanguage(language: String): String = if (language == "en") "ru" else language

/** Background public downloads may retry rate limits and server failures, never access failures. */
fun isRetryableBibleFailure(error: Throwable): Boolean =
    error is io.ktor.client.plugins.ResponseException &&
        (error.response.status.value == 429 || error.response.status.value in 500..599)

fun bibleRetryDelayMillis(error: Throwable): Long {
    val seconds = (error as? io.ktor.client.plugins.ResponseException)?.response?.headers
        ?.get(io.ktor.http.HttpHeaders.RetryAfter)?.toLongOrNull()?.takeIf { it > 0 }
    return (seconds ?: 60L).coerceAtMost(Long.MAX_VALUE / 1000) * 1000
}

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
