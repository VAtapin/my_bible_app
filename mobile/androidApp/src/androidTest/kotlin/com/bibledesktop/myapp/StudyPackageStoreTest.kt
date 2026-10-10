package com.bibledesktop.myapp

import androidx.test.platform.app.InstrumentationRegistry
import com.bibledesktop.myapp.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import com.bibledesktop.shared.api.*
import kotlinx.serialization.builtins.ListSerializer

class StudyPackageStoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val mediaId = "a".repeat(40)
    private fun archive(root: File, code: String, title: String = "Бог", extra: String? = null): Pair<StudyOfflinePackage, File> {
        val file = File(root, "${UUID.randomUUID()}.zip")
        ZipOutputStream(file.outputStream()).use { zip ->
            val files = linkedMapOf("module.json" to "{\"schema\":1,\"kind\":\"dictionary\",\"code\":\"$code\",\"name\":\"Test\"}",
                "entries.jsonl" to "{\"id\":\"entry\",\"topic\":\"$title\",\"body\":\"Explanation\",\"order\":0}\n{\"id\":\"second\",\"topic\":\"Second\",\"body\":\"Other\",\"order\":1}",
                "references.jsonl" to "{\"entry_id\":\"entry\",\"book_slug\":\"john\",\"chapter\":3,\"verse_from\":16,\"verse_to\":18}",
                "links.jsonl" to "{\"entry_id\":\"entry\",\"target_id\":\"second\",\"label\":\"Next\"}", "word_forms.jsonl" to "{\"standard_form\":\"Бог\",\"variation\":\"Богу\"}",
                "media_links.jsonl" to "{\"entry_id\":\"entry\",\"media_id\":\"$mediaId\"}", "media.jsonl" to "{\"id\":\"$mediaId\",\"mime_type\":\"image/png\",\"path\":\"media/$mediaId.png\"}", "media/$mediaId.png" to "image-fixture")
            if (extra != null) files[extra] = "escape"
            files.forEach { (name, contents) -> zip.putNextEntry(ZipEntry(name)); zip.write(contents.toByteArray()); zip.closeEntry() }
        }
        val hash = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
        return StudyOfflinePackage(code, "dictionary", hash, file.length(), hash, "/api/offline/packages/$code") to file
    }
    private suspend fun isolated(block: suspend (File, StudyPackageStore) -> Unit) {
        val root = File(context.cacheDir, "study-package-test-${UUID.randomUUID()}"); check(root.mkdirs())
        try { block(root, StudyPackageStore(context, directory = File(root, "installed"))) }
        finally { require(root.canonicalPath.startsWith(context.cacheDir.canonicalPath + File.separator)); root.deleteRecursively() }
    }
    @Test fun installsFullJsonlTablesMediaAndReopensPagedIndexWithoutNetwork() = runBlocking { isolated { root, store ->
        val (pack, zip) = archive(root, "TEST_DICT"); store.install(pack, zip)
        assertEquals(pack, store.installed().single()); assertEquals("TEST_DICT", store.metadata(pack.id)?.get("code")?.toString()?.trim('"'))
        assertEquals(2, store.rows(pack.id, "entries", limit = 1).total); assertEquals("\"entry\"", store.rows(pack.id, "entries", limit = 1).rows.single()["id"].toString()); assertEquals("\"second\"", store.rows(pack.id, "entries", offset = 1, limit = 1).rows.single()["id"].toString())
        assertEquals(1, store.rows(pack.id, "references", book = "john", chapter = 3, first = 17, last = 17).total)
        assertEquals(0, store.rows(pack.id, "references", book = "john", chapter = 3, first = 19, last = 19).total)
        assertEquals("image-fixture", store.media(pack.id, mediaId)?.readText())
        assertEquals(1,store.rows(pack.id,"entries",query="entry").total)
        assertEquals(0,store.rows(pack.id,"entries",query="entry",titleOnly=true).total)
        assertEquals(1,store.rows(pack.id,"entries",query="БОГ",titleOnly=true).total)
        assertEquals(1, StudyPackageStore(context, directory = File(root, "installed")).rows(pack.id, "word_forms", query = "Богу").total)
    } }
    @Test fun checksumFailurePreservesThePreviouslyInstalledVersion() = runBlocking { isolated { root, store ->
        val (old, oldZip) = archive(root, "TEST_DICT"); store.install(old, oldZip)
        val (fresh, freshZip) = archive(root, "TEST_DICT", "New"); freshZip.appendText("tampered")
        assertTrue(runCatching { store.install(fresh.copy(bytes = freshZip.length()), freshZip) }.isFailure)
        assertEquals(old, store.installed().single()); assertEquals("\"Бог\"", store.rows(old.id, "entries", ids = listOf("entry")).rows.single()["topic"].toString())
    } }
    @Test fun verifiedZipCannotEscapeItsContentDirectoryOrCommitAnIncompleteModule() = runBlocking { isolated { root, store ->
        val (pack, zip) = archive(root, "TEST_DICT", extra = "../../escape.txt")
        assertTrue(runCatching { store.install(pack, zip) }.isFailure); assertTrue(store.installed().isEmpty()); assertFalse(File(root.parentFile, "escape.txt").exists())
    } }
    @Test fun fullStrongDispatchRetainsPublishedSourceAndHebrewGreekIdentityWithoutNetwork() = runBlocking { isolated { root, store ->
        val zip = File(root,"strong.zip")
        ZipOutputStream(zip.outputStream()).use { output -> mapOf(
            "module.json" to "{\"schema\":1,\"kind\":\"strong\",\"code\":\"STRONG\"}",
            "lexicons.jsonl" to "{\"code\":\"EN\",\"name\":\"English source\",\"language\":\"en\"}\n{\"code\":\"RU\",\"name\":\"Русский источник\",\"language\":\"ru\"}",
            "entries.jsonl" to "{\"id\":\"H430\",\"lexicon_code\":\"EN\",\"word\":\"Elohim\",\"content\":\"English\"}\n{\"id\":\"H430\",\"lexicon_code\":\"RU\",\"word\":\"אלהים\",\"transliteration\":\"elohim\",\"pronunciation\":\"el-o-heem\",\"content\":\"<p>Полное объяснение</p>\"}\n{\"id\":\"G430\",\"lexicon_code\":\"RU\",\"word\":\"Greek word\",\"content\":\"Other\"}"
        ).forEach { (name,content)->output.putNextEntry(ZipEntry(name));output.write(content.toByteArray());output.closeEntry() } }
        val hash=MessageDigest.getInstance("SHA-256").digest(zip.readBytes()).joinToString(""){"%02x".format(it)}
        store.install(StudyOfflinePackage("STRONG","strong",hash,zip.length(),hash,"/api/offline/packages/STRONG"),zip)
        val api=BibleApiClient()
        try {
            val noNetwork=object:BibleContentSource by api {override suspend fun getStrongEntry(number:String,verseId:Long):StrongEntry=error("Unexpected network")}
            val repository=OfflineContentRepository(noNetwork,OfflineStore(File(root,"other"))).also{it.useStudyPackages(store,"ru")}
            val entry=repository.getStrongEntry("H0430",1)
            assertEquals(listOf("RU","EN"),repository.installedStrongEntries("H430").map{it.lexicon.code})
            assertEquals("H430",entry.number);assertEquals("אלהים",entry.word);assertEquals("<p>Полное объяснение</p>",entry.content);assertEquals("Русский источник",entry.lexicon.name);assertEquals("RU",entry.lexicon.code)
            assertEquals("Greek word",repository.getStrongEntry("G430",1).word)
            assertTrue(runCatching{repository.getStrongEntry("H99999",1)}.isFailure)
        }finally{api.close()}
    } }
    @Test fun commentaryBooksAndSectionsUsePublishedOrderAndNumericAliases() = runBlocking { isolated { root,store ->
        val zip=File(root,"commentary.zip")
        ZipOutputStream(zip.outputStream()).use { output -> mapOf(
            "module.json" to """{"schema":1,"kind":"commentary","code":"TEST_COMM","api_id":70,"name":"Source"}""",
            "books.jsonl" to """{"id":"later","api_id":200,"title":"Later book","order":9}
{"id":"first","api_id":100,"title":"First book","order":1}""",
            "entries.jsonl" to """{"id":"second-section","api_id":102,"commentary_book_id":"first","commentary_book_api_id":100,"book_slug":"john","book_osis":"John","chapter_from":1,"verse_from":2,"chapter_to":1,"verse_to":3,"body":"Second body","order":9}
{"id":"first-section","api_id":101,"commentary_book_id":"first","commentary_book_api_id":100,"book_slug":"john","book_osis":"John","chapter_from":1,"verse_from":1,"chapter_to":1,"verse_to":1,"body":"First body","order":1}"""
        ).forEach{(name,body)->output.putNextEntry(ZipEntry(name));output.write(body.toByteArray());output.closeEntry()} }
        val hash=MessageDigest.getInstance("SHA-256").digest(zip.readBytes()).joinToString(""){"%02x".format(it)}
        store.install(StudyOfflinePackage("TEST_COMM","commentary",hash,zip.length(),hash,"/api/offline/packages/TEST_COMM"),zip)
        assertEquals(listOf("\"first\"","\"later\""),store.rows("TEST_COMM","books").rows.map{it["id"].toString()})
        assertEquals(listOf("\"first-section\"","\"second-section\""),store.rows("TEST_COMM","entries",parentApiId=100).rows.map{it["id"].toString()})
        assertEquals(1,store.rows("TEST_COMM","entries",apiIds=listOf(101),bookOsis="John").total)
        assertEquals(0,store.rows("TEST_COMM","entries",apiIds=listOf(101),bookOsis="Gen").total)
    } }
    @Test fun deletingAnInstalledModulePreservesPersonalProfileAndOtherModules()=runBlocking {isolated{root,store->
        val preferences=context.getSharedPreferences("study-package-delete-personal-test",android.content.Context.MODE_PRIVATE)
        preferences.edit().putString("word-note","Keep this personal note").commit()
        val(first,firstZip)=archive(root,"FIRST");val(second,secondZip)=archive(root,"SECOND");store.install(first,firstZip);store.install(second,secondZip);store.remove("FIRST")
        assertEquals(listOf("SECOND"),store.installed().map{it.id});assertEquals("Keep this personal note",preferences.getString("word-note",null));assertEquals(2,store.rows("SECOND","entries").total)
        preferences.edit().clear().commit();Unit
    }}
    @Test fun legacyCrossReferencesKeepCanonicalAddressesUnknownWithoutInventingAnEditionPreview()=runBlocking {isolated{root,packages->
        val zip=File(root,"references.zip")
        ZipOutputStream(zip.outputStream()).use{output->mapOf(
            "module.json" to """{"schema":1,"kind":"cross_references","code":"CROSS_REFERENCES"}""",
            "sources.jsonl" to """{"code":"legacy_quote","name":"BibleQuote quote.tsk","record_count":2}""",
            "references.jsonl" to """{"id":"first","api_id":1,"type":"parallel","source_code":"legacy_quote","source_name":"BibleQuote quote.tsk","source_verse":{"api_id":10,"osis_ref":"John.3.16","book_slug":"john","book_osis":"John","chapter":3,"verse":16},"target_verse":{"api_id":100,"osis_ref":"Gen.1.1","book_slug":"genesis","book_osis":"Gen","chapter":1,"verse":1},"raw_range":"Быт. 1:1–2","metadata":{"legacy_quote_id":9,"raw_ref":"Быт. 1:1–2"}}
{"id":"second","api_id":2,"type":"parallel","source_code":"legacy_quote","source_name":"BibleQuote quote.tsk","source_verse":{"api_id":10,"osis_ref":"John.3.16","book_slug":"john","book_osis":"John","chapter":3,"verse":16},"target_verse":{"api_id":101,"osis_ref":"Gen.1.2","book_slug":"genesis","book_osis":"Gen","chapter":1,"verse":2},"raw_range":"Быт. 1:1–2","metadata":{"legacy_quote_id":9,"raw_ref":"Быт. 1:1–2"}}"""
        ).forEach{(name,body)->output.putNextEntry(ZipEntry(name));output.write(body.toByteArray());output.closeEntry()}}
        val hash=MessageDigest.getInstance("SHA-256").digest(zip.readBytes()).joinToString(""){"%02x".format(it)}
        packages.install(StudyOfflinePackage("CROSS_REFERENCES","cross_references",hash,zip.length(),hash,"/api/offline/packages/CROSS_REFERENCES"),zip)
        val content=OfflineStore(File(root,"bible"));val edition=TranslationSummary("RST","Russian",language=LanguageSummary("ru","Русский"))
        val john=BibleBook("rst-john","Иоанна",chaptersCount=3,canonicalBook=CanonicalBookSummary("John","new"));val genesis=BibleBook("rst-genesis","Бытие",chaptersCount=1,canonicalBook=CanonicalBookSummary("Gen","old"))
        content.write("books:RST",ListSerializer(BibleBook.serializer()),listOf(john,genesis))
        content.write(chapterKey("RST",john.slug,3),BibleChapter.serializer(),BibleChapter(edition,john,ChapterSummary(3,1),listOf(BibleVerse(999,16,"John.3.16","Source text","Source text"))))
        content.write(chapterKey("RST",genesis.slug,1),BibleChapter.serializer(),BibleChapter(edition,genesis,ChapterSummary(1,1),listOf(BibleVerse(201,1,"Gen.1.1","Actual edition text","Actual edition text"))))
        val api=BibleApiClient()
        try{val forbidden=object:BibleContentSource by api{override suspend fun getCrossReferences(verseId:Long,translationCode:String):CrossReferences=error("Unexpected network")}
            val repository=OfflineContentRepository(forbidden,content).also{it.useStudyPackages(packages)}
            repository.getChapter("RST",john.slug,3) // Registry uses actual id999; published source alias10 is stale.
            val refs=repository.getCrossReferences(999,"RST")
            assertEquals("John.3.16",refs.verse.osisRef);assertEquals(2,refs.references.size)
            assertEquals("genesis",refs.references.first().target.bookSlug);assertEquals(100,refs.references.first().target.verseId);assertNull(refs.references.first().target.text);assertEquals("unknown",refs.references.first().versification.status)
            assertNull(refs.references.last().target.text);assertEquals("Gen.1.2",refs.references.last().target.osisRef);assertEquals("BibleQuote quote.tsk",refs.references.first().source);assertEquals("Быт. 1:1–2",refs.references.first().metadata?.rawReference)
        }finally{api.close()}
    }}
}
