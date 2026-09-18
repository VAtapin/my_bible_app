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
    val excerpt: String,
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
)
