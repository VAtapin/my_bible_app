package com.bibledesktop.myapp

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.bibledesktop.myapp.data.*
import com.bibledesktop.myapp.ui.study.*
import com.bibledesktop.myapp.ui.theme.BibleDesktopTheme
import com.bibledesktop.shared.api.*
import kotlinx.serialization.json.*
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID

/** Actual captured JPEG/article replay, with bounded local catalog metadata. No generated map. */
class AtlasLibraryTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private val json=Json{ignoreUnknownKeys=true}
    private val code="MB_DICT_9E9E6475510E"
    private lateinit var image:File
    private lateinit var captured:DictionaryArticle
    private var saved=emptyMap<String,Any?>()
    private val preferences get()=context.getSharedPreferences("dictionary-reading",Context.MODE_PRIVATE)
    @Before fun fixture(){
        saved=preferences.all.toMap();check(preferences.edit().clear().commit())
        image=File(context.cacheDir,"actual-map-${UUID.randomUUID()}.jpg")
        instrumentation.context.assets.open("atlas/japan-media-578.jpg").use{input->image.outputStream().use{input.copyTo(it)}}
        val payload=instrumentation.context.assets.open("atlas/japan-article.json").bufferedReader().use{it.readText()}
        captured=json.decodeFromJsonElement(DictionaryArticle.serializer(),json.parseToJsonElement(payload).jsonObject.getValue("data"))
    }
    @After fun restore(){
        image.delete();val editor=preferences.edit().clear()
        saved.forEach{(key,value)->when(value){is String->editor.putString(key,value);is Int->editor.putInt(key,value);is Long->editor.putLong(key,value);is Boolean->editor.putBoolean(key,value);is Float->editor.putFloat(key,value);is Set<*>->editor.putStringSet(key,value.filterIsInstance<String>().toSet())}}
        check(editor.commit())
    }
    private fun source(failFirst:Boolean=false)=object:DictionaryReadingSource {
        var attempts=0
        val atlas=DictionaryModule(code,code,"ja","atlas","capture",1,1,0)
        override suspend fun modules()=listOf(atlas,DictionaryModule("ordinary","Ordinary dictionary","en","articles","capture",1,0,0))
        override suspend fun downloadedModules()=emptyList<DictionaryModule>()
        override suspend fun searchInstalled(query:String,codes:List<String>,offset:Int,limit:Int)=DictionaryPage(emptyList(),0)
        override suspend fun entries(module:String,query:String,offset:Int)=DictionaryPage(listOf(DictionaryTopic(captured.id,captured.key,captured.topic)),1)
        override suspend fun article(module:DictionaryModule,key:String)=captured.copy(links=captured.links+DictionaryLink("\u2003","a".repeat(40),"\u2003"))
        override suspend fun lookup(query:String,module:String)=emptyList<DictionaryWordForm>()
        override suspend fun image(module:String,media:DictionaryMedia,version:String?):File {
            assertEquals(code,module);assertEquals(578L,media.id);attempts++
            if(failFirst&&attempts==1)throw IOException("Unavailable source fixture")
            return this@AtlasLibraryTest.image
        }
        override fun close(){}
    }
    private fun waitTag(tag:String){
        try {
            // The tappable viewport merges descendants. Inspect the actual image success node,
            // rather than losing its tag inside the viewport's accessibility semantics.
            compose.waitUntil(10_000){compose.onAllNodesWithTag(tag,useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
        }catch(failure:Throwable){
            compose.onRoot(useUnmergedTree=true).printToLog("AtlasFailure")
            instrumentation.uiAutomation.takeScreenshot()?.let{bitmap->
                try{File(context.externalCacheDir,"atlas-failure-$tag.png").outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}}finally{bitmap.recycle()}
            }
            throw failure
        }
    }
    private fun openActualArticle(){
        waitTag("dictionary-module-$code")
        compose.onNodeWithTag("dictionary-module-ordinary").assertDoesNotExist()
        compose.onNodeWithTag("atlas-builtin").assertIsDisplayed()
        compose.onNodeWithTag("dictionary-module-$code").performClick()
        waitTag("dictionary-entry-${captured.key}")
        compose.onNodeWithTag("dictionary-entry-${captured.key}").performClick()
    }
    @Test fun unifiedAtlasCatalogLoadsActualJpegAndImageTapOpensFullscreenAndBackReturns(){
        val source=source()
        compose.setContent{BibleDesktopTheme{DictionariesScreen("ru",{},atlasOnly=true,repositoryOverride=source)}}
        openActualArticle()
        waitTag("atlas-image-viewport");compose.onNodeWithTag("atlas-image-viewport").performScrollTo()
        waitTag("atlas-image-ready")
        compose.onNodeWithContentDescription(captured.topic).assertIsDisplayed()
        compose.onNodeWithText("\u2003").assertDoesNotExist()
        compose.onNodeWithTag("atlas-image-viewport").performClick()
        compose.onNodeWithTag("atlas-image-fullscreen").assertIsDisplayed()
        Espresso.pressBack();compose.onNodeWithTag("atlas-image-fullscreen").assertDoesNotExist()
        compose.onNodeWithTag("atlas-image-viewport").assertIsDisplayed()
        assertEquals(1,source.attempts)
    }
    @Test fun mediaDownloadFailureIsVisibleAndRetryLoadsTheActualImage(){
        val source=source(failFirst=true)
        compose.setContent{BibleDesktopTheme{DictionariesScreen("ru",{},atlasOnly=true,repositoryOverride=source)}}
        openActualArticle()
        waitTag("atlas-download-retry-578")
        compose.onNodeWithTag("atlas-download-retry-578").performScrollTo().performClick()
        waitTag("atlas-image-viewport");compose.onNodeWithTag("atlas-image-viewport").performScrollTo()
        waitTag("atlas-image-ready")
        assertEquals(2,source.attempts)
    }
    @Test fun actualImageZoomAndPanStayClippedInsideItsViewport(){
        compose.setContent{BibleDesktopTheme{Column(Modifier.fillMaxSize().background(Color.Magenta)){
            AtlasImage(image,captured.topic,"ru")
            Box(Modifier.fillMaxWidth().height(40.dp).testTag("outside-map"))
        }}}
        waitTag("atlas-image-ready")
        compose.onNodeWithTag("atlas-image-zoom").performSemanticsAction(SemanticsActions.SetProgress){it(8f)}
        compose.onNodeWithTag("atlas-image-viewport").performTouchInput{swipe(center,center+androidx.compose.ui.geometry.Offset(70f,35f))}
        val bounds=compose.onNodeWithTag("outside-map").fetchSemanticsNode().boundsInRoot
        val bitmap=compose.onRoot().captureToImage().asAndroidBitmap()
        assertEquals(android.graphics.Color.MAGENTA,bitmap.getPixel(bounds.center.x.toInt(),(bounds.top+5).toInt()))
    }
    @Test fun actualImageIntegrityRejectsTruncatedGifAndNewMediaRevisionSupersedesArchiveOnlyPackage(){
        assertTrue(isReadableDictionaryImage(image))
        assertEquals("4dfdf0fc6bcc2a1acbc0575194cdf9e7ae5a89d7f36ea404c79982b1d7d67204",MessageDigest.getInstance("SHA-256").digest(image.readBytes()).joinToString(""){"%02x".format(it)})
        val gif=File(context.cacheDir,"bad-map-${UUID.randomUUID()}.gif")
        try{
            gif.writeBytes(ByteArray(60).also{val header="GIF89a".toByteArray();header.copyInto(it);it[6]=0x44;it[7]=4;it[8]=0x5b;it[9]=5;it[10]=0xf7.toByte();it[59]=0x3b})
            assertFalse(isReadableDictionaryImage(gif))
        }finally{gif.delete()}
        val old=buildJsonObject{put("source_archive_sha256","archive")}
        assertTrue(dictionaryPackageRevisionMatches(old,"archive"))
        assertFalse(dictionaryPackageRevisionMatches(old,"media-repaired"))
        val current=buildJsonObject{put("source_archive_sha256","archive");put("content_version","media-repaired")}
        assertTrue(dictionaryPackageRevisionMatches(current,"media-repaired"))
        assertEquals("https://bible-desktop.com/api/dictionaries/MAPS/media/578?v=hash-media-v2",dictionaryImageRevisionUrl("https://bible-desktop.com/api/dictionaries/MAPS/media/578","hash-media-v2"))
        assertTrue(readableDictionaryLinks(listOf(DictionaryLink("\u2003","a".repeat(40),"<p>&emsp;</p>"))).isEmpty())
    }
}
