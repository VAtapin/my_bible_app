package com.bibledesktop.myapp

import androidx.activity.ComponentActivity
import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.bibledesktop.myapp.data.*
import com.bibledesktop.myapp.ui.bible.*
import com.bibledesktop.myapp.ui.theme.BibleDesktopTheme
import com.bibledesktop.shared.api.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.UUID

class BibleComparisonUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val first = TranslationSummary("A", "Синодальный перевод", language = LanguageSummary("ru", "Русский"))
    private val second = TranslationSummary("B", "King James Version", language = LanguageSummary("en", "English"))
    private fun chapter(translation: TranslationSummary) = BibleChapter(translation, BibleBook("john", "Иоанна", chaptersCount = 21),
        ChapterSummary(3, 2), listOf(BibleVerse(1, 1, "John.3.1", "Text", if (translation.code == "A") "Между фарисеями был некто, именем Никодим, один из начальников Иудейских." else "There was a man of the Pharisees, named Nicodemus, a ruler of the Jews."),
            BibleVerse(2, 2, "John.3.2", "Text", "Another verse")))

    @Test fun phoneComparisonIsStackedAndMissingVersesAreExplicit() = layout("phone", 390, 844)
    @Test fun portraitTabletComparisonIsInterleaved() = layout("portrait", 960, 1280)
    @Test fun landscapeTabletComparisonIsInterleaved() = layout("landscape", 1280, 800)
    private fun layout(name: String, width: Int, height: Int) {
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(width.dp, height.dp))) {
                BibleDesktopTheme { ComparisonRows("ru", chapter(first), chapter(second).copy(verses = chapter(second).verses.take(1)), 19f, modifier = Modifier.fillMaxSize()) }
            }
        }
        compose.onNodeWithTag("compared-John.3.1").assertIsDisplayed()
        compose.onNodeWithTag("comparison-rows").performScrollToNode(hasText("В этом переводе стих отсутствует."))
        compose.onNodeWithText("В этом переводе стих отсутствует.").assertIsDisplayed()
        compose.onNodeWithTag("comparison-rows").performScrollToIndex(0)
        val ru = compose.onNodeWithText(chapter(first).verses.first().plainText).getUnclippedBoundsInRoot()
        val en = compose.onNodeWithText(chapter(second).verses.first().plainText).getUnclippedBoundsInRoot()
        assertTrue(en.top > ru.top)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getExternalFilesDir(null)!!.resolve("comparison-$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }
    @Test fun userCanSwitchModesAndSelectionSurvivesRecreation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences("bible-desktop-native-profile", Context.MODE_PRIVATE)
        val previous = preferences.all["comparePanes"]
        check(preferences.edit().putBoolean("comparePanes", false).commit())
        val api = BibleApiClient()
        val source = object : BibleContentSource by api {
            override suspend fun getBooks(translationCode: String) = listOf(chapter(first).book.copy(canonicalBook = CanonicalBookSummary("John", "new")))
            override suspend fun getChapter(translationCode: String, bookSlug: String, chapterNumber: Int) = chapter(second)
        }
        try {
            val restoration = StateRestorationTester(compose)
            restoration.setContent { BibleDesktopTheme { ComparisonPane("ru", chapter(first), listOf(first, second), "B", {}, source, 19f, 1, Modifier.fillMaxSize()) } }
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("comparison-rows").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("compare-panes").performClick()
            compose.onNodeWithTag("comparison-pane-0").assertIsDisplayed()
            compose.onNodeWithTag("comparison-pane-1").assertIsDisplayed()
            assertTrue(preferences.getBoolean("comparePanes", false))
            restoration.emulateSavedInstanceStateRestore()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("comparison-pane-0").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("compare-panes").assertIsSelected()
            compose.onNodeWithTag("compare-interleaved").performClick()
            compose.onNodeWithTag("comparison-rows").assertIsDisplayed()
            assertFalse(preferences.getBoolean("comparePanes", true))
        } finally {
            val editor = preferences.edit()
            if (previous is Boolean) editor.putBoolean("comparePanes", previous) else editor.remove("comparePanes")
            check(editor.commit()); api.close()
        }
    }

    @Test fun windowsAreVerticalAndScrollIndependently() {
        compose.setContent { BibleDesktopTheme {
            ComparisonWindows("ru", chapter(first), chapter(second).copy(verses = chapter(second).verses.take(1)), 19f,
                modifier = Modifier.fillMaxSize(), initialVerse = 1)
        } }
        val top = compose.onNodeWithTag("comparison-pane-0").getUnclippedBoundsInRoot()
        val bottom = compose.onNodeWithTag("comparison-pane-1").getUnclippedBoundsInRoot()
        assertTrue(bottom.top >= top.bottom)
        compose.onNodeWithTag("comparison-list-1").performScrollToNode(hasText("В этом переводе стих отсутствует."))
        compose.onNodeWithTag("pane-0-John.3.1").assertIsDisplayed().assertIsSelected()
        compose.onNodeWithText("В этом переводе стих отсутствует.").assertIsDisplayed()
    }

    @Test fun comparisonHighlightsOnlyTheExactNavigatedVerse() {
        compose.setContent { BibleDesktopTheme {
            ComparisonRows("ru", chapter(first), chapter(second), 19f, initialVerse = 2)
        } }
        compose.onNodeWithTag("compared-John.3.2").assertIsDisplayed().assertIsSelected()
        compose.onNodeWithTag("comparison-rows").performScrollToIndex(0)
        compose.onNodeWithTag("compared-John.3.1").assertIsNotSelected()
    }

    @Test fun translationPickerSearchesLanguagesAndSelectsActualCode() {
        var chosen = ""
        compose.setContent { BibleDesktopTheme { TranslationPicker("ru", listOf(first, second), "A", { chosen = it }, {}) } }
        compose.onNodeWithTag("translation-search").performTextInput("Unlisted translation")
        compose.onNodeWithText("Переводы не найдены. Измените поиск.").assertIsDisplayed()
        compose.onNodeWithTag("translation-search").performTextReplacement("English")
        compose.onNodeWithText("Синодальный перевод").assertDoesNotExist()
        compose.onNodeWithTag("translation-B").performClick()
        compose.runOnIdle { assertEquals("B", chosen) }
    }
    @Test fun canonicalBookMissingDoesNotFallBackToTranslatedTitleOrSlug() = runBlocking {
        val api = BibleApiClient()
        val source = object : BibleContentSource by api {
            override suspend fun getBooks(translationCode: String): List<BibleBook> = listOf(BibleBook("john", "Иоанна", chaptersCount = 21,
                canonicalBook = CanonicalBookSummary(if (translationCode == "A") "John" else "Gen", "new")))
            override suspend fun getChapter(translationCode: String, bookSlug: String, chapterNumber: Int): BibleChapter = error("No guessed chapter fetch")
        }
        try { assertTrue(runCatching { loadComparison(chapter(first), "B", source) }.exceptionOrNull() is ComparisonUnavailable) }
        finally { api.close() }
    }
    @Test fun emptyChapterShowsMissingTextInsteadOfABlankReader() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences("bible-desktop-native-profile", Context.MODE_PRIVATE)
        val keys = listOf("lastTranslation", "lastBookSlug", "lastChapter", "lastVerse")
        val previous = keys.associateWith { preferences.all[it] }
        check(preferences.edit().putString("lastTranslation", "A").putString("lastBookSlug", "john").putInt("lastChapter", 3).putInt("lastVerse", 0).commit())
        val api = BibleApiClient()
        val fixture = object : BibleContentSource by api {
            override suspend fun getBooks(translationCode: String) = listOf(chapter(first).book)
            override suspend fun getChapter(translationCode: String, bookSlug: String, chapterNumber: Int) = chapter(first).copy(chapter = ChapterSummary(3, 0), verses = emptyList())
        }
        try {
            compose.setContent { BibleDesktopTheme { BibleReader("ru", listOf(first), fixture, {}) } }
            val message = "В этом переводе API пока не содержит текста этой главы."
            compose.waitUntil(10_000) { compose.onAllNodesWithText(message).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(message).assertIsDisplayed()
        } finally {
            val editor = preferences.edit()
            previous.forEach { (key, value) -> when (value) { is String -> editor.putString(key, value); is Int -> editor.putInt(key, value); null -> editor.remove(key) } }
            check(editor.commit()); api.close()
        }
    }
    @Test fun realDifferentSlugsCompareAndReopenWithoutNetwork() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "compare-test-${UUID.randomUUID()}")
        check(root.mkdirs())
        val api = BibleApiClient()
        var offline = false
        val source = object : BibleContentSource by api {
            override suspend fun getBooks(translationCode: String): List<BibleBook> { check(!offline); return api.getBooks(translationCode) }
            override suspend fun getChapter(translationCode: String, bookSlug: String, chapterNumber: Int): BibleChapter {
                check(!offline); return api.getChapter(translationCode, bookSlug, chapterNumber)
            }
        }
        val repository = OfflineContentRepository(source, OfflineStore(root))
        try {
            val translations = api.getTranslations()
            val ru = translations.first { it.language.code == "ru" }
            val en = translations.first { it.language.code == "en" }
            // Installation is explicit; viewing a chapter must no longer fetch or cache it implicitly.
            val store = OfflineStore(root)
            val ruBooks = api.getBooks(ru.code)
            val enBooks = api.getBooks(en.code)
            store.write("books:${ru.code}", kotlinx.serialization.builtins.ListSerializer(BibleBook.serializer()), ruBooks)
            store.write("books:${en.code}", kotlinx.serialization.builtins.ListSerializer(BibleBook.serializer()), enBooks)
            val book = ruBooks.first { it.canonicalBook?.osisCode == "John" }
            val enBook = enBooks.first { it.canonicalBook?.osisCode == "John" }
            store.write(chapterKey(ru.code, book.slug, 3), BibleChapter.serializer(), api.getChapter(ru.code, book.slug, 3))
            store.write(chapterKey(en.code, enBook.slug, 3), BibleChapter.serializer(), api.getChapter(en.code, enBook.slug, 3))
            offline = true
            val primary = repository.getChapter(ru.code, book.slug, 3)
            val secondary = loadComparison(primary, en.code, repository)
            assertEquals("John.3.16", compareVerses(primary, secondary).first { it.reference == "John.3.16" }.secondary?.osisRef)
            offline = true
            val reopened = OfflineContentRepository(source, OfflineStore(root))
            compose.setContent { BibleDesktopTheme { ComparisonPane("ru", primary, translations, en.code, {}, reopened, 19f, 16, Modifier.fillMaxSize()) } }
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("compared-John.3.16").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("compared-John.3.16").assertIsDisplayed()
            assertTrue(loadComparison(primary, en.code, reopened).verses.isNotEmpty())
            reopened.close()
        } finally { repository.close(); root.walkBottomUp().forEach { check(it.delete()) } }
    }
}
