package com.bibledesktop.myapp.ui.study

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bibledesktop.myapp.R
import com.bibledesktop.myapp.ui.bible.*
import com.bibledesktop.myapp.ui.setup.localized
import com.bibledesktop.shared.api.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal val LocalTemporaryWindowAssignment=staticCompositionLocalOf<((BibleChapter,Int,Int)->Unit)?>{null}

/** Temporary reading has its own viewport and never writes the Bible reader's saved place. */
@Composable
internal fun TemporaryStudyPassage(language: String, code: String, targets: List<ReferenceTarget>, client: BibleContentSource, onClose: () -> Unit) {
    val labels = studyTexts(language)
    val windowLabels=temporaryWindowTexts(language)
    val assign=LocalTemporaryWindowAssignment.current
    var targetWindow by remember {mutableIntStateOf(-1)}
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var chapter by remember(code, targets) { mutableStateOf<BibleChapter?>(null) }
    var error by remember(code, targets) { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    var bookmarks by remember { mutableStateOf(BookmarkStore.load(context)) }
    var note by remember { mutableStateOf<BookmarkEntry?>(null) }
    var noteInitial by remember { mutableStateOf("") }
    LaunchedEffect(code, targets, retry) {
        chapter = null; error = false
        try {
            val target = targets.first()
            val location = client.getVerseLocations(code, listOf(target.osisRef)).firstOrNull() ?: error("Exact verse absent")
            val value = client.getChapter(code, location.book, location.chapter)
            require(value.translation.code == code && value.book.slug == location.book && value.chapter.number == location.chapter)
            require(value.verses.any { it.id == location.verseId && it.osisRef == target.osisRef && it.plainText.isNotBlank() })
            chapter = value
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = true }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BackHandler(onBack = onClose)
        Surface(Modifier.fillMaxSize().padding(8.dp), shape = MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(12.dp)) { Text(labels.temporary, Modifier.weight(1f)); TextButton(onClick = onClose) { Text(labels.back) } }
                if(assign!=null) Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                    FilterChip(selected=targetWindow == -1,onClick={targetWindow = -1},label={Text(windowLabels.separate)})
                    (0..1).forEach{id->FilterChip(selected=targetWindow == id,onClick={targetWindow=id},label={Text("${windowLabels.window} ${id+1}")})}
                    TextButton(enabled=chapter!=null&&targetWindow>=0,onClick={val value=chapter;val verse=value?.verses?.find{it.osisRef==targets.first().osisRef};if(value!=null&&verse!=null){assign(value,verse.number,targetWindow);onClose()}}){Text(labels.temporary)}
                }
                if (error) { Text(labels.missing, Modifier.padding(12.dp)); TextButton(onClick = { retry++ }) { Text(localized(R.string.retry, language)) } }
                else if (chapter == null) CircularProgressIndicator(Modifier.padding(12.dp))
                chapter?.let { value -> ChapterReadingContent(language, value, 19f, bookmarks.map { "${it.translationCode}:${it.reference}" }.toSet(),
                    onBookmark = { source, verse -> bookmarks = BookmarkStore.toggle(context, bookmarks, source, verse) },
                    onShare = { source, verse -> shareBiblePassage(context, versePassage(source, verse)) },
                    onNote = { source, verse -> val passage = versePassage(source, verse); scope.launch { try {
                        noteInitial = NoteStore.read(context).firstOrNull { it.passage.translationCode == passage.translationCode && it.passage.reference == passage.reference }?.body.orEmpty(); note = passage
                    } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { error = true } } },
                    modifier = Modifier.weight(1f), initialVerse = value.verses.first {it.osisRef==targets.first().osisRef}.number, client = client) }
            }
        }
    }
    note?.let { NoteEditor(language, it, noteInitial, onDismiss = { note = null }, onSaved = { note = null }) }
}
