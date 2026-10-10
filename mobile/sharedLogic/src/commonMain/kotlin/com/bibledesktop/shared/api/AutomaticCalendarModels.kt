package com.bibledesktop.shared.api

import kotlinx.serialization.Serializable

const val AutomaticCalendarPolicy = "calendar-language-v1"
fun calendarTextPriority(language: String): List<String> = when (language) {
    "ru" -> listOf("cu", "cu-civil", "ru")
    "uk" -> listOf("cu", "cu-civil", "uk", "ru")
    else -> listOf(language, "cu", "cu-civil", "ru")
}.distinct()

@Serializable
data class CalendarTextEdition(
    val selectedTextId: Long?, val language: String, val orthography: String,
    val edition: String?, val sourceKind: String,
)
@Serializable
data class AutomaticCalendarText(
    val title: String, val text: String, val familyId: String?, val workSlug: String?,
    val language: String?, val orthography: String?, val edition: String?, val sourceKind: String?,
    val selectedTextId: Long?, val contentHash: String?, val availableEditions: List<CalendarTextEdition>,
    val selection: String, val missingReason: String?,
    val textId: String? = null, val id: String? = null, val slot: String? = null,
    val insert: Boolean = false, val rubric: String? = null,
)
@Serializable
data class AutomaticCalendarServicePlan(
    val schemaVersion: Int, val textPolicy: String, val calendarLanguage: String,
    val date: String, val textLanguage: String,
    val assignments: List<AutomaticCalendarText>, val expansions: List<AutomaticCalendarText>,
    val properCoverage: CalendarServiceCoverage? = null,
)

private fun validEdition(language: String?, orthography: String?, edition: String?, sourceKind: String?, textId: Long?): Boolean {
    if (language.isNullOrBlank() || orthography.isNullOrBlank()) return false
    if (language == "cu" && orthography != "traditional") return false
    if (language == "cu-civil" && orthography !in listOf("civil", "civil-accented")) return false
    return when (sourceKind) {
        "calendar-engine" -> edition == null && textId == null
        "liturgical-corpus" -> !edition.isNullOrBlank() && textId != null && textId > 0
        else -> false
    }
}
private fun AutomaticCalendarText.validate(calendarLanguage: String) {
    require(availableEditions.all { validEdition(it.language, it.orthography, it.edition, it.sourceKind, it.selectedTextId) })
    val priority = calendarTextPriority(calendarLanguage)
    val preferred = priority.firstOrNull { language -> availableEditions.any { it.language == language } }
    if (selection == "missing") {
        require(preferred == null && text.isEmpty() && language == null && orthography == null && edition == null)
        require(sourceKind == null && selectedTextId == null && contentHash == null && !missingReason.isNullOrBlank())
        return
    }
    require(validEdition(language, orthography, edition, sourceKind, selectedTextId))
    val expectedSelection = if (preferred == priority.first()) "preferred" else "fallback"
    require(text.isNotBlank() && language == preferred && selection == expectedSelection)
    require(missingReason == null && contentHash?.matches(Regex("[a-f0-9]{64}")) == true)
    require(availableEditions.any { it.language == language && it.orthography == orthography && it.edition == edition && it.sourceKind == sourceKind && it.selectedTextId == selectedTextId })
    require(
        if (sourceKind == "liturgical-corpus") !workSlug.isNullOrBlank() && familyId == "work:$workSlug"
        else workSlug == null && !id.isNullOrBlank() && familyId == "calendar-expansion:$id",
    )
}
fun AutomaticCalendarServicePlan.validateAutomaticCalendar(expectedDate: String, language: String): AutomaticCalendarServicePlan {
    require(schemaVersion == 2 && textPolicy == AutomaticCalendarPolicy && calendarLanguage == language && date == expectedDate && textLanguage == "mixed")
    assignments.forEach {
        require(!it.textId.isNullOrBlank() && !it.slot.isNullOrBlank())
        it.validate(language)
    }
    expansions.forEach {
        require(!it.id.isNullOrBlank())
        it.validate(language)
    }
    return this
}
fun automaticCalendarServiceKey(date: String, calendarLanguage: String) = "service:auto:$AutomaticCalendarPolicy:$calendarLanguage:$date"
