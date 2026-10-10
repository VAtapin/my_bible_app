package com.bibledesktop.myapp

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.platform.app.InstrumentationRegistry
import android.content.Context
import kotlinx.coroutines.runBlocking
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.bibledesktop.myapp.ui.bible.*
import com.bibledesktop.myapp.ui.theme.BibleDesktopTheme
import com.bibledesktop.shared.api.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PersonalStudyUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val verse = BibleVerse(1, 1, "Gen.1.1", "В начале H7225 сотворил H1254 H853 Бог H430 небо H8064 и H853 землю H776.", "В начале сотворил Бог небо и землю.", true)
    private val chapter = BibleChapter(TranslationSummary("RST", "Russian", language = LanguageSummary("ru", "Русский")), BibleBook("gen", "Бытие", chaptersCount = 50), ChapterSummary(1,1), listOf(verse))
    @Test fun sourceStrongButtonsPreserveExactVerseAndHideWithoutLosingMarkup() {
        var visible by mutableStateOf(true)
        var selected = ""
        compose.setContent { BibleDesktopTheme { CompositionLocalProvider(LocalReaderPreferences provides ReaderPreferences(strongNumbers = visible)) {
            ChapterReadingContent("ru", chapter, 18f, emptySet(), {_,_->},{_,_->},{_,_->}, onStrong = { source, target, number -> selected = "${source.translation.code}:${target.osisRef}:$number" })
        } } }
        compose.onNodeWithText("H430").performClick()
        compose.runOnIdle { assertEquals("RST:Gen.1.1:H430", selected); visible = false }
        compose.onNodeWithText("H430").assertDoesNotExist()
        compose.runOnIdle { assertTrue(verse.hasStrongMarkup); assertTrue(verse.text.contains("H430")) }
    }
    @Test fun rangeSelectionIsVisibleOnlyInItsSavedEdition() {
        val passage = SavedPassage("RST", "Russian", "gen", "Бытие", PassagePoint(1,1),PassagePoint(1,1),listOf(PassageVerse(1,1,verse.osisRef,verse.plainText)))
        var source by mutableStateOf(chapter)
        compose.setContent { BibleDesktopTheme { ChapterReadingContent("ru", source,18f,emptySet(),{_,_->},{_,_->},{_,_->},selection=passage) } }
        compose.onNodeWithTag("verse-1").assertIsSelected()
        compose.runOnIdle { source = chapter.copy(translation=chapter.translation.copy(code="DE")) }
        compose.onNodeWithTag("verse-1").assertIsNotSelected()
    }
    @Test fun nearWordNoteIsAccessibleAndEditsItsExactPersistedAnchor() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val preferences=context.getSharedPreferences("bible-desktop-native-profile",Context.MODE_PRIVATE)
        val original=preferences.getString("personalStudyV1",null)
        val mark=WordMark("ui-note","RST",verse.osisRef,verse.plainText,0,1,"В","green",true,"Original note")
        try {
            runBlocking{PersonalStudyStore.update(context){it.copy(marks=it.marks.filter{m->m.id!=mark.id}+mark)}}
            compose.setContent{BibleDesktopTheme{ChapterReadingContent("ru",chapter,18f,emptySet(),{_,_->},{_,_->},{_,_->})}}
            compose.waitUntil(10000){compose.onAllNodes(hasText("В✎ начале",substring=true)).fetchSemanticsNodes().isNotEmpty()}
            val node=compose.onNode(hasText("В✎ начале",substring=true)).fetchSemanticsNode()
            val action=node.config[SemanticsActions.CustomActions].first{it.label.endsWith(": В")}
            compose.runOnIdle{assertTrue(action.action())}
            compose.onNodeWithText("Original note").assertIsDisplayed()
            compose.onNodeWithTag("word-note-save").performClick()
            compose.onNodeWithTag("word-note-editor").performTextReplacement("Edited near word")
            compose.onNodeWithTag("word-note-save").performClick()
            compose.waitUntil(10000){runBlocking{PersonalStudyStore.read(context)}.marks.first{it.id==mark.id}.note=="Edited near word"}
            val saved=runBlocking{PersonalStudyStore.read(context)}.marks.first{it.id==mark.id}
            assertEquals(mark.copy(note="Edited near word"),saved)
        } finally {val editor=preferences.edit();if(original==null)editor.remove("personalStudyV1")else editor.putString("personalStudyV1",original);check(editor.commit());PersonalStudyStore.changes.value+=1}
    }

    @Test fun positionedStrongUsesActualSourceOffsetAndCallbackEditionIdentity() {
        val actual=verse.copy(text="В начале H7225",annotations=SourceAnnotations("available",source=AnnotationSource("mybible","a".repeat(64)),strongTokens=listOf(SourceStrongAnnotation("H7225",7,8)),features=mapOf("headings" to "absent","footnotes" to "absent","added_words" to "absent","paragraphs" to "absent")))
        val source=chapter.copy(translation=chapter.translation.copy(code="POSITIONED"),verses=listOf(actual));var selected=""
        compose.setContent{BibleDesktopTheme{CompositionLocalProvider(LocalReaderPreferences provides ReaderPreferences(strongNumbers=true)){
            ChapterReadingContent("ru",source,18f,emptySet(),{_,_->},{_,_->},{_,_->},onStrong={c,v,n->selected="${c.translation.code}:${v.id}:$n"})
        }}}
        // Positioned annotations render once inside the actual source text; the raw fallback is suppressed.
        compose.onAllNodes(hasText("H7225",substring=true)).assertCountEquals(1)
        val node=compose.onNode(hasText("H7225",substring=true)).fetchSemanticsNode()
        assertTrue(node.config[SemanticsProperties.Text].first().text.contains("В началеH7225"))
        val action=node.config[SemanticsActions.CustomActions].first{it.label=="H7225"}
        compose.runOnIdle{assertTrue(action.action());assertEquals("POSITIONED:1:H7225",selected)}
    }

    @Test fun paletteLabelsAreLocalizedAndSelectionKeepsStorageKeys() {
        var selected = ""
        val data=PersonalStudy(palette=PersonalStudy().palette + ("Мои пометки" to StudyColor("#112233","#445566")))
        compose.setContent { BibleDesktopTheme { PersonalColorPicker(data,"yellow","ru") {selected=it} } }
        compose.onNodeWithText("Жёлтый").assertExists()
        compose.onNodeWithText("Зелёный").performClick()
        compose.runOnIdle {assertEquals("green",selected)}
        compose.onNodeWithText("Мои пометки").performClick()
        compose.runOnIdle {assertEquals("Мои пометки",selected)}
        assertEquals("Blau",personalColorName("de","blue"))
        assertEquals("Рожевий",personalColorName("uk","pink"))
    }
}
