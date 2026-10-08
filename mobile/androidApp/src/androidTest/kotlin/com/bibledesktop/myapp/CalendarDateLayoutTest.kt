package com.bibledesktop.myapp

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.bibledesktop.myapp.ui.daily.CalendarOverview
import com.bibledesktop.myapp.ui.daily.CalendarService
import com.bibledesktop.myapp.ui.theme.BibleDesktopTheme
import com.bibledesktop.shared.api.*
import org.junit.*
import org.junit.Assert.*
import java.time.LocalDate

class CalendarDateLayoutTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val api = BibleApiClient()
    private val source = object : BibleContentSource by api {
        override suspend fun getCalendarMonth(year: Int, month: Int, language: String) = emptyList<CalendarGridDay>()
        override suspend fun getCalendarDay(date: String, language: String, profile: String) =
            CalendarDay(date, LocalDate.parse(date).minusDays(13).toString(), "2026-04-12", "", "fixture")
        override suspend fun getCalendarService(date: String, language: String) = CalendarServicePlan(date, language,
            assignments = listOf(CalendarServiceText("Тропарь", "troparion", "Текст тропаря")),
            properCoverage = CalendarServiceCoverage("Служебное пояснение API"))
    }
    @After fun close() { api.close() }
    @Test fun phoneDatesAreAboveGridAndUpdateWithoutDuplicate() = checkLayout(390, 844)
    @Test fun tabletDatesAreAboveGridAndUpdateWithoutDuplicate() = checkLayout(1280, 800)
    @Test fun serviceKeepsTextsWithoutOfficeNameOrExplanations() {
        compose.setContent { BibleDesktopTheme {
            androidx.compose.foundation.layout.Column { CalendarService("2026-10-09", "ru", source) }
        } }
        compose.onNodeWithText("Богослужебные тексты").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Тропарь").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Шестой час").assertDoesNotExist()
        compose.onNodeWithText("Служебное пояснение API").assertDoesNotExist()
        compose.onAllNodes(hasText("справочный текст", substring = true)).assertCountEquals(0)
        compose.onNodeWithText("Тропарь").performClick()
        compose.onNodeWithText("Текст тропаря").assertIsDisplayed()
    }
    private fun checkLayout(width: Int, height: Int) {
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(width.dp, height.dp))) {
                BibleDesktopTheme { CalendarOverview("ru", source) }
            }
        }
        val initial = LocalDate.now()
        val oldStyle = "Старый стиль: ${initial.minusDays(13)}"
        compose.waitUntil(10_000) { compose.onAllNodesWithText(oldStyle).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("calendar-select-month").performClick()
        compose.onNodeWithTag("calendar-month-year").performTextReplacement("1899")
        compose.onNodeWithTag("calendar-month-2").assertIsNotEnabled()
        compose.onNodeWithTag("calendar-month-year").performTextReplacement("2028")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.waitForIdle()
        android.os.SystemClock.sleep(300)
        val monthInstrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        monthInstrumentation.targetContext.getExternalFilesDir(null)!!.resolve("native-month-picker-$width.png").outputStream().use {
            monthInstrumentation.uiAutomation.takeScreenshot()!!.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.onNodeWithTag("calendar-month-2").performClick()
        val chosenOldStyle = "Старый стиль: 2028-01-19"
        compose.waitUntil(10_000) { compose.onAllNodesWithText(chosenOldStyle).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("calendar-select-month").assertTextContains("февраль 2028", substring = true)
        compose.onNodeWithContentDescription("Сегодня").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText(oldStyle).fetchSemanticsNodes().isNotEmpty() }
        val heading = compose.onNodeWithTag("calendar-selected-date").getUnclippedBoundsInRoot()
        val grid = compose.onNodeWithTag("calendar-grid").getUnclippedBoundsInRoot()
        assertTrue(heading.bottom <= grid.top)
        compose.onAllNodesWithText(oldStyle).assertCountEquals(1)
        compose.onNodeWithText("Церковный календарь").assertDoesNotExist()
        compose.onNodeWithText("Сегодня").assertDoesNotExist()
        compose.waitForIdle()
        android.os.SystemClock.sleep(300)
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        instrumentation.targetContext.getExternalFilesDir(null)!!.resolve("native-calendar-date-$width.png").outputStream().use {
            instrumentation.uiAutomation.takeScreenshot()!!.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        val next = initial.withDayOfMonth(1).plusMonths(1)
        compose.onNodeWithContentDescription("Следующий месяц").performClick()
        val nextOldStyle = "Старый стиль: ${next.minusDays(13)}"
        compose.waitUntil(10_000) { compose.onAllNodesWithText(nextOldStyle).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText(nextOldStyle).assertCountEquals(1)
        compose.onNodeWithText(oldStyle).assertDoesNotExist()
        compose.onNodeWithContentDescription("Сегодня").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText(oldStyle).fetchSemanticsNodes().isNotEmpty() }
    }
}
