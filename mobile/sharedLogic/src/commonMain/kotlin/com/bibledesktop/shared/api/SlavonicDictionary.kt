package com.bibledesktop.shared.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.TimeMark
import kotlin.time.TimeSource

const val SlavonicDictionaryModule = "AZBYKA_CU_FULL"

@Serializable data class SlavonicWord(
    val key: String, val headword: String, val civil: String, val definition: String,
    @SerialName("body_format") val format: String,
    @SerialName("source_url") val sourceUrl: String,
    @SerialName("source_chapter") val chapter: Int,
    @SerialName("content_hash") val hash: String,
)
@Serializable data class SlavonicLetter(val chapter: Int, val label: String, val count: Int)
@Serializable data class SlavonicPage(val data: List<SlavonicWord>, val total: Int, val offset: Int, val limit: Int,
    @SerialName("module_code") val module: String, @SerialName("content_version") val version: String)
@Serializable internal data class SlavonicAlphabet(val data: List<SlavonicLetter>, @SerialName("module_code") val module: String)
@Serializable internal data class SlavonicArticle(val key: String, @SerialName("module_code") val module: String, val lexical: SlavonicWord)

interface SlavonicDictionarySource {
    suspend fun slavonicSearch(query: String = "", meaning: Boolean = false, letter: Int? = null, offset: Int = 0): SlavonicPage
    suspend fun slavonicAlphabet(): List<SlavonicLetter>
    suspend fun slavonicArticle(key: String): SlavonicWord
}
class SlavonicRateLimitException(val retryMillis: Long) : Exception("Dictionary rate limit")

internal fun SlavonicWord.validate(): SlavonicWord = also {
    require(Regex("[a-f0-9]{40}").matches(key) && Regex("[a-f0-9]{64}").matches(hash))
    require(format == "plain-text" && chapter in 1..36 && headword.isNotBlank())
    require(sourceUrl == "https://azbyka.ru/otechnik/Spravochniki/polnyj-pravoslavnyj-tserkovnoslavjanskij-slovar/$chapter")
}

/** Uses the existing dictionary HTTP client. Never silently substitutes a stale corpus. */
internal class SlavonicDictionaryRequests(private val client: HttpClient, private val base: String) : SlavonicDictionarySource {
    private var limitedAt: TimeMark? = null
    private var limitedMillis = 0L
    private suspend fun <T> request(block: suspend () -> T): T {
        val remaining = limitedAt?.let { limitedMillis - it.elapsedNow().inWholeMilliseconds } ?: 0L
        if (remaining > 0) throw SlavonicRateLimitException(remaining)
        try { return block() } catch (error: Exception) {
            if (!isRateLimitedBibleFailure(error)) throw error
            limitedMillis = bibleRetryDelayMillis(error)
            limitedAt = TimeSource.Monotonic.markNow()
            throw SlavonicRateLimitException(limitedMillis)
        }
    }
    override suspend fun slavonicSearch(query: String, meaning: Boolean, letter: Int?, offset: Int): SlavonicPage {
        require(query.length <= 120 && offset in 0..1_000_000 && (letter == null || letter in 1..36))
        return request { client.get("$base/dictionaries/$SlavonicDictionaryModule/search") {
            parameter("q", query.trim()); parameter("mode", if (meaning) "meaning" else "word")
            parameter("offset", offset); parameter("limit", 30); letter?.let { parameter("letter", it) }
        }.body<SlavonicPage>().also { page ->
            require(page.module == SlavonicDictionaryModule && page.offset == offset && page.limit == 30 && page.total >= 0)
            require(Regex("[a-f0-9]{64}").matches(page.version))
            page.data.forEach { it.validate() }
        } }
    }
    override suspend fun slavonicAlphabet(): List<SlavonicLetter> = request {
        client.get("$base/dictionaries/$SlavonicDictionaryModule/alphabet").body<SlavonicAlphabet>().also {
            require(it.module == SlavonicDictionaryModule)
            require(it.data.all { letter -> letter.chapter in 1..36 && letter.count >= 0 && letter.label.isNotBlank() })
            require(it.data.map { letter -> letter.chapter }.distinct().size == it.data.size)
        }.data
    }
    override suspend fun slavonicArticle(key: String): SlavonicWord {
        require(Regex("[a-f0-9]{40}").matches(key))
        return request { client.get("$base/dictionaries/$SlavonicDictionaryModule/entries/$key")
            .body<ApiEnvelope<SlavonicArticle>>().data.also {
                require(it.module == SlavonicDictionaryModule && it.key == key && it.lexical.key == key)
            }.lexical.validate() }
    }
}
