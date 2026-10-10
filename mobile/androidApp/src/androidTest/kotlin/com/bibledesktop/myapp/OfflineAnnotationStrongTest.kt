package com.bibledesktop.myapp

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.bibledesktop.myapp.data.*
import com.bibledesktop.myapp.ui.study.VerseStudyDialog
import com.bibledesktop.myapp.ui.theme.BibleDesktopTheme
import com.bibledesktop.shared.api.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class OfflineAnnotationStrongTest {
 @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
 @Test fun annotationNumberAndInitialStrongOpenInstalledArticleWithoutTokenNetwork()=verifyInstalledArticle(true)
 @Test fun initialStrongStillOpensInstalledArticleAfterTokenApiFails()=verifyInstalledArticle(false)
 private fun verifyInstalledArticle(withAnnotations:Boolean)=runBlocking {
  val root=File(compose.activity.cacheDir,"annotation-strong-${UUID.randomUUID()}").also{check(it.mkdirs())}
  val store=StudyPackageStore(compose.activity,directory=File(root,"packages"))
  val zip=File(root,"strong.zip")
  val files=mapOf("module.json" to """{"schema":1,"kind":"strong","code":"STRONG"}""",
   "lexicons.jsonl" to """{"code":"HEB","name":"Installed Hebrew lexicon","language":"en"}""",
   "entries.jsonl" to """{"id":"H430","lexicon_code":"HEB","word":"אלהים","content":"Full installed Elohim definition"}""")
  ZipOutputStream(zip.outputStream()).use{out->files.forEach{(name,body)->out.putNextEntry(ZipEntry(name));out.write(body.toByteArray());out.closeEntry()}}
  val hash=MessageDigest.getInstance("SHA-256").digest(zip.readBytes()).joinToString(""){"%02x".format(it)}
  store.install(StudyOfflinePackage("STRONG","strong",hash,zip.length(),hash,"/api/offline/packages/STRONG"),zip)
  val api=BibleApiClient()
  var tokenCalls=0
  var articleCalls=0
  val translation=TranslationSummary("FIXTURE","Source",language=LanguageSummary("en","English"),hasStrong=true)
  val book=BibleBook("gen","Genesis",chaptersCount=1)
  val annotations=SourceAnnotations("available",source=AnnotationSource("mybible","a".repeat(64)),strongTokens=listOf(SourceStrongAnnotation("H430",7,3,"N","God")),features=listOf("headings","footnotes","added_words","paragraphs").associateWith{"absent"})
  val verse=BibleVerse(1,1,"Gen.1.1","God<S>430</S>","God",hasStrongMarkup=true,annotations=annotations.takeIf{withAnnotations})
  val chapter=BibleChapter(translation,book,ChapterSummary(1,1),listOf(verse))
  val remote=object:BibleContentSource by api {
   override suspend fun getBooks(translationCode:String)=listOf(book)
   override suspend fun getTranslations(language:String?)=listOf(translation)
   override suspend fun getCommentaryModules()=emptyList<CommentaryModule>()
   override suspend fun getStrongTokens(verseId:Long,translationCode:String):StrongTokens {tokenCalls++;error("Offline tokens")}
   override suspend fun getStrongEntry(number:String,verseId:Long):StrongEntry {articleCalls++;error("Offline article")}
   override suspend fun getCrossReferences(verseId:Long,translationCode:String)=CrossReferences(StudyVerse(1,"Gen.1.1"),translationCode)
  }
  val client=OfflineContentRepository(remote,OfflineStore(File(root,"bibles"))).also{it.useStudyPackages(store,"en")}
  try {
   assertEquals(if(withAnnotations)listOf(StrongToken("H430",7,"God","N"))else emptyList(),verse.sourceStudyStrongTokens())
   compose.setContent{BibleDesktopTheme{VerseStudyDialog("en",chapter,verse,client,onOpen={},onClose={},initialStrong="H430")}}
   compose.waitUntil(10_000){compose.onAllNodesWithText("Full installed Elohim definition").fetchSemanticsNodes().isNotEmpty()}
   compose.onNodeWithText("Full installed Elohim definition").performScrollTo().assertIsDisplayed()
   compose.waitForIdle()
   assertEquals(if(withAnnotations)0 else 1,tokenCalls);assertEquals(0,articleCalls)
  } finally {client.close();require(root.canonicalPath.startsWith(compose.activity.cacheDir.canonicalPath+File.separator));root.deleteRecursively()}
 }
}
