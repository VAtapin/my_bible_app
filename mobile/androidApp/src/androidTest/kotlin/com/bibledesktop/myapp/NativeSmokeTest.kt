package com.bibledesktop.myapp

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.isDialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bibledesktop.shared.api.BibleApiClient
import com.bibledesktop.shared.api.isInstalled
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** Uses the real APK and read-only production API; never creates remote profiles. */
@RunWith(AndroidJUnit4::class)
class NativeSmokeTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val fixtureKeys = listOf("setupComplete", "uiLanguage", "sections", "translations", "lastTranslation", "lastBookSlug", "lastChapter", "lastVerse", "lastVerseOffset", "verseNotesV1", "compareTranslation")
    private var original: Map<String, Any?> = emptyMap()
    private val isolatedNamespaces=listOf("bible-desktop-reader-controls","bible-desktop-reader-history","bible-desktop-reader-windows")
    private var originalReaderSettings:Map<String,Map<String,*>> = emptyMap()
    private val fixture = object : ExternalResource() {
        override fun before() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            check(context.packageName == "com.bibledesktop.myapp.debug")
            val preferences = context.getSharedPreferences("bible-desktop-native-profile", Context.MODE_PRIVATE)
            original = fixtureKeys.associateWith { preferences.all[it] }
            originalReaderSettings=isolatedNamespaces.associateWith{name->context.getSharedPreferences(name,Context.MODE_PRIVATE).all.toMap()}
            isolatedNamespaces.forEach{name->check(context.getSharedPreferences(name,Context.MODE_PRIVATE).edit().clear().commit())}
            check(preferences.edit().putBoolean("setupComplete", false).putString("uiLanguage", "ru")
                .remove("lastTranslation").remove("lastBookSlug").remove("lastChapter").remove("lastVerse").remove("lastVerseOffset").remove("verseNotesV1").commit())
        }

        override fun after() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val editor = context.getSharedPreferences("bible-desktop-native-profile", Context.MODE_PRIVATE).edit()
            original.forEach { (key, value) ->
                when (value) {
                    is Boolean -> editor.putBoolean(key, value)
                    is String -> editor.putString(key, value)
                    is Int -> editor.putInt(key, value)
                    null -> editor.remove(key)
                }
            }
            check(editor.commit())
            originalReaderSettings.forEach{(name,values)->val saved=context.getSharedPreferences(name,Context.MODE_PRIVATE).edit().clear();values.forEach{(key,value)->when(value){is Boolean->saved.putBoolean(key,value);is String->saved.putString(key,value);is Int->saved.putInt(key,value);is Float->saved.putFloat(key,value);is Long->saved.putLong(key,value);is Set<*>->saved.putStringSet(key,value.filterIsInstance<String>().toSet())}};check(saved.commit())}
        }
    }

    @get:Rule val rules: RuleChain = RuleChain.outerRule(fixture).around(compose)

    @Test fun firstLaunchManualSetupAndSystemBack() {
        compose.onNodeWithText("Добро пожаловать").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Настроить самому").performScrollTo().performClick()
        compose.onNodeWithText("Выберите, что включить в приложение").assertIsDisplayed()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("Добро пожаловать").performScrollTo().assertIsDisplayed()
    }

    @Test fun manualSavePersistsSettingsAndOpensToday() {
        compose.onNodeWithText("Настроить самому").performScrollTo().performClick()
        compose.onNodeWithText("Далее").performClick()
        if (compose.onAllNodesWithTag("library-installed").fetchSemanticsNodes().isNotEmpty()) {
            compose.waitUntil(30_000) {
                compose.onAllNodes(hasText("Далее") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Далее").performClick()
        }
        compose.onNodeWithText("Всё готово").assertIsDisplayed()
        compose.onNodeWithText("Сохранить").performClick()
        compose.onNodeWithText("Мой день").assertIsDisplayed()
        val preferences = compose.activity.getSharedPreferences("bible-desktop-native-profile", Context.MODE_PRIVATE)
        assertTrue(preferences.getBoolean("setupComplete", false))
        assertEquals("ru", preferences.getString("uiLanguage", null))
        assertTrue(preferences.contains("sections"))
        assertTrue(preferences.contains("translations"))
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Мой день").assertIsDisplayed()
    }

    @Test fun currentBibleDesktopApiSupportsNativeCoreScreens() = runBlocking {
        val client = BibleApiClient()
        try {
            val translation = client.getTranslations("ru").first { it.language.code == "ru" }
            val book = client.getBooks(translation.code).first()
            val chapter = client.getChapter(translation.code, book.slug, 1)
            assertTrue(chapter.verses.isNotEmpty())
            assertEquals(1, chapter.chapter.number)
            val prayer = client.getPrayers("ru").first()
            assertTrue(client.getPrayer(prayer.id).body.isNotBlank())
            val day = client.getCalendarDay("2026-10-08", "ru")
            assertEquals("2026-10-08", day.date)
            assertEquals("bible-desktop-calendar-engine", day.source)
            assertTrue(day.events.isNotEmpty())
            assertTrue(day.events.any { it.typikonMark != null })
            assertTrue(day.icons.isNotEmpty())
            assertTrue(day.icons.first().imagePreviewUrl?.contains("preview=1") == true)
            val service = client.getCalendarService("2026-10-08", "en")
            assertEquals("2026-10-08", service.date)
            assertTrue(service.assignments.isNotEmpty())
            assertTrue(service.expansions.isNotEmpty())
            assertEquals("cu-civil", client.getCalendarService("2026-10-08", "cu-civil").textLanguage)
            assertEquals(31, client.getCalendarMonth(2026, 10, "uk").size)
        } finally { client.close() }
    }

    @Test fun setupAndCoreScreenNavigation() {
        compose.waitUntil(timeoutMillis = 30_000) {
            compose.onAllNodes(hasText("Быстро настроить") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Быстро настроить").performScrollTo().performClick()
        compose.onNodeWithText("Календарь").performClick()
        compose.onNodeWithText("Церковный календарь").assertIsDisplayed()
        back()
        compose.onNodeWithText("Молитвы").performClick()
        compose.onNodeWithText("Молитвы").assertIsDisplayed()
        back()
        compose.onNode(hasText("Библия") and isSelectable()).performClick()
        if (compose.onAllNodesWithTag("reader-install").fetchSemanticsNodes().isNotEmpty())
            compose.onNodeWithTag("reader-install").assertIsDisplayed()
        else compose.onNodeWithText("Книги Библии").assertIsDisplayed()
        back()
        compose.onNodeWithText("Календарь").assertIsDisplayed()
    }

    @Test fun studyAndRemindersAreRealDestinationsInQuickSetup() {
        compose.waitUntil(30_000) { compose.onAllNodes(hasText("Быстро настроить") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Быстро настроить").performScrollTo().performClick()
        compose.onNodeWithText("Изучение Библии").performScrollTo().performClick()
        compose.onNodeWithText("Выбрать место в Библии").assertIsDisplayed().performClick()
        if (compose.onAllNodesWithTag("reader-install").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithTag("reader-install").assertIsDisplayed()
            compose.onNodeWithContentDescription("На главную").performClick()
        } else {
            compose.onNode(hasText("Книги Библии") and androidx.compose.ui.test.hasAnyAncestor(androidx.compose.ui.test.isDialog())).assertIsDisplayed()
            compose.onNode(hasContentDescription("На главную") and androidx.compose.ui.test.hasAnyAncestor(androidx.compose.ui.test.isDialog())).performClick()
        }
        compose.onNodeWithText("Напоминания").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("reminder-toggle-calendar").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("reminder-toggle-morning").assertIsOff()
        compose.onNodeWithContentDescription("На главную").performClick()
        compose.onNodeWithText("Мой день").assertIsDisplayed()
    }

    @Test fun notificationLaunchOpensBibleDirectly() {
        compose.waitUntil(30_000) { compose.onAllNodes(hasText("Быстро настроить") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Быстро настроить").performScrollTo().performClick()
        compose.activity.intent.putExtra(com.bibledesktop.myapp.data.ReminderScheduler.destinationExtra, "bible")
        // New activity, not saved Compose navigation restored from an old activity.
        val intent = android.content.Intent(compose.activity, MainActivity::class.java)
            .putExtra(com.bibledesktop.myapp.data.ReminderScheduler.destinationExtra, "bible")
        androidx.test.core.app.ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            compose.waitUntil(15_000) { compose.onAllNodes(hasText("Книги Библии")).fetchSemanticsNodes().isNotEmpty() || compose.onAllNodesWithTag("reader-install").fetchSemanticsNodes().isNotEmpty() }
        }
    }

    @Test fun aboutIsVisibleInMoreAndSurvivesRecreation() {
        compose.waitUntil(30_000) { compose.onAllNodes(hasText("Быстро настроить") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Быстро настроить").performScrollTo().performClick()
        compose.onNodeWithText("Ещё").performClick()
        compose.onNodeWithText("О приложении").performScrollTo().performClick()
        compose.onNodeWithText("Vladimir Atapin").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("О приложении").assertIsDisplayed()
        compose.onNodeWithText("Vladimir Atapin").assertIsDisplayed()
        back()
        compose.onNodeWithText("Ещё").assertIsDisplayed()
        compose.onNodeWithText("О приложении").performScrollTo().performClick()
        compose.onNodeWithContentDescription("На главную").performClick()
        compose.onNodeWithText("Мой день").assertIsDisplayed()
    }

    @Test fun parallelReferenceOpensExactVerseAndBackRestoresSource() {
        requireInstalled("BQ_RUSSIAN_RST_STRONG")
        val client = BibleApiClient()
        val loaded = try { runBlocking {
            val actual=com.bibledesktop.myapp.data.OfflineContentRepository(client,com.bibledesktop.myapp.data.OfflineStore(compose.activity))
            val book = actual.getBooks("BQ_RUSSIAN_RST_STRONG").first { it.canonicalBook?.osisCode == "John" }
            val source = actual.getChapter("BQ_RUSSIAN_RST_STRONG", book.slug, 3)
            val location = actual.getVerseLocations(source.translation.code, listOf("1John.4.10")).single()
            val target = actual.getChapter(source.translation.code, location.book, location.chapter).verses.single {
                it.id == location.verseId && it.number == location.verse && it.osisRef == location.osis && it.plainText.isNotBlank()
            }
            source to target
        } } finally { client.close() }
        val source = loaded.first
        val target = loaded.second
        assertEquals("BQ_RUSSIAN_RST_STRONG",source.translation.code)
        val verse = source.verses.first { it.number == 16 }
        val preferences = compose.activity.getSharedPreferences("bible-desktop-native-profile", Context.MODE_PRIVATE)
        check(preferences.edit().putBoolean("setupComplete", true).putString("sections", "bible,study")
            .putString("translations", source.translation.code).putString("lastTranslation", source.translation.code)
            .putString("lastBookSlug", source.book.slug).putInt("lastChapter", 3).putInt("lastVerse", 16).commit())
        val intent = android.content.Intent(compose.activity, MainActivity::class.java)
            .putExtra(com.bibledesktop.myapp.data.ReminderScheduler.destinationExtra, "bible")
        androidx.test.core.app.ActivityScenario.launch<MainActivity>(intent).use {
            compose.waitUntil(30_000) { compose.onAllNodes(hasContentDescription("Действия со стихом 16")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Действия со стихом 16").performClick()
            compose.onNodeWithText("Изучить стих").performClick()
            compose.waitUntil(30_000) { compose.onAllNodesWithTag("reference-1John.4.10").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("reference-1John.4.10").performScrollTo().performClick()
            compose.waitUntil(30_000) { compose.onAllNodes(hasText(target.plainText, substring = true) and hasAnyAncestor(isDialog())).fetchSemanticsNodes().isNotEmpty() }
            compose.onNode(androidx.compose.ui.test.hasTestTag("verse-${target.number}") and hasAnyAncestor(isDialog())).assertIsDisplayed().assertIsSelected()
            compose.onNode(hasText(target.plainText, substring = true) and hasAnyAncestor(isDialog())).assertIsDisplayed()
            assertEquals("1John.4.10", target.osisRef)
            assertEquals(source.book.slug, preferences.getString("lastBookSlug", null))
            assertEquals(3, preferences.getInt("lastChapter", 0))
            assertEquals(16, preferences.getInt("lastVerse", 0))
            compose.onNodeWithText("Вернуться к сравнению").performClick()
            compose.onNodeWithText("Закрыть").performScrollTo().performClick()
            compose.waitUntil(30_000) { compose.onAllNodes(hasText("${source.book.name} · Глава 3", substring = true)).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("verse-16").assertIsDisplayed().assertIsSelected()
        }
    }

    @Test fun readerComparesInstalledTranslationAndKeepsItOnRecreation() {
        requireInstalled("BQ_RUSSIAN_RST_STRONG")
        requireInstalled("BQ_ENGLISH_KJV_1769")
        val api = BibleApiClient()
        val source = try { runBlocking {
            val actual = com.bibledesktop.myapp.data.OfflineContentRepository(api, com.bibledesktop.myapp.data.OfflineStore(compose.activity))
            val book = actual.getBooks("BQ_RUSSIAN_RST_STRONG").first { it.canonicalBook?.osisCode == "John" }
            actual.getChapter("BQ_RUSSIAN_RST_STRONG", book.slug, 3)
        } } finally { api.close() }
        assertEquals("BQ_RUSSIAN_RST_STRONG", source.translation.code)
        val preferences = compose.activity.getSharedPreferences("bible-desktop-native-profile", Context.MODE_PRIVATE)
        check(preferences.edit().putBoolean("setupComplete", true).putString("sections", "bible")
            .putString("translations", source.translation.code).putString("lastTranslation", source.translation.code)
            .putString("lastBookSlug", source.book.slug).putInt("lastChapter", 3).putInt("lastVerse", 16).remove("compareTranslation").commit())
        val intent = android.content.Intent(compose.activity, MainActivity::class.java)
            .putExtra(com.bibledesktop.myapp.data.ReminderScheduler.destinationExtra, "bible")
        androidx.test.core.app.ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            compose.waitUntil(30_000) { compose.onAllNodesWithTag("verse-16").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Сравнить переводы").performClick()
            compose.onNodeWithTag("compare-translation").performClick()
            compose.onNodeWithTag("translation-search").performTextInput("King James")
            compose.onNodeWithTag("translation-BQ_ENGLISH_KJV_1769").performClick()
            compose.waitUntil(30_000) { compose.onAllNodesWithTag("compared-John.3.16").fetchSemanticsNodes().isNotEmpty() }
            assertEquals("BQ_ENGLISH_KJV_1769", preferences.getString("compareTranslation", null))
            compose.onNodeWithTag("compared-John.3.16").assertIsDisplayed()
            scenario.recreate()
            compose.waitUntil(30_000) { compose.onAllNodesWithTag("compared-John.3.16").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Назад").performClick()
            compose.onNodeWithTag("bible-comparison").assertDoesNotExist()
            compose.onNodeWithTag("verse-16").assertIsDisplayed()
        }
    }

    @Test fun directChapterPickerSurvivesRecreationAndOpensRealChapter() {
        requireInstalled("BQ_RUSSIAN_RST_STRONG")
        val preferences = compose.activity.getSharedPreferences("bible-desktop-native-profile", Context.MODE_PRIVATE)
        check(preferences.edit().putString("lastTranslation", "BQ_RUSSIAN_RST_STRONG")
            .putString("lastBookSlug", "genesis").putInt("lastChapter", 18).putInt("lastVerse", 0).commit())
        compose.waitUntil(30_000) { compose.onAllNodes(hasText("Быстро настроить") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Быстро настроить").performScrollTo().performClick()
        compose.onNode(hasText("Библия") and isSelectable()).performClick()
        compose.waitUntil(30_000) { compose.onAllNodesWithTag("reader-choose-chapter").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("reader-choose-chapter").performClick()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(30_000) { compose.onAllNodesWithTag("choose-chapter-5").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("choose-chapter-5").performClick()
        compose.waitUntil(30_000) { compose.onAllNodesWithTag("choose-verse-1").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("choose-verse-1").performClick()
        compose.waitUntil(30_000) { compose.onAllNodes(hasText("Глава 5",substring=true) and androidx.compose.ui.test.hasTestTag("reader-choose-chapter")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("reader-choose-chapter").assertTextContains("Глава 5", substring = true)
        assertEquals(5, preferences.getInt("lastChapter", 0))
    }

    @Test fun fourLanguagesAndCalendarBeforeSetup() {
        compose.onNodeWithText("Українська").assertDoesNotExist()
        chooseLanguageInSettings("Українська")
        compose.onNodeWithText("Швидко налаштувати").assertExists()
        chooseLanguageInSettings("English")
        compose.onNodeWithText("Quick setup").assertExists()
        compose.onNodeWithTag("calendar-overview").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Calendar texts are currently in Russian.").assertExists()
        chooseLanguageInSettings("Deutsch")
        compose.onNodeWithText("Schnell einrichten").assertExists()
        chooseLanguageInSettings("Русский")
        compose.onNodeWithText("Быстро настроить").assertExists()
    }

    @Test fun setupSurvivesActivityRecreation() {
        chooseLanguageInSettings("English")
        compose.onNodeWithText("Set up manually").performScrollTo().performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Choose what to include").assertIsDisplayed()
        back()
        compose.onNodeWithText("Quick setup").assertExists()
    }

    @Test fun systemRotationKeepsManualSetupAndLanguage() {
        chooseLanguageInSettings("Українська")
        compose.onNodeWithText("Налаштувати самостійно").performScrollTo().performClick()
        try {
            compose.runOnUiThread {
                compose.activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            compose.waitUntil(10_000) {
                compose.activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
            }
            compose.onNodeWithText("Виберіть, що додати до застосунку").assertIsDisplayed()
            back()
            compose.onNodeWithText("Швидко налаштувати").assertExists()
        } finally {
            compose.runOnUiThread {
                compose.activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    private fun chooseLanguageInSettings(name: String) {
        compose.onNodeWithTag("welcome-settings").performScrollTo().performClick()
        compose.onNodeWithText(name).performClick()
        assertEquals(mapOf("Русский" to "ru", "Deutsch" to "de", "Українська" to "uk", "English" to "en")[name],
            compose.activity.getSharedPreferences("bible-desktop-native-profile", Context.MODE_PRIVATE).getString("uiLanguage", null))
        back()
        compose.onNodeWithTag("welcome-settings").assertExists()
    }

    private fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    /** Reader integration requires genuinely installed editions, not implicit on-demand HTTP. */
    private fun requireInstalled(code: String) = runBlocking {
        org.junit.Assume.assumeTrue("Install the available edition before this reader integration test: $code",
            com.bibledesktop.myapp.data.OfflineStore(compose.activity).biblePackages().any { it.translation.code == code && it.isInstalled })
    }

    @Test fun verseNoteIsAccessibleFromMoreAfterLeavingReader() {
        requireInstalled("BQ_RUSSIAN_RST_STRONG")
        val client = BibleApiClient()
        val book = try { runBlocking {
            com.bibledesktop.myapp.data.OfflineContentRepository(client,com.bibledesktop.myapp.data.OfflineStore(compose.activity)).getBooks("BQ_RUSSIAN_RST_STRONG").minBy { it.order }
        } } finally { client.close() }
        check(compose.activity.getSharedPreferences("bible-desktop-native-profile",Context.MODE_PRIVATE).edit().putString("lastTranslation","BQ_RUSSIAN_RST_STRONG").commit())
        compose.waitUntil(30_000) { compose.onAllNodes(hasText("Быстро настроить") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Быстро настроить").performScrollTo().performClick()
        compose.onNode(hasText("Библия") and isSelectable()).performClick()
        compose.waitUntil(30_000) { compose.onAllNodes(hasText(book.name)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("book-search").performTextInput(book.name)
        compose.onNodeWithTag("book-${book.slug}").performScrollTo().performClick()
        compose.waitUntil(30_000) { compose.onAllNodesWithTag("choose-chapter-1").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("choose-chapter-1").performClick()
        // The main book grid opens a chapter directly; the modal passage picker has a verse step.
        compose.waitUntil(30_000) { compose.onAllNodes(hasText("${book.name} · Глава 1", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("reader-choose-chapter").assertTextContains("Глава 1", substring = true)
        compose.waitUntil(30_000) { compose.onAllNodes(hasContentDescription("Действия со стихом 1")).fetchSemanticsNodes().isNotEmpty() }
        assertEquals("BQ_RUSSIAN_RST_STRONG", compose.activity.getSharedPreferences("bible-desktop-native-profile", Context.MODE_PRIVATE).getString("lastTranslation", null))
        compose.onNodeWithContentDescription("Действия со стихом 1").performClick()
        compose.onNodeWithText("Заметка к стиху").performClick()
        compose.onNodeWithTag("note-body").performTextInput("Проверочная заметка")
        compose.onNodeWithText("Сохранить").performClick()
        compose.waitUntil(10_000) { com.bibledesktop.myapp.ui.bible.NoteStore.load(compose.activity).any { it.body == "Проверочная заметка" } }
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) { compose.onAllNodes(hasContentDescription("Действия со стихом 1")).fetchSemanticsNodes().isNotEmpty() }
        back(); back()
        compose.onNodeWithTag("book-search").assertTextContains(book.name)
        compose.onNodeWithContentDescription("На главную").performClick()
        compose.onAllNodes(hasText("Церковный календарь"))[0].assertExists()
        compose.onNodeWithText("Ещё").performClick()
        // Notes are below the study/download controls and may not be composed by LazyColumn yet.
        compose.waitUntil(10_000) { runCatching {
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Проверочная заметка"))
        }.isSuccess }
        compose.onNodeWithText("Проверочная заметка").performScrollTo().assertIsDisplayed()
    }
}
