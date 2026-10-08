package com.bibledesktop.myapp.ui.daily

import com.bibledesktop.shared.api.CalendarIcon
import java.net.URI
import java.time.LocalDate
import java.time.YearMonth

private val apiOrigin = URI("https://bible-desktop.com")
private val bundledPaths = setOf(
    "typikon/great.svg", "typikon/vigil.svg", "typikon/polyeleos.svg",
    "typikon/doxology.svg", "typikon/six-stichera.svg",
    "markers/minimal-dark/boiled-no-oil.png", "markers/minimal-dark/boiled-with-oil.png",
    "markers/minimal-dark/dairy-eggs.png", "markers/minimal-dark/dry-eating.png",
    "markers/minimal-dark/fast-no-fish.png", "markers/minimal-dark/fish.png",
    "markers/minimal-dark/memorial.png", "markers/minimal-dark/strict-fast.png",
)

/** Resolve only the original, bundled BibleDesktop signs, never a locally guessed sign. */
internal fun bundledCalendarAsset(source: String?): String? = runCatching {
    val url = apiOrigin.resolve(source ?: return null)
    if (url.scheme != "https" || url.host != apiOrigin.host || url.port != -1 ||
        url.userInfo != null || url.query != null || url.fragment != null) return null
    val path = url.path.removePrefix("/assets/")
    if (path in bundledPaths) "file:///android_asset/calendar/$path" else null
}.getOrNull()

/** Calendar image downloads stay on BibleDesktop; preview and gallery are distinct. */
internal fun calendarImageUrl(source: String?): String? = com.bibledesktop.myapp.data.CalendarMedia.url(source)

internal fun calendarGalleryUrls(icon: CalendarIcon): List<String> =
    (listOfNotNull(icon.imageUrl) + icon.images.map { it.url }).mapNotNull(::calendarImageUrl).distinct()

/** Civil display dates only. Feasts, old-style dates and signs always come from the API. */
internal fun calendarPeriodDates(selected: LocalDate, month: YearMonth, week: Boolean): List<LocalDate?> {
    if (week) {
        val monday = selected.minusDays((selected.dayOfWeek.value - 1).toLong())
        return (0..6).map { monday.plusDays(it.toLong()) }
    }
    val offset = month.atDay(1).dayOfWeek.value - 1
    val size = ((offset + month.lengthOfMonth() + 6) / 7) * 7
    return (0 until size).map { index ->
        (index - offset + 1).takeIf { it in 1..month.lengthOfMonth() }?.let(month::atDay)
    }
}
