package com.bibledesktop.shared.api

/** Shared reading boundary: Android can combine the existing API with durable local content. */
interface BibleContentSource {
    suspend fun getTranslations(language: String? = null): List<TranslationSummary>
    suspend fun getBooks(translationCode: String): List<BibleBook>
    suspend fun getChapter(translationCode: String, bookSlug: String, chapterNumber: Int): BibleChapter
    suspend fun getPrayers(language: String): List<PrayerSummary>
    suspend fun getPrayer(id: Long): PrayerDetail
    suspend fun getCalendarDay(date: String, language: String, profile: String = "typikon-strict"): CalendarDay
    suspend fun getCalendarMonth(year: Int, month: Int, language: String): List<CalendarGridDay>
    suspend fun getCalendarService(date: String, language: String): CalendarServicePlan
    fun close()
}
