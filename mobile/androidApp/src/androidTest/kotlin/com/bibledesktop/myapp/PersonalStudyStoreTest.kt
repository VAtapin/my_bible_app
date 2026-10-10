package com.bibledesktop.myapp

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bibledesktop.myapp.ui.bible.PersonalStudyStore
import com.bibledesktop.shared.api.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersonalStudyStoreTest {
    private val base = InstrumentationRegistry.getInstrumentation().targetContext
    private fun context(name: String): Context = object : android.content.ContextWrapper(base) {
        override fun getSharedPreferences(nameOfPreferences: String?, mode: Int) = base.getSharedPreferences("personal-study-test-$name", mode)
    }
    @Test fun collectionsMemorizationAndWordNotesPersistWithoutChangingLegacyProfile() = runBlocking {
        val context = context("save")
        val preferences = context.getSharedPreferences("", Context.MODE_PRIVATE)
        preferences.edit().clear().putString("bookmarks", "legacy").commit()
        val passage = SavedPassage("RST", "Russian", "john", "John", PassagePoint(1, 1), PassagePoint(2, 1), listOf(PassageVerse(1, 1, "John.1.1", "Text"), PassageVerse(2, 1, "John.2.1", "Next")))
        listOf(async { PersonalStudyStore.update(context) { it.copy(bookmarks = it.bookmarks + StudyBookmark("bookmark", passage, "Lesson", "Notes", collection = "Lessons")) } }, async { PersonalStudyStore.update(context) { it.copy(cards = it.cards + MemoryCard("memory", passage)) } }).awaitAll()
        val reloaded = PersonalStudyStore.read(context)
        assertEquals("Lessons", reloaded.bookmarks.single().collection); assertFalse(reloaded.cards.single().learned); assertEquals("legacy", preferences.getString("bookmarks", null))
        PersonalStudyStore.update(context) { it.copy(cards = it.cards.map { card -> card.copy(learned = true) }) }
        assertTrue(PersonalStudyStore.read(context).cards.single().learned)
        preferences.edit().clear().commit()
        Unit
    }
    @Test fun invalidDataCannotBeOverwritten() = runBlocking {
        val context = context("invalid"); val preferences = context.getSharedPreferences("", Context.MODE_PRIVATE)
        preferences.edit().clear().putString("personalStudyV1", "{\"version\":99}").commit()
        val failed = runCatching { PersonalStudyStore.update(context) { it.copy(cards = emptyList()) } }
        assertTrue(failed.isFailure); assertEquals("{\"version\":99}", preferences.getString("personalStudyV1", null))
        preferences.edit().clear().commit()
        Unit
    }
}
