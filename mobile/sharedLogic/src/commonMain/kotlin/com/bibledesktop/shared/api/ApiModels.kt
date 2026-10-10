package com.bibledesktop.shared.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class ApiEnvelope<T>(
    val data: T,
)

@Serializable
data class LanguageSummary(
    val code: String,
    val name: String,
    @SerialName("native_name") val nativeName: String? = null,
)

@Serializable
data class TranslationSummary(
    val code: String,
    val name: String,
    @SerialName("short_name") val shortName: String? = null,
    val language: LanguageSummary,
    @SerialName("canon_code") val canonCode: String? = null,
    @SerialName("has_old_testament") val hasOldTestament: Boolean = true,
    @SerialName("has_new_testament") val hasNewTestament: Boolean = true,
    @SerialName("has_apocrypha") val hasApocrypha: Boolean = false,
    @SerialName("has_strong") val hasStrong: Boolean = false,
    @SerialName("is_default") val isDefault: Boolean = false,
    @SerialName("offline_size_estimate_bytes") val offlineSizeEstimateBytes: Long? = null,
    @SerialName("content_revision") val contentRevision: String? = null,
)

@Serializable
data class CanonicalBookSummary(
    @SerialName("osis_code") val osisCode: String,
    val testament: String,
    @SerialName("is_deuterocanonical") val isDeuterocanonical: Boolean = false,
)

@Serializable
data class BibleBook(
    val slug: String,
    val name: String,
    @SerialName("short_name") val shortName: String? = null,
    val order: Int = 0,
    @SerialName("chapters_count") val chaptersCount: Int,
    @SerialName("canonical_book") val canonicalBook: CanonicalBookSummary? = null,
)

@Serializable
internal data class BibleBooksPayload(
    val translation: TranslationSummary,
    val books: List<BibleBook>,
)

@Serializable
data class ChapterSummary(
    val number: Int,
    @SerialName("verses_count") val versesCount: Int,
)

@Serializable
data class BibleVerse(
    val id: Long,
    val number: Int,
    @SerialName("osis_ref") val osisRef: String,
    val text: String,
    @SerialName("plain_text") val plainText: String,
    @SerialName("has_strong_markup") val hasStrongMarkup: Boolean = false,
    @SerialName("markup_format") val markupFormat:String?=null,
    val annotations:SourceAnnotations?=null,
)

@Serializable
data class BibleChapter(
    val translation: TranslationSummary,
    val book: BibleBook,
    val chapter: ChapterSummary,
    val verses: List<BibleVerse>,
)

@Serializable
data class PrayerSummary(
    val id: Long,
    @SerialName("language_code") val languageCode: String,
    val category: String,
    @SerialName("liturgy_key") val liturgyKey: String? = null,
    val title: String,
    @SerialName("short_title") val shortTitle: String? = null,
    val intro: String? = null,
    val excerpt: String?,
    @SerialName("canonical_slug") val canonicalSlug: String? = null,
    @SerialName("liturgical_work_id") val liturgicalWorkId: Long? = null,
    val group: String? = null,
    val groups: List<String> = emptyList(),
    @SerialName("available_languages") val availableLanguages: List<String> = emptyList(),
    val completeness: String? = null,
    @SerialName("review_status") val reviewStatus: String? = null,
    @SerialName("content_revision") val contentRevision: String? = null,
    @SerialName("catalog_visible") val catalogVisible: Boolean? = null,
)

@Serializable
data class PrayerSection(
    val id: Long,
    val title: String? = null,
    @SerialName("sort_order") val sortOrder: Int,
)

@Serializable
data class PrayerDetail(
    val id: Long,
    @SerialName("language_code") val languageCode: String,
    val category: String,
    @SerialName("liturgy_key") val liturgyKey: String? = null,
    val title: String,
    @SerialName("short_title") val shortTitle: String? = null,
    val intro: String? = null,
    val body: String,
    @SerialName("source_url") val sourceUrl: String? = null,
    val sections: List<PrayerSection> = emptyList(),
    @SerialName("plain_text") val plainText: String? = null,
    @SerialName("canonical_slug") val canonicalSlug: String? = null,
    @SerialName("liturgical_work_id") val liturgicalWorkId: Long? = null,
    val group: String? = null,
    val groups: List<String> = emptyList(),
    @SerialName("available_languages") val availableLanguages: List<String> = emptyList(),
    val completeness: String? = null,
    @SerialName("review_status") val reviewStatus: String? = null,
    @SerialName("content_revision") val contentRevision: String? = null,
    @SerialName("catalog_visible") val catalogVisible: Boolean? = null,
)

@Serializable
data class CalendarEventType(
    val code: String,
    val name: String,
)

@Serializable
data class CalendarEvent(
    val id: String,
    val name: String,
    @SerialName("is_icon_commemoration") val isIconCommemoration: Boolean = false,
    @SerialName("is_fasting") val isFasting: Boolean = false,
    val type: CalendarEventType? = null,
    @SerialName("type_code") val typeCode: Int? = null,
    @SerialName("typikon_mark") val typikonMark: CalendarMark? = null,
    val description: String? = null,
)

@Serializable
data class CalendarReading(
    val id: String,
    val title: String,
    @SerialName("display_ref") val displayRef: String,
    @SerialName("passage_ref") val passageRef: String,
)

@Serializable
data class CalendarDay(
    val date: String,
    @SerialName("old_style_date") val oldStyleDate: String,
    @SerialName("pascha_date") val paschaDate: String,
    @SerialName("liturgical_period") val liturgicalPeriod: String,
    val source: String,
    val events: List<CalendarEvent> = emptyList(),
    @SerialName("fasting_events") val fastingEvents: List<CalendarEvent> = emptyList(),
    val readings: List<CalendarReading> = emptyList(),
    val food: CalendarFood? = null,
    val icons: List<CalendarIcon> = emptyList(),
    @SerialName("memorial_markers") val memorialMarkers: List<CalendarMark> = emptyList(),
    @SerialName("other_events") val otherEvents: List<CalendarOtherEvent> = emptyList(),
    @SerialName("day_style") val dayStyle: CalendarDayStyle? = null,
    val tone: Int? = null,
    @SerialName("week_after_pentecost") val weekAfterPentecost: Int? = null,
)

@Serializable
data class CalendarFood(val label: String, val reason: String? = null, val color: String? = null,
    @SerialName("image_url") val imageUrl: String? = null)

@Serializable
data class CalendarMark(val label: String, @SerialName("image_url") val imageUrl: String)

@Serializable
data class CalendarOtherEvent(val id: String, val name: String, val category: String, val description: String? = null)

@Serializable
data class CalendarIcon(
    val id: Long,
    val title: String,
    @SerialName("image_url") val imageUrl: String? = null,
    val imagePreviewUrl: String? = null,
    val credit: String? = null,
    @SerialName("local_caching_allowed") val localCachingAllowed: Boolean = false,
    val description: String? = null,
    val images: List<CalendarIconImage> = emptyList(),
    val dates: List<CalendarIconDate> = emptyList(),
)

@Serializable
data class CalendarIconImage(val url: String, val previewUrl: String? = null)
@Serializable
data class CalendarIconDate(val label: String)

@Serializable
data class CalendarServicePlan(
    val date: String,
    val textLanguage: String,
    val assignments: List<CalendarServiceText> = emptyList(),
    val expansions: List<CalendarServiceExpansion> = emptyList(),
    val properCoverage: CalendarServiceCoverage? = null,
)

@Serializable
data class CalendarServiceText(val title: String, val slot: String, val text: String, val insert: Boolean = false, val rubric: String? = null)
@Serializable
data class CalendarServiceExpansion(val id: String, val title: String, val text: String)
@Serializable
data class CalendarServiceCoverage(val message: String? = null)

@Serializable
data class CalendarGridDay(
    val date: String,
    val oldStyleDate: String,
    val weekday: Int,
    val dayStyle: CalendarDayStyle,
    val foodLabel: String,
    val fastingColor: String,
    val events: List<CalendarGridEvent> = emptyList(),
)

@Serializable
data class CalendarDayStyle(val rank: String, val color: String, val fontWeight: Int)

@Serializable
data class CalendarGridEvent(
    val id: String,
    val title: String,
    val typeCode: Int,
    val category: String,
    val typikonMark: CalendarGridMark? = null,
)

@Serializable
data class CalendarGridMark(val label: String, val svgSource: String)
