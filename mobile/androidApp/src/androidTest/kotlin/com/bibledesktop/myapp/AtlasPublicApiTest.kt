package com.bibledesktop.myapp

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.bibledesktop.myapp.ui.study.DictionariesScreen
import com.bibledesktop.myapp.ui.theme.BibleDesktopTheme
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Explicit read-only public API acceptance. Ordinary hermetic runs skip this class. */
class AtlasPublicApiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private var saved:Map<String,*>?=null
    private val preferences get()=instrumentation.targetContext.getSharedPreferences("dictionary-reading",android.content.Context.MODE_PRIVATE)
    @Before fun optIn(){
        assumeTrue(InstrumentationRegistry.getArguments().getString("verifyAtlasPublicApi")=="true")
        saved=preferences.all.toMap()
    }
    @After fun restore(){saved?.let{values->
        val editor=preferences.edit().clear()
        values.forEach{(key,value)->when(value){is String->editor.putString(key,value);is Int->editor.putInt(key,value);is Long->editor.putLong(key,value);is Boolean->editor.putBoolean(key,value);is Float->editor.putFloat(key,value);is Set<*>->editor.putStringSet(key,value.filterIsInstance<String>().toSet())}}
        check(editor.commit())
    }}
    private fun waitForActualImage(){
        try{
            compose.waitUntil(30_000){compose.onAllNodesWithTag("atlas-image-ready",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
        }catch(failure:Throwable){
            compose.onRoot(useUnmergedTree=true).printToLog("AtlasPublicApiFailure")
            screenshot("atlas-public-api-failure.png")
            throw failure
        }
    }
    private fun screenshot(name:String){
        instrumentation.uiAutomation.takeScreenshot()?.let{image->
            try{File(requireNotNull(instrumentation.targetContext.externalCacheDir),name).outputStream().use{image.compress(Bitmap.CompressFormat.PNG,100,it)}}finally{image.recycle()}
        }
    }
    @Test fun publicJapaneseMapLoadsThroughProductionRepositoryAndTapOpensActualImage(){
        compose.setContent{BibleDesktopTheme{
            DictionariesScreen("ru",{},initialModule="MB_DICT_9E9E6475510E",
                initialEntry="cdd7620cb1d4632b7b679252e8a3fb96e54517d0",atlasOnly=true)
        }}
        try{
            compose.waitUntil(30_000){compose.onAllNodesWithTag("atlas-image-viewport").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithTag("atlas-image-viewport").performScrollTo()
            waitForActualImage()
            compose.onNodeWithContentDescription("#第1図").assertIsDisplayed()
            screenshot("atlas-public-api-image.png")
            compose.onNodeWithTag("atlas-image-viewport").performClick()
            compose.onNodeWithTag("atlas-image-fullscreen").assertIsDisplayed()
            screenshot("atlas-public-api-fullscreen.png")
            Espresso.pressBack()
            compose.onNodeWithTag("atlas-image-fullscreen").assertDoesNotExist()
            compose.onNodeWithTag("atlas-image-viewport").assertIsDisplayed()
        }catch(failure:Throwable){
            compose.onAllNodes(isRoot(),useUnmergedTree=true).fetchSemanticsNodes().forEachIndexed{index,_->
                compose.onAllNodes(isRoot(),useUnmergedTree=true)[index].printToLog("AtlasPublicApiFailure")
            }
            screenshot("atlas-public-api-failure.png")
            throw failure
        }
    }
}
