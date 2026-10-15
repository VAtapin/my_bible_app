package com.bibledesktop.shared.presentation

import com.bibledesktop.shared.api.LanguageSummary
import com.bibledesktop.shared.api.TranslationSummary
import kotlin.test.*

class SetupDefaultsTest {
    @Test fun quickStartUsesInterfaceLanguageRatherThanBundledRussian() {
        val available = listOf(translation("BQ_RUSSIAN_RST_STRONG", "ru"), translation("DE", "de", true), translation("CU", "cu"))
        assertEquals(setOf("DE"), recommendedNativeTranslations(available, "de"))
        assertEquals(setOf("BQ_RUSSIAN_RST_STRONG"), recommendedNativeTranslations(available, "ru"))
        assertTrue(recommendedNativeTranslations(available, "uk").isEmpty())
    }
    private fun translation(code: String, lang: String, default: Boolean = false) =
        TranslationSummary(code, code, language = LanguageSummary(lang, lang), isDefault = default)

    @Test fun languageUsesSavedThenDeviceThenRussian() {
        assertEquals("uk", initialInterfaceLanguage("uk", "de"))
        assertEquals("en", initialInterfaceLanguage(null, "en"))
        assertEquals("ru", initialInterfaceLanguage("invalid", "fr"))
        assertEquals(setOf("ru", "de", "uk", "en"), interfaceLanguages.keys)
    }

    @Test fun quickIncludesNativeSectionsAndActualEditions() {
        val all = listOf(translation("RU", "ru", true), translation("UK", "uk"), translation("EN", "en"), translation("CU", "cu"), translation("CIVIL", "cu-civil"))
        assertEquals(setOf("UK"), recommendedNativeTranslations(all, "uk"))
        assertEquals(setOf("EN"), recommendedNativeTranslations(all, "en"))
        assertEquals(setOf("bible", "prayer", "calendar", "study", "reminders"), quickNativeSections)
        assertTrue(recommendedNativeTranslations(all, "de").isEmpty())
        assertTrue(recommendedNativeTranslations(emptyList(), "ru").isEmpty())
    }

    @Test fun summaryKeepsChosenGermanWhileDownloadIsIncomplete() {
        val russian = translation("RU", "ru")
        val german = translation("DE", "de")
        assertEquals(listOf(german), selectedSetupTranslations(listOf(russian, german), listOf(russian), german, setOf("DE")))
        assertEquals(listOf(german), selectedSetupTranslations(emptyList(), listOf(russian), german, setOf("DE")))
    }
}
