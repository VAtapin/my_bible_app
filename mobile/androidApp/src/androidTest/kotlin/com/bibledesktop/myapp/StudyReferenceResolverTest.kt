package com.bibledesktop.myapp

import androidx.test.platform.app.InstrumentationRegistry
import com.bibledesktop.myapp.data.*
import com.bibledesktop.shared.api.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class StudyReferenceResolverTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private val sourceText="Слово & ещё, конец."
    private val targetText="Блаженны непорочные в пути"
    private fun obj(vararg values:Pair<String,Any>)=JSONObject().apply { values.forEach{put(it.first,it.second)} }
    private suspend fun install(root:File,store:StudyPackageStore,scenario:String) {
        val identity=scenario.startsWith("source-identity")||scenario.startsWith("both-identity")
        val query=if(identity)"Ps.119.1" else "Ps.118.1"
        val raw=obj("api_id" to 91,"source_code" to "legacy_quote","source_name" to "TSK","type" to "tsk",
            "source_verse" to obj("api_id" to 1191,"osis_ref" to "Ps.119.1","book_slug" to "psalms","chapter" to 119,"verse" to 1),
            "target_verse" to obj("api_id" to 1192,"osis_ref" to "Ps.119.2","book_slug" to "psalms","chapter" to 119,"verse" to 2),
            "metadata" to obj("raw_ref" to "Ps 119:2","legacy_quote_id" to 7))
        fun entry(source:String,target:String)=obj("map_set_id" to 7,"source_osis_ref" to source,"target_osis_ref" to target,"relation" to "exact","evidence_uri" to "https://evidence.example/map","evidence_id" to "review:$source")
        val parent=entry("Ps.119.1",query).apply {
            put("source_reference_sha256","13e95c9f384f067a8cc9abfd32ac012241aa447851f4f451fb1c0470bce3970a")
            put("target_content_sha256","6a896d577f3116c1293eaac02a6b5013bb95f6946422af00c90dca5ec2437bea")
            if(scenario=="missing-source-hash"||scenario=="source-identity-missing-hash")remove("source_reference_sha256")
            if(scenario=="missing-query-hash")remove("target_content_sha256")
            if(scenario.startsWith("both-identity")){remove("source_reference_sha256");remove("target_content_sha256")}
            if(scenario=="both-identity-bad-query-hash")put("target_content_sha256","b".repeat(64))
            if(scenario=="both-identity-target-only-source")put("target_content_sha256","6a896d577f3116c1293eaac02a6b5013bb95f6946422af00c90dca5ec2437bea")
        }
        val target=entry("Ps.119.2",if(scenario.startsWith("both-identity"))"Ps.119.2" else "Ps.118.2").apply {
            put("target_content_sha256",studyTextDigest(targetText))
            // This belongs to the target's own source group, not the parent query group.
            put("source_reference_sha256","c".repeat(64))
            if(scenario=="missing-target-hash")remove("target_content_sha256")
            if(scenario.startsWith("both-identity"))remove("target_content_sha256")
            if(scenario=="both-identity-bad-target-hash")put("target_content_sha256","")
        }
        fun profile(code:String)=obj("code" to code,"revision" to "v1","provenance_uri" to "https://evidence.example/profile","sha256" to "a".repeat(64))
        fun assignment(type:String,code:String,profile:String)=obj("subject_type" to type,"subject_code" to code,"profile_code" to profile,"evidence_uri" to "https://evidence.example/assignment")
        val parents=listOf(parent,target)
        val entries=if(scenario=="split")parents+entry("Ps.119.2","Ps.118.3") else parents
        val inverse=entries.map { JSONObject(it.toString()).apply {
            remove("source_reference_sha256");remove("target_content_sha256")
            if(scenario=="stale-inverse"&&getString("source_osis_ref")=="Ps.119.1")put("evidence_id","old-review")
        } }
        val rawRows=if(scenario=="changed-source-group")listOf(raw,JSONObject(raw.toString()).put("api_id",92)) else listOf(raw)
        val files=mapOf(
            "module.json" to obj("schema" to 1,"kind" to "cross_references","code" to "CROSS_REFERENCES","versification_schema" to 2).toString(),
            "sources.jsonl" to obj("code" to "legacy_quote","name" to "TSK").toString(),
            "references.jsonl" to rawRows.joinToString("\n"),
            "versification_profiles.jsonl" to listOf(profile("SOURCE"),profile("EDITION")).joinToString("\n"),
            "versification_assignments.jsonl" to listOf(assignment("reference_source","legacy_quote","SOURCE"),assignment("translation","RST","EDITION")).joinToString("\n"),
            "versification_map_sets.jsonl" to obj("id" to 7,"source_profile_code" to "SOURCE","target_profile_code" to "EDITION","version" to "reviewed-v2","sha256" to "b".repeat(64),"provenance_uri" to "https://evidence.example/map","verified_at" to "2026-10-13T12:00:00Z","reviewer" to "Reviewed fixture").toString(),
            "versification_map_entries.jsonl" to entries.joinToString("\n"),
            "versification_inverse_entries.jsonl" to inverse.joinToString("\n"),
        )
        val zip=File(root,"${UUID.randomUUID()}.zip")
        ZipOutputStream(zip.outputStream()).use { output -> files.forEach { (name,value) -> output.putNextEntry(ZipEntry(name));output.write(value.toByteArray(Charsets.UTF_8));output.closeEntry() } }
        val hash=java.security.MessageDigest.getInstance("SHA-256").digest(zip.readBytes()).joinToString(""){"%02x".format(it)}
        store.install(StudyOfflinePackage("CROSS_REFERENCES","cross_references",hash,zip.length(),hash,"/api/offline/packages/CROSS_REFERENCES"),zip)
    }
    private suspend fun runScenario(scenario:String,block:(CrossReferences)->Unit) {
        val root=File(context.cacheDir,"reference-resolver-${UUID.randomUUID()}");check(root.mkdirs())
        val api=BibleApiClient()
        try {
            val packages=StudyPackageStore(context,directory=File(root,"packages"));install(root,packages,scenario)
            val identity=scenario.startsWith("source-identity")||scenario.startsWith("both-identity");val number=if(identity)119 else 118
            val book=BibleBook("actual-psalms","Псалтирь",chaptersCount=150,canonicalBook=CanonicalBookSummary("Ps","old"))
            val translation=TranslationSummary("RST","Russian",language=LanguageSummary("ru","Русский"))
            val content=OfflineStore(File(root,"bible"))
            content.write("books:RST",ListSerializer(BibleBook.serializer()),listOf(book))
            val queryVerse=BibleVerse(number*10L+1,1,"Ps.$number.1",sourceText,if(scenario=="changed-query-text")"Changed query" else sourceText)
            val verses=if(scenario.startsWith("both-identity"))listOf(queryVerse,BibleVerse(1192,2,"Ps.119.2",targetText,targetText)) else if(identity)listOf(queryVerse) else listOf(queryVerse,BibleVerse(1182,2,"Ps.118.2",targetText,if(scenario=="changed-target-text")"Changed target" else targetText),BibleVerse(1183,3,"Ps.118.3","Third","Third"))
            content.write(chapterKey("RST",book.slug,number),BibleChapter.serializer(),BibleChapter(translation,book,ChapterSummary(number,verses.size),verses))
            if(identity)content.write(chapterKey("RST",book.slug,118),BibleChapter.serializer(),BibleChapter(translation,book,ChapterSummary(118,1),listOf(BibleVerse(1182,2,"Ps.118.2",targetText,targetText))))
            val remote=object:BibleContentSource by api { override suspend fun getCrossReferences(verseId:Long,translationCode:String):CrossReferences=error("No network references permitted") }
            val repository=OfflineContentRepository(remote,content).also{it.useStudyPackages(packages)}
            repository.getChapter("RST",book.slug,number)
            block(repository.getCrossReferences(queryVerse.id,"RST"))
        } finally {
            api.close();require(root.canonicalPath.startsWith(context.cacheDir.canonicalPath+File.separator));root.deleteRecursively()
        }
    }
    @Test fun bothIdentityMayOmitFingerprintsButNeverIgnoreSuppliedInvalidHash()=runBlocking {
        runScenario("both-identity") { data -> assertEquals("verified",data.references.single().resolution?.status);assertEquals("Ps.119.2",data.references.single().readingTarget?.osisRef) }
        for(scenario in listOf("both-identity-bad-query-hash","both-identity-bad-target-hash","both-identity-target-only-source"))runScenario(scenario) { data ->
            assertNull(scenario,data.references.single().readingTarget)
            assertEquals(scenario,"raw",data.references.single().resolution?.status)
            if(scenario=="both-identity-target-only-source")assertEquals("raw",data.referenceResolution!!.sources.single().status)
        }
    }
    @Test fun inverseSourceAndEachTargetUseActualEditionTextAndKeepOriginalCoordinates()=runBlocking {
        for(scenario in listOf("verified","source-identity"))runScenario(scenario) { data ->
            val item=data.references.single()
            assertEquals("verified",item.resolution?.status)
            assertEquals("Ps.119.2",item.target.osisRef);assertNull(item.target.text)
            assertEquals("Ps.118.2",item.readingTarget?.osisRef);assertEquals(1182L,item.readingTarget?.verseId)
            assertEquals("Псалтирь 118:2",item.readingTarget?.reference);assertEquals(targetText,item.readingTarget?.text)
            assertEquals("Ps.119.1",item.originalSource?.osisRef)
            assertEquals(1,referenceGroups(data.references).size)
        }
    }
    @Test fun changedSourceOrTextMissingHashesAndSplitNeverAutomaticallyPreview()=runBlocking {
        for(scenario in listOf("changed-query-text","changed-target-text","changed-source-group","missing-source-hash","source-identity-missing-hash","missing-query-hash","missing-target-hash","stale-inverse","split"))runScenario(scenario) { data ->
            assertTrue(scenario,data.references.isNotEmpty())
            assertTrue(scenario,data.references.all{it.readingTarget==null&&!it.versification.verified&&it.target.text==null})
            if(scenario=="split") {
                assertEquals("ambiguous",data.references.single().resolution?.status)
                assertEquals(2,data.references.single().resolvedTargets.size)
                assertEquals(listOf("Ps.118.2","Ps.118.3"),data.references.single().resolvedTargets.map{it.osisRef})
                assertTrue(data.references.single().resolvedTargets.all{it.relation=="ambiguous"&&it.cardinality=="one-to-many"})
            }
        }
    }
}
