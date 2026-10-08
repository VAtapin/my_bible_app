package com.bibledesktop.myapp.ui.study

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bibledesktop.myapp.R
import com.bibledesktop.myapp.ui.bible.*
import com.bibledesktop.myapp.ui.reading.*
import com.bibledesktop.myapp.ui.setup.localized
import com.bibledesktop.myapp.ui.theme.*
import com.bibledesktop.shared.api.*
import kotlinx.coroutines.*

@Composable
internal fun StudyScreen(language: String, client: BibleContentSource, onBack: () -> Unit, onBible: () -> Unit, onOpen: (BookmarkEntry) -> Unit) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("bible-desktop-native-profile", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    var chapter by remember { mutableStateOf<BibleChapter?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }
    var openError by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf<Result<List<VerseNote>>?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var selectedVerse by rememberSaveable { mutableIntStateOf(preferences.getInt("lastVerse", 0)) }
    var chooseVerse by remember { mutableStateOf(false) }
    LaunchedEffect(retry) {
        notes = runCatching { NoteStore.read(context) }
        loading = true; error = false
        val code = preferences.getString("lastTranslation", null)
        val book = preferences.getString("lastBookSlug", null)
        val number = preferences.getInt("lastChapter", 0)
        if (code != null && book != null && number > 0) try { chapter = client.getChapter(code, book, number) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = true }
        loading = false
    }
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(Cream).statusBarsPadding().navigationBarsPadding()) {
        ReadingHeader(localized(R.string.section_study, language), language, onBack, onBack)
        ReadingViewport(Modifier.weight(1f)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(localized(R.string.study_hint, language), color = PrimaryBlue)
                OutlinedButton(onClick = onBible) { Text(localized(R.string.study_choose_passage, language)) }
                if (loading) CircularProgressIndicator()
                if (error) StudyError(language) { retry++ }
                chapter?.let { value ->
                    val verse = value.verses.firstOrNull { it.number == selectedVerse } ?: value.verses.firstOrNull()
                    if (verse != null) {
                        Box {
                            Button(onClick = { chooseVerse = true }) { Text("${value.book.name} ${value.chapter.number}:${verse.number} ▾") }
                            DropdownMenu(expanded = chooseVerse, onDismissRequest = { chooseVerse = false }, modifier = Modifier.heightIn(max = 320.dp)) {
                                value.verses.forEach { item -> DropdownMenuItem(text = { Text("${value.book.name} ${value.chapter.number}:${item.number}") }, onClick = { selectedVerse = item.number; chooseVerse = false }) }
                            }
                        }
                        VerseStudy(language, value, verse, client) { target ->
                            scope.launch {
                                try {
                                    openError = false
                                    val book = client.getBooks(value.translation.code).firstOrNull { it.canonicalBook?.osisCode == target.osisRef.substringBefore('.') }
                                        ?: kotlin.error("No canonical book in this translation")
                                    onOpen(BookmarkEntry(reference = target.osisRef, text = target.text.orEmpty(), bookName = book.name,
                                        bookSlug = book.slug, chapter = target.chapterNumber, verse = target.verseNumber,
                                        translationCode = value.translation.code, translationName = value.translation.name))
                                } catch (cancelled: CancellationException) { throw cancelled }
                                catch (_: Exception) { openError = true }
                            }
                        }
                        if (openError) Text(localized(R.string.study_open_error, language))
                    }
                }
                Text(localized(R.string.notes_title, language), style = MaterialTheme.typography.titleLarge)
                if (notes == null) CircularProgressIndicator()
                else if (notes!!.isFailure) Text(localized(R.string.notes_error, language))
                else if (notes!!.getOrThrow().isEmpty()) Text(localized(R.string.notes_empty, language))
                notes?.getOrNull()?.forEach { note ->
                    NoteCard(language, note, onOpen = { onOpen(note.passage) }, onChanged = { scope.launch { notes = runCatching { NoteStore.read(context) } } })
                }
            }
        }
    }
}
