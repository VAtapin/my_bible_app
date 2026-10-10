package com.bibledesktop.myapp

import android.graphics.Bitmap
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.bibledesktop.myapp.ui.bible.*
import com.bibledesktop.myapp.ui.theme.BibleDesktopTheme
import com.bibledesktop.shared.api.*
import kotlinx.serialization.json.Json
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.zip.GZIPInputStream

/** Actual ChapterScreen and continuous reading of the genuine APK Synodal Genesis 29. */
class ReaderToolbarTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private val api=BibleApiClient()
    private val chapters=GZIPInputStream(context.assets.open("bibles/synodal.bundle")).bufferedReader(Charsets.UTF_8).use { reader ->
        reader.readLine();val result=mutableMapOf<Int,BibleChapter>();val json=Json{ignoreUnknownKeys=true}
        while(result.size<3){val chapter=json.decodeFromString(BibleChapter.serializer(),requireNotNull(reader.readLine()));if(chapter.chapter.number in 28..30)result[chapter.chapter.number]=chapter}
        result
    }
    private val chapter=chapters.getValue(29)
    private var commentaryRequests=0
    private val source=object:BibleContentSource by api {
        override suspend fun getTranslations(language:String?)=listOf(chapter.translation)
        override suspend fun getBooks(translationCode:String)=listOf(chapter.book)
        override suspend fun getChapter(translationCode:String,bookSlug:String,chapterNumber:Int):BibleChapter=chapters[chapterNumber]?:error("Outside bounded actual asset fixture")
        override suspend fun getCommentaryModules():List<CommentaryModule>{commentaryRequests++;return emptyList()}
        override suspend fun getCanonicalSlug(canon:String,osis:String)=chapter.book.slug
        override suspend fun getCommentaries(book:String,chapter:Int?,modules:List<String>,offset:Int)=CommentaryPage(book,chapter,emptyList(),0)
    }
    private val actions=mutableListOf<String>()
    private var place=""
    @After fun after(){api.close()}
    private fun show(){
        compose.setContent {BibleDesktopTheme {CompositionLocalProvider(LocalReaderPreferences provides ReaderPreferences(crossReferences=false,commentaryLinks=false,strongNumbers=false,tapPaging=false)){
            ChapterScreen(language="ru",studyClient=source,state=LoadState.Ready(chapter),chapterNumber=29,chaptersCount=chapter.book.chaptersCount,fontSize=18f,bookmarkedKeys=emptySet(),
                onBack={actions+="navigate-back"},onHome={actions+="home"},onRetry={},onSearch={actions+="search"},onSettings={actions+="settings"},onToggleNight={actions+="night"},onSourceInfo={actions+="source"},onTranslations={actions+="favorites"},onHistory={actions+="history"},onHistoryBack={actions+="back"},onHistoryForward={actions+="forward"},onPrevious={},onNext={},onFontSmaller={},onFontLarger={},onBookmark={_,_->},onShare={_,_->},onNote={_,_->},onStudy={_,_->},onPersonal={_,_->},selection=null,onStrong={_,_,_->},
                onVisiblePlace={value,first,_,offset->place="${value.chapter.number}:${first.number}:$offset"},initialVerse=4,comparison=null,comparing=false,onCompare={actions+="compare"},onDownloads={actions+="downloads"},textLanguage=chapter.translation.language.code,onChooseBook={actions+="book"},onChooseChapter={actions+="chapter"})
        }}}
        compose.waitUntil(10000){place.isNotEmpty()}
    }
    private fun open(){compose.onNodeWithTag("reader-tool-open").performClick();compose.onNodeWithTag("reader-tools-popup").assertIsDisplayed()}
    private fun screenshot(name:String){
        val image=requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        try{File(requireNotNull(context.externalCacheDir),name).outputStream().use{image.compress(Bitmap.CompressFormat.PNG,100,it)}}finally{image.recycle()}
    }
    @Test fun closedByDefaultOverlayKeepsReadingPlaceAndAllElevenActionsRemainAvailable(){
        show();compose.onNodeWithTag("reader-tools-popup").assertDoesNotExist()
        screenshot("reader-toolbar-closed.png")
        val before=place;val bounds=compose.onNodeWithTag("continuous-reader").getUnclippedBoundsInRoot()
        open();screenshot("reader-toolbar-open.png")
        compose.onNodeWithTag("reader-tool-back").assertDoesNotExist()
        compose.onNodeWithTag("reader-tool-forward").assertDoesNotExist()
        val ids=listOf("compare","downloads","place","search","commentary","settings","night","source","favorites","history")
        ids.forEach{compose.onNodeWithTag("reader-tool-$it").assertIsDisplayed()}
        assertEquals(bounds,compose.onNodeWithTag("continuous-reader").getUnclippedBoundsInRoot());assertEquals(before,place)
        compose.onNodeWithTag("reader-tool-close").performClick();compose.onNodeWithTag("reader-tools-popup").assertDoesNotExist()
        ids.filterNot{it=="commentary"}.forEach{id->open();compose.onNodeWithTag("reader-tool-$id").performClick();compose.onNodeWithTag("reader-tools-popup").assertDoesNotExist();assertEquals(if(id=="place")"chapter"else id,actions.last())}
        open();compose.onNodeWithTag("reader-tool-commentary").performClick();compose.onNodeWithTag("reader-tools-popup").assertDoesNotExist()
        compose.waitUntil(10000){commentaryRequests>0}
    }
    @Test fun physicalTopEdgeSwipeOpensButTextScrollHorizontalAndTwoFingerDoNot(){
        show();val viewport=compose.onNodeWithTag("reader-screen-viewport")
        val initial=place
        compose.onNodeWithTag("continuous-reader").performTouchInput{swipeUp()}
        compose.waitUntil(10000){place!=initial}
        compose.onNodeWithTag("reader-tools-popup").assertDoesNotExist()
        viewport.performTouchInput{swipe(Offset(width/2f,4f),Offset(width/2f+100f,4f),300)}
        compose.onNodeWithTag("reader-tools-popup").assertDoesNotExist()
        viewport.performTouchInput{down(0,Offset(width/2f-20f,4f));down(1,Offset(width/2f+20f,4f));moveTo(0,Offset(width/2f-20f,164f));moveTo(1,Offset(width/2f+20f,164f));up(0);up(1)}
        compose.onNodeWithTag("reader-tools-popup").assertDoesNotExist()
        val before=place
        val edgeDistance=with(compose.density){80.dp.toPx()}
        viewport.performTouchInput{swipe(Offset(width/2f,4f),Offset(width/2f,edgeDistance),500)}
        compose.onNodeWithTag("reader-tools-popup").assertIsDisplayed();assertEquals(before,place)
        Espresso.pressBack();compose.onNodeWithTag("reader-tools-popup").assertDoesNotExist()
        assertFalse(actions.contains("navigate-back"))
        open()
        // Compose root dispatch bypasses WindowManager outside touches. Inject at screen level
        // inside the real Activity and outside the anchored popup to exercise native dismissal.
        var outside=Offset.Zero
        compose.runOnIdle {
            val decor=compose.activity.window.decorView;val position=IntArray(2);decor.getLocationOnScreen(position)
            outside=Offset(position[0]+decor.width/2f,position[1]+decor.height*0.75f)
        }
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        val time=SystemClock.uptimeMillis()
        listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP).forEach{action->
            val event=MotionEvent.obtain(time,SystemClock.uptimeMillis(),action,outside.x,outside.y,0).apply{source=InputDevice.SOURCE_TOUCHSCREEN}
            try{assertTrue(automation.injectInputEvent(event,true))}finally{event.recycle()}
        }
        compose.onNodeWithTag("reader-tools-popup").assertDoesNotExist()
        compose.onNodeWithTag("reader-choose-book").performClick();assertEquals("book",actions.last())
        compose.onNodeWithTag("reader-choose-chapter").performClick();assertEquals("chapter",actions.last())
    }
    @Test fun longPressShowsActualLocalizedHintWithoutOpeningThePanel(){
        show();compose.onNodeWithTag("reader-tool-open").performTouchInput{longClick()}
        compose.onNodeWithText("Ещё").assertIsDisplayed()
        compose.onNodeWithTag("reader-tools-popup").assertDoesNotExist()
        open();compose.onNodeWithTag("reader-tool-source").performTouchInput{longClick()}
        compose.onNodeWithText(com.bibledesktop.myapp.ui.study.moduleSourceTitle("ru")).assertIsDisplayed()
        assertFalse(actions.contains("source"))
    }
}
