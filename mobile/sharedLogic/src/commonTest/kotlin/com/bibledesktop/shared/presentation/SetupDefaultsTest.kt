package com.bibledesktop.shared.presentation

import com.bibledesktop.shared.api.LanguageSummary
import com.bibledesktop.shared.api.TranslationSummary
import kotlin.test.*

class SetupDefaultsTest {
    @Test fun bundledSynodalIsDefaultRegardlessOfInterfaceLanguage() {
        val available = listOf(translation("BQ_RUSSIAN_RST_STRONG", "ru"), translation("DE", "de", true), translation("CU", "cu"))
        for (language in interfaceLanguages.keys) assertEquals(setOf("BQ_RUSSIAN_RST_STRONG"), recommendedNativeTranslations(available, language))
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
        assertEquals(setOf("UK", "CU", "CIVIL"), recommendedNativeTranslations(all, "uk"))
        assertEquals(setOf("EN", "CU", "CIVIL"), recommendedNativeTranslations(all, "en"))
        assertEquals(setOf("bible", "prayer", "calendar", "study", "reminders"), quickNativeSections)
        assertEquals(setOf("RU", "CU", "CIVIL"), recommendedNativeTranslations(all, "de"))
        assertTrue(recommendedNativeTranslations(emptyList(), "ru").isEmpty())
    }
}
