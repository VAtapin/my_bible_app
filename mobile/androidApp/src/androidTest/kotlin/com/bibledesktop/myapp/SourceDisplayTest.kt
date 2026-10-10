package com.bibledesktop.myapp

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.*
import com.bibledesktop.myapp.ui.bible.*
import com.bibledesktop.myapp.ui.theme.BibleDesktopTheme
import com.bibledesktop.shared.api.*
import org.junit.*
import org.junit.Assert.*

class SourceDisplayTest {
 @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
 private val verse=BibleVerse(1,1,"Gen.1.1","God","God")
 @Test fun cleanHidesNoteIconsAndActionsWithoutRemovingSavedMarks(){
  val mark=WordMark("note","X",verse.osisRef,verse.plainText,0,3,"God",note="Saved note")
  val body=AnnotatedString("God",listOf(AnnotatedString.Range(SpanStyle(background=Color.Yellow),0,3)))
  val clean=mutableStateOf(true)
  compose.setContent{BibleDesktopTheme{SourceVerseText(verse,body,ReaderPreferences(clean=clean.value),TextStyle.Default,listOf(mark),{})}}
  val node=compose.onNodeWithText("God").fetchSemanticsNode()
  assertTrue(node.config[SemanticsActions.CustomActions].isEmpty())
  compose.onAllNodes(hasText("✎",substring=true)).assertCountEquals(0)
  val rendered=annotatedSourceVerse(verse,body,ReaderPreferences(clean=true),listOf(mark))
  assertEquals("God",rendered.text);assertTrue(rendered.spanStyles.any{it.item.background==Color.Yellow});assertEquals("Saved note",mark.note)
  compose.runOnIdle{clean.value=false}
  compose.onNodeWithText("God✎").assertExists()
 }
 @Test fun sourceFootnoteShowsActualPassageAndScrollableLongBody(){
  val source=AnnotationSource("mybible","a".repeat(64))
  val annotations=SourceAnnotations("available",source=source,footnotes=listOf(FootnoteAnnotation("footnote","[1]",("Source note line\n").repeat(100),3,"inline_note",true,source)),features=mapOf("headings" to "absent","footnotes" to "present","added_words" to "absent","paragraphs" to "absent"))
  val actual=verse.copy(annotations=annotations)
  compose.setContent{BibleDesktopTheme{CompositionLocalProvider(LocalSourceAnnotationLanguage provides "ru"){SourceVerseText(actual,AnnotatedString("God"),ReaderPreferences(),TextStyle.Default)}}}
  val action=compose.onNodeWithText("God[1]").fetchSemanticsNode().config[SemanticsActions.CustomActions].first{it.label.contains("[1]")}
  compose.runOnIdle{assertTrue(action.action())}
  compose.onNodeWithText("Gen.1.1").assertIsDisplayed()
  val scroll=compose.onNode(hasScrollAction()).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
  compose.runOnIdle{assertTrue(scroll.maxValue()>0f)}
  compose.onNodeWithText("Вернуться к чтению").performClick()
  compose.onNodeWithText("God[1]").assertIsDisplayed()
 }
}
