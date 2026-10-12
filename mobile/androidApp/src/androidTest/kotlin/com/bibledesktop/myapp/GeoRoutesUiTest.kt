package com.bibledesktop.myapp

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.bibledesktop.myapp.ui.study.GeoAtlasScreen
import com.bibledesktop.myapp.ui.theme.BibleDesktopTheme
import org.junit.Rule
import org.junit.Test

class GeoRoutesUiTest {
 @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
 @Test fun offlineRouteSelectionShowsActualStopsAndPlaceRoutes(){
  compose.setContent{BibleDesktopTheme{GeoAtlasScreen("ru",{})}}
  compose.waitUntil(10_000){compose.onAllNodesWithTag("geo-route-picker").fetchSemanticsNodes().isNotEmpty()}
  compose.onNodeWithTag("geo-route-picker").performClick()
  compose.onNodeWithText("Павел: Македония и Греция",useUnmergedTree=true).performClick()
  compose.onNodeWithTag("geo-route-stops").performScrollTo().performClick()
  compose.onNodeWithTag("geo-atlas-details").performScrollToNode(hasText("4. Philippi · Acts.16.12"))
  compose.onNodeWithText("4. Philippi · Acts.16.12").assertIsDisplayed().performClick()
  compose.onNodeWithText("Маршруты этого места").performScrollTo().assertIsDisplayed()
  compose.onNodeWithTag("geo-route-picker").performClick()
  compose.onNodeWithText("Авраам: Бытие 11–13",useUnmergedTree=true).performClick()
  compose.onNodeWithTag("geo-route-stops").performScrollTo().performClick()
  compose.onNodeWithTag("geo-atlas-details").performScrollToNode(hasText("8. Hebron · Gen.13.18"))
  compose.onNodeWithText("8. Hebron · Gen.13.18").assertIsDisplayed()
  compose.onNodeWithTag("geo-route-picker").performClick()
  compose.onNodeWithText("Без маршрута",useUnmergedTree=true).performClick()
  compose.onNodeWithTag("geo-route-stops").assertDoesNotExist()
 }
}
