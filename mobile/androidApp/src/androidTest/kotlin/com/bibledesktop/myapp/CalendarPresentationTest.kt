package com.bibledesktop.myapp

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.bibledesktop.myapp.ui.daily.*
import com.bibledesktop.myapp.ui.theme.BibleDesktopTheme
import com.bibledesktop.shared.api.CalendarIcon
import com.bibledesktop.shared.api.CalendarIconImage
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class CalendarPresentationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun weekCrossesMonthAndYearWithoutLosingDays() {
        val dates = calendarPeriodDates(LocalDate.parse("2026-01-01"), YearMonth.of(2026, 1), true)
        assertEquals("2025-12-29", dates.first().toString())
        assertEquals("2026-01-04", dates.last().toString())
        assertEquals(7, dates.size)
        val leap = calendarPeriodDates(LocalDate.parse("2024-02-29"), YearMonth.of(2024, 2), false)
        assertEquals(29, leap.filterNotNull().size)
        assertEquals(0, leap.size % 7)
    }

    @Test fun bundledSignsExistAndRejectForeignPaths() {
        val assets = InstrumentationRegistry.getInstrumentation().targetContext.assets
        val signs = listOf("great", "vigil", "polyeleos", "doxology", "six-stichera")
        signs.forEach { name ->
            val url = bundledCalendarAsset("https://bible-desktop.com/assets/typikon/$name.svg")!!
            assets.open(url.removePrefix("file:///android_asset/")).use { assertTrue(it.read() >= 0) }
        }
        assertNotNull(bundledCalendarAsset("/assets/markers/minimal-dark/memorial.png"))
        assertNull(bundledCalendarAsset("https://foreign.test/assets/typikon/vigil.svg"))
        assertNull(bundledCalendarAsset("/assets/typikon/vigil.svg?x=1"))
        assertNull(bundledCalendarAsset("/assets/typikon/unknown.svg"))
        assertNull(calendarImageUrl("https://foreign.test/api/calendar/icons/1/images/2"))
        assertNull(calendarImageUrl("https://bible-desktop.com/other.png"))
    }

    @Test fun galleryRetainsServerOrderAndSupportsFingerSwipeAndArrows() {
        val first = "https://bible-desktop.com/api/calendar/icons/3054/images/7023?preview=1"
        val second = "https://bible-desktop.com/api/calendar/icons/3054/images/7024?preview=1"
        val icon = CalendarIcon(3054, "Галерея", first, images = listOf(CalendarIconImage(first), CalendarIconImage(second)))
        assertEquals(listOf(first, second), calendarGalleryUrls(icon))
        compose.setContent { BibleDesktopTheme { CalendarIconGallery(icon, "ru") {} } }
        compose.waitUntil(30_000) { compose.onAllNodesWithTag("calendar-image-ready").fetchSemanticsNodes().isNotEmpty() }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getExternalFilesDir(null)!!.resolve("calendar-gallery.png").outputStream().use {
            compose.onNodeWithTag("calendar-gallery").captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.onNodeWithTag("calendar-gallery-counter").assertTextEquals("1 / 2")
        compose.onNodeWithTag("calendar-gallery").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithTag("calendar-gallery-counter").assertTextEquals("2 / 2")
        compose.onNodeWithContentDescription("Следующее изображение").performClick()
        compose.onNodeWithTag("calendar-gallery-counter").assertTextEquals("1 / 2")
        val gallery = compose.onNodeWithTag("calendar-gallery").fetchSemanticsNode().boundsInRoot
        assertTrue("Gallery image should be large", gallery.height > 250f)
    }
}
