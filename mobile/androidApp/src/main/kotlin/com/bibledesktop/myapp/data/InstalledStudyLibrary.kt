package com.bibledesktop.myapp.data
import android.content.Context
import com.bibledesktop.shared.api.*
import kotlinx.serialization.json.*

/** Exact online aliases from a verified version; strings stay source stable keys in the package. */
internal class InstalledStudyLibrary(private val store: StudyPackageStore) {
 constructor(context: Context): this(StudyPackageStore(context))
 private fun JsonObject.text(key:String)=this[key]?.jsonPrimitive?.contentOrNull
 private fun JsonObject.id(key:String)=this[key]?.jsonPrimitive?.longOrNull?.takeIf { it>0 } ?: error("Package API alias unavailable")
 private fun JsonObject.number(key:String)=this[key]?.jsonPrimitive?.intOrNull
 private suspend fun sources()=store.installed().filter { it.kind=="commentary" }
 private fun book(code:String,row:JsonObject)=StudyBook(row.id("api_id"),row.text("id")!!,row.text("title")!!,row.text("author"),row.text("description"),code)
 private fun annotations(row:JsonObject)=row["annotations"]?.takeUnless{it is JsonNull}?.let { Json { ignoreUnknownKeys=true }.decodeFromJsonElement(CommentaryAnnotations.serializer(),it) }
 private fun section(row:JsonObject)=StudySection(row.id("api_id"),row.text("title"),row.text("author"),row.number("chapter_from")?:0,row.number("verse_from")?:0,row.number("chapter_to"),row.number("verse_to"),row.text("book_osis"),row.text("body"),annotations(row))
 private suspend fun all(code:String,table:String,book:String?=null,chapter:Int?=null):List<JsonObject>{val rows=mutableListOf<JsonObject>();var offset=0;while(true){val page=store.rows(code,table,book=book,chapter=chapter,offset=offset,limit=500);rows+=page.rows;offset+=page.rows.size;if(page.rows.isEmpty()||offset>=page.total)break};return rows}
 suspend fun books(query:String,offset:Int):StudyBookPage? {val sources=sources();if(sources.isEmpty())return null;val results=sources.flatMap { pack->all(pack.id,"books").map { book(pack.id,it) } }.filter { query.isBlank()||"${it.title} ${it.author.orEmpty()}".contains(query.trim(),ignoreCase=true) }.sortedWith(compareBy<StudyBook>{it.title}.thenBy{it.id});return StudyBookPage(results.drop(offset).take(20),results.size)}
 private suspend fun findBook(id:Long):Pair<String,JsonObject>? {for(pack in sources()){val row=store.rows(pack.id,"books",apiIds=listOf(id),limit=1).rows.firstOrNull();if(row!=null)return pack.id to row};return null}
 suspend fun contents(id:Long,offset:Int):BookContents? {val(code,row)=findBook(id)?:return null;val page=store.rows(code,"entries",parentApiId=id,offset=offset,limit=20,headersOnly=true);return BookContents(book(code,row),page.rows.map(::section),page.total)}
 suspend fun article(book:Long,sectionId:Long):StudySection? {val(code,_)=findBook(book)?:return null;val row=store.rows(code,"entries",apiIds=listOf(sectionId),parentApiId=book,limit=1).rows.firstOrNull() ?: error("Installed section unavailable");return section(row)}
 suspend fun modules():List<CommentaryModule> = sources().map { pack->CommentaryModule(pack.id,store.metadata(pack.id)?.text("name") ?: pack.id,null,store.rows(pack.id,"entries",limit=1).total) }
 suspend fun commentaries(book:String,chapter:Int?,modules:List<String>,offset:Int):CommentaryPage? {val sources=sources().filter{it.id in modules};if(modules.isEmpty()||sources.size!=modules.size)return null;val results=mutableListOf<CommentaryEntry>();for(pack in sources){val name=store.metadata(pack.id)?.text("name") ?: pack.id;for(row in all(pack.id,"entries",book,chapter)){if(chapter==null&&(row.number("chapter_from")?:0)!=0)continue;results+=CommentaryEntry(row.id("api_id"),row.text("title"),row.text("author"),row.text("body")!!,row.number("chapter_from")?:0,row.number("verse_from")?:0,row.number("chapter_to"),row.number("verse_to"),row["commentary_book_api_id"]?.jsonPrimitive?.longOrNull,pack.id,name,annotations(row))}};results.sortBy{it.id};return CommentaryPage(book,chapter,results.drop(offset).take(10),results.size)}
 suspend fun canonicalSlug(osis:String):String? {for(pack in store.installed()){val table=when(pack.kind){"dictionary"->"references";"commentary"->"entries";else->continue};val row=store.rows(pack.id,table,bookOsis=osis,limit=1).rows.firstOrNull();row?.text("book_slug")?.let{return it}};return null}
}
