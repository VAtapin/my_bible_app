package com.bibledesktop.myapp

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.espresso.Espresso
import com.bibledesktop.myapp.ui.study.GeoAtlasScreen
import com.bibledesktop.myapp.ui.study.geoZoomDescription
import com.bibledesktop.myapp.ui.theme.BibleDesktopTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class GeoMapViewportTest {
 @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
 private fun show(){
  compose.setContent{BibleDesktopTheme{GeoAtlasScreen("ru",{})}}
  compose.waitUntil(10_000){compose.onAllNodesWithTag("geo-map").fetchSemanticsNodes().isNotEmpty()}
  compose.waitForIdle()
 }
 private fun assertLandIsClipped(tag:String){
  val node=compose.onNodeWithTag(tag)
  val bounds=node.getUnclippedBoundsInRoot()
  val bitmap=compose.onRoot().captureToImage().asAndroidBitmap()
  val scale=compose.density.density
  val left=bounds.left.value*scale;val right=bounds.right.value*scale
  val top=bounds.top.value*scale;val bottom=bounds.bottom.value*scale
  var outside=0;var inside=0
  for(y in 0 until bitmap.height)for(x in 0 until bitmap.width){
   if(bitmap.getPixel(x,y)==0xffdde1c6.toInt()){
    if(x+1<left||x>right||y+1<top||y>bottom)outside++ else inside++
   }
  }
  assertEquals("Land must never paint over atlas controls or place list",0,outside)
  assertTrue("Actual bundled land should remain visible in its map",inside>0)
 }
 @Test fun zoomAndPanKeepActualLandInsideTheMap(){
  show();assertLandIsClipped("geo-map")
  compose.onNodeWithText("Весь мир").performClick()
  assertLandIsClipped("geo-map")
  repeat(3){compose.onNodeWithContentDescription(geoZoomDescription("ru",true)).performClick()}
  compose.onNodeWithTag("geo-map").performTouchInput{swipe(center,Offset(center.x+width/5f,center.y+height/5f),500)}
  assertLandIsClipped("geo-map")
 }
 @Test fun mouseHoverDoesNotMoveMapAndFullscreenReturnKeepsItBounded(){
  show();compose.onNodeWithText("Весь мир").performClick()
  val map=compose.onNodeWithTag("geo-map")
  val before=map.captureToImage().asAndroidBitmap()
  map.performMouseInput{moveTo(center);moveTo(Offset(width/4f,height/4f))}
  val after=map.captureToImage().asAndroidBitmap()
  assertTrue("Hover without pressed buttons must not pan the map",before.sameAs(after))
  compose.onNodeWithText("На весь экран").performClick()
  compose.onNodeWithTag("geo-map-fullscreen").assertIsDisplayed()
  compose.onNode(hasContentDescription(geoZoomDescription("ru",true)) and hasAnyAncestor(hasTestTag("geo-atlas-fullscreen"))).performClick()
  Espresso.pressBack()
  compose.onNodeWithTag("geo-map-fullscreen").assertDoesNotExist()
  assertLandIsClipped("geo-map")
 }
}
