package com.bibledesktop.myapp

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.bibledesktop.myapp.ui.bible.*
import com.bibledesktop.myapp.ui.study.*
import com.bibledesktop.myapp.ui.theme.BibleDesktopTheme
import com.bibledesktop.shared.api.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class ReaderStudyPresentationTest {
 @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
 @Test fun emptySourceCardAndMissingLabelsNeverOccupyReadingSpace(){
  val metadata=mutableStateOf(buildJsonObject{put("name",JsonNull);put("author"," ");put("language",buildJsonObject{put("name"," ")})})
  compose.setContent{BibleDesktopTheme{ModuleSourceCard("ru",metadata.value," ")}}
  compose.onNodeWithText("Об источнике").assertDoesNotExist()
  compose.runOnIdle{metadata.value=buildJsonObject{put("name","Published source");put("author",JsonNull);put("edition"," ")}}
  compose.onNodeWithText("Об источнике").performClick()
  compose.onNodeWithText("Published source").assertIsDisplayed()
  listOf("Автор / переводчик / составитель","Издание","Дата обновления","Состав и разметка","Не указано источником").forEach{compose.onNodeWithText(it).assertDoesNotExist()}
 }
 @Test fun settingsUseCompactSingleLineCheckboxAndLabelTogglesOnce(){
  val value=mutableStateOf(ReaderPreferences())
  compose.setContent{BibleDesktopTheme{ReaderSettingsDialog("ru",value.value,{value.value=it},{})}}
  val label=readerControlText("ru","chapterLabels")
  val option=compose.onNode(hasText(label) and isToggleable())
  option.assertIsOn();option.assertHeightIsEqualTo(48.dp)
  option.performClick();option.assertIsOff()
  compose.runOnIdle{assertFalse(value.value.chapterLabels)}
 }
 @Test fun absentStrongPreferenceDefaultsOnButExplicitOffAndCleanArePreserved(){
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  val prefs=context.getSharedPreferences("bible-desktop-reader-controls",Context.MODE_PRIVATE)
  val existed=prefs.contains("strongNumbers");val old=prefs.getBoolean("strongNumbers",false)
  try{
   check(prefs.edit().remove("strongNumbers").commit());assertTrue(ReaderPreferencesStore(context).load().strongNumbers)
   check(prefs.edit().putBoolean("strongNumbers",false).commit());assertFalse(ReaderPreferencesStore(context).load().strongNumbers)
   assertFalse(ReaderPreferences(strongNumbers=true,clean=true).effective().strongNumbers)
  }finally{check(prefs.edit().apply{if(existed)putBoolean("strongNumbers",old)else remove("strongNumbers")}.commit())}
 }
 @Test fun numberAtSourceWordOpensOnlyClickedArticleWithoutNumberGrid(){
  val features=mapOf("headings" to "absent","footnotes" to "absent","added_words" to "absent","paragraphs" to "absent")
  val verse=BibleVerse(1,1,"John.1.1","Word","Word",annotations=SourceAnnotations("available",source=AnnotationSource("mybible","a".repeat(64)),strongTokens=listOf(SourceStrongAnnotation("G3056",0,4)),features=features))
  val chapter=BibleChapter(TranslationSummary(code="fixture",name="Fixture",language=LanguageSummary("en","English")),BibleBook("john","John",chaptersCount=21),ChapterSummary(1,1),listOf(verse))
  val calls=AtomicInteger();var chosen by mutableStateOf<String?>(null)
  val source=object:BibleContentSource by BibleApiClient(){override suspend fun getStrongEntry(number:String,verseId:Long):StrongEntry{calls.incrementAndGet();return StrongEntry(number,content="Fixture dictionary article",lexicon=StrongLexicon("Fixture lexicon","en"))}}
  compose.setContent{BibleDesktopTheme{Column{
   SourceVerseText(verse,AnnotatedString(verse.plainText),ReaderPreferences(),TextStyle.Default,onStrong={chosen=it})
   chosen?.let{StrongArticle("en",chapter,verse.id,source,it)}
  }}}
  assertEquals(0,calls.get());compose.onNodeWithText("G3056").assertDoesNotExist()
  val action=compose.onNodeWithText("WordG3056").fetchSemanticsNode().config[SemanticsActions.CustomActions].single{it.label=="G3056"}
  compose.runOnIdle{assertTrue(action.action())}
  compose.waitUntil(10_000){compose.onAllNodesWithText("Fixture dictionary article").fetchSemanticsNodes().isNotEmpty()}
  assertEquals(1,calls.get());assertEquals("G3056",chosen)
 }
}
