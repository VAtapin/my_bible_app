package com.bibledesktop.myapp.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.bibledesktop.shared.api.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.long
import kotlinx.serialization.json.int
import kotlinx.serialization.json.JsonObject

/** Local-first reads; downloads explicitly refresh the same storage through this boundary. */
internal class OfflineContentRepository(
    private val remote: BibleContentSource,
    internal val store: OfflineStore,
    private val canRefresh: () -> Boolean = { false },
    private val installBuiltIn: suspend () -> Unit = {},
) : BibleContentSource {
    constructor(context: Context) : this(BibleApiClient(), OfflineStore(context), { isConnected(context) }, { BundledBible.install(context) }) {
        studyPackages = StudyPackageStore(context)
        strongLanguage = context.getSharedPreferences("bible-desktop-native-profile", Context.MODE_PRIVATE).getString("uiLanguage", "ru") ?: "ru"
        refreshScope.launch { try{BibleSearchIndexes.bootstrap(context.applicationContext)}catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){ /* Startup discovery retries next process. */ } }
    }
    private var studyPackages: StudyPackageStore? = null
    private fun installedStudyLibrary() = studyPackages?.let(::InstalledStudyLibrary)
    private var strongLanguage = "ru"
    internal fun useStudyPackages(packages: StudyPackageStore, language: String = "ru") { studyPackages = packages; strongLanguage = language }
    private val refreshScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshing = ConcurrentHashMap.newKeySet<String>()
    private val sourceVerseIdentities=ConcurrentHashMap<String,String>()

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
            ?.also { chapter -> chapter.verses.forEach { verse -> sourceVerseIdentities["$translationCode:${verse.id}"]=verse.osisRef } }
            ?: throw BibleNotInstalled(translationCode)
    }
    override suspend fun getVerseLocations(translationCode: String, osis: List<String>): List<VerseLocation> =
        resolveStoredVerseLocations(translationCode, osis, this, readChapter = { book, number -> store.read(chapterKey(translationCode, book, number), BibleChapter.serializer()) })
    private suspend fun <T> studyContent(key: String, serializer: kotlinx.serialization.KSerializer<T>, fetch: suspend () -> T): T {
        if (!canRefresh()) store.read(key, serializer)?.let { return it }
        val value = try { fetch() } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: java.io.IOException) { return store.read(key, serializer) ?: throw error }
        store.write(key, serializer, value)
        return value
    }
    override suspend fun getStudyBooks(query: String, offset: Int): StudyBookPage {
        if (!canRefresh()) installedStudyLibrary()?.books(query, offset)?.let { return it }
        return studyContent("study-books:$query:$offset", StudyBookPage.serializer()) { remote.getStudyBooks(query, offset) }
    }
    override suspend fun getBookContents(book: Long, offset: Int): BookContents {
        if (!canRefresh()) installedStudyLibrary()?.contents(book, offset)?.let { return it }
        return studyContent("study-contents:$book:$offset", BookContents.serializer()) { remote.getBookContents(book, offset) }
    }
    override suspend fun getBookSection(book: Long, section: Long): StudySection {
        if (!canRefresh()) installedStudyLibrary()?.article(book, section)?.let { return it }
        return studyContent("study-article:$book:$section", StudySection.serializer()) { remote.getBookSection(book, section) }
    }
    override suspend fun getCommentaryModules(): List<CommentaryModule> {
        if (!canRefresh()) installedStudyLibrary()?.modules()?.takeIf { it.isNotEmpty() }?.let { return it }
        return studyContent("commentary-modules", ListSerializer(CommentaryModule.serializer())) { remote.getCommentaryModules() }
    }
    override suspend fun getCanonicalSlug(canon: String, osis: String): String {
        if (!canRefresh()) installedStudyLibrary()?.canonicalSlug(osis)?.let { return it }
        return content("canonical:$canon:$osis", kotlinx.serialization.serializer<String>()) { remote.getCanonicalSlug(canon, osis) }
    }
    override suspend fun resolveCanonicalOsis(canon: String, bookSlug: String) = content("canonical-osis:$canon:$bookSlug", kotlinx.serialization.serializer<String>()) { remote.resolveCanonicalOsis(canon, bookSlug) }
    override suspend fun getCommentaries(book: String, chapter: Int?, modules: List<String>, offset: Int): CommentaryPage {
        if (!canRefresh()) installedStudyLibrary()?.commentaries(book, chapter, modules, offset)?.let { return it }
        return studyContent("commentaries:$book:$chapter:${modules.sorted().joinToString(",")}:$offset", CommentaryPage.serializer()) { remote.getCommentaries(book, chapter, modules, offset) }
    }
    override suspend fun getCrossReferences(verseId: Long, translationCode: String):CrossReferences =
        getCrossReferencesAt(verseId,translationCode,sourceVerseIdentities["$translationCode:$verseId"])
    internal suspend fun getCrossReferencesAt(verseId:Long,translationCode:String,sourceOsis:String?):CrossReferences {
        val packages=studyPackages
        if(packages?.has("CROSS_REFERENCES")==true){
            val rows=mutableListOf<JsonObject>();var offset=0
            do { val page=packages.rows("CROSS_REFERENCES","references",sourceOsis=sourceOsis,sourceApiId=if(sourceOsis==null)verseId else null,offset=offset,limit=500);rows+=page.rows;offset+=page.rows.size }while(offset<page.total)
            val identity=sourceOsis?:rows.firstOrNull()?.get("source_verse")?.jsonObject?.get("osis_ref")?.jsonPrimitive?.contentOrNull
                ?:error("Source verse identity unavailable in installed references")
            require(rows.all{it["source_verse"]?.jsonObject?.get("osis_ref")?.jsonPrimitive?.contentOrNull==identity})
            val verifier=StudyVersification.create(packages)
            var statuses=rows.map{row->verifier?.reference((row["source_code"] as? kotlinx.serialization.json.JsonPrimitive)?.takeIf{it.isString}?.contentOrNull.orEmpty(),identity,row.getValue("target_verse").jsonObject.getValue("osis_ref").jsonPrimitive.content,translationCode)?:ReferenceVersification()}
            if(statuses.any{it.verified}){
                val sourceLocations=getVerseLocations(translationCode,listOf(identity))
                if(sourceLocations.singleOrNull()?.let{it.osis==identity&&it.verseId==verseId}!=true)statuses=statuses.map{if(it.verified)it.copy(status="raw")else it}
            }
            val targets=rows.mapIndexedNotNull{index,row->if(statuses[index].verified)row.getValue("target_verse").jsonObject.getValue("osis_ref").jsonPrimitive.content else null}.distinct()
            val locations=targets.chunked(200).flatMap{getVerseLocations(translationCode,it)}.groupBy{it.osis}
            val chapters=mutableMapOf<Pair<String,Int>,BibleChapter>()
            val references=rows.mapIndexed{index,row->
                val target=row.getValue("target_verse").jsonObject
                val osis=target.getValue("osis_ref").jsonPrimitive.content
                var status=statuses[index]
                val candidates=locations[osis].orEmpty()
                if(status.verified&&candidates.size>1)status=status.copy(status="ambiguous")
                val location=if(status.verified)candidates.singleOrNull()else null
                val actual=location?.let{place->val key=place.book to place.chapter;val chapter=chapters[key]?:getChapter(translationCode,place.book,place.chapter).also{chapters[key]=it};require(chapter.translation.code==translationCode&&chapter.book.slug==place.book&&chapter.chapter.number==place.chapter);chapter to chapter.verses.single{it.osisRef==osis&&it.id==place.verseId&&it.number==place.verse&&it.plainText.isNotBlank()}}
                val book=location?.book?:target.getValue("book_slug").jsonPrimitive.content
                val chapter=location?.chapter?:target.getValue("chapter").jsonPrimitive.int
                val verse=location?.verse?:target.getValue("verse").jsonPrimitive.int
                val metadata=row["metadata"] as? JsonObject
                CrossReference(row.getValue("api_id").jsonPrimitive.long,
                    ReferenceTarget(location?.verseId?:target.getValue("api_id").jsonPrimitive.long,osis,actual?.let{"${it.first.book.name} $chapter:$verse"}?:osis,book,chapter,verse,if(status.verified)actual?.second?.plainText else null,versification=status),
                    row["type"]?.jsonPrimitive?.contentOrNull,row["source_name"]?.jsonPrimitive?.contentOrNull?:row["source_code"]?.jsonPrimitive?.contentOrNull,
                    CrossReferenceMetadata((metadata?.get("legacy_quote_id") as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull?.toLongOrNull(),metadata?.get("raw_ref")?.jsonPrimitive?.contentOrNull?:row["raw_range"]?.jsonPrimitive?.contentOrNull),versification=status)
            }
            return CrossReferences(StudyVerse(verseId,identity),translationCode,references)
        }
        return content("references:$translationCode:$verseId",CrossReferences.serializer()){remote.getCrossReferences(verseId,translationCode)}
    }
    override suspend fun getStrongTokens(verseId: Long, translationCode: String) = content("tokens:$translationCode:$verseId", StrongTokens.serializer()) { remote.getStrongTokens(verseId, translationCode) }
    override suspend fun getStrongEntry(number: String, verseId: Long): StrongEntry {
        val explicit = explicitStrongNumber(number)
        val packages = studyPackages
        if (explicit != null && packages?.has("STRONG") == true) {
            // Prefer the interface language; the user can choose any installed source in StrongWords.
            installedStrongEntries(explicit).firstOrNull()?.let { return it }
            // An installed complete lexicon that lacks a number is explicit, not a cache miss disguised by another source.
            error("Strong entry unavailable in installed lexicons: $explicit")
        }
        return content("strong:$number:$verseId", StrongEntry.serializer()) { remote.getStrongEntry(number, verseId) }
    }
    internal suspend fun installedStrongEntries(number:String):List<StrongEntry> {
        val explicit=explicitStrongNumber(number)?:return emptyList()
        val packages=studyPackages?.takeIf{it.has("STRONG")}?:return emptyList()
        val entries=packages.rows("STRONG","entries",ids=listOf(explicit),limit=500)
        val lexiconPage=packages.rows("STRONG","lexicons",limit=500)
        require(entries.total<=500&&lexiconPage.total<=500)
        val lexicons=lexiconPage.rows.associateBy{it["code"]?.jsonPrimitive?.contentOrNull}
        return entries.rows.map { value->
            val code=value["lexicon_code"]?.jsonPrimitive?.contentOrNull?:error("Missing lexicon")
            val lexicon=lexicons[code]?:error("Missing source metadata")
            fun field(name:String)=value[name]?.jsonPrimitive?.contentOrNull
            StrongEntry(explicit,field("word"),field("transliteration"),field("content"),StrongLexicon(lexicon["name"]?.jsonPrimitive?.contentOrNull?:error("Missing source name"),lexicon["language"]?.jsonPrimitive?.contentOrNull.orEmpty(),code),field("pronunciation"))
        }.sortedWith(compareBy({if(it.lexicon.language==strongLanguage)0 else 1},{it.lexicon.code.orEmpty()}))
    }
    override suspend fun getPrayers(language: String) = content("prayers:$language", ListSerializer(PrayerSummary.serializer())) { remote.getPrayers(language) }
    override suspend fun getPrayer(id: Long) = content("prayer:$id", PrayerDetail.serializer()) { remote.getPrayer(id) }
    override suspend fun getCalendarDay(date: String, language: String, profile: String) = content("day:$date:${calendarContentLanguage(language)}:$profile", CalendarDay.serializer()) { remote.getCalendarDay(date, language, profile) }
    override suspend fun getCalendarMonth(year: Int, month: Int, language: String) = content("month:$year:$month:${calendarContentLanguage(language)}", ListSerializer(CalendarGridDay.serializer())) { remote.getCalendarMonth(year, month, language) }
    override suspend fun getCalendarService(date: String, language: String) = content("service:$date:${calendarContentLanguage(language)}", CalendarServicePlan.serializer()) { remote.getCalendarService(date, language) }
    override suspend fun getAutomaticCalendarService(date: String, calendarLanguage: String): AutomaticCalendarServicePlan {
        val key = automaticCalendarServiceKey(date, calendarLanguage)
        val saved = try {
            store.read(key, AutomaticCalendarServicePlan.serializer())?.validateAutomaticCalendar(date, calendarLanguage)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IllegalArgumentException) {
            null // A corrupt or obsolete contract cannot render, but must not prevent online repair.
        }
        if (!canRefresh() && saved != null) return saved
        val value = try {
            remote.getAutomaticCalendarService(date, calendarLanguage).validateAutomaticCalendar(date, calendarLanguage)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (!isCalendarTransportFailure(error)) throw error
            return saved ?: throw error
        }
        store.write(key, AutomaticCalendarServicePlan.serializer(), value)
        return value
    }

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
    suspend fun refreshAutomaticService(date: String, calendarLanguage: String): AutomaticCalendarServicePlan {
        val plan = remote.getAutomaticCalendarService(date, calendarLanguage).validateAutomaticCalendar(date, calendarLanguage)
        store.write(automaticCalendarServiceKey(date, calendarLanguage), AutomaticCalendarServicePlan.serializer(), plan)
        return plan
    }
    override fun close() { refreshScope.cancel(); remote.close() }
}

internal class BibleNotInstalled(code: String) : IllegalStateException("Bible text is not installed: $code")

internal fun isConnected(context: Context): Boolean {
    val manager = context.getSystemService(ConnectivityManager::class.java)
    return manager.getNetworkCapabilities(manager.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
}
