package com.bibledesktop.myapp

import android.content.Context
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.bibledesktop.myapp.data.*
import com.bibledesktop.myapp.ui.bible.*
import com.bibledesktop.myapp.ui.theme.BibleDesktopTheme
import com.bibledesktop.shared.api.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.UUID

class ReaderControlsTest {
    @Test fun dictionaryQueryUsesOnlyTheActualValidSelectedSubstring() {
        assertEquals("Богу",dictionarySelectionQuery("Слава Богу",6,10))
        assertEquals("Богу",dictionarySelectionQuery("Слава Богу",10,6))
        assertNull(dictionarySelectionQuery("Слава Богу",0,0));assertNull(dictionarySelectionQuery("Богу",0,5))
        assertNull(dictionarySelectionQuery("a".repeat(121),0,121));assertNull(dictionarySelectionQuery("😀",0,1))
    }
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun historyRestoresExactOffsetsAndKeepsWindowsSeparate() {
        val id=UUID.randomUUID().toString(); val a=ReaderHistoryStore(context,"a-$id");val b=ReaderHistoryStore(context,"b-$id")
        val exodus=ReaderHistoryPlace("RST","exodus",4,5,23);val psalm=ReaderHistoryPlace("KJV","psalms",22,3,0);val later=psalm.copy(chapter=23,verse=1,offset=44)
        a.observe(exodus);a.navigate(psalm);a.observe(later);b.observe(exodus.copy(offset=9))
        assertEquals(2,a.entries.size);assertEquals(psalm,a.back());assertEquals(exodus,a.back())
        assertEquals(psalm,a.forward());assertEquals(later,a.forward())
        assertEquals(later,ReaderHistoryStore(context,"a-$id").current)
        assertEquals(9,ReaderHistoryStore(context,"b-$id").current?.offset)
        context.getSharedPreferences("bible-desktop-reader-history",Context.MODE_PRIVATE).edit().remove("a-$id").remove("b-$id").commit()
    }
    @Test fun volumeKeysOnlyScrollWhenReaderExplicitlyEnabled() {
        val enabled=mutableStateOf(false);val showing=mutableStateOf(true);var total=0
        compose.setContent {if(showing.value) ReaderVolumePaging(enabled.value){total+=it}}
        assertFalse(ReaderVolumeKeys.dispatch(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_VOLUME_DOWN)))
        compose.runOnIdle{enabled.value=true};compose.waitForIdle()
        assertTrue(ReaderVolumeKeys.dispatch(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_VOLUME_DOWN)))
        assertTrue(ReaderVolumeKeys.dispatch(KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_VOLUME_DOWN)))
        assertEquals(1,total)
        assertFalse(ReaderVolumeKeys.dispatch(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BACK)))
        compose.runOnIdle{showing.value=false};compose.waitForIdle()
        assertFalse(ReaderVolumeKeys.dispatch(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_VOLUME_UP)))
    }
    @Test fun realOfflineVerseGridAndDigitalNavigationRejectMissingVerses() {
        val directory=File(context.cacheDir,"verse-picker-${UUID.randomUUID()}");val store=OfflineStore(File(directory,"content"))
        runBlocking {BundledBible.install(context,store)}
        val api=BibleApiClient();val source=OfflineContentRepository(object:BibleContentSource by api {
            override suspend fun getChapter(translationCode:String,bookSlug:String,chapterNumber:Int):BibleChapter=error("No network")
        },store,{false})
        val books=runBlocking{source.getBooks(BundledBible.code)};val book=books.first()
        var opened:Pair<Int,Int>?=null
        val prefs=context.getSharedPreferences("bible-desktop-reader-controls",Context.MODE_PRIVATE)
        val oldMode=prefs.getBoolean("digitalNavigation",false);prefs.edit().putBoolean("digitalNavigation",false).commit()
        compose.setContent {BibleDesktopTheme {PassagePicker("ru",emptyList(),source,BundledBible.code,book,1,false,{_,_,_->},{},{},onVerseSelect={_,_,ch,v->opened=ch to v})}}
        compose.waitUntil(10_000) {compose.onAllNodesWithTag("choose-chapter-1").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("choose-chapter-1").performClick()
        compose.waitUntil(10_000) {compose.onAllNodesWithTag("choose-verse-3").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("choose-verse-3").performClick();assertEquals(1 to 3,opened)
        compose.onNodeWithText("Глава:стих").performClick()
        compose.onNodeWithTag("picker-digital-position").performTextReplacement("1:999")
        compose.onNodeWithTag("picker-digital-go").performClick()
        compose.waitUntil(5_000) {compose.onAllNodesWithText("Такого места нет в выбранном тексте.").fetchSemanticsNodes().isNotEmpty()}
        assertEquals(1 to 3,opened)
        compose.onNodeWithTag("picker-digital-position").performTextReplacement("2:4")
        compose.onNodeWithTag("picker-digital-go").performClick()
        compose.waitUntil(5_000) {opened==2 to 4}
        prefs.edit().putBoolean("digitalNavigation",oldMode).commit();source.close()
    }
}
