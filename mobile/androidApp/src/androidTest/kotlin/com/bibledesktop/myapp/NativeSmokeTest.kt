package com.bibledesktop.myapp

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bibledesktop.shared.api.BibleApiClient
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
    private val fixtureKeys = listOf("setupComplete", "uiLanguage", "sections", "translations", "lastTranslation", "lastBookSlug", "lastChapter", "verseNotesV1")
    private var original: Map<String, Any?> = emptyMap()
    private val fixture = object : ExternalResource() {
        override fun before() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            check(context.packageName == "com.bibledesktop.myapp.debug")
            val preferences = context.getSharedPreferences("bible-desktop-native-profile", Context.MODE_PRIVATE)
            original = fixtureKeys.associateWith { preferences.all[it] }
            check(preferences.edit().putBoolean("setupComplete", false).putString("uiLanguage", "ru")
                .remove("lastTranslation").remove("lastBookSlug").remove("lastChapter").remove("verseNotesV1").commit())
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
        if (compose.onAllNodes(hasText("Выберите переводы Библии")).fetchSemanticsNodes().isNotEmpty()) {
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
        compose.onNodeWithText("Книги Библии").assertIsDisplayed()
        back()
        compose.onNodeWithText("Календарь").assertIsDisplayed()
    }

    @Test fun fourLanguagesAndCalendarBeforeSetup() {
        compose.onNodeWithText("Українська").performClick()
        compose.onNodeWithText("Швидко налаштувати").assertExists()
        compose.onNodeWithText("English").performScrollTo().performClick()
        compose.onNodeWithText("Quick setup").assertExists()
        compose.onNodeWithTag("calendar-overview").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Calendar texts are currently in Russian.").assertExists()
        compose.onNodeWithText("Deutsch").performScrollTo().performClick()
        compose.onNodeWithText("Schnell einrichten").assertExists()
        compose.onNodeWithText("Русский").performClick()
        compose.onNodeWithText("Быстро настроить").assertExists()
    }

    @Test fun setupSurvivesActivityRecreation() {
        compose.onNodeWithText("English").performClick()
        compose.onNodeWithText("Set up manually").performScrollTo().performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Choose what to include").assertIsDisplayed()
        back()
        compose.onNodeWithText("Quick setup").assertExists()
    }

    @Test fun systemRotationKeepsManualSetupAndLanguage() {
        compose.onNodeWithText("Українська").performClick()
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

    private fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    @Test fun verseNoteIsAccessibleFromMoreAfterLeavingReader() {
        val client = BibleApiClient()
        val book = try { runBlocking {
            val translation = client.getTranslations("ru").let { all -> all.firstOrNull { it.isDefault } ?: all.first() }
            client.getBooks(translation.code).minBy { it.order }
        } } finally { client.close() }
        compose.waitUntil(30_000) { compose.onAllNodes(hasText("Быстро настроить") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Быстро настроить").performScrollTo().performClick()
        compose.onNode(hasText("Библия") and isSelectable()).performClick()
        compose.waitUntil(30_000) { compose.onAllNodes(hasText(book.name)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("book-search").performTextInput(book.name)
        compose.onNodeWithTag("book-${book.slug}").performScrollTo().performClick()
        compose.onNodeWithText("1").performClick()
        compose.waitUntil(30_000) { compose.onAllNodes(hasContentDescription("Действия со стихом 1")).fetchSemanticsNodes().isNotEmpty() }
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
        compose.onNodeWithText("Проверочная заметка").performScrollTo().assertIsDisplayed()
    }
}
