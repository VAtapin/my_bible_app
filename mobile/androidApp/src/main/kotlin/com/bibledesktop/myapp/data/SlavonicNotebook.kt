package com.bibledesktop.myapp.data

import android.content.Context
import com.bibledesktop.shared.api.SlavonicWord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable data class SavedSlavonicWord(val key: String, val word: String, val civil: String)
@Serializable data class SlavonicNotebookData(val version: Int = 1,
    val favorites: List<SavedSlavonicWord> = emptyList(), val recent: List<SavedSlavonicWord> = emptyList())

/** Additive record in the existing native profile; no article body or learning data is replaced. */
class SlavonicNotebook(context: Context, preferencesName: String = "bible-desktop-native-profile") {
    private val preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    private fun readStored(): SlavonicNotebookData {
        val raw = preferences.getString(Key, null) ?: return SlavonicNotebookData()
        return Json.decodeFromString<SlavonicNotebookData>(raw).also { data ->
            check(data.version == 1 && (data.favorites + data.recent).all { Regex("[a-f0-9]{40}").matches(it.key) })
        }
    }
    suspend fun read() = withContext(Dispatchers.IO) { writes.withLock { readStored() } }
    suspend fun remember(word: SlavonicWord) = update { data ->
        data.copy(recent = (listOf(word.saved()) + data.recent.filterNot { it.key == word.key }).take(30))
    }
    suspend fun toggle(word: SlavonicWord) = update { data ->
        data.copy(favorites = if (data.favorites.any { it.key == word.key }) data.favorites.filterNot { it.key == word.key }
            else listOf(word.saved()) + data.favorites)
    }
    private suspend fun update(change: (SlavonicNotebookData) -> SlavonicNotebookData) = withContext(Dispatchers.IO) {
        writes.withLock {
            val next = change(readStored()) // Corrupt personal data is never permission to overwrite it.
            check(preferences.edit().putString(Key, Json.encodeToString(next)).commit())
            next
        }
    }
    private fun SlavonicWord.saved() = SavedSlavonicWord(key, headword, civil)
    private companion object { const val Key = "slavonicDictionaryNotebookV1"; val writes = Mutex() }
}
