package com.bibledesktop.myapp

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import com.bibledesktop.myapp.ui.bible.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class NoteStoreTest {
    private val target = InstrumentationRegistry.getInstrumentation().targetContext
    private val context = object : ContextWrapper(target) {
        override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences("native-note-store-test", mode)
    }
    private val preferences = context.getSharedPreferences("native-note-store-test", Context.MODE_PRIVATE)
    private val passage = BookmarkEntry("John.3.16", "Verse", "John", "john", 3, 16, "EN", "KJV")
    @Before fun initialize() { check(target.packageName == "com.bibledesktop.myapp.debug"); preferences.edit().remove("verseNotesV1").remove("bookmarkEntries").commit() }
    @After fun cleanup() { preferences.edit().remove("verseNotesV1").remove("bookmarkEntries").commit() }

    @Test fun saveEditReloadAndDeleteArePersistentAndTranslationSpecific() = runBlocking {
        NoteStore.save(context, passage, "First")
        NoteStore.save(context, passage, "Changed")
        NoteStore.save(context, passage.copy(translationCode = "RU"), "Русский")
        val document = org.json.JSONObject(preferences.getString("verseNotesV1", null)!!)
        assertEquals(1, document.getInt("schemaVersion"))
        assertEquals(2, document.getJSONArray("entries").length())
        assertEquals(setOf("Changed", "Русский"), NoteStore.load(context).map { it.body }.toSet())
        NoteStore.remove(context, passage)
        assertEquals("RU", NoteStore.load(context).single().passage.translationCode)
        NoteStore.remove(context, passage.copy(translationCode = "RU"))
        assertTrue(NoteStore.load(context).isEmpty())
    }

    @Test fun invalidDataCannotBeOverwrittenAndOtherProfileDataStaysIntact() = runBlocking {
        preferences.edit().putString("verseNotesV1", "invalid").putString("bookmarkEntries", "[]").commit()
        assertTrue(runCatching { NoteStore.save(context, passage, "New") }.isFailure)
        assertEquals("invalid", preferences.getString("verseNotesV1", null))
        assertEquals("[]", preferences.getString("bookmarkEntries", null))
    }

    @Test fun bookmarkSerializationStillReadsExistingRecords() {
        val raw = """[{"reference":"John.3.16","text":"Verse","bookName":"John","bookSlug":"john","chapter":3,"verse":16,"translationCode":"EN","translationName":"KJV"}]"""
        preferences.edit().putString("bookmarkEntries", raw).commit()
        assertEquals(listOf(passage), BookmarkStore.load(context))
        assertEquals(passage, bookmarkFromJson(passage.toJson()))
    }

    @Test fun blankAndOversizeInputDoNotChangeSavedNote() = runBlocking {
        NoteStore.save(context, passage, "Keep")
        assertTrue(runCatching { NoteStore.save(context, passage, "  ") }.isFailure)
        assertTrue(runCatching { NoteStore.save(context, passage, "x".repeat(20_001)) }.isFailure)
        assertEquals("Keep", NoteStore.load(context).single().body)
    }
}
