package com.bibledesktop.myapp

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import com.bibledesktop.myapp.ui.bible.*
import com.bibledesktop.myapp.ui.theme.BibleDesktopTheme
import com.bibledesktop.shared.api.*
import org.junit.*
import org.junit.Assert.*
import kotlin.math.abs
import kotlinx.coroutines.launch

class ReaderPagingTest {
 @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
 @Test fun measuredBoundaryUsesPartialLineAndRealParagraphSpacing(){
  val lines=listOf(ReadingLine(12f,37f),ReadingLine(48f,89f),ReadingLine(114f,147f))
  assertEquals(104f,measuredPageDistance(lines,10f,130f,1)!!,.01f)
  assertEquals(104f,measuredPageDistance(lines,10f,100f,1)!!,.01f)
  assertEquals(-119f,measuredPageDistance(listOf(ReadingLine(-109f,-78f),ReadingLine(-60f,-29f)),10f,110f,-1)!!,.01f)
 }
 @Test fun actualTextLayoutAlignsClippedLineAtTopAndVolumeUsesSamePager(){
  val measurements=ReaderLineMeasurements()
  val text=(1..60).joinToString("\n"){"Measured reader line $it"}
  val verse=BibleVerse(1,1,"Gen.1.1",text,text)
  var page:(()->Unit)?=null
  compose.setContent{BibleDesktopTheme{
   val state=rememberLazyListState();val scope=rememberCoroutineScope()
   val action={scope.launch{pageMeasuredReader(state,measurements,1)};Unit}
   SideEffect{page=action}
   ReaderVolumePaging(true){direction->scope.launch{pageMeasuredReader(state,measurements,direction)}}
   CompositionLocalProvider(LocalReaderLineMeasurements provides measurements){
    LazyColumn(Modifier.width(280.dp).height(137.dp).onGloballyPositioned{measurements.viewport=it},state=state){
     item{SourceVerseText(verse,AnnotatedString(text),ReaderPreferences(paragraphs=true),TextStyle(fontSize=21.sp,lineHeight=37.sp))}
    }
   }
  }}
  compose.waitUntil(5_000){measurements.page(1)!=null}
  val before=measurements.bounds()!!
  val clippedIndex=measurements.lines().indexOfLast{it.top<before.bottom&&it.bottom>before.bottom}
  assertTrue("Fixture must include a genuinely clipped line",clippedIndex>=0)
  compose.runOnIdle{page!!()}
  compose.waitForIdle()
  compose.runOnIdle{
   val bounds=measurements.bounds()!!
   assertEquals("The same previously clipped line must start at the viewport top",bounds.top,measurements.lines()[clippedIndex].top,1.1f)
  }
  val volumeIndex=measurements.lines().indexOfLast{it.top<measurements.bounds()!!.bottom&&it.bottom>measurements.bounds()!!.bottom}
  assertTrue(volumeIndex>=0)
  compose.runOnIdle{assertTrue(ReaderVolumeKeys.dispatch(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN,android.view.KeyEvent.KEYCODE_VOLUME_DOWN)))}
  compose.waitForIdle()
  compose.runOnIdle{assertEquals(measurements.bounds()!!.top,measurements.lines()[volumeIndex].top,1.1f)}
 }
 @Test fun actualVerseRowTextTapPagesButSourceFootnoteTapOnlyOpensItsDialog(){
  val measurements=ReaderLineMeasurements()
  val firstLine="W".repeat(80)
  val text=firstLine+"\n"+(1..60).joinToString("\n"){"Measured reader line $it"}
  val provenance=AnnotationSource("mybible","a".repeat(64))
  val annotations=SourceAnnotations("available",source=provenance,footnotes=(4 until firstLine.length step 4).map{offset->FootnoteAnnotation("note-$offset","*","Actual source note body",offset,"inline_note",true,provenance)},features=mapOf("headings" to "absent","footnotes" to "present","added_words" to "absent","paragraphs" to "absent"))
  val verse=BibleVerse(1,1,"Gen.1.1",text,text,annotations=annotations)
  val chapter=BibleChapter(TranslationSummary("FIXTURE","Source",language=LanguageSummary("en","English")),BibleBook("gen","Genesis",chaptersCount=1),ChapterSummary(1,1),listOf(verse))
  val display=ReaderPreferences(tapPaging=true,paragraphs=true,lineHeight=37f/21f)
  val selectionBlocked=mutableStateOf(false)
  var pages=0
  compose.setContent{BibleDesktopTheme{
   val state=rememberLazyListState();val scope=rememberCoroutineScope()
   val selectedAtComposition=selectionBlocked.value
   CompositionLocalProvider(LocalReaderPreferences provides display,LocalReaderLineMeasurements provides measurements){
    LazyColumn(Modifier.width(400.dp).height(177.dp).testTag("tap-pager").onGloballyPositioned{measurements.viewport=it}
     .readerGestures(display,{selectedAtComposition},{direction->pages++;scope.launch{pageMeasuredReader(state,measurements,direction)}},{},{},measurements::ordinaryTextAt),state=state){
     item{VerseRow("en",chapter,verse,21f,false,{},{},{},onPersonal={})}
    }
   }
  }}
  compose.waitUntil(5_000){measurements.page(1)!=null}
  val body=compose.onNode(hasText("Measured reader line",substring=true),useUnmergedTree=true)
  val layouts=mutableListOf<TextLayoutResult>()
  body.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult){assertTrue(it(layouts))}
  val layout=layouts.single()
  val viewportBounds=compose.onNodeWithTag("tap-pager").fetchSemanticsNode().boundsInRoot
  val bodyBounds=body.fetchSemanticsNode().boundsInRoot
  val footnote=layout.layoutInput.text.getStringAnnotations("source-footnote",0,layout.layoutInput.text.length).firstOrNull{annotation->
   val glyph=layout.getBoundingBox(annotation.start)
   bodyBounds.left+glyph.center.x>viewportBounds.left+viewportBounds.width*.75f&&bodyBounds.top+glyph.bottom<viewportBounds.bottom
  }
  assertNotNull("A genuine source footnote must lie in the paging edge",footnote)
  val origin=measurements.lines().first().top
  val noteGlyph=layout.getBoundingBox(footnote!!.start)
  val notePoint=Offset(noteGlyph.left+noteGlyph.width*.25f,noteGlyph.center.y)
  val hit=layout.getOffsetForPosition(notePoint)
  assertTrue("Measured glyph must resolve to the actual annotation",layout.layoutInput.text.getStringAnnotations("source-footnote",hit,hit).isNotEmpty())
  body.performTouchInput{click(notePoint)}
  compose.waitUntil(5_000){compose.onAllNodes(hasText("Actual source note body",substring=true)).fetchSemanticsNodes().isNotEmpty()}
  compose.onNode(hasText("Actual source note body",substring=true)).assertIsDisplayed()
  assertEquals("Footnote activation must not page the reader",origin,measurements.lines().first().top,.1f)
  compose.onNodeWithText("Return to reading").performClick()
  val before=measurements.bounds()!!
  val clippedIndex=measurements.lines().indexOfLast{it.top<before.bottom&&it.bottom>before.bottom}
  assertTrue(clippedIndex>=0)
  // This point hits ordinary SourceVerseText inside the real selectable VerseRow, not a button.
  val ordinary=(0 until layout.layoutInput.text.length).firstOrNull{offset->
   val glyph=layout.getBoundingBox(offset)
   bodyBounds.left+glyph.left+glyph.width*.25f>viewportBounds.left+viewportBounds.width*.78f&&bodyBounds.top+glyph.bottom<viewportBounds.bottom&&
    layout.layoutInput.text.getStringAnnotations("source-footnote",offset,offset).isEmpty()&&layout.layoutInput.text[offset].isLetter()
  }
  assertNotNull("Plain reading text must lie in the paging edge",ordinary)
  val ordinaryGlyph=layout.getBoundingBox(ordinary!!)
  val ordinaryPoint=Offset(ordinaryGlyph.left+ordinaryGlyph.width*.25f,ordinaryGlyph.center.y)
  assertTrue(measurements.ordinaryTextAt(bodyBounds.topLeft+ordinaryPoint-viewportBounds.topLeft))
  compose.runOnIdle{selectionBlocked.value=true}
  body.performTouchInput{click(ordinaryPoint)}
  compose.waitForIdle()
  assertEquals("An active selection must prevent paging",0,pages)
  compose.runOnIdle{selectionBlocked.value=false}
  body.performTouchInput{click(ordinaryPoint)}
  compose.waitForIdle()
  compose.runOnIdle{assertEquals("The actual right-edge text tap must align the clipped line",measurements.bounds()!!.top,measurements.lines()[clippedIndex].top,1.1f)}
  assertEquals(1,pages)
  compose.onNodeWithTag("tap-pager").performTouchInput{swipeUp()}
  compose.waitForIdle()
  assertEquals("Ordinary vertical scrolling must not become a page tap",1,pages)
  compose.onNodeWithTag("tap-pager").performTouchInput{down(Offset(width*.82f,height*.5f));advanceEventTime(700);up()}
  compose.waitForIdle()
  assertEquals("Long press and native selection must not become a page tap",1,pages)
 }
}
