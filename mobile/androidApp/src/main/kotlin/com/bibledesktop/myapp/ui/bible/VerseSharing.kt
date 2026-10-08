package com.bibledesktop.myapp.ui.bible

import android.content.Context
import android.content.Intent
import com.bibledesktop.myapp.data.ReaderLink

internal fun verseShareIntent(passage: BookmarkEntry): Intent {
    val url = ReaderLink(passage.translationCode, passage.bookSlug, passage.chapter, passage.verse).onlineUrl()
    val reference = "${passage.bookName} ${passage.chapter}:${passage.verse} · ${passage.translationName}"
    return Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "${passage.text}\n\n$reference\n$url")
    }
}

internal fun shareBiblePassage(context: Context, passage: BookmarkEntry) {
    context.startActivity(Intent.createChooser(verseShareIntent(passage), null))
}
