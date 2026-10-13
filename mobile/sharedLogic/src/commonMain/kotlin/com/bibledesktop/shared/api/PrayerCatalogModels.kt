package com.bibledesktop.shared.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PrayerExternalSource(
    val language: String, val title: String, val url: String,
    val availability: String,
    @SerialName("offline_available") val offlineAvailable: Boolean,
    @SerialName("canonical_slug") val canonicalSlug:String? = null,
    val edition:String? = null,
)

@Serializable
data class PrayerCatalog(
    val data: List<PrayerSummary>,
    @SerialName("catalog_version") val catalogVersion: Int? = null,
    val groups: Map<String, String> = emptyMap(),
    @SerialName("external_sources") val externalSources: List<PrayerExternalSource> = emptyList(),
)

@Serializable
internal data class PrayerDetailEnvelope(
    val data: PrayerDetail,
    @SerialName("catalog_version") val catalogVersion: Int? = null,
)

@Serializable
data class LiturgicalEditionSummary(
    val code: String, val title: String, val language: String, val orthography: String,
    @SerialName("reader_profile") val readerProfile: String,
)

@Serializable
data class LiturgicalWorkSummary(
    val id: Long, val slug: String, val title: String,
    val collections: List<String>,
    @SerialName("available_languages") val availableLanguages: List<String>,
    val editions: List<LiturgicalEditionSummary>,
    @SerialName("source_url") val sourceUrl: String? = null,
    @SerialName("prayer_group") val prayerGroup: String? = null,
    @SerialName("prayer_groups") val prayerGroups: List<String> = emptyList(),
    val intro: String? = null,
    @SerialName("usage_titles") val usageTitles: List<String> = emptyList(),
    val completeness: String? = null,
    @SerialName("content_revision") val contentRevision: String? = null,
    @SerialName("legacy_slugs") val legacySlugs: List<String> = emptyList(),
)

@Serializable
data class LiturgicalBlock(val id: String, val kind: String, val text: String)

@Serializable
data class LiturgicalBookReference(val slug:String)
@Serializable
data class LiturgicalBookSection(val id:String,val title:String,@SerialName("printed_pages") val printedPages:List<Int> = emptyList(),val works:List<LiturgicalBookReference> = emptyList(),val startBlockId:String? = null,val endBlockId:String? = null)

@Serializable
data class LiturgicalWorkVersion(
    val slug: String, val title: String, val language: String, val edition: String,
    @SerialName("edition_title") val editionTitle: String,
    val orthography: String,
    @SerialName("reader_profile") val readerProfile: String,
    val blocks: List<LiturgicalBlock>, val credit: String,
    @SerialName("source_url") val sourceUrl: String,
    @SerialName("content_hash") val contentHash: String,
    @SerialName("review_status") val reviewStatus: String,
    val completeness: String? = null,
    val artifactType:String? = null,
    val bookCompleteness:String? = null,
    val sourceBook:String? = null,
    val printedPages:List<Int> = emptyList(),
    val sourceEditionNotes:List<String> = emptyList(),
    val bookSections:List<LiturgicalBookSection> = emptyList(),
)

private val prayerGroups = setOf("short", "rules", "occasions", "initial")
private fun validPrayerMetadata(slug: String?, work: Long?, group: String?, groups: List<String>, language: String,
    languages: List<String>, completeness: String?, review: String?, revision: String?, visible: Boolean?): Boolean =
    !slug.isNullOrBlank() && work != null && work > 0 && group in prayerGroups && group in groups && groups.all { it in prayerGroups } &&
        language in languages && languages.all { it.isNotBlank() } && completeness == "complete" && review == "source-verified" &&
        revision?.matches(Regex("[a-f0-9]{64}")) == true && visible != null

fun PrayerCatalog.validatePrayerCatalog(): PrayerCatalog {
    require(catalogVersion == null || catalogVersion == 2)
    require(data.all { it.id > 0 && it.title.isNotBlank() && it.languageCode.isNotBlank() })
    if (catalogVersion == 2) {
        require(groups.keys == prayerGroups && groups.values.all { it.isNotBlank() })
        require(data.all { validPrayerMetadata(it.canonicalSlug, it.liturgicalWorkId, it.group, it.groups, it.languageCode,
            it.availableLanguages, it.completeness, it.reviewStatus, it.contentRevision, it.catalogVisible) })
        require(externalSources.all { source ->
            val uri = io.ktor.http.Url(source.url)
            source.language.isNotBlank() && source.title.isNotBlank() && ((source.availability == "external-only" && !source.offlineAvailable)||
                    (source.availability=="full-book-internal"&&source.offlineAvailable&&source.canonicalSlug?.matches(Regex("[a-z0-9-]+"))==true&&!source.edition.isNullOrBlank())) &&
                uri.protocol.name == "https" && uri.host.isNotBlank() && uri.user == null && uri.password == null
        })
    } else require(groups.isEmpty() && externalSources.isEmpty())
    return this
}

fun PrayerDetail.validatePrayerDetail(version: Int? = null): PrayerDetail {
    require(version == null || version == 2)
    require(id > 0 && title.isNotBlank() && languageCode.isNotBlank())
    if (version == 2 || canonicalSlug != null || liturgicalWorkId != null) {
        require(validPrayerMetadata(canonicalSlug, liturgicalWorkId, group, groups, languageCode,
            availableLanguages, completeness, reviewStatus, contentRevision, catalogVisible))
        require(body.isNotBlank() && !plainText.isNullOrBlank())
    }
    return this
}

fun LiturgicalWorkSummary.validateLiturgicalWork(): LiturgicalWorkSummary {
    require(id > 0 && slug.isNotBlank() && title.isNotBlank())
    require(editions.all { it.code.isNotBlank() && it.language in availableLanguages })
    if (prayerGroup != null) {
        require(prayerGroup in prayerGroups && prayerGroup in setOf("short", "rules", "occasions", "initial"))
        require(completeness == "complete" && !contentRevision.isNullOrBlank())
    }
    return this
}

fun LiturgicalWorkVersion.validateLiturgicalVersion(requestedLanguage: String, requestedEdition: String? = null, requireReviewed: Boolean = false): LiturgicalWorkVersion {
    require(language == requestedLanguage && (requestedEdition == null || edition == requestedEdition))
    require(slug.isNotBlank() && edition.isNotBlank() && blocks.isNotEmpty())
    require(printedPages.all{it>0} && bookSections.all{section->
        section.id.isNotBlank() && section.title.isNotBlank() && section.printedPages.all{it>0} && section.works.all{it.slug.matches(Regex("[a-z0-9-]+"))} &&
        (section.startBlockId==null||blocks.any{it.id==section.startBlockId}) && (section.endBlockId==null||blocks.any{it.id==section.endBlockId}) && (section.startBlockId==null||section.endBlockId==null||blocks.indexOfFirst{it.id==section.startBlockId}<=blocks.indexOfFirst{it.id==section.endBlockId})
    })
    if (requireReviewed) require(completeness == "complete")
    if (completeness == "complete") {
        require(reviewStatus in setOf("prayer-reviewed", "source-verified-complete") && contentHash.matches(Regex("[a-f0-9]{64}")) && blocks.any { it.text.isNotBlank() })
        require(language != "cu" || orthography == "traditional")
        require(language != "cu-civil" || orthography in listOf("civil", "civil-accented"))
    }
    return this
}

/** Same transport boundary as calendar; no access/contract failure can resurrect an old prayer. */
fun isPrayerTransportFailure(error: Throwable): Boolean = isCalendarTransportFailure(error)

/** Only an actual final server response can revoke a previously published prayer request. */
fun isPrayerUnavailableFailure(error: Throwable): Boolean =
    error is io.ktor.client.plugins.ResponseException && error.response.status.value in setOf(404, 409)
