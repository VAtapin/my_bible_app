package com.bibledesktop.myapp.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.bibledesktop.shared.api.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

/** Local-first reads; downloads explicitly refresh the same storage through this boundary. */
internal class OfflineContentRepository(
    private val remote: BibleContentSource,
    internal val store: OfflineStore,
    private val canRefresh: () -> Boolean = { false },
    private val installBuiltIn: suspend () -> Unit = {},
) : BibleContentSource {
    constructor(context: Context) : this(BibleApiClient(), OfflineStore(context), { isConnected(context) }, { BundledBible.install(context) })
    private val refreshScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshing = ConcurrentHashMap.newKeySet<String>()

    private suspend fun <T> content(key: String, serializer: kotlinx.serialization.KSerializer<T>, fetch: suspend () -> T): T {
        store.read(key, serializer)?.let { cached ->
            if (canRefresh() && System.currentTimeMillis() - store.savedAt(key) > 24 * 60 * 60 * 1000L && refreshing.add(key)) {
                refreshScope.launch {
                    try { store.write(key, serializer, fetch()) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { /* Keep the previously committed public content; retry on a later opening. */ }
                    finally { refreshing.remove(key) }
                }
            }
            return cached
        }
        val value = fetch()
        // A failed durable write is a real error; never promise this resource is available offline.
        store.write(key, serializer, value)
        return store.read(key, serializer) ?: error("Saved content is unreadable")
    }

    override suspend fun getTranslations(language: String?): List<TranslationSummary> {
        installBuiltIn()
        val serializer = ListSerializer(TranslationSummary.serializer())
        val saved = store.read("translations:available:${language.orEmpty()}", serializer)
            ?: store.read("translations:${language.orEmpty()}", serializer)
        if (saved != null) return saved
        if (!canRefresh()) {
            val local = store.biblePackages().filter { it.isInstalled }.map { it.translation }.filter { language == null || it.language.code == language }
            if (local.isNotEmpty()) return local
        }
        return remote.getTranslations(language).also { store.write("translations:available:${language.orEmpty()}", serializer, it) }
    }
    suspend fun refreshTranslations(): List<TranslationSummary> {
        if (!canRefresh()) return store.read("translations:available:", ListSerializer(TranslationSummary.serializer()))
            ?: store.read("translations:", ListSerializer(TranslationSummary.serializer())) ?: getTranslations()
        return remote.getTranslations().also { store.write("translations:available:", ListSerializer(TranslationSummary.serializer()), it) }
    }
    suspend fun installedTranslations(): List<TranslationSummary> {
        installBuiltIn()
        return store.biblePackages().filter { it.isInstalled }.map { it.translation }
            .sortedBy { if (it.code == BundledBible.code) 0 else 1 }
    }
    override suspend fun getBooks(translationCode: String): List<BibleBook> {
        installBuiltIn()
        return store.read("books:$translationCode", ListSerializer(BibleBook.serializer())) ?: throw BibleNotInstalled(translationCode)
    }
    override suspend fun getChapter(translationCode: String, bookSlug: String, chapterNumber: Int): BibleChapter {
        installBuiltIn()
        return store.read(chapterKey(translationCode, bookSlug, chapterNumber), BibleChapter.serializer())
            ?.takeIf { it.translation.code == translationCode && it.book.slug == bookSlug && it.chapter.number == chapterNumber && it.verses.isNotEmpty() }
            ?: throw BibleNotInstalled(translationCode)
    }
    private suspend fun <T> studyContent(key: String, serializer: kotlinx.serialization.KSerializer<T>, fetch: suspend () -> T): T {
        if (!canRefresh()) store.read(key, serializer)?.let { return it }
        val value = try { fetch() } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: java.io.IOException) { return store.read(key, serializer) ?: throw error }
        store.write(key, serializer, value)
        return value
    }
    override suspend fun getStudyBooks(query: String, offset: Int) = studyContent("study-books:$query:$offset", StudyBookPage.serializer()) { remote.getStudyBooks(query, offset) }
    override suspend fun getBookContents(book: Long, offset: Int) = studyContent("study-contents:$book:$offset", BookContents.serializer()) { remote.getBookContents(book, offset) }
    override suspend fun getBookSection(book: Long, section: Long) = studyContent("study-article:$book:$section", StudySection.serializer()) { remote.getBookSection(book, section) }
    override suspend fun getCommentaryModules() = studyContent("commentary-modules", ListSerializer(CommentaryModule.serializer())) { remote.getCommentaryModules() }
    override suspend fun getCanonicalSlug(canon: String, osis: String) = content("canonical:$canon:$osis", kotlinx.serialization.serializer<String>()) { remote.getCanonicalSlug(canon, osis) }
    override suspend fun getCommentaries(book: String, chapter: Int?, modules: List<String>, offset: Int) = studyContent("commentaries:$book:$chapter:${modules.sorted().joinToString(",")}:$offset", CommentaryPage.serializer()) { remote.getCommentaries(book, chapter, modules, offset) }
    override suspend fun getCrossReferences(verseId: Long, translationCode: String) = content("references:$translationCode:$verseId", CrossReferences.serializer()) { remote.getCrossReferences(verseId, translationCode) }
    override suspend fun getStrongTokens(verseId: Long, translationCode: String) = content("tokens:$translationCode:$verseId", StrongTokens.serializer()) { remote.getStrongTokens(verseId, translationCode) }
    override suspend fun getStrongEntry(number: String, verseId: Long) = content("strong:$number:$verseId", StrongEntry.serializer()) { remote.getStrongEntry(number, verseId) }
    override suspend fun getPrayers(language: String) = content("prayers:$language", ListSerializer(PrayerSummary.serializer())) { remote.getPrayers(language) }
    override suspend fun getPrayer(id: Long) = content("prayer:$id", PrayerDetail.serializer()) { remote.getPrayer(id) }
    override suspend fun getCalendarDay(date: String, language: String, profile: String) = content("day:$date:${calendarContentLanguage(language)}:$profile", CalendarDay.serializer()) { remote.getCalendarDay(date, language, profile) }
    override suspend fun getCalendarMonth(year: Int, month: Int, language: String) = content("month:$year:$month:${calendarContentLanguage(language)}", ListSerializer(CalendarGridDay.serializer())) { remote.getCalendarMonth(year, month, language) }
    override suspend fun getCalendarService(date: String, language: String) = content("service:$date:${calendarContentLanguage(language)}", CalendarServicePlan.serializer()) { remote.getCalendarService(date, language) }

    suspend fun refreshMonth(year: Int, month: Int, language: String) {
        val value = remote.getCalendarMonth(year, month, language)
        store.write("month:$year:$month:${calendarContentLanguage(language)}", ListSerializer(CalendarGridDay.serializer()), value)
    }
    suspend fun refreshDay(date: String, language: String): CalendarDay {
        val day = remote.getCalendarDay(date, language)
        require(day.date == date)
        store.write("day:$date:${calendarContentLanguage(language)}:typikon-strict", CalendarDay.serializer(), day)
        return day
    }
    suspend fun refreshService(date: String, language: String) {
        val plan = remote.getCalendarService(date, language)
        store.write("service:$date:${calendarContentLanguage(language)}", CalendarServicePlan.serializer(), plan)
    }
    override fun close() { refreshScope.cancel(); remote.close() }
}

internal class BibleNotInstalled(code: String) : IllegalStateException("Bible text is not installed: $code")

internal fun isConnected(context: Context): Boolean {
    val manager = context.getSystemService(ConnectivityManager::class.java)
    return manager.getNetworkCapabilities(manager.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
}
