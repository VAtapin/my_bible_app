package com.bibledesktop.myapp.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.AtomicFile
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipFile

@Serializable internal data class StudyOfflinePackage(val id: String, val kind: String, val version: String, val bytes: Long, val sha256: String, val url: String)
@Serializable internal data class StudyOfflineManifest(val schema: Int, val generated_at: String?, val packages: List<StudyOfflinePackage>)
internal data class StudyPackageRows(val total: Int, val rows: List<JsonObject>)
internal class StudyPackageHttpError(val status: Int) : IOException("Package HTTP $status")
internal class StudyPackageIntegrityError : IllegalStateException("Package checksum mismatch")

/** Verified complete modules; SQLite indexes JSONL without putting a whole dictionary into memory. */
internal class StudyPackageStore(context: Context, private val baseUrl: String = "https://bible-desktop.com/api", directory: File? = null) {
    private val root = directory ?: File(context.noBackupFilesDir, "study-packages-v1")
    private val json = Json { ignoreUnknownKeys = true }
    companion object {
        private val writes = Mutex()
        val tables = setOf("entries", "books", "references", "links", "word_forms", "media_links", "media", "lexicons", "sources", "versification_profiles", "versification_assignments", "versification_map_sets", "versification_map_entries")
    }
    private fun key(code: String): String = MessageDigest.getInstance("SHA-256").digest(code.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun packageRoot(code: String) = File(root, key(code))
    private fun valid(pack: StudyOfflinePackage) {
        require(pack.id.isNotBlank() && pack.id.length <= 200 && pack.kind in setOf("dictionary", "commentary", "strong", "cross_references"))
        require(pack.bytes > 0 && Regex("[a-f0-9]{64}").matches(pack.sha256) && pack.version == pack.sha256)
        val expected = URL(baseUrl)
        val url = URL(URL(baseUrl + "/"), pack.url)
        require(url.protocol == "https" && url.host == expected.host && url.port == expected.port && url.path.startsWith(expected.path + "/offline/packages/") && url.userInfo == null && url.ref == null)
    }
    suspend fun manifest(): StudyOfflineManifest = withContext(Dispatchers.IO) {
        val connection = URL("$baseUrl/offline/packages").openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000; connection.readTimeout = 30_000; connection.instanceFollowRedirects = false
        try {
            if (connection.responseCode != 200) throw StudyPackageHttpError(connection.responseCode)
            val text = connection.inputStream.use { stream -> val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192); while(true){val count=stream.read(buffer);if(count<0)break;check(output.size()+count<=5*1024*1024);output.write(buffer,0,count)}; output.toString(Charsets.UTF_8.name()) }
            json.decodeFromString<StudyOfflineManifest>(text).also { require(it.schema == 1 && it.packages.map { p -> p.id }.distinct().size == it.packages.size); it.packages.forEach(::valid) }
        } finally { connection.disconnect() }
    }
    suspend fun installed(): List<StudyOfflinePackage> = withContext(Dispatchers.IO) {
        root.listFiles()?.filter { it.isDirectory }?.mapNotNull { directory ->
            runCatching { AtomicFile(File(directory, "installed.json")).openRead().use { json.decodeFromString<StudyOfflinePackage>(it.reader().readText()) }.also { pack -> valid(pack); check(File(directory, "${pack.version}/index.sqlite").isFile) } }.getOrNull()
        }.orEmpty()
    }
    suspend fun metadata(code: String): JsonObject? = installed().firstOrNull { it.id == code }?.let { pack -> withContext(Dispatchers.IO) { json.parseToJsonElement(File(packageRoot(code), "${pack.version}/module.json").readText()).jsonObject } }
    suspend fun hasTable(code:String,table:String):Boolean=withContext(Dispatchers.IO){require(table in tables);val pack=installed().firstOrNull{it.id==code}?:return@withContext false;File(packageRoot(code),"${pack.version}/$table.jsonl").isFile}
    suspend fun has(code: String): Boolean = installed().any { it.id == code }
    suspend fun remove(code:String)=withContext(Dispatchers.IO){writes.withLock{
        require(code.isNotBlank())
        val directory=packageRoot(code)
        require(directory.canonicalPath.startsWith(root.canonicalPath+File.separator)&&directory.canonicalPath!=root.canonicalPath)
        // Only downloaded versions, indexes and their installed marker live here; personal profile is separate.
        if(directory.exists())check(directory.deleteRecursively())
    }}
    suspend fun partialBytes(pack: StudyOfflinePackage): Long = withContext(Dispatchers.IO) { valid(pack); File(packageRoot(pack.id), "${pack.version}.zip.part").length() }
    suspend fun download(pack: StudyOfflinePackage, progress: suspend (Long, Long) -> Unit) = withContext(Dispatchers.IO) {
        valid(pack)
        writes.withLock {
            val directory = packageRoot(pack.id); check(directory.mkdirs() || directory.isDirectory)
            val partial = File(directory, "${pack.version}.zip.part")
            var done = partial.length()
            if (done > pack.bytes) { check(partial.delete()); done = 0 }
            check(root.usableSpace > pack.bytes - done + 16 * 1024 * 1024)
            if (done < pack.bytes) {
                val connection = URL(URL(baseUrl + "/"), pack.url).openConnection() as HttpURLConnection
                connection.connectTimeout = 15_000; connection.readTimeout = 30_000; connection.instanceFollowRedirects = false
                if (done > 0) connection.setRequestProperty("Range", "bytes=$done-")
                try {
                    val response = connection.responseCode
                    if (response != 200 && response != 206) throw StudyPackageHttpError(response)
                    if (response == 206) require(connection.getHeaderField("Content-Range")?.startsWith("bytes $done-") == true)
                    else done = 0 // Servers without Range safely restart; never append their full response.
                    connection.inputStream.use { input -> java.io.FileOutputStream(partial, response == 206).use { output ->
                        val buffer = ByteArray(64 * 1024); var lastProgress = 0L
                        while (true) { currentCoroutineContext().ensureActive(); val count = input.read(buffer); if (count < 0) break; done += count; check(done <= pack.bytes); output.write(buffer, 0, count); if (done - lastProgress >= 256 * 1024 || done == pack.bytes) { progress(done, pack.bytes); lastProgress = done } }
                        output.fd.sync()
                    } }
                } finally { connection.disconnect() }
            }
            check(partial.length() == pack.bytes)
            try { installVerified(pack, partial, progress) } catch(error:StudyPackageIntegrityError){partial.delete();throw error}
            check(partial.delete())
        }
    }
    /** Testable ZIP application shares exactly the production checksum and validation path. */
    suspend fun install(pack: StudyOfflinePackage, zip: File) = withContext(Dispatchers.IO) { valid(pack); writes.withLock { installVerified(pack, zip) { _, _ -> } } }
    private suspend fun installVerified(pack: StudyOfflinePackage, zip: File, progress: suspend (Long, Long) -> Unit) {
        require(zip.length() == pack.bytes)
        val digest = MessageDigest.getInstance("SHA-256")
        zip.inputStream().use { input -> val buffer = ByteArray(64 * 1024); while (true) { currentCoroutineContext().ensureActive(); val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) } }
        if(digest.digest().joinToString("") { "%02x".format(it) } != pack.sha256)throw StudyPackageIntegrityError()
        val directory = packageRoot(pack.id); check(directory.mkdirs() || directory.isDirectory)
        val staging = File(directory, "staging-${pack.version}")
        require(staging.canonicalPath.startsWith(root.canonicalPath + File.separator)); if (staging.exists()) check(staging.deleteRecursively()); check(staging.mkdirs())
        try {
            var expanded = 0L
            ZipFile(zip).use { archive ->
                val entries = archive.entries(); val names = mutableSetOf<String>()
                while (entries.hasMoreElements()) {
                    currentCoroutineContext().ensureActive(); val entry = entries.nextElement(); val name = entry.name
                    require(names.add(name) && (name == "module.json" || name.removeSuffix(".jsonl") in tables && name.endsWith(".jsonl") || Regex(if(pack.kind=="commentary")"media/[a-f0-9]{64}\\.(png|jpg|webp|gif)" else "media/[a-f0-9]{40}\\.(png|jpg|webp|gif)").matches(name)))
                    require(!entry.isDirectory && entry.size >= 0 && entry.size <= staging.usableSpace)
                    val target = File(staging, name); require(target.canonicalPath.startsWith(staging.canonicalPath + File.separator)); check(target.parentFile!!.mkdirs() || target.parentFile!!.isDirectory)
                    archive.getInputStream(entry).use { input -> target.outputStream().use { output -> val buffer = ByteArray(64 * 1024); var size = 0L; while (true) { currentCoroutineContext().ensureActive(); val count = input.read(buffer); if (count < 0) break; size += count; expanded += count; check(expanded <= maxOf(pack.bytes * 300, 64 * 1024 * 1024) && size <= entry.size); output.write(buffer, 0, count) }; check(size == entry.size) } }
                }
            }
            val metadata = json.parseToJsonElement(File(staging, "module.json").readText()).jsonObject
            require(metadata["schema"]?.jsonPrimitive?.int == 1 && metadata["kind"]?.jsonPrimitive?.content == pack.kind && metadata["code"]?.jsonPrimitive?.content == pack.id)
            if(pack.kind=="cross_references")require(File(staging,"sources.jsonl").isFile&&File(staging,"references.jsonl").isFile)else require(File(staging, "entries.jsonl").isFile)
            if(pack.kind=="dictionary")require(listOf("references","links","word_forms","media_links","media").all{File(staging,"$it.jsonl").isFile})
            if(pack.kind=="commentary")require(File(staging,"books.jsonl").isFile)
            if(pack.kind=="strong")require(File(staging,"lexicons.jsonl").isFile)
            index(staging,pack.kind)
            if(pack.kind=="commentary")validateStudyCommentaryMedia(staging,pack.id,baseUrl)
            val destination = File(directory, pack.version)
            // A previously installed version is immutable; retain it if the download was repeated.
            if (destination.exists()) { require(File(destination, "index.sqlite").isFile); check(staging.deleteRecursively()) } else check(staging.renameTo(destination))
            val marker = AtomicFile(File(directory, "installed.json")); val output = marker.startWrite()
            try { output.write(json.encodeToString(pack).toByteArray()); marker.finishWrite(output) } catch (error: Exception) { marker.failWrite(output); throw error }
            progress(pack.bytes, pack.bytes)
        } catch (error: Exception) { require(staging.canonicalPath.startsWith(root.canonicalPath + File.separator)); staging.deleteRecursively(); throw error }
    }
    private suspend fun index(directory: File,kind:String) {
        SQLiteDatabase.openOrCreateDatabase(File(directory, "index.sqlite"), null).use { db ->
            db.execSQL("CREATE TABLE records(table_name TEXT NOT NULL,row_number INTEGER NOT NULL,content_order INTEGER NOT NULL,id TEXT,api_id INTEGER,parent_api_id INTEGER,source_api_id INTEGER,source_osis TEXT,subject_type TEXT,subject_code TEXT,source_profile TEXT,target_profile TEXT,map_set_id INTEGER,map_verified TEXT,title TEXT,parent TEXT,book TEXT,book_osis TEXT,chapter INTEGER,last_chapter INTEGER,first_verse INTEGER,last_verse INTEGER,standard TEXT,variation TEXT,payload TEXT NOT NULL,PRIMARY KEY(table_name,row_number))")
            db.execSQL("CREATE INDEX api_ids ON records(table_name,api_id)");db.execSQL("CREATE INDEX parent_api_ids ON records(table_name,parent_api_id)")
            db.execSQL("CREATE INDEX osis_refs ON records(table_name,book_osis,chapter,first_verse,last_verse)")
            db.execSQL("CREATE INDEX assignments ON records(table_name,subject_type,subject_code)")
            db.execSQL("CREATE INDEX map_sets ON records(table_name,source_profile,target_profile,map_verified)")
            db.execSQL("CREATE INDEX map_entries ON records(table_name,map_set_id,source_osis)")
            db.execSQL("CREATE INDEX source_refs ON records(table_name,source_osis)");db.execSQL("CREATE INDEX source_api_refs ON records(table_name,source_api_id)")
            db.execSQL("CREATE INDEX ids ON records(table_name,id)"); db.execSQL("CREATE INDEX refs ON records(table_name,book,chapter,first_verse,last_verse)"); db.execSQL("CREATE INDEX forms ON records(table_name,variation)")
            db.beginTransaction()
            try {
                for (table in tables) {
                    val file = File(directory, "$table.jsonl"); if (!file.exists()) continue
                    file.bufferedReader().use { reader -> var line = reader.readLine(); var row = 0
                        while (line != null) { currentCoroutineContext().ensureActive(); require(line.length <= 10 * 1024 * 1024); if (line.isNotBlank()) { val value = JSONObject(line); val record = ContentValues().apply {
                            put("table_name", table); put("row_number", row++); put("id", value.optString("id", value.optString("entry_id", value.optString("code", "")))); put("title", value.optString("topic", value.optString("title", value.optString("word", value.optString("id", "")))).lowercase(java.util.Locale.ROOT)); put("parent",value.optString("commentary_book_id","")); put("book", value.optString("book_slug", "")); put("chapter", value.optInt("chapter", value.optInt("chapter_from", 0))); put("last_chapter", value.optInt("chapter_to", value.optInt("chapter", value.optInt("chapter_from", 0)))); put("first_verse", value.optInt("verse_from", 0)); put("last_verse", value.optInt("verse_to", value.optInt("verse_from", 0))); put("standard", value.optString("standard_form", "").lowercase(java.util.Locale.ROOT)); put("variation", value.optString("variation", "").lowercase(java.util.Locale.ROOT)); put("payload", line)
                        }; val source=value.optJSONObject("source_verse");if(source!=null)record.put("title",(source.optString("osis_ref","")+" → "+value.optJSONObject("target_verse")?.optString("osis_ref","").orEmpty()).lowercase(java.util.Locale.ROOT));record.put("source_api_id",source?.optLong("api_id",0)?:0);record.put("source_osis",source?.optString("osis_ref","")?:value.optString("source_osis_ref",""));record.put("subject_type",value.optString("subject_type",""));record.put("subject_code",value.optString("subject_code",""));record.put("source_profile",value.optString("source_profile_code",""));record.put("target_profile",value.optString("target_profile_code",""));record.put("map_set_id",value.optLong("map_set_id",0));record.put("map_verified",value.optString("verified_at",""));record.put("content_order",value.optInt("order",row-1));record.put("book_osis",value.optString("book_osis",""));record.put("api_id",value.optLong("api_id",value.optLong("entry_api_id",0)));record.put("parent_api_id",value.optLong("commentary_book_api_id",0)); check(db.insertOrThrow("records", null, record) >= 0) }; line = reader.readLine() }
                    }
                }
                check(db.rawQuery(if(kind=="cross_references")"SELECT count(*) FROM records WHERE table_name = 'references'" else "SELECT count(*) FROM records WHERE table_name IN ('entries','word_forms')",null).use{it.moveToFirst();it.getInt(0)}>0)
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
        }
    }
    suspend fun rows(code: String, table: String, query: String = "", ids: List<String>? = null, offset: Int = 0, limit: Int = 30, book: String? = null, chapter: Int? = null, first: Int? = null, last: Int? = null, bookId: String? = null, apiIds: List<Long>? = null, parentApiId: Long? = null, bookOsis: String? = null,sourceOsis:String?=null,sourceApiId:Long?=null,titleOnly:Boolean=false,subjectType:String?=null,subjectCode:String?=null,sourceProfile:String?=null,targetProfile:String?=null,mapSetId:Long?=null,newestMaps:Boolean=false,headersOnly:Boolean=false): StudyPackageRows = withContext(Dispatchers.IO) {
        require(table in tables && offset >= 0 && limit in 1..500 && query.length <= 200)
        val pack = installed().firstOrNull { it.id == code } ?: error("Package not installed")
        SQLiteDatabase.openDatabase(File(packageRoot(code), "${pack.version}/index.sqlite").path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            val conditions = mutableListOf("table_name = ?"); val args = mutableListOf(table)
            if (query.isNotBlank()) { conditions += if(titleOnly) "title LIKE ? ESCAPE '\\'" else "(title LIKE ? ESCAPE '\\' OR variation LIKE ? ESCAPE '\\' OR standard LIKE ? ESCAPE '\\' OR lower(id) LIKE ? ESCAPE '\\')"; val pattern = "%" + query.lowercase(java.util.Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"; args += if(titleOnly)listOf(pattern)else listOf(pattern, pattern, pattern, pattern) }
            if(subjectType!=null){conditions+="subject_type = ?";args+=subjectType}
            if(subjectCode!=null){conditions+="subject_code = ?";args+=subjectCode}
            if(sourceProfile!=null){conditions+="source_profile = ?";args+=sourceProfile}
            if(targetProfile!=null){conditions+="target_profile = ?";args+=targetProfile}
            if(mapSetId!=null){require(mapSetId>0);conditions+="map_set_id = ?";args+=mapSetId.toString()}
            if(bookId!=null){conditions+="parent = ?";args+=bookId}
            if(apiIds!=null){if(apiIds.isEmpty())return@withContext StudyPackageRows(0,emptyList());require(apiIds.size<=500&&apiIds.all{it>0});conditions+="api_id IN (${apiIds.joinToString(","){"?"}})";args+=apiIds.map{it.toString()}}
            if(parentApiId!=null){require(parentApiId>0);conditions+="parent_api_id = ?";args+=parentApiId.toString()}
            if (ids != null) { if (ids.isEmpty()) return@withContext StudyPackageRows(0, emptyList()); require(ids.size <= 500); conditions += "id IN (${ids.joinToString(",") { "?" }})"; args += ids }
            if (book != null) { conditions += "book = ?"; args += book }
            if(bookOsis!=null){conditions+="book_osis = ?";args+=bookOsis}
            if(sourceOsis!=null){conditions+="source_osis = ?";args+=sourceOsis}
            if(sourceApiId!=null){require(sourceApiId>0);conditions+="source_api_id = ?";args+=sourceApiId.toString()}
            if (chapter != null) { conditions += "(chapter = 0 OR chapter <= ? AND (last_chapter = 0 OR last_chapter >= ?))"; args += listOf(chapter.toString(), chapter.toString()) }
            if (first != null && last != null) { conditions += "(first_verse = 0 OR first_verse <= ? AND (last_verse = 0 OR last_verse >= ?))"; args += listOf(last.toString(), first.toString()) }
            val where = conditions.joinToString(" AND ")
            val total = db.rawQuery("SELECT count(*) FROM records WHERE $where", args.toTypedArray()).use { it.moveToFirst(); it.getInt(0) }
            val order=if(newestMaps){require(table=="versification_map_sets");"map_verified DESC,CAST(id AS INTEGER) DESC"}else"content_order,row_number"
            val records = db.rawQuery("SELECT row_number,CASE WHEN length(payload)<=131072 THEN payload ELSE NULL END,length(payload) FROM records WHERE $where ORDER BY $order LIMIT ? OFFSET ?", (args + listOf(limit.toString(), offset.toString())).toTypedArray()).use { cursor -> buildList { while (cursor.moveToNext()) {
                currentCoroutineContext().ensureActive()
                val payload=if(cursor.isNull(1))largePayload(db,table,cursor.getInt(0),cursor.getInt(2))else cursor.getString(1)
                val decoded=json.parseToJsonElement(payload).jsonObject
                add(if(headersOnly)JsonObject(decoded.filterKeys{it!="body"&&it!="content"&&it!="annotations"})else decoded)
            } } }
            StudyPackageRows(total, records)
        }
    }
    /** Android CursorWindow cannot hold a whole large book section; SQLite slices preserve code points. */
    private suspend fun largePayload(db:SQLiteDatabase,table:String,row:Int,length:Int):String {
        require(length in 0..10*1024*1024)
        val output=StringBuilder(length);var start=1
        // A scalar statement bypasses CursorWindow entirely, including Android OEM window limits.
        db.compileStatement("SELECT substr(payload,?,65536) FROM records WHERE table_name=? AND row_number=?").use { statement ->
            statement.bindString(2,table);statement.bindLong(3,row.toLong())
            while(start<=length){
                currentCoroutineContext().ensureActive();statement.bindLong(1,start.toLong())
                val chunk=statement.simpleQueryForString()
                val points=chunk.codePointCount(0,chunk.length)
                check(points==minOf(65536,length-start+1))
                output.append(chunk);require(output.length<=10*1024*1024)
                start+=points // SQLite substr offsets count Unicode code points, not UTF16 units.
            }
        }
        return output.toString()
    }
    suspend fun media(code: String, mediaId: String): File? = withContext(Dispatchers.IO) {
        val pack = installed().firstOrNull { it.id == code } ?: return@withContext null
        require(Regex(if(pack.kind=="commentary")"[a-f0-9]{64}"else"[a-f0-9]{40}").matches(mediaId))
        val metadata = rows(code, "media", ids = listOf(mediaId), limit = 1).rows.firstOrNull() ?: return@withContext null
        val path = metadata["path"]?.jsonPrimitive?.content ?: return@withContext null
        require(Regex("media/$mediaId\\.(png|jpg|webp|gif)").matches(path))
        val directory=File(packageRoot(code),pack.version);val file=File(directory,path);require(file.canonicalPath.startsWith(directory.canonicalPath+File.separator))
        if(!file.isFile)return@withContext null
        if(pack.kind=="commentary")verifyCommentaryMediaFile(file,mediaId,metadata["bytes"]?.jsonPrimitive?.longOrNull?:error("Missing image size"),metadata["mime_type"]?.jsonPrimitive?.content?:error("Missing image type"),metadata["width"]?.jsonPrimitive?.intOrNull,metadata["height"]?.jsonPrimitive?.intOrNull)
        file
    }
}
