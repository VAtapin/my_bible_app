package com.bibledesktop.myapp

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bibledesktop.myapp.data.ReaderLink
import com.bibledesktop.myapp.data.parseReaderLink
import com.bibledesktop.myapp.ui.bible.BookmarkEntry
import com.bibledesktop.myapp.ui.bible.verseShareIntent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderLinksTest {
    private val link = ReaderLink("BQ_RUSSIAN_RST_STRONG", "john", 3, 16)
    private val url = "https://bible-app.online/reader?translation=BQ_RUSSIAN_RST_STRONG&book=john&chapter=3&verse=16"

    @Test fun onlineContractRoundTripsExactEditionAndVerse() {
        assertEquals(url, link.onlineUrl())
        assertEquals(link, parseReaderLink(Uri.parse(url)))
        assertEquals(link, parseReaderLink(Uri.parse(url.replace("https:", "http:"))))
        assertEquals(link.copy(verse = 0), parseReaderLink(Uri.parse(url.substringBefore("&verse="))))
    }

    @Test fun rejectsOtherOriginsAndAmbiguousQueries() {
        listOf(url.replace("bible-app.online", "bible-desktop.com"),
            url.replace("bible-app.online", "bible-app.online.evil.test"),
            url.replace("https:", "intent:"), url.replace("/reader?", "/reader/other?"),
            url.replace("bible-app.online", "user@bible-app.online"),
            url.replace("bible-app.online", "bible-app.online:5173"),
            "$url#fragment", "$url&verse=17", "$url&book=luke",
            "$url&translation=BQ_ENGLISH_KJV_1769", "$url&chapter=4",
        ).forEach { assertNull(it, parseReaderLink(Uri.parse(it))) }
    }

    @Test fun rejectsMissingInvalidAndUnsafePassages() {
        listOf(url.replace("chapter=3", "chapter=0"), url.replace("chapter=3", "chapter=1001"),
            url.replace("verse=16", "verse=-1"), url.replace("verse=16", "verse=0"),
            url.replace("verse=16", "verse=no"), url.replace("book=john", "book=..%2Fluke"),
            url.replace("translation=BQ_RUSSIAN_RST_STRONG&", ""),
            url.replace("book=john&", ""), url.replace("chapter=3&", ""),
        ).forEach { assertNull(it, parseReaderLink(Uri.parse(it))) }
        assertNull(parseReaderLink(null))
    }

    @Test fun verseAndBookmarkSharingUseOnlineAppNotApiSource() {
        val passage = BookmarkEntry("John.3.16", "Ибо так возлюбил Бог мир…", "Иоанна", "john", 3, 16,
            link.translationCode, "RST-Strong")
        val intent = verseShareIntent(passage)
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("text/plain", intent.type)
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)!!
        assertEquals("${passage.text}\n\nИоанна 3:16 · RST-Strong\n$url", text)
        assertFalse(text.contains("bible-desktop.com/api"))
        assertEquals(link, parseReaderLink(Uri.parse(text.lineSequence().last())))
    }
}
