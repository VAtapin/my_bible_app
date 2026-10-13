package com.bibledesktop.myapp

import androidx.test.platform.app.InstrumentationRegistry
import com.bibledesktop.myapp.data.*
import com.bibledesktop.shared.api.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.UUID

/** Explicit publication acceptance only: no endpoint argument means no HTTP request or download. */
class PublishedReferenceResolutionTest {
    private val code="BQ_MYBIBLE_KJV_343776B4C6F1"
    private val query="Ps.119.1"
    private val source="legacy_quote"
    private fun publishedApi():String {
        val value=InstrumentationRegistry.getArguments().getString("referenceMapApi")?.trim()?.trimEnd('/')
        assumeTrue("Opt-in referenceMapApi argument is absent",!value.isNullOrBlank())
        require(value=="https://bible-desktop.com/api") { "This acceptance test uses the actual public BibleDesktop API" }
        return value
    }
    private data class Reading(val id:Long,val originalSource:String,val originalTarget:String,val target:ReferenceTarget)
    private data class Acceptance(val data:CrossReferences,val queryLocation:VerseLocation,val chapters:List<BibleChapter>,val readings:List<Reading>)

    private suspend fun acceptOnline(client:BibleContentSource):Acceptance {
        val location=client.getVerseLocations(code,listOf(query)).single()
        assertEquals(query,location.osis)
        val chapters=linkedMapOf<Pair<String,Int>,BibleChapter>()
        suspend fun chapter(book:String,number:Int)=chapters.getOrPut(book to number) {
            client.getChapter(code,book,number).also {
                assertEquals(code,it.translation.code);assertEquals(book,it.book.slug);assertEquals(number,it.chapter.number)
            }
        }
        val queryChapter=chapter(location.book,location.chapter)
        val queryVerse=queryChapter.verses.single{it.osisRef==query&&it.id==location.verseId&&it.number==location.verse}
        assertTrue(queryVerse.plainText.isNotBlank())
        val data=client.getCrossReferences(location.verseId,code)
        assertEquals(query,data.verse.osisRef);assertEquals(location.verseId,data.verse.id);assertEquals(code,data.translationCode)
        assertEquals(2,data.referenceResolution?.schema)
        assertEquals(query,data.referenceResolution?.queryOsisRef)
        val admission=data.referenceResolution!!.sources.single{it.source==source}
        assertEquals("verified",admission.status)
        assertEquals(listOf(query),admission.originalOsisRefs)
        assertTrue(data.references.isNotEmpty())
        assertTrue(data.references.all{it.originalSource?.osisRef==query})
        val references=data.references.filter{it.source==source}
        assertTrue("Published source group is absent",references.isNotEmpty())
        assertTrue("Published source group contains an unadmitted edge",references.all{it.resolution?.status=="verified"&&it.readingTarget!=null})
        val osis=references.map{it.readingTarget!!.osisRef}.distinct()
        val locations=osis.chunked(200).flatMap{client.getVerseLocations(code,it)}
        assertEquals(osis.toSet(),locations.map{it.osis}.toSet())
        assertEquals(locations.size,locations.map{it.osis}.distinct().size)
        val readings=references.map { ref ->
            val target=ref.readingTarget!!
            val actual=locations.single{it.osis==target.osisRef}
            assertEquals(actual.verseId,target.verseId);assertEquals(actual.book,target.bookSlug)
            assertEquals(actual.chapter,target.chapterNumber);assertEquals(actual.verse,target.verseNumber)
            val body=chapter(actual.book,actual.chapter).verses.single{it.id==actual.verseId&&it.osisRef==actual.osis&&it.number==actual.verse}
            assertTrue(body.plainText.isNotBlank());assertEquals(body.plainText,target.text)
            val originalTarget=requireNotNull(ref.originalTarget)
            assertEquals(originalTarget.osisRef,ref.target.osisRef)
            Reading(ref.id,ref.originalSource!!.osisRef,originalTarget.osisRef,target)
        }
        return Acceptance(data,location,chapters.values.toList(),readings.sortedBy{it.id})
    }

    @Test fun actualPublishedKjvSourceGroupUsesActualEditionIdsAndTexts()=runBlocking {
        publishedApi()
        val api=BibleApiClient()
        try { acceptOnline(api) } finally { api.close() }
        Unit
    }

    @Test fun actualPublishedBoundedPsalmRemapKeepsUnsupportedLinksRaw()=runBlocking {
        publishedApi()
        val edition=InstrumentationRegistry.getArguments().getString("publishedRemapEdition").orEmpty()
        assumeTrue("Bounded edition remap acceptance is opt-in",edition.isNotBlank())
        require(edition in setOf("BQ_MYBIBLE_RST_9827BA0EF963","BQ_CSL_PNM")) { "Only independently reviewed editions are accepted" }
        val api=BibleApiClient()
        try {
            val originalLocation=api.getVerseLocations(code,listOf("Ps.119.2")).single()
            val original=api.getCrossReferences(originalLocation.verseId,code).references.filter{it.source==source}
            val selectedLocation=api.getVerseLocations(edition,listOf("Ps.118.2")).single()
            val selected=api.getCrossReferences(selectedLocation.verseId,edition)
            assertEquals("Ps.118.2",selected.verse.osisRef)
            assertEquals(listOf("Ps.119.2"),selected.referenceResolution!!.sources.single{it.source==source}.originalOsisRefs)
            val links=selected.references.filter{it.source==source}
            assertEquals(original.map{it.id}.sorted(),links.map{it.id}.sorted())
            assertEquals(14,links.size)
            val verified=links.filter{it.readingTarget!=null}
            val raw=links.filter{it.readingTarget==null}
            assertEquals(3,verified.size);assertEquals(11,raw.size)
            val chapters=linkedMapOf<Pair<String,Int>,BibleChapter>()
            for(link in verified){
                val target=requireNotNull(link.readingTarget)
                val originalTarget=requireNotNull(link.originalTarget)
                assertEquals("Ps.119.2",link.originalSource?.osisRef)
                assertTrue(originalTarget.osisRef.startsWith("Ps.119."))
                assertEquals(originalTarget.osisRef.replace("Ps.119.","Ps.118."),target.osisRef)
                assertEquals("verified",link.resolution?.status)
                val location=api.getVerseLocations(edition,listOf(target.osisRef)).single()
                assertEquals(location.verseId,target.verseId)
                assertEquals(location.book,target.bookSlug)
                assertEquals(location.chapter,target.chapterNumber)
                assertEquals(location.verse,target.verseNumber)
                val chapter=chapters.getOrPut(location.book to location.chapter){api.getChapter(edition,location.book,location.chapter)}
                assertEquals(chapter.verses.single{it.id==location.verseId}.plainText,target.text)
            }
            for(link in raw){assertEquals("Ps.119.2",link.originalSource?.osisRef);assertNull(link.target.text);assertNotEquals("verified",link.resolution?.status)}
            val heldLocation=api.getVerseLocations(edition,listOf("Ps.118.5")).single()
            val held=api.getCrossReferences(heldLocation.verseId,edition)
            assertTrue(held.references.filter{it.source==source}.all{it.readingTarget==null&&it.target.text==null})
        } finally { api.close() }
        Unit
    }

    @Test fun actualPublishedPackageMatchesOnlineThenReadsWithoutRemoteAccess()=runBlocking {
        val base=publishedApi()
        assumeTrue("Full published package acceptance is opt-in",InstrumentationRegistry.getArguments().getString("publishedPackage")=="true")
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val directory=File(context.cacheDir,"published-reference-acceptance-${UUID.randomUUID()}")
        check(directory.mkdirs())
        val api=BibleApiClient()
        try {
            val online=acceptOnline(api)
            val packages=StudyPackageStore(context,base,directory=File(directory,"packages"))
            val manifest=packages.manifest()
            val pack=manifest.packages.single{it.id==StudyVersification.Code}
            assertEquals("cross_references",pack.kind)
            // Stream the actual published ZIP through the production SHA/size/index validation path.
            packages.download(pack) { done,total -> assertEquals(pack.bytes,total);assertTrue(done in 0..total) }
            assertEquals(pack,packages.installed().single())
            assertEquals(2,packages.metadata(pack.id)?.get("versification_schema")?.jsonPrimitive?.intOrNull)
            assertTrue(packages.hasTable(pack.id,"versification_inverse_entries"))
            val store=OfflineStore(File(directory,"bible"))
            store.write("books:$code",ListSerializer(BibleBook.serializer()),api.getBooks(code))
            online.chapters.forEach { store.write(chapterKey(code,it.book.slug,it.chapter.number),BibleChapter.serializer(),it) }
            api.close()
            val blocked=object:BibleContentSource by api {
                override suspend fun getCrossReferences(verseId:Long,translationCode:String):CrossReferences=error("Remote references are disabled")
                override suspend fun getVerseLocations(translationCode:String,osis:List<String>):List<VerseLocation> = error("Remote locations are disabled")
                override suspend fun getChapter(translationCode:String,bookSlug:String,chapterNumber:Int):BibleChapter=error("Remote chapters are disabled")
                override suspend fun getBooks(translationCode:String):List<BibleBook> = error("Remote books are disabled")
            }
            val repository=OfflineContentRepository(blocked,store).also{it.useStudyPackages(packages)}
            repository.getChapter(code,online.queryLocation.book,online.queryLocation.chapter)
            val offline=repository.getCrossReferences(online.queryLocation.verseId,code)
            val onlineAdmission=online.data.referenceResolution!!.sources.single{it.source==source}
            val offlineAdmission=offline.referenceResolution!!.sources.single{it.source==source}
            assertEquals(onlineAdmission,offlineAdmission)
            val readings=offline.references.filter{it.source==source}.map { ref ->
                assertEquals("verified",ref.resolution?.status)
                Reading(ref.id,ref.originalSource!!.osisRef,ref.originalTarget!!.osisRef,requireNotNull(ref.readingTarget))
            }.sortedBy{it.id}
            assertEquals(online.readings,readings)
        } finally {
            api.close()
            require(directory.canonicalPath.startsWith(context.cacheDir.canonicalPath+File.separator))
            directory.deleteRecursively()
        }
        Unit
    }
}
