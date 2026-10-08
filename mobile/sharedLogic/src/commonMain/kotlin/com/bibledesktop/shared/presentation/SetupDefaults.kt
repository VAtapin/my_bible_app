package com.bibledesktop.shared.presentation

import com.bibledesktop.shared.api.TranslationSummary

val interfaceLanguages = linkedMapOf("ru" to "Русский", "de" to "Deutsch", "uk" to "Українська", "en" to "English")

fun initialInterfaceLanguage(saved: String?, device: String): String =
    saved?.takeIf { it in interfaceLanguages } ?: device.takeIf { it in interfaceLanguages } ?: "ru"

/** Only functioning native sections; reminders require explicit system opt-in later. */
val quickNativeSections = setOf("bible", "prayer", "calendar")

fun recommendedNativeTranslations(available: List<TranslationSummary>, language: String): Set<String> {
    val primary = available.firstOrNull { it.language.code == language && it.isDefault }
        ?: available.firstOrNull { it.language.code == language }
        ?: available.firstOrNull { it.language.code == "ru" && it.isDefault }
        ?: available.firstOrNull { it.language.code == "ru" }
    return available.filter { it == primary || it.language.code in setOf("cu", "cu-civil") }
        .map { it.code }.toSet()
}
