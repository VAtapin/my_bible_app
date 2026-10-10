package com.bibledesktop.myapp

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import androidx.compose.ui.unit.height
import androidx.test.platform.app.InstrumentationRegistry
import com.bibledesktop.myapp.ui.bible.ChapterReadingContent
import com.bibledesktop.myapp.ui.bible.VerseRow
import com.bibledesktop.myapp.ui.reading.*
import com.bibledesktop.myapp.ui.theme.*
import com.bibledesktop.shared.api.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Small layout fixtures. Real API and app navigation remain covered by NativeSmokeTest. */
class ReaderLayoutTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val sentences = listOf(
        "И явился ему Господь у дубравы Мамре, когда он сидел при входе в шатер, во время зноя дневного.",
        "Он возвел очи свои и взглянул, и вот, три мужа стоят против него. Увидев, он побежал навстречу им от входа в шатер и поклонился до земли",
        "и сказал: Владыка! если я обрел благоволение пред очами Твоими, не пройди мимо раба Твоего;",
        "и принесут немного воды, и омоют ноги ваши; и отдохните под сим деревом;",
    )
    private fun chapter(code: String = "ru") = BibleChapter(
        TranslationSummary("test-$code", "Test edition", language = LanguageSummary(code, code)),
        BibleBook("genesis", "Бытие", chaptersCount = 50), ChapterSummary(18, sentences.size),
        sentences.mapIndexed { i, body -> BibleVerse(i.toLong(), i + 1, "Gen.18.${i + 1}", body, body) },
    )

    @Test fun phoneReaderUsesAvailableWidth() = layout("phone", 390, 844)
    @Test fun portraitTabletUsesFullReadingWidth() = layout("portrait", 960, 1280)
    @Test fun landscapeReaderHasCompactParagraphs() = layout("landscape", 1280, 800)

    private fun layout(name: String, width: Int, height: Int) {
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(width.dp, height.dp))) {
                BibleDesktopTheme {
                    Column(Modifier.fillMaxSize().background(Cream)) {
                        ReadingHeader("Бытие · Глава 18", "ru", {}, {})
                        ChapterReadingContent("ru", chapter(), 19f, emptySet(), { _, _ -> }, { _, _ -> }, { _, _ -> }, Modifier.weight(1f))
                    }
                }
            }
        }
        val measure = compose.onNodeWithTag("reading-measure").getUnclippedBoundsInRoot()
        assertEquals(width.toFloat(), measure.width.value, 2f)
        val row = compose.onNodeWithTag("verse-1").getUnclippedBoundsInRoot()
        assertTrue("Rows should follow their text, not three action buttons", row.height.value < if (width == 390) 145f else 110f)
        compose.onNodeWithTag("verse-4").assertIsDisplayed()
        screenshot("reader-$name")
    }

    @Test fun exactNavigatedVerseIsHighlightedAndNormalChapterClearsHighlight() {
        var focused by mutableIntStateOf(3)
        compose.setContent { BibleDesktopTheme {
            ChapterReadingContent("ru", chapter(), 19f, emptySet(), { _, _ -> }, { _, _ -> }, { _, _ -> }, initialVerse = focused)
        } }
        compose.onNodeWithTag("verse-3").assertIsDisplayed().assertIsSelected()
        compose.onNodeWithTag("verse-4").assertIsNotSelected()
        screenshot("reader-highlighted-verse")
        compose.runOnIdle { focused = 0 }
        compose.onNodeWithTag("verse-3").assertIsNotSelected()
    }

    @Test fun singleActionButtonKeepsShortVerseCompactAndAllActionsWork() {
        val short = chapter().copy(verses = listOf(BibleVerse(1, 1, "Gen.18.1", "Текст", "Текст")))
        var bookmarks = 0; var shares = 0; var notes = 0
        compose.setContent { BibleDesktopTheme {
            VerseRow("ru", short, short.verses.first(), 19f, false, { bookmarks++ }, { shares++ }, { notes++ })
        } }
        assertTrue(compose.onNodeWithTag("verse-1").getUnclippedBoundsInRoot().height < 80.dp)
        listOf("Добавить закладку", "Поделиться стихом", "Заметка к стиху").forEach { title ->
            compose.onNodeWithContentDescription("Действия со стихом 1").performClick()
            compose.onNodeWithText(title).performClick()
        }
        compose.runOnIdle { assertEquals(1, bookmarks); assertEquals(1, shares); assertEquals(1, notes) }
    }

    @Test fun bookmarkedVerseCanBeRemoved() {
        var removed = false
        val chapter = chapter()
        compose.setContent { BibleDesktopTheme {
            VerseRow("ru", chapter, chapter.verses.first(), 19f, true, { removed = true }, {}, {})
        } }
        compose.onNodeWithContentDescription("Действия со стихом 1").performClick()
        compose.onNodeWithText("Удалить закладку").performClick()
        compose.runOnIdle { assertTrue(removed) }
    }

    @Test fun prayerParsesIntroAndPreservesSlavonicEdition() {
        val prayer = PrayerDetail(1, "cu-civil", "basic", title = "Отче наш", intro = "<p></p>",
            body = "<p>О́тче наш, И́же еси́ на небесе́х!</p><p>Да святится имя Твое.</p>")
        var current by mutableStateOf(prayer)
        compose.setContent { BibleDesktopTheme {
            ReadingViewport(Modifier.fillMaxSize()) {
                Column(Modifier.verticalScroll(rememberScrollState())) { PrayerReadingContent(current, 22f) }
            }
        } }
        compose.onNodeWithText("<p></p>").assertDoesNotExist()
        compose.onNodeWithTag("prayer-body").assertTextContains("О́тче наш, И́же еси́ на небесе́х!", substring = true)
        compose.runOnIdle { current = prayer.copy(intro = "<p><b>Вступление</b>&nbsp;</p>") }
        compose.onNodeWithText("Вступление").assertIsDisplayed()
        assertEquals("", readingText("<p>&nbsp;</p>"))
        screenshot("prayer")
    }

    @Test fun fontsAreBundledAndEditionIsIndependentOfInterface() {
        assertEquals(ReadingSerif, readingFont("ru")); assertEquals(ReadingSerif, readingFont("de"))
        assertEquals(ReadingSerif, readingFont("uk")); assertEquals(ReadingSerif, readingFont("en"))
        assertNotEquals(readingFont("cu"), readingFont("cu-civil"))
        assertNotEquals(ReadingSerif, readingFont("cu"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        listOf(R.font.inter, R.font.noto_serif, R.font.ponomar, R.font.monomakh).forEach { resource ->
            val typeface = context.resources.getFont(resource)
            assertNotNull(typeface)
        }
    }

    @Test fun homeAndBackAreSeparateAndLocalized() {
        var language by mutableStateOf("ru")
        var homes = 0; var backs = 0
        compose.setContent { BibleDesktopTheme { ReadingHeader("Бытие", language, { backs++ }, { homes++ }) } }
        listOf("ru" to "На главную", "de" to "Zur Startseite", "uk" to "На головну", "en" to "Go to home").forEach { (code, label) ->
            compose.runOnIdle { language = code }
            compose.onNodeWithContentDescription(label).performClick()
        }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.runOnIdle { assertEquals(4, homes); assertEquals(1, backs) }
    }

    @Test fun largeSystemFontDoesNotClipVerseOrHomeButton() {
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(2f)) {
                DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(390.dp, 844.dp))) {
                    BibleDesktopTheme {
                        Column(Modifier.fillMaxSize().background(Cream)) {
                            ReadingHeader("Бытие · Глава 18", "ru", {}, {})
                            ChapterReadingContent("ru", chapter(), 28f, emptySet(), { _, _ -> }, { _, _ -> }, { _, _ -> }, Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        compose.onNodeWithContentDescription("На главную").assertIsDisplayed()
        compose.onNodeWithContentDescription("Действия со стихом 1").performClick()
        compose.onNodeWithText("Заметка к стиху").assertIsDisplayed()
        screenshot("reader-large-font")
    }

    private fun screenshot(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "com.bibledesktop.myapp.debug")
        context.getExternalFilesDir(null)!!.resolve("$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
