package com.bibledesktop.myapp

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.bibledesktop.myapp.ui.bible.*
import com.bibledesktop.myapp.ui.daily.PrayersScreen
import com.bibledesktop.myapp.ui.study.StudyScreen
import com.bibledesktop.myapp.ui.theme.*
import com.bibledesktop.shared.api.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain

/** Local UI fixtures; StudyTest/NativeSmokeTest exercise read-only production data. */
class PassageNavigationTest {
    private val compose = createAndroidComposeRule<ComponentActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferences get() = context.getSharedPreferences("bible-desktop-native-profile", Context.MODE_PRIVATE)
    private val keys = listOf("lastTranslation", "lastBookSlug", "lastChapter", "lastVerse", "lastVerseOffset", "verseNotesV1")
    private var original = emptyMap<String, Any?>()
    private var originalControls:Map<String,*> = emptyMap<String,Any?>()
    private val api = BibleApiClient()
    private val translation = TranslationSummary("fixture", "Синодальный", language = LanguageSummary("ru", "Русский"))
    private val books = listOf(BibleBook("genesis", "Бытие", chaptersCount = 50), BibleBook("john", "Иоанна", chaptersCount = 21))
    private val source = object : BibleContentSource by api {
        override suspend fun getBooks(translationCode: String) = books
        override suspend fun getChapter(translationCode: String, bookSlug: String, chapterNumber: Int) = BibleChapter(
            translation, books.first { it.slug == bookSlug }, ChapterSummary(chapterNumber, 1),
            listOf(BibleVerse(chapterNumber.toLong(), 1, "${if(bookSlug=="genesis")"Gen"else"John"}.$chapterNumber.1", "", "Текст $bookSlug $chapterNumber")))
        override suspend fun getPrayers(language: String) = if (language == "cu") listOf(
            PrayerSummary(2, "cu", "common", title = "Отче наш", excerpt = "")) else emptyList()
    }
    private val fixture = object : ExternalResource() {
        override fun before() {
            check(context.packageName == "com.bibledesktop.myapp.debug")
            original = keys.associateWith { preferences.all[it] }
            val controls=context.getSharedPreferences("bible-desktop-reader-controls",Context.MODE_PRIVATE)
            originalControls=controls.all.toMap();check(controls.edit().clear().commit())
            check(preferences.edit().putString("lastTranslation", translation.code).putString("lastBookSlug", "genesis")
                .putInt("lastChapter", 18).putInt("lastVerse", 0).remove("lastVerseOffset").remove("verseNotesV1").commit())
        }
        override fun after() {
            val editor = preferences.edit()
            original.forEach { (key, value) -> when (value) {
                is String -> editor.putString(key, value)
                is Int -> editor.putInt(key, value)
                else -> editor.remove(key)
            } }
            check(editor.commit()); api.close()
            val controls=context.getSharedPreferences("bible-desktop-reader-controls",Context.MODE_PRIVATE).edit().clear()
            originalControls.forEach{(key,value)->when(value){is Boolean->controls.putBoolean(key,value);is Int->controls.putInt(key,value);is String->controls.putString(key,value);is Float->controls.putFloat(key,value);is Long->controls.putLong(key,value);is Set<*>->controls.putStringSet(key,value.filterIsInstance<String>().toSet())}}
            check(controls.commit())
        }
    }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(fixture).around(compose)

    private fun reader(width: Int = 390, height: Int = 844) {
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(width.dp, height.dp))) {
                BibleDesktopTheme { BibleReader("ru", listOf(translation), source, {}) }
            }
        }
        awaitTag("reader-choose-chapter")
        compose.onNodeWithTag("reader-choose-book").assertTextEquals("Выбрать книгу")
    }
    private fun awaitTag(tag: String) = compose.waitUntil(10_000) {
        compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }
    private fun chooseChapterAndActualVerse(chapter:Int){
        awaitTag("choose-chapter-$chapter");compose.onNodeWithTag("choose-chapter-$chapter").performClick()
        awaitTag("choose-verse-1");compose.onNodeWithTag("choose-verse-1").performClick()
        compose.waitUntil(10_000){compose.onAllNodes(hasTestTag("reader-choose-chapter") and hasText("Глава $chapter",substring=true)).fetchSemanticsNodes().isNotEmpty()}
        compose.onNode(hasTestTag("verse-1") and isSelected()).assertIsDisplayed()
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        // Wait for the separate dialog window's SurfaceControl frame, not just Compose idleness.
        android.os.SystemClock.sleep(300)
        context.getExternalFilesDir(null)!!.resolve("$name.png").outputStream().use {
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()!!.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun chapterChangesDirectlyAndReaderCanReopenBookPicker() {
        reader()
        compose.onNodeWithTag("reader-choose-chapter").assertIsDisplayed().performClick()
        awaitTag("choose-chapter-5")
        capture("native-chapter-picker")
        chooseChapterAndActualVerse(5)
        compose.onNodeWithTag("reader-choose-chapter").assertTextContains("Глава 5", substring = true)
        compose.onNodeWithTag("reader-choose-book").performClick()
        awaitTag("book-john")
        compose.onNodeWithTag("book-john").performClick()
        chooseChapterAndActualVerse(3)
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Текст john 3").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("reader-choose-chapter").assertTextContains("Глава 3", substring = true)
        capture("native-direct-reader")
        assertEquals("john", preferences.getString("lastBookSlug", null))
        assertEquals(3, preferences.getInt("lastChapter", 0))
    }
    @Test fun dismissKeepsPassageAndBookCanChangeFromChapterGrid() {
        reader()
        compose.onNodeWithTag("reader-choose-book").performClick()
        awaitTag("book-john")
        compose.onNodeWithTag("book-john").performClick()
        compose.onNode(hasContentDescription("Назад") and hasAnyAncestor(isDialog())).performClick()
        compose.onNodeWithTag("reader-choose-chapter").assertTextContains("Глава 18", substring = true)
        assertEquals("genesis", preferences.getString("lastBookSlug", null))
        compose.onNodeWithTag("reader-choose-chapter").performClick()
        awaitTag("picker-choose-book")
        compose.onNodeWithTag("picker-choose-book").performClick()
        compose.onNodeWithTag("book-search").performTextInput("john")
        compose.onNodeWithTag("book-john").performClick()
        chooseChapterAndActualVerse(1)
        compose.onNodeWithTag("reader-choose-chapter").assertTextContains("Глава 1", substring = true)
    }
    @Test fun wideReaderKeepsDirectControlsAndChapterGrid() {
        reader(1280, 800)
        compose.onNodeWithTag("reader-choose-book").assertIsDisplayed()
        compose.onNodeWithTag("reader-choose-chapter").performClick()
        awaitTag("choose-chapter-21")
        chooseChapterAndActualVerse(21)
        compose.onNodeWithTag("reader-choose-chapter").assertTextContains("Глава 21", substring = true)
        capture("native-direct-reader-wide")
    }
    @Test fun pickerOpenedOnEntryDoesNotNeedBack() {
        compose.setContent { BibleDesktopTheme {
            BibleReader("ru", listOf(translation), source, {}, choosePassageOnOpen = true)
        } }
        awaitTag("book-john")
        compose.onNodeWithTag("book-john").performClick()
        chooseChapterAndActualVerse(2)
        compose.onNodeWithTag("reader-choose-chapter").assertTextContains("Глава 2", substring = true)
    }
    @Test fun slavonicBookNamesUseEditionFontInCatalogue() {
        val name = "Кни́га пе́рваа мѡѷсе́ова Бы́тіѐ"
        val book = books.first().copy(name = name)
        var edition by mutableStateOf("cu")
        compose.setContent { BibleDesktopTheme {
            BookPicker("de", listOf(translation.copy(language = LanguageSummary(edition, edition))), "fixture", listOf(book),
                false, {}, {}, {}, {})
        } }
        listOf("cu", "cu-civil").forEach { code ->
            compose.runOnIdle { edition = code }
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(name, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals(readingFont(code), layouts.single().layoutInput.style.fontFamily)
            capture("native-slavonic-books-$code")
        }
    }
    @Test fun slavonicChapterTitleUsesTheTextEditionNotInterfaceLanguage() {
        val name = "Кни́га пе́рваа мѡѷсе́ова Бы́тіѐ"
        compose.setContent { BibleDesktopTheme {
            ChaptersScreen("de", books.first().copy(name = name), {}, {}, {}, textLanguage = "cu")
        } }
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(name).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(readingFont("cu"), layouts.single().layoutInput.style.fontFamily)
    }
    @Test fun translationChangeInPickerIsStagedUntilAChapterIsChosen() {
        val second = translation.copy(code = "second", name = "Other edition")
        var selection: String? = null
        var closed = false
        compose.setContent { BibleDesktopTheme {
            PassagePicker("ru", listOf(translation, second), source, translation.code, books.first(), 18, true,
                onSelect = { code, _, _ -> selection = code }, onClose = { closed = true }, onHome = {})
        } }
        awaitTag("book-translation")
        compose.onNodeWithTag("book-translation").performClick()
        compose.onNodeWithTag("translation-second").performClick()
        awaitTag("book-john")
        compose.runOnIdle { assertNull(selection) }
        compose.onNodeWithContentDescription("Назад").performClick()
        compose.runOnIdle { assertTrue(closed); assertNull(selection) }
    }
    @Test fun prayersExplainActualEditionCountWithoutRussianSubstitution() {
        compose.setContent { BibleDesktopTheme { PrayersScreen("ru", source, {}) } }
        compose.onNodeWithTag("prayer-edition-cu").performClick()
        awaitTag("prayer-source-count")
        compose.onNodeWithTag("prayer-source-count").assertTextContains("BibleDesktop: 1", substring = true)
        compose.onNodeWithText("Отче наш").assertExists()
        compose.onNodeWithTag("prayer-edition-cu-civil").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText("BibleDesktop: 0", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Отче наш").assertDoesNotExist()
    }
    @Test fun studyExplainsRealToolsAndPassageActionIsVisibleWithoutPriorReading() {
        check(preferences.edit().remove("lastChapter").commit())
        var opened = false
        compose.setContent { BibleDesktopTheme { StudyScreen("ru", source, {}, { opened = true }, {}) } }
        compose.onNodeWithTag("study-guide").assertIsDisplayed()
        compose.onNodeWithText("Изучение Библии").assertIsDisplayed()
        compose.onNodeWithTag("study-choose-passage").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(opened) }
        capture("native-study-guide")
    }
}
