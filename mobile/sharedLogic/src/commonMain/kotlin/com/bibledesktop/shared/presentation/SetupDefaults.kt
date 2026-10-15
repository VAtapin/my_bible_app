package com.bibledesktop.shared.presentation

import com.bibledesktop.shared.api.TranslationSummary

val interfaceLanguages = linkedMapOf("ru" to "Русский", "de" to "Deutsch", "uk" to "Українська", "en" to "English")

fun initialInterfaceLanguage(saved: String?, device: String): String =
    saved?.takeIf { it in interfaceLanguages } ?: device.takeIf { it in interfaceLanguages } ?: "ru"

/** Available native sections; showing reminders does not enable notifications. */
val quickNativeSections = setOf("bible", "prayer", "calendar", "study", "reminders")

fun recommendedNativeTranslations(available: List<TranslationSummary>, language: String): Set<String> {
    val primary = available.firstOrNull { it.language.code == language && it.isDefault }
        ?: available.firstOrNull { it.language.code == language }
    if (primary == null) return emptySet()
    return setOf(primary.code)
}

fun selectedSetupTranslations(available: List<TranslationSummary>, installed: List<TranslationSummary>,
    chosen: TranslationSummary?, codes: Set<String>): List<TranslationSummary> =
    (available + installed + listOfNotNull(chosen)).distinctBy { it.code }.filter { it.code in codes }
