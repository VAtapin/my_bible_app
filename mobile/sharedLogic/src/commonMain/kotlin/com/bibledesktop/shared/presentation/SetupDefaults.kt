package com.bibledesktop.shared.presentation

import com.bibledesktop.shared.api.TranslationSummary

val interfaceLanguages = linkedMapOf("ru" to "Русский", "de" to "Deutsch", "uk" to "Українська", "en" to "English")

fun initialInterfaceLanguage(saved: String?, device: String): String =
    saved?.takeIf { it in interfaceLanguages } ?: device.takeIf { it in interfaceLanguages } ?: "ru"

/** Available native sections; showing reminders does not enable notifications. */
val quickNativeSections = setOf("bible", "prayer", "calendar", "study", "reminders")

fun recommendedNativeTranslations(available: List<TranslationSummary>, language: String): Set<String> {
    available.firstOrNull { it.code == "BQ_RUSSIAN_RST_STRONG" }?.let { return setOf(it.code) }
    val primary = available.firstOrNull { it.language.code == language && it.isDefault }
        ?: available.firstOrNull { it.language.code == language }
        ?: available.firstOrNull { it.language.code == "ru" && it.isDefault }
        ?: available.firstOrNull { it.language.code == "ru" }
    return available.filter { it == primary || it.language.code in setOf("cu", "cu-civil") }
        .map { it.code }.toSet()
}
