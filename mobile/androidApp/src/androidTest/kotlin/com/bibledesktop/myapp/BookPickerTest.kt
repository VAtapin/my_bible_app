package com.bibledesktop.myapp

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.bibledesktop.myapp.ui.bible.BookPicker
import com.bibledesktop.myapp.ui.bible.matchingBooks
import com.bibledesktop.myapp.ui.theme.BibleDesktopTheme
import com.bibledesktop.shared.api.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Local selection/layout fixtures. NativeSmokeTest verifies the real API → chapter path. */
class BookPickerTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val translations = listOf(
        TranslationSummary("rst", "Библия. Синодальный текст с номерами Стронга", "RST-Strong", LanguageSummary("ru", "Russian", "Русский")),
        TranslationSummary("kjv", "King James Version", "KJV", LanguageSummary("en", "English", "English")),
    )
    private val books = listOf(
        book("genesis", "Бытие", "Быт", "old", 50), book("exodus", "Исход", "Исх", "old", 40),
        book("leviticus", "Левит", "Лев", "old", 27), book("numbers", "Числа", "Чис", "old", 36),
        book("deuteronomy", "Второзаконие", "Втор", "old", 34),
        book("matthew", "От Матфея", "Мф", "new", 28), book("mark", "От Марка", "Мк", "new", 16),
        book("john", "От Иоанна", "Ин", "new", 21),
    )
    private fun book(slug: String, title: String, short: String, group: String, chapters: Int) =
        BibleBook(slug, title, short, chaptersCount = chapters, canonicalBook = CanonicalBookSummary(slug, group))

    @Composable private fun picker(language: String = "ru", onBook: (BibleBook) -> Unit = {}, error: Boolean = false, onRetry: () -> Unit = {}) {
        var selected by remember { mutableStateOf("rst") }
        BibleDesktopTheme { BookPicker(language, translations, selected, books, error, { selected = it }, onBook, onRetry, {}) }
    }

    @Test fun searchMatchesShortNamesSlugsCaseAndAccentsWithoutReordering() {
        assertEquals(listOf("matthew"), matchingBooks(books, "  мФ  ", "all").map { it.slug })
        assertEquals(listOf("john"), matchingBooks(books, "JOHN", "new").map { it.slug })
        assertEquals(emptyList<BibleBook>(), matchingBooks(books, "john", "old"))
        val accented = listOf(book("example", "Е́здра", "Езд", "old", 1))
        assertEquals(accented, matchingBooks(accented, "ездра", "all"))
        assertEquals(books, matchingBooks(books, " ", "all"))
        val unknown = BibleBook("unknown", "Без метаданных", chaptersCount = 1)
        assertEquals(listOf(unknown), matchingBooks(listOf(unknown), "", "other"))
    }

    @Test fun newTestamentIsOneTapAndBookCallbackUsesOriginalRecord() {
        var selected: BibleBook? = null
        compose.setContent { picker(onBook = { selected = it }) }
        compose.onNodeWithTag("book-tab-new").performClick()
        compose.onNodeWithTag("book-genesis").assertDoesNotExist()
        compose.onNodeWithTag("book-matthew").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(books.first { it.slug == "matthew" }, selected) }
    }

    @Test fun searchAndClearHaveUsefulEmptyState() {
        compose.setContent { picker() }
        compose.onNodeWithTag("book-search").performTextInput("НетТакойКниги")
        compose.onNodeWithText("Книги не найдены. Измените поиск или выберите другой Завет.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Очистить поиск").performClick()
        compose.onNodeWithTag("book-genesis").assertExists()
        compose.onNodeWithTag("book-search").performTextInput("Мф")
        compose.onNodeWithTag("book-matthew").assertExists()
        compose.onNodeWithTag("book-genesis").assertDoesNotExist()
    }

    @Test fun translationsShowFullNamesAndChangeOnSingleSelection() {
        compose.setContent { picker() }
        compose.onNodeWithText(translations[0].name).assertIsDisplayed()
        compose.onNodeWithTag("book-search").performTextInput("Бытие")
        compose.onNodeWithTag("book-tab-old").performClick()
        compose.onNodeWithTag("book-translation").performClick()
        compose.onNodeWithText("King James Version").performClick()
        compose.onNodeWithText("King James Version").assertIsDisplayed()
        compose.onNodeWithText("Выбрать перевод").assertDoesNotExist()
        compose.onNodeWithTag("book-translation").assertTextContains("English · KJV", substring = true)
        compose.onNodeWithTag("book-tab-all").assertIsSelected()
        compose.onNodeWithTag("book-exodus").assertExists()
    }

    @Test fun searchAndTabSurviveLeavingAndReturningToPicker() {
        var open by mutableStateOf(true)
        compose.setContent {
            val holder = rememberSaveableStateHolder()
            if (open) holder.SaveableStateProvider("picker") { picker(onBook = { open = false }) }
        }
        compose.onNodeWithTag("book-tab-new").performClick()
        compose.onNodeWithTag("book-search").performTextInput("Мф")
        compose.onNodeWithTag("book-matthew").performClick()
        compose.runOnIdle { open = true }
        compose.onNodeWithTag("book-search").assertTextContains("Мф")
        compose.onNodeWithTag("book-tab-new").assertIsSelected()
        compose.onNodeWithTag("book-matthew").assertExists()
    }

    @Test fun retryDoesNotHideTranslationOrSearch() {
        var retry = false
        compose.setContent { picker(error = true, onRetry = { retry = true }) }
        compose.onNodeWithTag("book-translation").assertIsDisplayed()
        compose.onNodeWithTag("book-search").assertIsDisplayed()
        compose.onNodeWithText("Повторить").performClick()
        compose.runOnIdle { assertTrue(retry) }
    }

    @Test fun controlsAreLocalizedInFourLanguages() {
        var language by mutableStateOf("ru")
        compose.setContent { picker(language) }
        listOf("ru" to "Найти книгу", "de" to "Buch suchen", "uk" to "Знайти книгу", "en" to "Find a book").forEach { (code, label) ->
            compose.runOnIdle { language = code }
            compose.onNodeWithTag("book-search").assertTextContains(label)
        }
    }

    @Test fun phoneUsesOneColumn() = layout("phone", 390, 844, false)
    @Test fun portraitTabletUsesMultipleColumns() = layout("portrait", 960, 1280, true)
    @Test fun landscapeTabletUsesMultipleColumns() = layout("landscape", 1280, 800, true)

    private fun layout(name: String, width: Int, height: Int, multiple: Boolean) {
        compose.setContent { DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(width.dp, height.dp))) { picker() } }
        val first = compose.onNodeWithTag("book-genesis").fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithTag("book-exodus").fetchSemanticsNode().boundsInRoot
        if (multiple) { assertTrue(second.left > first.left); assertEquals(first.top, second.top, 1f) }
        else { assertEquals(first.left, second.left, 1f); assertTrue(second.top > first.top) }
        compose.onNodeWithTag("book-search").assertIsDisplayed()
        screenshot("books-$name")
    }

    @Test fun systemFont200PercentKeepsControlsAndBooksAccessible() {
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(2f)) {
                DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(390.dp, 844.dp))) { picker() }
            }
        }
        compose.onNodeWithContentDescription("На главную").assertIsDisplayed()
        compose.onNodeWithTag("book-tab-new").performClick()
        compose.onNodeWithTag("book-matthew").performScrollTo().assertIsDisplayed()
        screenshot("books-large-font")
    }

    private fun screenshot(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "com.bibledesktop.myapp.debug")
        context.getExternalFilesDir(null)!!.resolve("$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
