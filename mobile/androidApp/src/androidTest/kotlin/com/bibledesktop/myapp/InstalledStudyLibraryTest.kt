package com.bibledesktop.myapp

import androidx.test.platform.app.InstrumentationRegistry
import com.bibledesktop.myapp.data.*
import com.bibledesktop.shared.api.DictionaryApi
import com.bibledesktop.shared.api.CommentarySourceRule
import com.bibledesktop.shared.api.StudyPosition
import com.bibledesktop.shared.api.commentarySourcesAt
import com.bibledesktop.myapp.ui.study.readCommentarySourceRules
import com.bibledesktop.myapp.ui.study.writeCommentarySourceRules
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class InstalledStudyLibraryTest {
 private val context=InstrumentationRegistry.getInstrumentation().targetContext
 @Test fun intervalSourceRulesPersistWithoutReplacingBookOrGlobalPreferences(){
  val preferences=context.getSharedPreferences("commentary-rule-test-${UUID.randomUUID()}",android.content.Context.MODE_PRIVATE)
  try{preferences.edit().putStringSet("sources",setOf("GLOBAL")).putStringSet("sources-book:John",setOf("BOOK")).commit()
   val rule=CommentarySourceRule("rule","John",StudyPosition(3,16),StudyPosition(4,2),listOf("RANGE"))
   writeCommentarySourceRules(preferences,listOf(rule));assertEquals(listOf(rule),readCommentarySourceRules(preferences))
   assertEquals(listOf("RANGE"),commentarySourcesAt("John",StudyPosition(4,2),listOf("BOOK"),readCommentarySourceRules(preferences)))
   assertEquals(setOf("GLOBAL"),preferences.getStringSet("sources",emptySet()));assertEquals(setOf("BOOK"),preferences.getStringSet("sources-book:John",emptySet()))
   writeCommentarySourceRules(preferences,emptyList());assertTrue(readCommentarySourceRules(preferences).isEmpty())
  }finally{preferences.edit().clear().commit()}
 }
 private suspend fun installed(root:File,store:StudyPackageStore,code:String,kind:String,files:Map<String,String>){
  val zip=File(root,"$code.zip");ZipOutputStream(zip.outputStream()).use{out->(mapOf("module.json" to """{"schema":1,"kind":"$kind","code":"$code","name":"Published $code"}""")+files).forEach{(name,body)->out.putNextEntry(ZipEntry(name));out.write(body.toByteArray());out.closeEntry()}}
  val hash=MessageDigest.getInstance("SHA-256").digest(zip.readBytes()).joinToString(""){"%02x".format(it)}
  store.install(StudyOfflinePackage(code,kind,hash,zip.length(),hash,"/api/offline/packages/$code"),zip)
 }
 @Test fun adaptersPreserveRealAliasesSectionOrderCrossChapterRangesAndDictionaryOsis()=runBlocking {
  val root=File(context.cacheDir,"installed-library-${UUID.randomUUID()}");check(root.mkdirs());val store=StudyPackageStore(context,directory=File(root,"installed"))
  try {
   installed(root,store,"COMMENT","commentary",mapOf("books.jsonl" to """{"id":"published-book","api_id":9000000001,"title":"Actual book","order":0}""",
    "entries.jsonl" to """{"id":"later","api_id":801,"commentary_book_id":"published-book","commentary_book_api_id":9000000001,"book_slug":"john","book_osis":"John","chapter_from":3,"verse_from":16,"chapter_to":4,"verse_to":2,"body":"Full later body","order":2}
{"id":"intro","api_id":802,"commentary_book_id":"published-book","commentary_book_api_id":9000000001,"book_slug":"john","book_osis":"John","chapter_from":0,"verse_from":0,"body":"Whole book introduction","order":1}"""))
   val library=InstalledStudyLibrary(store)
   assertEquals(9000000001L,library.books("Actual",0)!!.data.single().id)
   assertEquals(listOf(802L,801L),library.contents(9000000001L,0)!!.sections.map{it.id})
   assertEquals("Full later body",library.article(9000000001L,801)!!.body)
   assertEquals("john",library.canonicalSlug("John"))
   assertEquals(2,library.commentaries("john",4,listOf("COMMENT"),0)!!.total)
   assertEquals(9000000001L,library.commentaries("john",4,listOf("COMMENT"),0)!!.entries.first().commentaryBookId)
   assertEquals(1,library.commentaries("john",null,listOf("COMMENT"),0)!!.total)
   installed(root,store,"DICT","dictionary",mapOf("entries.jsonl" to """{"id":"${"a".repeat(40)}","api_id":1701,"topic":"Actual topic","body":"Full definition","order":0}""",
    "references.jsonl" to """{"entry_id":"${"a".repeat(40)}","entry_api_id":1701,"book_slug":"john","book_osis":"John","chapter":3,"verse_from":16,"verse_to":18}""",
    "links.jsonl" to "","word_forms.jsonl" to "","media_links.jsonl" to "","media.jsonl" to ""))
   val repository=DictionaryRepository(context,DictionaryApi(baseUrl="http://127.0.0.1:1/api"),store)
   try {val module=repository.downloadedModules().single();val article=repository.article(module,"a".repeat(40));assertEquals(1701L,article.id);assertEquals("John",article.references.single().bookOsis);assertEquals("Full definition",article.body)
    assertEquals(1,repository.context("wrong-slug",3,listOf("DICT"),0,17,17,"John").total)
    assertEquals("DICT",repository.searchInstalled("topic",listOf("DICT")).data.single().moduleCode)
   }finally{repository.close()}
  }finally{require(root.canonicalPath.startsWith(context.cacheDir.canonicalPath+File.separator));root.deleteRecursively()}
 }
}
