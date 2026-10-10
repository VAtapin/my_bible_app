package com.bibledesktop.myapp.data

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
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
    /** Called by installation/background work only. Each staged chapter survives process death. */
    suspend fun prepare(code: String, shouldYield: () -> Boolean = { false }, progress: (Int, Int) -> Unit = { _, _ -> }): Boolean = withContext(Dispatchers.IO) {
      mutex.withLock {
        val pack=store.read(biblePackageKey(code),BiblePackage.serializer()) ?: return@withLock true
        if(!pack.isInstalled) return@withLock true
        val refresh=store.read(bibleRefreshKey(code),BibleRefreshPass.serializer())
        if(refresh!=null&&!refresh.finished) return@withLock true
        val stamp=store.savedAt(biblePackageKey(code))
        indexFile.parentFile?.mkdirs()
        // Repair belongs to the worker so losing this shared DB requeues every installed edition.
        val db = SQLiteDatabase.openDatabase(indexFile.path,null,SQLiteDatabase.CREATE_IF_NECESSARY,android.database.DatabaseErrorHandler{_ -> })
        try {
            db.enableWriteAheadLogging()
            db.execSQL("CREATE TABLE IF NOT EXISTS editions (code TEXT PRIMARY KEY, stamp INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS verses (code TEXT, book TEXT, book_name TEXT, book_order INTEGER, testament TEXT, osis TEXT, chapter INTEGER, verse INTEGER, reference TEXT, text TEXT, raw TEXT, normalized TEXT, stems TEXT, PRIMARY KEY(code, book, chapter, verse))")
            val columns=db.rawQuery("PRAGMA table_info(verses)",null).use{cursor->buildSet{while(cursor.moveToNext())add(cursor.getString(1))}}
            if("stems" !in columns) { db.execSQL("ALTER TABLE verses ADD COLUMN stems TEXT");db.delete("editions",null,null) }
            val editionColumns=db.rawQuery("PRAGMA table_info(editions)",null).use{cursor->buildSet{while(cursor.moveToNext())add(cursor.getString(1))}}
            if("language" !in editionColumns){db.execSQL("ALTER TABLE editions ADD COLUMN language TEXT");db.execSQL("ALTER TABLE editions ADD COLUMN unavailable INTEGER NOT NULL DEFAULT 0");db.delete("editions",null,null)}
            db.execSQL("CREATE INDEX IF NOT EXISTS verses_code ON verses(code, book_order, chapter, verse)")
            db.execSQL("CREATE TABLE IF NOT EXISTS pending_verses AS SELECT * FROM verses WHERE 0")
            db.execSQL("CREATE TABLE IF NOT EXISTS building (code TEXT PRIMARY KEY, stamp INTEGER NOT NULL, next_chapter INTEGER NOT NULL)")
            db.execSQL("PRAGMA user_version=2")
                val current = db.rawQuery("SELECT stamp FROM editions WHERE code=?", arrayOf(code)).use { it.moveToFirst() && it.getLong(0) == stamp }
                if (current) return@withLock true
                var next=db.rawQuery("SELECT stamp,next_chapter FROM building WHERE code=?",arrayOf(code)).use{if(it.moveToFirst()&&it.getLong(0)==stamp)it.getInt(1)else 0}
                if(next==0){db.beginTransaction();try{db.delete("pending_verses","code=?",arrayOf(code));db.execSQL("INSERT OR REPLACE INTO building VALUES (?,?,0)",arrayOf<Any>(code,stamp));db.setTransactionSuccessful()}finally{db.endTransaction()}}
                    val stemming=pack.translation.language.code.takeIf { it in stemmingLanguages }?.let(::SearchStemming)
                    val insert = db.compileStatement("INSERT INTO pending_verses VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)")
                    try {
                        var ordinal=0
                        for (book in pack.books.sortedBy { it.order }) for (number in 1..book.chaptersCount) {
                            currentCoroutineContext().ensureActive()
                            if(ordinal++<next)continue
                            if(shouldYield())return@withLock false
                            val chapter = store.read(chapterKey(code, book.slug, number), BibleChapter.serializer())
                            require(chapter != null || "${book.name} $number" in pack.unavailable) { "Installed chapter is missing or unreadable" }
                            db.beginTransaction()
                            try {
                            if (chapter != null) {
                                require(chapter.translation.code == code && chapter.book.slug == book.slug && chapter.chapter.number == number)
                                chapter.verses.filter { it.plainText.isNotBlank() }.forEach { verse ->
                                    insert.clearBindings()
                                    val stems=stemming?.tokens(verse.plainText)?.distinct()?.joinToString(" ",prefix=" ",postfix=" ").orEmpty()
                                    listOf(code, book.slug, book.name, book.order.toString(), book.canonicalBook?.testament.orEmpty(), book.canonicalBook?.osisCode.orEmpty(), number.toString(), verse.number.toString(), verse.osisRef, verse.plainText, if (verse.hasStrongMarkup) verse.text else "", normalized(verse.plainText),stems).forEachIndexed { n, value -> insert.bindString(n + 1, value) }
                                    insert.executeInsert()
                                }
                            }
                            db.execSQL("UPDATE building SET next_chapter=? WHERE code=?",arrayOf<Any>(ordinal,code))
                            db.setTransactionSuccessful()
                            } finally { db.endTransaction() }
                            next=ordinal;progress(next,pack.total)
                        }
                    } finally { insert.close() }
                    currentCoroutineContext().ensureActive()
                    // An update/removal racing this pass must never publish a mixed or deleted edition.
                    val latestRefresh=store.read(bibleRefreshKey(code),BibleRefreshPass.serializer())
                    if(store.savedAt(biblePackageKey(code))!=stamp||store.read(biblePackageKey(code),BiblePackage.serializer())?.isInstalled!=true||latestRefresh!=refresh)return@withLock false
                    db.beginTransaction()
                    try {
                    db.delete("verses", "code=?", arrayOf(code))
                    db.execSQL("INSERT INTO verses SELECT * FROM pending_verses WHERE code=?",arrayOf<Any>(code))
                    db.execSQL("INSERT OR REPLACE INTO editions (code,stamp,language,unavailable) VALUES (?,?,?,?)", arrayOf<Any>(code, stamp, pack.translation.language.code,pack.unavailable.size))
                    db.delete("pending_verses","code=?",arrayOf(code));db.delete("building","code=?",arrayOf(code))
                    db.setTransactionSuccessful()
                } finally { db.endTransaction() }
            true
        } finally { db.close() }
      }
    }
    suspend fun remove(code:String)=withContext(Dispatchers.IO){mutex.withLock{if(!indexFile.exists())return@withLock;SQLiteDatabase.openDatabase(indexFile.path,null,SQLiteDatabase.OPEN_READWRITE).use{db->db.beginTransaction();try{for(table in listOf("verses","editions","pending_verses","building"))if(db.rawQuery("SELECT name FROM sqlite_master WHERE name=?",arrayOf(table)).use{it.moveToFirst()})db.delete(table,"code=?",arrayOf(code));db.setTransactionSuccessful()}finally{db.endTransaction()}}}}
    internal suspend fun discardCorruptIndex()=withContext(Dispatchers.IO){mutex.withLock{SQLiteDatabase.deleteDatabase(indexFile)}}
    private fun readIndex():SQLiteDatabase {
        if(!indexFile.isFile)throw BibleSearchIndexNotReady()
        // DefaultDatabaseErrorHandler deletes a corrupt file; readers must leave repair to the worker.
        val db=try{SQLiteDatabase.openDatabase(indexFile.path,null,SQLiteDatabase.OPEN_READONLY,android.database.DatabaseErrorHandler{_ -> })}catch(error:SQLiteException){throw BibleSearchIndexNotReady(error)}
        try{if(db.version!=2)throw BibleSearchIndexNotReady()}catch(error:Exception){db.close();throw error}
        return db
    }
    suspend fun status(codes:Set<String>):SearchIndexReadiness=withContext(Dispatchers.IO){
        if(codes.isEmpty())return@withContext SearchIndexReadiness(false,false)
        try{readIndex().use{db->
            var ready=true;var current=true
            for(code in codes){
                if(store.read(biblePackageKey(code),BiblePackage.serializer())==null){ready=false;current=false;continue}
                db.rawQuery("SELECT stamp FROM editions WHERE code=?",arrayOf(code)).use{cursor->
                    if(!cursor.moveToFirst()){ready=false;current=false}else if(cursor.getLong(0)!=store.savedAt(biblePackageKey(code)))current=false
                }
            }
            SearchIndexReadiness(ready,current)
        }}catch(_:BibleSearchIndexNotReady){SearchIndexReadiness(false,false)}catch(_:SQLiteException){SearchIndexReadiness(false,false)}
    }
    suspend fun readiness(codes:Set<String>):Boolean=status(codes).ready
    suspend fun search(codes: Set<String>, query: String, match: VerseSearchMatch, scope: VerseSearchScope, book: String? = null,
        offset: Int = 0): LocalVerseSearchPage = withContext(Dispatchers.IO) {
        require(codes.isNotEmpty() && query.trim().length in 2..500 && offset >= 0)
        require(match != VerseSearchMatch.STRONG || Regex("[HG]\\d{1,5}", RegexOption.IGNORE_CASE).matches(query.trim()))
            val db = readIndex()
            try {
                val metadata=codes.associateWith{code->
                    if(store.read(biblePackageKey(code),BiblePackage.serializer())==null)throw BibleSearchIndexNotReady()
                    db.rawQuery("SELECT language,unavailable FROM editions WHERE code=?",arrayOf(code)).use{if(!it.moveToFirst())throw BibleSearchIndexNotReady();it.getString(0) to it.getInt(1)}
                }
                val tokens = Regex("[\\p{L}\\p{M}\\p{N}]+").findAll(normalized(query)).map { it.value }.toList()
                val longest = tokens.maxByOrNull(String::length) ?: ""
                val escaped = longest.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
                val clauses = mutableListOf("code IN (${codes.joinToString(",") { "?" }})")
                val args = codes.toMutableList()
                if (match == VerseSearchMatch.MORPHOLOGY) {
                    require(metadata.values.all { it.first in stemmingLanguages }) { "Unsupported stemming language" }
                    val variants=metadata.map { (code,info) ->
                        val stems=SearchStemming(info.first).tokens(query).distinct()
                        require(stems.isNotEmpty())
                        args+=code
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
                LocalVerseSearchPage(hits, total, metadata.values.sumOf { it.second })
            } catch(error:SQLiteException){throw BibleSearchIndexNotReady(error)}finally { db.close() }
    }
}

internal data class SearchIndexReadiness(val ready:Boolean,val current:Boolean)
internal class BibleSearchIndexNotReady(cause:Throwable?=null):IllegalStateException("Bible search index is being prepared in the background",cause)
