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
) : BibleContentSource {
    constructor(context: Context) : this(BibleApiClient(), OfflineStore(context), { isConnected(context) })
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

    override suspend fun getTranslations(language: String?) = content("translations:${language.orEmpty()}", ListSerializer(TranslationSummary.serializer())) { remote.getTranslations(language) }
    override suspend fun getBooks(translationCode: String) = content("books:$translationCode", ListSerializer(BibleBook.serializer())) { remote.getBooks(translationCode) }
    override suspend fun getChapter(translationCode: String, bookSlug: String, chapterNumber: Int) = content("chapter:$translationCode:$bookSlug:$chapterNumber", BibleChapter.serializer()) { remote.getChapter(translationCode, bookSlug, chapterNumber) }
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

internal fun isConnected(context: Context): Boolean {
    val manager = context.getSystemService(ConnectivityManager::class.java)
    return manager.getNetworkCapabilities(manager.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
}
