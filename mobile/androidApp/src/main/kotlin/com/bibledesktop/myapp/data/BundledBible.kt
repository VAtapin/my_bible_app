package com.bibledesktop.myapp.data

import android.content.Context
import com.bibledesktop.shared.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.zip.GZIPInputStream

/** Public Synodal API snapshot shipped inside the APK, not a first-launch network job. */
internal object BundledBible {
    const val code = "BQ_RUSSIAN_RST_STRONG"
    private val lock = Mutex()
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    suspend fun install(context: Context, store: OfflineStore = OfflineStore(context)) = withContext(Dispatchers.IO) {
        lock.withLock {
            // Preserve a newer user-downloaded copy. A interrupted installation has no final package marker.
            if (store.read(biblePackageKey(code), BiblePackage.serializer())?.isInstalled == true) return@withLock
            GZIPInputStream(context.assets.open("bibles/synodal.bundle")).bufferedReader(Charsets.UTF_8).use { reader ->
                val pack = json.decodeFromString(BiblePackage.serializer(), requireNotNull(reader.readLine()))
                require(pack.translation.code == code && pack.isInstalled)
                var count = 0
                val identities = mutableSetOf<String>()
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val line = reader.readLine() ?: break
                    if (line.isBlank()) continue
                    val chapter = json.decodeFromString(BibleChapter.serializer(), line)
                    val book = pack.books.firstOrNull { it.slug == chapter.book.slug } ?: error("Unknown bundled book")
                    require(chapter.translation.code == code && chapter.chapter.number in 1..book.chaptersCount)
                    require(chapter.verses.size == chapter.chapter.versesCount && chapter.verses.any { it.plainText.isNotBlank() })
                    val key = chapterKey(code, book.slug, chapter.chapter.number)
                    require(identities.add(key))
                    val saved = store.read(key, BibleChapter.serializer())
                    if (saved == null || saved.translation.code != code || saved.book.slug != book.slug || saved.chapter.number != chapter.chapter.number
                        || saved.verses.size != saved.chapter.versesCount || saved.verses.none { it.plainText.isNotBlank() }) {
                        store.write(key, BibleChapter.serializer(), chapter)
                    }
                    count++
                }
                require(count == pack.done && count + pack.unavailable.size == pack.total)
                store.write("books:$code", ListSerializer(BibleBook.serializer()), pack.books)
                // Commit visibility last, so partial/cancelled copies are resumed rather than advertised as ready.
                store.write(biblePackageKey(code), BiblePackage.serializer(), pack)
            }
        }
    }
}
