package com.bibledesktop.myapp

import androidx.test.platform.app.InstrumentationRegistry
import com.bibledesktop.myapp.data.*
import com.bibledesktop.shared.api.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class StudyVersificationTest {
 private val context=InstrumentationRegistry.getInstrumentation().targetContext
 private fun objectRow(vararg fields:Pair<String,Any>):JSONObject=JSONObject().apply{fields.forEach{(key,value)->put(key,value)}}
 private suspend fun install(root:File,store:StudyPackageStore,scenario:String="verified"){
  val code="CROSS_REFERENCES";val source="John.3.16";val target="Gen.1.1"
  fun profile(name:String)=objectRow("code" to name,"name" to name,"revision" to "v1","provenance_uri" to "https://evidence.example/profile/$name","sha256" to if(scenario=="invalid-profile"&&name=="SOURCE")"invalid"else"a".repeat(64))
  fun assignment(type:String,subject:String,profile:String)=objectRow("subject_type" to type,"subject_code" to subject,"profile_code" to profile,"evidence_uri" to "https://evidence.example/assignment/$subject")
  fun mapEntry(ref:String,mapped:String=ref)=objectRow("map_set_id" to 7,"source_osis_ref" to ref,"target_osis_ref" to mapped,"relation" to "exact","evidence_uri" to "https://evidence.example/map","evidence_id" to if(scenario=="missing-evidence"&&ref==target)""else"line:$ref")
  val module=objectRow("schema" to 1,"kind" to "cross_references","code" to code)
  val files=linkedMapOf("module.json" to module.toString(),"sources.jsonl" to objectRow("code" to "legacy_quote","name" to "BibleQuote quote.tsk","record_count" to 1).toString(),"references.jsonl" to objectRow("id" to "reference","api_id" to 1,"source_code" to "legacy_quote","source_name" to "BibleQuote quote.tsk","source_verse" to objectRow("api_id" to 10,"osis_ref" to source,"book_slug" to "john","book_osis" to "John","chapter" to 3,"verse" to 16),"target_verse" to objectRow("api_id" to 100,"osis_ref" to target,"book_slug" to "genesis","book_osis" to "Gen","chapter" to 1,"verse" to 1)).toString())
  if(scenario!="legacy"){
   module.put("versification_schema",1);files["module.json"]=module.toString()
   files["versification_profiles.jsonl"]=listOf(profile("SOURCE"),profile("EDITION")).joinToString("\n")
   files["versification_assignments.jsonl"]=listOf(assignment("reference_source","legacy_quote","SOURCE"),assignment("translation","RST","EDITION")).joinToString("\n")
   files["versification_map_sets.jsonl"]=if(scenario=="missing-map")""else objectRow("id" to 7,"source_profile_code" to "SOURCE","target_profile_code" to "EDITION","version" to "reviewed-v1","provenance_uri" to "https://evidence.example/map","sha256" to "b".repeat(64),"verified_at" to "2026-10-10 12:00:00","reviewer" to if(scenario=="missing-reviewer")""else"Documented reviewer").toString()
   val entries=mutableListOf(mapEntry(source),mapEntry(target,if(scenario=="remap")"Gen.2.1"else target));if(scenario=="ambiguous")entries+=mapEntry(target,"Gen.1.2")
   files["versification_map_entries.jsonl"]=entries.joinToString("\n")
  }
  val zip=File(root,"${UUID.randomUUID()}.zip");ZipOutputStream(zip.outputStream()).use{out->files.forEach{(name,text)->out.putNextEntry(ZipEntry(name));out.write(text.toByteArray());out.closeEntry()}}
  val sha=MessageDigest.getInstance("SHA-256").digest(zip.readBytes()).joinToString(""){"%02x".format(it)};store.install(StudyOfflinePackage(code,"cross_references",sha,zip.length(),sha,"/api/offline/packages/$code"),zip)
 }
 private suspend fun isolated(block:suspend(File,StudyPackageStore)->Unit){val root=File(context.cacheDir,"versification-${UUID.randomUUID()}");check(root.mkdirs());try{block(root,StudyPackageStore(context,directory=File(root,"installed")))}finally{require(root.canonicalPath.startsWith(context.cacheDir.canonicalPath+File.separator));root.deleteRecursively()}}
 @Test fun actualIndexedProvenanceVerifiesBothEndsAndRejectsRemapsAmbiguityOrMissingEvidence()=runBlocking{isolated{root,store->
  for((scenario,expected) in listOf("verified" to "verified","remap" to "raw","ambiguous" to "ambiguous","missing-evidence" to "raw","missing-map" to "raw","missing-reviewer" to "raw","invalid-profile" to "unknown")){
   install(root,store,scenario);val verifier=requireNotNull(StudyVersification.create(store));val status=verifier.reference("legacy_quote","John.3.16","Gen.1.1","RST");assertEquals(scenario,expected,status.status)
   assertEquals("unknown",verifier.reference("legacy_quote","John.3.16","Gen.1.1","OTHER_EDITION").status)
  }
  install(root,store,"legacy");assertNull(StudyVersification.create(store))
 }}
 @Test fun verifiedDispatchUsesActualEditionIdsWhileUnknownKeepsStoredAddressWithoutPreview()=runBlocking{isolated{root,store->
  install(root,store)
  val content=OfflineStore(File(root,"bible"));val edition=TranslationSummary("RST","Russian",language=LanguageSummary("ru","Русский"));val john=BibleBook("rst-john","Иоанна",chaptersCount=3,canonicalBook=CanonicalBookSummary("John","new"));val gen=BibleBook("rst-gen","Бытие",chaptersCount=1,canonicalBook=CanonicalBookSummary("Gen","old"))
  content.write("books:RST",ListSerializer(BibleBook.serializer()),listOf(john,gen));content.write(chapterKey("RST",john.slug,3),BibleChapter.serializer(),BibleChapter(edition,john,ChapterSummary(3,1),listOf(BibleVerse(999,16,"John.3.16","Source","Source"))));content.write(chapterKey("RST",gen.slug,1),BibleChapter.serializer(),BibleChapter(edition,gen,ChapterSummary(1,1),listOf(BibleVerse(201,1,"Gen.1.1","Actual edition text","Actual edition text"))))
  val api=BibleApiClient();try{val remote=object:BibleContentSource by api{override suspend fun getCrossReferences(verseId:Long,translationCode:String):CrossReferences=error("No network reference fetch")};val repository=OfflineContentRepository(remote,content).also{it.useStudyPackages(store)};repository.getChapter("RST",john.slug,3)
   val verified=repository.getCrossReferences(999,"RST").references.single();assertEquals("verified",verified.versification.status);assertEquals(201,verified.target.verseId);assertEquals("rst-gen",verified.target.bookSlug);assertEquals("Actual edition text",verified.target.text)
   install(root,store,"remap");val raw=repository.getCrossReferences(999,"RST").references.single();assertEquals("raw",raw.versification.status);assertEquals("Gen.1.1",raw.target.osisRef);assertEquals("genesis",raw.target.bookSlug);assertNull(raw.target.text)
  }finally{api.close()}
 }}
}
