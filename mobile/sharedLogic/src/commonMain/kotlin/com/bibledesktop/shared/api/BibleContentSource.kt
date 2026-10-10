package com.bibledesktop.shared.api

/** Shared reading boundary: Android can combine the existing API with durable local content. */
interface BibleContentSource {
    suspend fun getStudyBooks(query: String = "", offset: Int = 0): StudyBookPage = error("Books are unavailable from this source")
    suspend fun getBookContents(book: Long, offset: Int = 0): BookContents = error("Books are unavailable from this source")
    suspend fun getBookSection(book: Long, section: Long): StudySection = error("Books are unavailable from this source")
    suspend fun getCommentaryModules(): List<CommentaryModule> = error("Commentaries are unavailable from this source")
    suspend fun getCanonicalSlug(canon: String, osis: String): String = error("Canonical book unavailable")
    suspend fun resolveCanonicalOsis(canon: String, bookSlug: String): String = error("Canonical book unavailable")
    suspend fun getCommentaries(book: String, chapter: Int?, modules: List<String>, offset: Int = 0): CommentaryPage = error("Commentaries are unavailable from this source")
    suspend fun getTranslations(language: String? = null): List<TranslationSummary>
    suspend fun getBooks(translationCode: String): List<BibleBook>
    suspend fun getChapter(translationCode: String, bookSlug: String, chapterNumber: Int): BibleChapter
    suspend fun getVerseLocations(translationCode: String, osis: List<String>): List<VerseLocation> = resolveStoredVerseLocations(translationCode, osis, this)
    suspend fun getCrossReferences(verseId: Long, translationCode: String): CrossReferences
    suspend fun getStrongTokens(verseId: Long, translationCode: String): StrongTokens
    suspend fun getStrongEntry(number: String, verseId: Long): StrongEntry
    suspend fun getPrayers(language: String): List<PrayerSummary>
    suspend fun getPrayer(id: Long): PrayerDetail
    suspend fun getCalendarDay(date: String, language: String, profile: String = "typikon-strict"): CalendarDay
    suspend fun getCalendarMonth(year: Int, month: Int, language: String): List<CalendarGridDay>
    suspend fun getCalendarService(date: String, language: String): CalendarServicePlan
    suspend fun getAutomaticCalendarService(date: String, calendarLanguage: String): AutomaticCalendarServicePlan = error("Automatic calendar text policy unavailable")
    fun close()
}
