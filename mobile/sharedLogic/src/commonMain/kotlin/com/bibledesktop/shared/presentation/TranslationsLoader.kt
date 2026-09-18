package com.bibledesktop.shared.presentation

import com.bibledesktop.shared.api.BibleApiClient
import com.bibledesktop.shared.api.TranslationSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class TranslationsLoader {
    private val client = BibleApiClient()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun load(
        language: String?,
        onSuccess: (List<TranslationSummary>) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            runCatching { client.getTranslations(language) }
                .onSuccess(onSuccess)
                .onFailure { onError(it.message ?: "Unknown network error") }
        }
    }

    fun close() {
        scope.cancel()
        client.close()
    }
}
