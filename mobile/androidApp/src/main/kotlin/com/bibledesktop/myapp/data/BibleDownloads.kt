package com.bibledesktop.myapp.data

import android.content.Context
import androidx.work.*
import com.bibledesktop.shared.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.IOException
import java.util.concurrent.TimeUnit

internal fun biblePackageKey(code: String) = "bible-package:$code"
internal fun chapterKey(code: String, slug: String, number: Int) = "chapter:$code:$slug:$number"

@Serializable internal data class BibleRefreshPass(val token:String,val books:List<BibleBook>,val completed:List<String> = emptyList(),val finished:Boolean=false,val previousBooks:List<BibleBook> = emptyList(),val translation:TranslationSummary?=null,val sourceEmptyKeys:List<String> = emptyList(),val attempted:Boolean=false)
internal fun bibleRefreshKey(code:String)="bible-refresh:$code"

/** One chapter is committed atomically. A restarted job audits actual files, not a hopeful counter. */
internal class BibleDownloadEngine(
    private val source: BibleContentSource, private val store: OfflineStore,
    private val pause: suspend () -> Unit = { delay(350) },
    private val shouldYield: () -> Boolean = { false },
) {
    private val json = Json { explicitNulls = false }
    private fun validate(chapter: BibleChapter, code: String, book: BibleBook, number: Int) {
        require(chapter.translation.code == code && chapter.book.slug == book.slug && chapter.chapter.number == number)
        require(chapter.verses.isNotEmpty() && chapter.verses.size == chapter.chapter.versesCount) {
            "Incomplete chapter $code/${book.slug}/$number: ${chapter.verses.size}/${chapter.chapter.versesCount}"
        }
        require(chapter.verses.all { it.number > 0 && it.osisRef.isNotBlank() })
        require(chapter.verses.any { it.plainText.isNotBlank() })
        require(chapter.verses.map { it.osisRef }.distinct().size == chapter.verses.size)
    }
    suspend fun download(code: String, refreshToken:String?=null, progress: suspend (BiblePackage) -> Unit): Boolean {
        require(code.isNotBlank())
        var catalog = store.read("translations:available:", ListSerializer(TranslationSummary.serializer()))
            ?: store.read("translations:", ListSerializer(TranslationSummary.serializer()))
            ?: source.getTranslations().also { store.write("translations:available:", ListSerializer(TranslationSummary.serializer()), it) }
        if (catalog.none { it.code == code }) {
            catalog = source.getTranslations()
            store.write("translations:available:", ListSerializer(TranslationSummary.serializer()), catalog)
        }
        val translation = catalog.firstOrNull { it.code == code } ?: error("Unknown translation")
        var pack = store.read(biblePackageKey(code), BiblePackage.serializer()) ?: run {
            pause()
            val books = source.getBooks(code)
            require(books.isNotEmpty() && books.size <= 200 && books.all { it.chaptersCount in 1..200 })
            require(books.map { it.slug }.distinct().size == books.size)
            store.write("books:$code", ListSerializer(BibleBook.serializer()), books)
            BiblePackage(translation, books).also { store.write(biblePackageKey(code), BiblePackage.serializer(), it) }
        }
        var refreshPass=store.read(bibleRefreshKey(code),BibleRefreshPass.serializer())
        if(refreshToken!=null && refreshPass?.token!=refreshToken){
            pause();val freshCatalog=source.getTranslations()
            val freshTranslation=freshCatalog.firstOrNull{it.code==code} ?: error("Unknown translation")
            store.write("translations:available:",ListSerializer(TranslationSummary.serializer()),freshCatalog)
            pause();val freshBooks=source.getBooks(code)
            require(freshBooks.isNotEmpty()&&freshBooks.size<=200&&freshBooks.all{it.chaptersCount in 1..200}&&freshBooks.map{it.slug}.distinct().size==freshBooks.size)
            refreshPass=BibleRefreshPass(refreshToken,freshBooks,previousBooks=(pack.books+refreshPass?.previousBooks.orEmpty()).distinctBy{it.slug to it.chaptersCount},translation=freshTranslation)
            store.write(bibleRefreshKey(code),BibleRefreshPass.serializer(),refreshPass)
        }
        val targetTranslation=refreshPass?.translation
        if(refreshPass?.finished==false)pack=BiblePackage(pack.translation,refreshPass.books)
        require(pack.translation.code == code && pack.total == pack.books.sumOf { it.chaptersCount })
        require(pack.books.isNotEmpty() && pack.books.size <= 200 && pack.books.all { it.slug.isNotBlank() && it.chaptersCount in 1..200 })
        require(pack.books.map { it.slug }.distinct().size == pack.books.size && pack.total > 0)
        store.write("books:$code", ListSerializer(BibleBook.serializer()), pack.books)
        // Repair missing/corrupt chapter records even if an old checkpoint said complete.
        val missing = mutableListOf<Pair<BibleBook, Int>>()
        var bytes = 0L
        for (book in pack.books) for (number in 1..book.chaptersCount) {
            currentCoroutineContext().ensureActive()
            val saved = store.read(chapterKey(code, book.slug, number), BibleChapter.serializer())
            if (chapterKey(code,book.slug,number) !in refreshPass?.sourceEmptyKeys.orEmpty() && (refreshPass?.finished!=false || chapterKey(code,book.slug,number) in refreshPass.completed) && saved != null && runCatching { validate(saved, code, book, number) }.isSuccess && saved.verses.none { it.plainText.isBlank() })
                bytes += json.encodeToString(BibleChapter.serializer(), saved).encodeToByteArray().size
            else missing += book to number
        }
        pack = pack.copy(done = pack.total - missing.size, bytes = bytes, complete = missing.isEmpty(), unavailable = emptyList(), missingVerses = emptyList())
        store.write(biblePackageKey(code), BiblePackage.serializer(), pack)
        if(pack.complete&&targetTranslation!=null){pack=pack.copy(translation=targetTranslation);store.write(biblePackageKey(code),BiblePackage.serializer(),pack)}
        progress(pack)
        for ((book, number) in missing) {
            currentCoroutineContext().ensureActive()
            if (shouldYield()) return false
            pause()
            val chapter = source.getChapter(code, book.slug, number)
            require(chapter.translation.code == code && chapter.book.slug == book.slug && chapter.chapter.number == number)
            if (chapter.verses.size == chapter.chapter.versesCount && chapter.verses.all { it.plainText.isBlank() }) {
                if(refreshPass!=null){refreshPass=refreshPass.copy(sourceEmptyKeys=(refreshPass.sourceEmptyKeys+chapterKey(code,book.slug,number)).distinct());store.write(bibleRefreshKey(code),BibleRefreshPass.serializer(),refreshPass)}
                pack = pack.copy(unavailable = pack.unavailable + "${book.name} $number", complete = false)
                store.write(biblePackageKey(code), BiblePackage.serializer(), pack)
                progress(pack)
                continue // API has no text. Continue the rest, never manufacture or count this chapter.
            }
            validate(chapter, code, book, number)
            val key = chapterKey(code, book.slug, number)
            if(refreshPass!=null && chapter.verses.any{it.plainText.isBlank()} && store.read(key,BibleChapter.serializer())!=null){
                // A partial replacement is not allowed to erase the readable installed chapter.
                refreshPass=refreshPass.copy(sourceEmptyKeys=(refreshPass.sourceEmptyKeys+key).distinct())
                store.write(bibleRefreshKey(code),BibleRefreshPass.serializer(),refreshPass)
                pack=pack.copy(missingVerses=(pack.missingVerses+chapter.verses.filter{it.plainText.isBlank()}.map{it.osisRef}).distinct(),complete=false)
                store.write(biblePackageKey(code),BiblePackage.serializer(),pack);progress(pack)
                continue
            }
            store.write(key, BibleChapter.serializer(), chapter)
            val saved = store.read(key, BibleChapter.serializer()) ?: error("Chapter not committed")
            validate(saved, code, book, number)
            if(refreshPass!=null){refreshPass=refreshPass.copy(completed=(refreshPass.completed+key).distinct(),sourceEmptyKeys=refreshPass.sourceEmptyKeys-key);store.write(bibleRefreshKey(code),BibleRefreshPass.serializer(),refreshPass)}
            pack = pack.copy(done = pack.done + 1,
                bytes = pack.bytes + json.encodeToString(BibleChapter.serializer(), saved).encodeToByteArray().size,
                missingVerses = pack.missingVerses + saved.verses.filter { it.plainText.isBlank() }.map { it.osisRef },
                complete = pack.done + 1 == pack.total && pack.missingVerses.isEmpty() && saved.verses.none { it.plainText.isBlank() })
            if(pack.complete&&targetTranslation!=null)pack=pack.copy(translation=targetTranslation)
            store.write(biblePackageKey(code), BiblePackage.serializer(), pack)
            progress(pack)
        }
        if(refreshPass!=null)store.write(bibleRefreshKey(code),BibleRefreshPass.serializer(),refreshPass.copy(finished=pack.complete,attempted=true))
        if(pack.isInstalled)store.scheduleBibleSearch(code)
        return true
    }
}

internal object BibleDownloads {
    const val name = "native-bible-offline"
    private val enqueueLock = Mutex()
    internal val downloadLock = Mutex()
    suspend fun enqueue(context: Context, code: String, wifi: Boolean, refresh:Boolean=false) = withContext(Dispatchers.IO) {
        enqueueLock.withLock {
            require(code.isNotBlank() && code.length <= 128)
            val manager = WorkManager.getInstance(context)
            if (manager.getWorkInfosByTag(name).get().any { !it.state.isFinished && "bible-code:$code" in it.tags }) return@withLock
            val builder = OneTimeWorkRequestBuilder<BibleDownloadWorker>()
                .addTag(name).addTag("bible-code:$code").addTag("enqueued-at:${System.currentTimeMillis()}")
                .setInputData(workDataOf("code" to code,"refresh-token" to if(refresh)java.util.UUID.randomUUID().toString()else null))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(if (wifi) NetworkType.UNMETERED else NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
            if(refresh)builder.addTag("bible-refresh")
            val request=builder.build()
            check(context.getSharedPreferences(name, Context.MODE_PRIVATE).edit()
                .putString("code", code).putString("id", request.id.toString()).putString("id:$code", request.id.toString()).putBoolean("wifi", wifi).commit())
            manager.enqueueUniqueWork("$name:$code", ExistingWorkPolicy.KEEP, request).result.get()
        }
    }
}

/** Bounded batches stay below WorkManager's time limit; subsequent runs resume durable chapters. */
class BibleDownloadWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val code = inputData.getString("code")?.takeIf { it.isNotBlank() } ?: return Result.failure()
        val source = BibleApiClient()
        val store = OfflineStore(applicationContext)
        try {
            if (System.currentTimeMillis() < (store.read("bible-api-retry-at", Long.serializer()) ?: 0)) return Result.retry()
            val complete = BibleDownloads.downloadLock.withLock {
                val deadline = android.os.SystemClock.elapsedRealtime() + 6 * 60_000
                BibleDownloadEngine(source, store, shouldYield = { android.os.SystemClock.elapsedRealtime() >= deadline }).download(code,refreshToken=inputData.getString("refresh-token")) {
                    setProgress(workDataOf("done" to it.done, "total" to it.total))
                }
            }
            if (!complete) return Result.retry()
            return if (store.read(biblePackageKey(code), BiblePackage.serializer())?.complete == true) Result.success()
                else Result.failure(workDataOf("error" to "source-empty"))
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: IOException) { return if (runAttemptCount < 5) Result.retry() else Result.failure(workDataOf("error" to "network-or-storage")) }
        catch (error: Exception) {
            if (isRetryableBibleFailure(error) && runAttemptCount < 5) {
                val now = System.currentTimeMillis()
                store.write("bible-api-retry-at", Long.serializer(), now + bibleRetryDelayMillis(error).coerceAtMost(Long.MAX_VALUE - now))
                return Result.retry()
            }
            return Result.failure(workDataOf("error" to error.javaClass.simpleName))
        } finally { source.close() }
    }
}
