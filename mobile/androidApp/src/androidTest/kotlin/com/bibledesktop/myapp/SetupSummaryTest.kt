package com.bibledesktop.myapp

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.bibledesktop.myapp.ui.setup.SummaryScreen
import com.bibledesktop.myapp.ui.theme.BibleDesktopTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SetupSummaryTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun russianSummarySaves() = checkSave("ru", "Сохранить", "Создать моё приложение")
    @Test fun germanSummarySaves() = checkSave("de", "Speichern", "Meine App erstellen")
    @Test fun ukrainianSummarySaves() = checkSave("uk", "Зберегти", "Створити мій застосунок")
    @Test fun englishSummarySaves() = checkSave("en", "Save", "Create my app")

    private fun checkSave(language: String, title: String, oldTitle: String) {
        var saves = 0
        compose.setContent {
            BibleDesktopTheme {
                SummaryScreen(language, setOf("prayer", "calendar"), emptyList(), {}, { saves++ })
            }
        }
        compose.onNodeWithText(oldTitle).assertDoesNotExist()
        compose.onNodeWithText(title).assertIsDisplayed().assertHasClickAction().performClick()
        compose.runOnIdle { assertEquals(1, saves) }
    }
}
