package com.bibledesktop.myapp

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.bibledesktop.myapp.data.SlavonicNotebook
import com.bibledesktop.shared.api.SlavonicWord
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import java.util.UUID

class SlavonicNotebookTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun word(key: String) = SlavonicWord(key, "А҆́бїе", "Абие", "Скоро", "plain-text",
        "https://azbyka.ru/otechnik/Spravochniki/polnyj-pravoslavnyj-tserkovnoslavjanskij-slovar/1", 1, "b".repeat(64))
    @Test fun metadataSurvivesRestartAndDoesNotChangeExistingProfile() = runBlocking {
        val name = "slavonic-notebook-${UUID.randomUUID()}"
        val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        preferences.edit().putString("translations", "unchanged").commit()
        val store = SlavonicNotebook(context, name)
        val saved = word("a".repeat(40))
        store.toggle(saved)
        for (index in 0..34) store.remember(word(index.toString(16).padStart(40, '0')))
        store.remember(saved); store.remember(saved)
        val restarted = SlavonicNotebook(context, name).read()
        assertEquals(30, restarted.recent.size); assertEquals(saved.key, restarted.recent.first().key)
        assertEquals(saved.key, restarted.favorites.single().key)
        assertEquals("unchanged", preferences.getString("translations", ""))
        assertTrue(store.toggle(saved).favorites.isEmpty())
    }
    @Test fun corruptNotebookIsReportedAndNotOverwritten() = runBlocking {
        val name = "slavonic-corrupt-${UUID.randomUUID()}"
        val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        preferences.edit().putString("slavonicDictionaryNotebookV1", "invalid").commit()
        try { SlavonicNotebook(context, name).toggle(word("a".repeat(40))); fail("Corrupt data must not be replaced") }
        catch (_: kotlinx.serialization.SerializationException) { }
        assertEquals("invalid", preferences.getString("slavonicDictionaryNotebookV1", ""))
    }
}
