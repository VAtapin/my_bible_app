package com.bibledesktop.myapp.data

import android.net.Uri

/** Same /reader query contract as the online app; never a URL to the content API. */
data class ReaderLink(val translationCode: String, val bookSlug: String, val chapter: Int, val verse: Int = 0) {
    fun onlineUrl(): String {
        require(safeSegment(translationCode) && safeSegment(bookSlug) && chapter in 1..1000 && verse in 0..1000)
        return Uri.Builder().scheme("https").authority("bible-app.online").path("/reader")
            .appendQueryParameter("translation", translationCode).appendQueryParameter("book", bookSlug)
            .appendQueryParameter("chapter", chapter.toString()).apply {
                if (verse > 0) appendQueryParameter("verse", verse.toString())
            }.build().toString()
    }
}

private fun safeSegment(value: String) = value.length in 1..160 && value.all { it.isLetterOrDigit() || it == '_' || it == '-' }

fun parseReaderLink(uri: Uri?): ReaderLink? = runCatching {
    if (uri == null || !uri.isHierarchical || uri.host != "bible-app.online" || uri.path != "/reader" ||
        uri.scheme !in setOf("https", "http") || uri.userInfo != null || uri.fragment != null ||
        uri.port !in setOf(-1, if (uri.scheme == "https") 443 else 80)) return null
    fun single(key: String): String? {
        val values = uri.getQueryParameters(key)
        require(values.size <= 1)
        return values.singleOrNull()
    }
    val translation = single("translation") ?: return null
    val book = single("book") ?: return null
    val chapter = single("chapter")?.toIntOrNull()?.takeIf { it in 1..1000 } ?: return null
    val verseText = single("verse")
    val verse = if (verseText == null) 0 else verseText.toIntOrNull()?.takeIf { it in 1..1000 } ?: return null
    if (!safeSegment(translation) || !safeSegment(book)) return null
    ReaderLink(translation, book, chapter, verse)
}.getOrNull()
