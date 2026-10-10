package com.bibledesktop.myapp.data

import android.content.Context
import com.bibledesktop.shared.api.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.serialization.json.*

/** Opened public pages, versioned by the source. Never labelled as a complete installed module. */
internal class DictionaryRepository(context: Context, val api: DictionaryApi = DictionaryApi(), private val packages: StudyPackageStore = StudyPackageStore(context)) : AutoCloseable {
    private val store = OfflineStore(context)
    private val imageDirectory = File(context.noBackupFilesDir, "dictionary-images-v1")
    private suspend fun <T> saved(key: String, serializer: KSerializer<T>, fetch: suspend () -> T): T {
        try { val value = fetch(); runCatching { store.write(key, serializer, value) }; return value }
        catch (error: IOException) { return store.read(key, serializer) ?: throw error }
    }
    private fun JsonObject.text(name:String) = this[name]?.jsonPrimitive?.contentOrNull
    private fun JsonObject.long(name:String) = this[name]?.jsonPrimitive?.longOrNull ?: error("Package API alias unavailable")
    private fun JsonObject.number(name:String) = this[name]?.jsonPrimitive?.intOrNull
    private suspend fun installedModules(): List<DictionaryModule> = packages.installed().filter { it.kind == "dictionary" }.map { pack ->
        val metadata = packages.metadata(pack.id) ?: error("Missing module metadata")
        val entries = packages.rows(pack.id,"entries",limit=1).total; val media = packages.rows(pack.id,"media",limit=1).total; val forms = packages.rows(pack.id,"word_forms",limit=1).total
        DictionaryModule(pack.id,metadata.text("name") ?: pack.id, metadata.text("language_code"),if(media>0) "atlas" else if(entries==0) "word_forms" else "articles",metadata.text("source_archive_sha256") ?: pack.version,entries,media,forms)
    }
    suspend fun modules(): List<DictionaryModule> { val local=installedModules(); val remote=try{saved("dictionary:modules", ListSerializer(DictionaryModule.serializer())) { api.modules() }}catch(error:IOException){if(local.isEmpty())throw error;emptyList()};return (remote+local).associateBy { it.code }.values.toList() }
    suspend fun downloadedModules() = installedModules()
    suspend fun searchInstalled(query:String,codes:List<String>,offset:Int=0,limit:Int=50):DictionaryPage {
        require(query.isNotBlank() && offset>=0 && limit in 1..100)
        val sources=installedModules().filter { it.code in codes }.sortedWith(compareBy({it.name},{it.code}))
        var remaining=offset;var total=0;val result=mutableListOf<DictionaryTopic>()
        for(module in sources){val page=packages.rows(module.code,"entries",query=query,titleOnly=true,offset=remaining,limit=(limit-result.size).coerceAtLeast(1));total+=page.total
            if(remaining>=page.total){remaining-=page.total;continue};remaining=0
            result+=page.rows.take(limit-result.size).map { DictionaryTopic(it.long("api_id"),it.text("id")!!,it.text("topic")!!,module.code,module.name) }
        }
        return DictionaryPage(result,total)
    }
    suspend fun entries(module: String, query: String, offset: Int): DictionaryPage {
        if(packages.has(module)){val page=packages.rows(module,"entries",query=query,offset=offset,limit=30);return DictionaryPage(page.rows.map { DictionaryTopic(it.long("api_id"),it.text("id")!!,it.text("topic")!!) },page.total)}
        return saved("dictionary:entries:$module:$query:$offset", DictionaryPage.serializer()) { api.entries(module, query, offset) }
    }
    private suspend fun allRows(module:String,table:String,ids:List<String>?=null,book:String?=null,query:String="",osis:String?=null): List<JsonObject> { val result=mutableListOf<JsonObject>();var offset=0;while(true){val page=packages.rows(module,table,ids=ids,book=book,query=query,bookOsis=osis,offset=offset,limit=500);result+=page.rows;offset+=page.rows.size;if(page.rows.isEmpty()||offset>=page.total)break};return result }
    suspend fun article(module: DictionaryModule, key: String): DictionaryArticle {
        if(packages.has(module.code)){
            val row=packages.rows(module.code,"entries",ids=listOf(key),limit=1).rows.firstOrNull() ?: error("Installed article unavailable")
            val references=allRows(module.code,"references",ids=listOf(key)).map { DictionaryReference(it.text("book_slug")!!,it.number("chapter"),it.number("verse_from"),it.number("verse_to"),it.text("book_osis")) }
            val links=allRows(module.code,"links",ids=listOf(key)).mapNotNull { link -> val target=packages.rows(module.code,"entries",ids=listOf(link.text("target_id")!!),limit=1).rows.firstOrNull();target?.let { DictionaryLink(link.text("label") ?: "",it.text("id")!!,it.text("topic")!!) } }
            val media=allRows(module.code,"media_links",ids=listOf(key)).mapNotNull { link -> packages.rows(module.code,"media",ids=listOf(link.text("media_id")!!),limit=1).rows.firstOrNull()?.let { val id=it.long("api_id");DictionaryMedia(id,it.text("fragment_id")!!,"/api/dictionaries/${module.code}/media/$id") } }
            return DictionaryArticle(row.long("api_id"),key,row.text("topic")!!,row.text("body")!!,media,links,references)
        }
        return saved("dictionary:article:${module.code}:${module.version}:$key", DictionaryArticle.serializer()) { api.article(module.code, key) }
    }
    suspend fun lookup(query: String, module: String): List<DictionaryWordForm> { if(packages.has(module))return allRows(module,"word_forms",query=query).filter { it.text("variation")?.lowercase()==query.trim().lowercase() }.map { DictionaryWordForm(module,it.text("standard_form")!!) };return saved("dictionary:lookup:$module:$query", ListSerializer(DictionaryWordForm.serializer())) { api.lookup(query, listOf(module)) } }
    suspend fun context(book: String, chapter: Int?, modules: List<String>, offset: Int, first:Int?=null,last:Int?=null,osis:String?=null): DictionaryPage {
        if(modules.isNotEmpty()&&modules.all { packages.has(it) }){
            val topics=linkedMapOf<String,DictionaryTopic>()
            for(module in modules){val metadata=packages.metadata(module);val refs=allRows(module,"references",book=if(osis==null)book else null,osis=osis).filter { (osis==null||it.text("book_osis")==osis) && (if(chapter==null)it.number("chapter")==null else it.number("chapter")==null||it.number("chapter")==chapter) && (first==null||it.number("verse_from")==null||it.number("verse_from")!! <= (last?:first) && (it.number("verse_to")?:it.number("verse_from")!!) >= first) };for(ref in refs){val key=ref.text("entry_id")!!;if("$module:$key" in topics)continue;val row=packages.rows(module,"entries",ids=listOf(key),limit=1).rows.firstOrNull() ?: continue;topics["$module:$key"]=DictionaryTopic(row.long("api_id"),key,row.text("topic")!!,module,metadata?.text("name"))}}
            return DictionaryPage(topics.values.toList().drop(offset).take(30),topics.size)
        }
        return saved("dictionary:context:$book:$chapter:${modules.joinToString(",")}:$offset", DictionaryPage.serializer()) { api.context(book, chapter, modules, offset) }
    }
    suspend fun verse(id: Long, modules: List<String>, offset: Int) = saved("dictionary:verse:$id:${modules.joinToString(",")}:$offset", DictionaryPage.serializer()) { api.verse(id, modules, offset) }
    suspend fun verseAt(id:Long,book:String,chapter:Int,number:Int,modules:List<String>,offset:Int,osis:String?=null):DictionaryPage = if(modules.isNotEmpty()&&modules.all { packages.has(it) })context(book,chapter,modules,offset,number,number,osis) else verse(id,modules,offset)
    suspend fun image(module: String, media: DictionaryMedia, version: String? = null): File = withContext(Dispatchers.IO) {
        if(packages.has(module)){val row=packages.rows(module,"media",apiIds=listOf(media.id),limit=1).rows.firstOrNull() ?: error("Installed image unavailable");return@withContext packages.media(module,row.text("id")!!) ?: error("Missing installed image")}
        val url = api.imageUrl(module, media)
        val target = File(imageDirectory, MessageDigest.getInstance("SHA-256").digest("$url:$version".toByteArray()).joinToString("") { "%02x".format(it) })
        if (target.isFile && target.length() in 1..20*1024*1024L) return@withContext target
        imageDirectory.mkdirs()
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = false; connection.connectTimeout = 10000; connection.readTimeout = 15000
        val temporary = File(target.path + ".part")
        try {
            check(connection.responseCode == 200)
            check(connection.contentType?.substringBefore(';') in listOf("image/png", "image/jpeg", "image/webp", "image/gif"))
            connection.inputStream.use { input -> temporary.outputStream().use { output -> val buffer = ByteArray(8192); var total = 0; while(true) { val size = input.read(buffer); if (size < 0) break; total += size; check(total <= 20*1024*1024); output.write(buffer, 0, size) }; check(total > 0) } }
            check(temporary.renameTo(target)); target
        } finally { connection.disconnect(); temporary.delete() }
    }
    override fun close() = api.close()
}
