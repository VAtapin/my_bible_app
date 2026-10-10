package com.bibledesktop.shared.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable data class DictionaryModule(val code: String, val name: String, @SerialName("language_code") val language: String? = null, val kind: String, @SerialName("content_version") val version: String? = null, @SerialName("entries_count") val entries: Int, @SerialName("media_count") val media: Int, @SerialName("word_forms_count") val forms: Int)
@Serializable data class DictionaryTopic(val id: Long, val key: String, val topic: String, @SerialName("module_code") val moduleCode: String? = null, @SerialName("module_name") val moduleName: String? = null)
@Serializable data class DictionaryMedia(val id: Long, @SerialName("fragment_id") val fragment: String, val url: String)
@Serializable data class DictionaryLink(val label: String, val key: String, val topic: String)
@Serializable data class DictionaryReference(@SerialName("book_slug") val book: String, @SerialName("chapter_number") val chapter: Int? = null, @SerialName("verse_from") val first: Int? = null, @SerialName("verse_to") val last: Int? = null, @SerialName("book_osis") val bookOsis: String? = null)
@Serializable data class DictionaryArticle(val id: Long, val key: String, val topic: String, val body: String, val media: List<DictionaryMedia>, val links: List<DictionaryLink>, val references: List<DictionaryReference>)
@Serializable data class DictionaryPage(val data: List<DictionaryTopic>, val total: Int)
@Serializable data class DictionaryWordForm(@SerialName("module_code") val moduleCode: String, @SerialName("standard_form") val standard: String)
class DictionaryApi(private val client: HttpClient = createPlatformHttpClient(), private val baseUrl: String = "https://bible-desktop.com/api") : AutoCloseable {
    private fun code(value: String): String { require(Regex("[A-Za-z0-9][A-Za-z0-9_.-]*").matches(value)); return value }
    suspend fun modules(): List<DictionaryModule> = client.get("$baseUrl/dictionaries").body<ApiEnvelope<List<DictionaryModule>>>().data.also { require(it.all { m -> m.entries >= 0 && m.media >= 0 && m.forms >= 0 }) }
    suspend fun entries(module: String, query: String = "", offset: Int = 0): DictionaryPage { require(offset >= 0 && query.length <= 120); return client.get("$baseUrl/dictionaries/${code(module)}/entries") { parameter("q", query); parameter("offset", offset); parameter("limit", 30) }.body<DictionaryPage>().also { require(it.total >= 0 && it.data.all { e -> e.id > 0 && Regex("[a-f0-9]{40}").matches(e.key) }) } }
    suspend fun article(module: String, key: String): DictionaryArticle { require(Regex("[a-f0-9]{40}").matches(key)); return client.get("$baseUrl/dictionaries/${code(module)}/entries/$key").body<ApiEnvelope<DictionaryArticle>>().data.also { require(it.key == key && it.id > 0); it.media.forEach { image -> require(image.url == "/api/dictionaries/$module/media/${image.id}") } } }
    suspend fun lookup(query: String, modules: List<String>): List<DictionaryWordForm> { require(query.isNotBlank() && query.length <= 120 && modules.size <= 30); return client.get("$baseUrl/dictionaries/lookup") { parameter("q", query); parameter("modules", modules.joinToString(",")) }.body<ApiEnvelope<List<DictionaryWordForm>>>().data }
    suspend fun context(book: String, chapter: Int?, modules: List<String>, offset: Int = 0): DictionaryPage { require(Regex("[A-Za-z0-9_-]+").matches(book) && (chapter == null || chapter > 0) && modules.size <= 30 && offset >= 0); val path = "$baseUrl/bible/books/$book" + (chapter?.let { "/chapters/$it" } ?: "") + "/dictionary-entries"; return client.get(path) { parameter("modules", modules.joinToString(",")); parameter("offset", offset); parameter("limit", 30) }.body() }
    suspend fun verse(id: Long, modules: List<String>, offset: Int = 0): DictionaryPage { require(id > 0 && modules.size <= 30 && offset >= 0); return client.get("$baseUrl/verses/$id/dictionary-entries") { parameter("modules", modules.joinToString(",")); parameter("offset", offset); parameter("limit", 30) }.body() }
    fun imageUrl(module: String, media: DictionaryMedia): String { require(media.url == "/api/dictionaries/${code(module)}/media/${media.id}"); return baseUrl.removeSuffix("/api") + media.url }
    override fun close() = client.close()
}
