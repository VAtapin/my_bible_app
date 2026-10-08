package com.bibledesktop.myapp.ui.bible

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class VerseNote(val passage: BookmarkEntry, val body: String, val updatedAt: Long)

/** Small personal records share the existing native profile, never a browser cache. */
object NoteStore {
    private const val Key = "verseNotesV1"
    private val writes = Mutex()
    private fun preferences(context: Context) = context.getSharedPreferences("bible-desktop-native-profile", Context.MODE_PRIVATE)

    suspend fun read(context: Context): List<VerseNote> = withContext(Dispatchers.IO) { load(context) }

    fun load(context: Context): List<VerseNote> {
        val raw = preferences(context).getString(Key, null) ?: return emptyList()
        val document = JSONObject(raw)
        check(document.getInt("schemaVersion") == 1)
        val entries = document.getJSONArray("entries")
        return (0 until entries.length()).map { index ->
            val entry = entries.getJSONObject(index)
            VerseNote(bookmarkFromJson(entry.getJSONObject("passage")), entry.getString("body"), entry.getLong("updatedAt"))
        }.sortedByDescending { it.updatedAt }
    }

    suspend fun save(context: Context, passage: BookmarkEntry, body: String): List<VerseNote> {
        require(body.isNotBlank() && body.length <= 20_000)
        return update(context, passage) { it + VerseNote(passage, body.trim(), System.currentTimeMillis()) }
    }

    suspend fun remove(context: Context, passage: BookmarkEntry): List<VerseNote> = update(context, passage) { it }

    private suspend fun update(context: Context, passage: BookmarkEntry, transform: (List<VerseNote>) -> List<VerseNote>): List<VerseNote> =
        withContext(Dispatchers.IO) {
            writes.withLock {
                // Read fresh; invalid stored JSON is an error, never permission to overwrite it.
                val values = transform(load(context).filterNot {
                    it.passage.reference == passage.reference && it.passage.translationCode == passage.translationCode
                }).sortedByDescending { it.updatedAt }
                val entries = JSONArray()
                values.forEach { note -> entries.put(JSONObject().put("passage", note.passage.toJson())
                    .put("body", note.body).put("updatedAt", note.updatedAt)) }
                val document = JSONObject().put("schemaVersion", 1).put("entries", entries)
                check(preferences(context).edit().putString(Key, document.toString()).commit())
                values
            }
        }
}
