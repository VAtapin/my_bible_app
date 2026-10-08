package com.bibledesktop.myapp.data

import java.net.URI

/** Both native rendering and durable downloads accept only BibleDesktop's calendar media. */
internal object CalendarMedia {
    private val origin = URI("https://bible-desktop.com")
    fun url(source: String?): String? = runCatching {
        val url = origin.resolve(source ?: return null)
        if (url.scheme != "https" || url.host != origin.host || url.port != -1 ||
            url.userInfo != null || url.fragment != null ||
            !(Regex("/api/calendar/icons/[0-9]+/images/[0-9]+").matches(url.path) ||
              Regex("/storage/calendar-icons/[a-f0-9]{64}\\.(png|jpg|jpeg|webp)").matches(url.path)) ||
            (url.query != null && url.query != "preview=1")) return null
        url.toString()
    }.getOrNull()
}
