package com.bibledesktop.myapp

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.platform.app.InstrumentationRegistry
import com.bibledesktop.myapp.data.*
import com.bibledesktop.myapp.ui.study.*
import com.bibledesktop.myapp.ui.theme.BibleDesktopTheme
import com.bibledesktop.shared.api.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class LargeStudyBodyTest {
 @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
 @Test fun fullActualZipSectionReachesItsTailAndRestoresItsSavedPositionWithoutHugeTextLayout(){
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  val root=File(context.cacheDir,"large-study-render-${UUID.randomUUID()}");check(root.mkdirs())
  val preferencesName="large-study-render-${UUID.randomUUID()}";val prefs=context.getSharedPreferences(preferencesName,Context.MODE_PRIVATE)
  val visible=mutableStateOf(true);val api=BibleApiClient()
  try{
   val body=buildString{
    append("<p>BEGIN_LONG_SOURCE</p>")
    repeat(12_000){index->append("<p>PARAGRAPH_").append(index).append(" ").append("Слово λόγος 𐍈 👋 !".repeat(15)).append("</p>")}
    append("<p>END_LONG_SOURCE</p>")
   }
   val store=StudyPackageStore(context,directory=File(root,"installed"))
   val(pack,zip)=CommentaryPackageMediaTest().archive(root,"LARGE_SOURCE",withMedia=false,body=body)
   runBlocking{store.install(pack,zip)}
   val library=InstalledStudyLibrary(store)
   val section=runBlocking{requireNotNull(library.article(31,17))};assertEquals(body,section.body)
   val parts=runBlocking{prepareStudyBody(requireNotNull(section.body))};assertTrue(section.body!!.length>2_400_000)
   assertEquals(studyReadingText(body),parts.joinToString(""));assertTrue(parts.all{it.length<=2048&&!it.last().isHighSurrogate()})
   val client=object:BibleContentSource by api{
    override suspend fun getStudyBooks(query:String,offset:Int)=requireNotNull(library.books(query,offset))
    override suspend fun getBookContents(book:Long,offset:Int)=requireNotNull(library.contents(book,offset))
    override suspend fun getBookSection(book:Long,section:Long)=requireNotNull(library.article(book,section))
    override suspend fun getCommentaryModules()=library.modules()
   }
   prefs.edit().putLong("book",31).putLong("section",17).commit()
   compose.setContent{BibleDesktopTheme{if(visible.value)BooksScreen("ru",client,{},preferencesName)}}
   val reader=hasTestTag("study-book-body") and hasScrollToIndexAction()
   compose.waitUntil(30_000){compose.onAllNodes(reader).fetchSemanticsNodes().isNotEmpty()}
   compose.onNode(reader).performScrollToIndex(parts.size)
   compose.onNodeWithText("END_LONG_SOURCE",substring=true).assertIsDisplayed()
   compose.onNode(reader).performScrollToIndex(300)
   compose.waitUntil(10_000){prefs.getInt("position:31:17:row",0)>250}
   val savedRow=prefs.getInt("position:31:17:row",0);val savedOffset=prefs.getInt("position:31:17:offset",0)
   compose.waitForIdle()
   val savedAxis=compose.onNode(reader).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
   val savedVisibleText=parts[savedRow-1]
   compose.onNodeWithText(savedVisibleText).assertIsDisplayed()
   compose.runOnIdle{visible.value=false};compose.waitForIdle();compose.runOnIdle{visible.value=true}
   compose.waitUntil(30_000){compose.onAllNodes(reader).fetchSemanticsNodes().isNotEmpty()}
   compose.onNode(reader).assertIsDisplayed();compose.waitForIdle()
   assertEquals(savedAxis,compose.onNode(reader).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value(),0.001f)
   compose.onNodeWithText(savedVisibleText).assertIsDisplayed()
   assertEquals(savedRow,prefs.getInt("position:31:17:row",0));assertEquals(savedOffset,prefs.getInt("position:31:17:offset",0))
  }finally{
   compose.runOnIdle{visible.value=false};api.close();prefs.edit().clear().commit()
   require(root.canonicalPath.startsWith(context.cacheDir.canonicalPath+File.separator));root.deleteRecursively()
  }
 }
 @Test fun chunkBoundariesPreserveUnbrokenSupplementaryCharacters(){
  val text="𐍈👋".repeat(100_000);val parts=splitStudyText(text,2049)
  assertEquals(text,parts.joinToString(""));assertTrue(parts.all{it.length<=2049&&!it.last().isHighSurrogate()&&!it.first().isLowSurrogate()})
 }
}
