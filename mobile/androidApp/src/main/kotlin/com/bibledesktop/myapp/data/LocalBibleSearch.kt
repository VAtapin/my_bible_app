package com.bibledesktop.myapp.data

import android.database.sqlite.SQLiteDatabase
import com.bibledesktop.shared.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.text.Normalizer
import java.util.Locale

/** Rebuildable local index, separate from installed text and personal notes. No network source. */
internal class LocalBibleSearch(private val store: OfflineStore, private val indexFile: File) {
    companion object { private val mutex = Mutex() }
    private fun normalized(text: String) = Normalizer.normalize(text, Normalizer.Form.NFC).lowercase(Locale.ROOT)
    private suspend fun openIndex(codes: Set<String>, progress: (Int, Int) -> Unit): SQLiteDatabase {
        indexFile.parentFile?.mkdirs()
        val db = SQLiteDatabase.openOrCreateDatabase(indexFile, null)
        try {
            db.execSQL("CREATE TABLE IF NOT EXISTS editions (code TEXT PRIMARY KEY, stamp INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS verses (code TEXT, book TEXT, book_name TEXT, book_order INTEGER, testament TEXT, osis TEXT, chapter INTEGER, verse INTEGER, reference TEXT, text TEXT, raw TEXT, normalized TEXT, stems TEXT, PRIMARY KEY(code, book, chapter, verse))")
            val columns=db.rawQuery("PRAGMA table_info(verses)",null).use{cursor->buildSet{while(cursor.moveToNext())add(cursor.getString(1))}}
            if("stems" !in columns) { db.execSQL("ALTER TABLE verses ADD COLUMN stems TEXT");db.delete("editions",null,null) }
            db.execSQL("CREATE INDEX IF NOT EXISTS verses_code ON verses(code, book_order, chapter, verse)")
            val packages = store.biblePackages().filter { it.isInstalled && it.translation.code in codes }
            require(packages.map { it.translation.code }.toSet() == codes) { "Only installed Bibles can be searched" }
            val total = packages.sumOf { it.done }
            var done = 0
            for (pack in packages) {
                val code = pack.translation.code
                val stamp = store.savedAt(biblePackageKey(code))
                val current = db.rawQuery("SELECT stamp FROM editions WHERE code=?", arrayOf(code)).use { it.moveToFirst() && it.getLong(0) == stamp }
                if (current) { done += pack.done; progress(done, total); continue }
                db.beginTransaction()
                try {
                    db.delete("verses", "code=?", arrayOf(code))
                    val stemming=pack.translation.language.code.takeIf { it in stemmingLanguages }?.let(::SearchStemming)
                    val insert = db.compileStatement("INSERT INTO verses VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)")
                    try {
                        for (book in pack.books.sortedBy { it.order }) for (number in 1..book.chaptersCount) {
                            currentCoroutineContext().ensureActive()
                            val chapter = store.read(chapterKey(code, book.slug, number), BibleChapter.serializer())
                            require(chapter != null || "${book.name} $number" in pack.unavailable) { "Installed chapter is missing or unreadable" }
                            if (chapter != null) {
                                require(chapter.translation.code == code && chapter.book.slug == book.slug && chapter.chapter.number == number)
                                chapter.verses.filter { it.plainText.isNotBlank() }.forEach { verse ->
                                    insert.clearBindings()
                                    val stems=stemming?.tokens(verse.plainText)?.distinct()?.joinToString(" ",prefix=" ",postfix=" ").orEmpty()
                                    listOf(code, book.slug, book.name, book.order.toString(), book.canonicalBook?.testament.orEmpty(), book.canonicalBook?.osisCode.orEmpty(), number.toString(), verse.number.toString(), verse.osisRef, verse.plainText, if (verse.hasStrongMarkup) verse.text else "", normalized(verse.plainText),stems).forEachIndexed { n, value -> insert.bindString(n + 1, value) }
                                    insert.executeInsert()
                                }
                                done++
                            }
                            progress(done, total)
                        }
                    } finally { insert.close() }
                    db.execSQL("INSERT OR REPLACE INTO editions VALUES (?,?)", arrayOf<Any>(code, stamp))
                    db.setTransactionSuccessful()
                } finally { db.endTransaction() }
            }
            return db
        } catch (error: Throwable) { db.close(); throw error }
    }
    suspend fun search(codes: Set<String>, query: String, match: VerseSearchMatch, scope: VerseSearchScope, book: String? = null,
        offset: Int = 0, progress: (Int, Int) -> Unit = { _, _ -> }): LocalVerseSearchPage = withContext(Dispatchers.IO) {
        require(codes.isNotEmpty() && query.trim().length in 2..500 && offset >= 0)
        require(match != VerseSearchMatch.STRONG || Regex("[HG]\\d{1,5}", RegexOption.IGNORE_CASE).matches(query.trim()))
        mutex.withLock {
            val db = openIndex(codes, progress)
            try {
                val tokens = Regex("[\\p{L}\\p{M}\\p{N}]+").findAll(normalized(query)).map { it.value }.toList()
                val longest = tokens.maxByOrNull(String::length) ?: ""
                val escaped = longest.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
                val clauses = mutableListOf("code IN (${codes.joinToString(",") { "?" }})")
                val args = codes.toMutableList()
                if (match == VerseSearchMatch.MORPHOLOGY) {
                    val selected=store.biblePackages().filter { it.translation.code in codes }
                    require(selected.all { it.translation.language.code in stemmingLanguages }) { "Unsupported stemming language" }
                    val variants=selected.map { pack ->
                        val stems=SearchStemming(pack.translation.language.code).tokens(query).distinct()
                        require(stems.isNotEmpty())
                        args+=pack.translation.code
                        args+=stems.map { "% $it %" }
                        "(code=? AND ${stems.joinToString(" AND "){"stems LIKE ?"}})"
                    }
                    clauses+="(${variants.joinToString(" OR ")})"
                } else if (match != VerseSearchMatch.STRONG) { clauses += "normalized LIKE ? ESCAPE '\\'"; args += "%$escaped%" }
                when (scope) {
                    VerseSearchScope.OLD -> { clauses += "testament=?"; args += "old" }
                    VerseSearchScope.NEW -> { clauses += "testament=?"; args += "new" }
                    VerseSearchScope.PSALMS -> { clauses += "osis=?"; args += "Ps" }
                    else -> Unit
                }
                if (book != null) { clauses += "osis=?"; args += book }
                var total = 0
                val hits = mutableListOf<VerseSearchHit>()
                db.rawQuery("SELECT code, book, book_name, chapter, verse, reference, text, raw FROM verses WHERE ${clauses.joinToString(" AND ")} ORDER BY code, book_order, chapter, verse", args.toTypedArray()).use { cursor ->
                    while (cursor.moveToNext()) {
                        currentCoroutineContext().ensureActive()
                        val plain = cursor.getString(6)
                        if (match != VerseSearchMatch.MORPHOLOGY && !verseMatches(normalized(plain), normalized(query), match, cursor.getString(7))) continue
                        if (total >= offset && hits.size < 50) hits += VerseSearchHit(cursor.getString(0), cursor.getString(1), cursor.getString(2), cursor.getInt(3), cursor.getInt(4), cursor.getString(5), plain)
                        total++
                    }
                }
                LocalVerseSearchPage(hits, total, store.biblePackages().filter { it.translation.code in codes }.sumOf { it.unavailable.size })
            } finally { db.close() }
        }
    }
}
