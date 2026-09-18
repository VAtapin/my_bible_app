package com.bibledesktop.shared.presentation

import com.bibledesktop.shared.api.BibleApiClient
import com.bibledesktop.shared.api.CalendarDay
import com.bibledesktop.shared.api.PrayerDetail
import com.bibledesktop.shared.api.PrayerSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class DailyContentLoader {
    private val client = BibleApiClient()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun loadPrayers(
        language: String,
        onSuccess: (List<PrayerSummary>) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            runCatching { client.getPrayers(language) }
                .onSuccess(onSuccess)
                .onFailure { onError(it.message ?: "Unknown network error") }
        }
    }

    fun loadPrayer(
        id: Long,
        onSuccess: (PrayerDetail) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            runCatching { client.getPrayer(id) }
                .onSuccess(onSuccess)
                .onFailure { onError(it.message ?: "Unknown network error") }
        }
    }

    fun loadCalendarDay(
        date: String,
        language: String,
        onSuccess: (CalendarDay) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            runCatching { client.getCalendarDay(date, language) }
                .onSuccess(onSuccess)
                .onFailure { onError(it.message ?: "Unknown network error") }
        }
    }

    fun close() {
        scope.cancel()
        client.close()
    }
}
