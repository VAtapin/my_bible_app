package com.bibledesktop.myapp.ui.bible

import android.content.Context
import com.bibledesktop.shared.api.BibleChapter
import com.bibledesktop.shared.api.BibleVerse
import org.json.JSONArray
import org.json.JSONObject

data class BookmarkEntry(
    val reference: String,
    val text: String,
    val bookName: String,
    val bookSlug: String,
    val chapter: Int,
    val verse: Int,
    val translationCode: String,
    val translationName: String,
)

object BookmarkStore {
    private const val PreferencesName = "bible-desktop-native-profile"
    private const val EntriesKey = "bookmarkEntries"
    private const val LegacyRefsKey = "bookmarks"

    fun load(context: Context): List<BookmarkEntry> {
        val raw = preferences(context).getString(EntriesKey, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        BookmarkEntry(
                            reference = item.getString("reference"),
                            text = item.getString("text"),
                            bookName = item.getString("bookName"),
                            bookSlug = item.getString("bookSlug"),
                            chapter = item.getInt("chapter"),
                            verse = item.getInt("verse"),
                            translationCode = item.getString("translationCode"),
                            translationName = item.getString("translationName"),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun toggle(
        context: Context,
        entries: List<BookmarkEntry>,
        chapter: BibleChapter,
        verse: BibleVerse,
    ): List<BookmarkEntry> {
        val values = entries.toMutableList()
        val existing = values.indexOfFirst {
            it.reference == verse.osisRef && it.translationCode == chapter.translation.code
        }
        if (existing >= 0) {
            values.removeAt(existing)
        } else {
            values.add(
                BookmarkEntry(
                    reference = verse.osisRef,
                    text = verse.plainText,
                    bookName = chapter.book.name,
                    bookSlug = chapter.book.slug,
                    chapter = chapter.chapter.number,
                    verse = verse.number,
                    translationCode = chapter.translation.code,
                    translationName = chapter.translation.shortName ?: chapter.translation.name,
                ),
            )
        }
        save(context, values)
        return values
    }

    fun remove(context: Context, entry: BookmarkEntry): List<BookmarkEntry> {
        val values = load(context).filterNot {
            it.reference == entry.reference && it.translationCode == entry.translationCode
        }
        save(context, values)
        return values
    }

    private fun save(context: Context, values: List<BookmarkEntry>) {
        val array = JSONArray()
        values.forEach { entry ->
            array.put(
                JSONObject()
                    .put("reference", entry.reference)
                    .put("text", entry.text)
                    .put("bookName", entry.bookName)
                    .put("bookSlug", entry.bookSlug)
                    .put("chapter", entry.chapter)
                    .put("verse", entry.verse)
                    .put("translationCode", entry.translationCode)
                    .put("translationName", entry.translationName),
            )
        }
        preferences(context).edit()
            .putString(EntriesKey, array.toString())
            .putStringSet(LegacyRefsKey, values.map(BookmarkEntry::reference).toSet())
            .apply()
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
}
