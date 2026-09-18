package com.bibledesktop.shared.presentation

import com.bibledesktop.shared.api.BibleApiClient
import com.bibledesktop.shared.api.BibleBook
import com.bibledesktop.shared.api.BibleChapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class BibleBrowserLoader {
    private val client = BibleApiClient()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun loadBooks(
        translationCode: String,
        onSuccess: (List<BibleBook>) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            runCatching { client.getBooks(translationCode) }
                .onSuccess(onSuccess)
                .onFailure { onError(it.message ?: "Unknown network error") }
        }
    }

    fun loadChapter(
        translationCode: String,
        bookSlug: String,
        chapterNumber: Int,
        onSuccess: (BibleChapter) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            runCatching { client.getChapter(translationCode, bookSlug, chapterNumber) }
                .onSuccess(onSuccess)
                .onFailure { onError(it.message ?: "Unknown network error") }
        }
    }

    fun close() {
        scope.cancel()
        client.close()
    }
}
