package com.bibledesktop.myapp

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.bibledesktop.myapp.ui.bible.*
import com.bibledesktop.myapp.ui.theme.BibleDesktopTheme
import org.junit.*
import org.junit.Assert.*
import kotlinx.coroutines.runBlocking

class NoteEditorTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferences = context.getSharedPreferences("bible-desktop-native-profile", Context.MODE_PRIVATE)
    private var original: String? = null
    private val passage = BookmarkEntry("Test.1.1", "Test verse", "Test", "test", 1, 1, "TEST", "Test edition")
    @Before fun preserve() { check(context.packageName == "com.bibledesktop.myapp.debug"); original = preferences.getString("verseNotesV1", null) }
    @After fun restore() {
        val editor = preferences.edit()
        if (original == null) editor.remove("verseNotesV1") else editor.putString("verseNotesV1", original)
        check(editor.commit())
    }

    @Test fun editorSavesTypedTextAndClosesOnlyAfterPersistence() {
        compose.setContent {
            var open by remember { mutableStateOf(true) }
            BibleDesktopTheme {
                if (open) NoteEditor("en", passage, "", { open = false }, { open = false }) else Text("Saved")
            }
        }
        compose.onNodeWithTag("note-body").performTextInput("A personal thought")
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Saved")).fetchSemanticsNodes().isNotEmpty() }
        assertEquals("A personal thought", NoteStore.load(context).first { it.passage.reference == "Test.1.1" }.body)
    }

    @Test fun cancelNeverWritesDraft() {
        compose.setContent {
            var open by remember { mutableStateOf(true) }
            BibleDesktopTheme {
                if (open) NoteEditor("uk", passage, "", { open = false }, { open = false }) else Text("Closed")
            }
        }
        compose.onNodeWithTag("note-body").performTextInput("Чернетка")
        compose.onNodeWithText("Скасувати").performClick()
        compose.onNodeWithText("Closed").assertIsDisplayed()
        assertEquals(original, preferences.getString("verseNotesV1", null))
    }

    @Test fun deletionNeedsConfirmationAndCanBeCancelled() {
        runBlocking { NoteStore.save(context, passage, "Keep") }
        compose.setContent {
            var deleted by remember { mutableStateOf(false) }
            BibleDesktopTheme {
                if (!deleted) NoteCard("en", VerseNote(passage, "Keep", 1), {}, { deleted = true }) else Text("Deleted")
            }
        }
        compose.onNodeWithText("Delete note").performClick()
        compose.onNodeWithText("Delete this note?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals("Keep", NoteStore.load(context).first { it.passage.reference == "Test.1.1" }.body)
        compose.onNodeWithText("Delete note").performClick()
        compose.onAllNodes(hasText("Delete note")).onLast().performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Deleted")).fetchSemanticsNodes().isNotEmpty() }
        assertFalse(NoteStore.load(context).any { it.passage.reference == "Test.1.1" })
    }
}
