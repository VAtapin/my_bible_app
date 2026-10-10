package com.bibledesktop.myapp

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import com.bibledesktop.myapp.data.*
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class CommentaryPackageMediaTest {
 private val context=InstrumentationRegistry.getInstrumentation().targetContext
 private fun hash(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
 private fun png():ByteArray=ByteArrayOutputStream().use{out->val bitmap=Bitmap.createBitmap(2,1,Bitmap.Config.ARGB_8888);try{check(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));out.toByteArray()}finally{bitmap.recycle()}}
 internal fun archive(root:File,code:String,withMedia:Boolean=true,declaredHash:String?=null,mime:String="image/png",owner:String=code,wrongSize:Boolean=false,body:String="<p>Illustration<img src=\"picture.png\"></p>"):Pair<StudyOfflinePackage,File>{
  val image=png();val sha=declaredHash?:hash(image);val bytes=image.size.toLong()+if(wrongSize)1 else 0;val extension=if(mime=="image/jpeg")"jpg"else"png"
  val annotations=JSONObject().put("source_sha256","b".repeat(64)).put("links",JSONArray()).put("media",JSONArray().put(JSONObject().put("status","resolved").put("src","picture.png").put("alt","Original illustration").put("media_id",42).put("url","/api/commentary-modules/$owner/media/42").put("sha256",sha).put("mime_type",mime).put("bytes",bytes)))
  val entry=JSONObject().put("id","entry").put("api_id",17).put("commentary_book_id","book").put("commentary_book_api_id",31).put("title","Large section").put("author",JSONObject.NULL).put("chapter_from",3).put("verse_from",16).put("chapter_to",JSONObject.NULL).put("verse_to",JSONObject.NULL).put("book_slug","john").put("book_osis","John").put("body",body).put("order",0)
  if(withMedia)entry.put("annotations",annotations)
  val files=linkedMapOf("module.json" to JSONObject().put("schema",1).put("kind","commentary").put("code",code).toString().toByteArray(),"books.jsonl" to "{\"id\":\"book\",\"api_id\":31,\"title\":\"Book\",\"order\":0}".toByteArray(),"entries.jsonl" to entry.toString().toByteArray())
  if(withMedia){files["media.jsonl"]=JSONObject().put("id",sha).put("api_id",42).put("mime_type",mime).put("bytes",bytes).put("width",2).put("height",1).put("path","media/$sha.$extension").toString().toByteArray();files["media/$sha.$extension"]=image}
  val zip=File(root,"${UUID.randomUUID()}.zip");ZipOutputStream(zip.outputStream()).use{out->files.forEach{(name,content)->out.putNextEntry(ZipEntry(name));out.write(content);out.closeEntry()}}
  val digest=hash(zip.readBytes());return StudyOfflinePackage(code,"commentary",digest,zip.length(),digest,"/api/offline/packages/$code")to zip
 }
 private suspend fun isolated(block:suspend(File,StudyPackageStore)->Unit){val root=File(context.cacheDir,"commentary-media-${UUID.randomUUID()}");check(root.mkdirs());try{block(root,StudyPackageStore(context,directory=File(root,"installed")))}finally{require(root.canonicalPath.startsWith(context.cacheDir.canonicalPath+File.separator));root.deleteRecursively()}}
 @Test fun installsActualOwnedPngAndRetainsLegacyPackagesWithoutMedia()=runBlocking{isolated{root,store->
  val(old,oldZip)=archive(root,"LEGACY",withMedia=false);store.install(old,oldZip);assertEquals(0,store.rows("LEGACY","media").total)
  val(pack,zip)=archive(root,"COMMENTARY");store.install(pack,zip)
  val row=store.rows(pack.id,"media",apiIds=listOf(42)).rows.single();val sha=row["id"]!!.toString().trim('"');val image=store.media(pack.id,sha)!!
  assertEquals(sha,hash(image.readBytes()));val bitmap=requireNotNull(android.graphics.BitmapFactory.decodeFile(image.path));try{assertEquals(2,bitmap.width);assertEquals(1,bitmap.height)}finally{bitmap.recycle()}
  assertEquals(pack,store.installed().first{it.id==pack.id})
 }}
 @Test fun wrongImageChecksumTypeSizeOrModuleCannotReplaceInstalledContent()=runBlocking{isolated{root,store->
  val(good,goodZip)=archive(root,"COMMENTARY");store.install(good,goodZip)
  val invalid=listOf(archive(root,"COMMENTARY",declaredHash="a".repeat(64)),archive(root,"COMMENTARY",mime="image/jpeg"),archive(root,"COMMENTARY",owner="OTHER_MODULE"),archive(root,"COMMENTARY",wrongSize=true))
  invalid.forEach{(pack,zip)->assertTrue(runCatching{store.install(pack,zip)}.isFailure);assertEquals(good,store.installed().single())}
 }}
 @Test fun completeLargeSectionAndUnicodeSurviveCursorWindowLimits()=runBlocking{isolated{root,store->
  val body="<p>"+"Слово λόγος 𐍈 👋 !".repeat(180000)+"<img src=\"picture.png\"></p>"
  val(pack,zip)=archive(root,"LARGE_SOURCE",body=body);store.install(pack,zip)
  val row=store.rows(pack.id,"entries",ids=listOf("entry"),limit=1).rows.single()
  assertEquals(body,row["body"]!!.let{it as kotlinx.serialization.json.JsonPrimitive}.content)
  val library=InstalledStudyLibrary(store)
  val headers=requireNotNull(library.contents(31,0))
  assertEquals(1,headers.total);assertNull(headers.sections.single().body);assertNull(headers.sections.single().annotations)
  assertEquals(17L,headers.sections.single().id);assertEquals("Large section",headers.sections.single().title)
  assertEquals(body,requireNotNull(library.article(31,17)).body)
 }}

}
