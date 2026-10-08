package com.bibledesktop.myapp.ui.bible

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bibledesktop.myapp.R
import com.bibledesktop.myapp.ui.setup.localized
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun NoteCard(language: String, note: VerseNote, onOpen: () -> Unit, onChanged: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var editing by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val passage = note.passage
            Text("${passage.bookName} ${passage.chapter}:${passage.verse} · ${passage.translationName}")
            Text(note.body)
            if (error) Text(localized(R.string.notes_error, language), color = MaterialTheme.colorScheme.error)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { editing = true }) { Text(localized(R.string.note_edit, language)) }
                TextButton(onClick = { confirmDelete = true }) { Text(localized(R.string.note_delete, language)) }
                TextButton(onClick = onOpen) { Text(localized(R.string.bookmark_open, language)) }
            }
        }
    }
    if (editing) NoteEditor(language, note.passage, note.body, { editing = false }, { editing = false; onChanged() })
    if (confirmDelete) AlertDialog(
        onDismissRequest = { if (!deleting) confirmDelete = false },
        title = { Text(localized(R.string.note_delete_confirm, language)) },
        confirmButton = {
            TextButton(enabled = !deleting, onClick = {
                deleting = true
                scope.launch {
                    try { NoteStore.remove(context, note.passage); confirmDelete = false; onChanged() }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { error = true; confirmDelete = false }
                    finally { deleting = false }
                }
            }) { Text(localized(R.string.note_delete, language)) }
        },
        dismissButton = { TextButton(enabled = !deleting, onClick = { confirmDelete = false }) { Text(localized(R.string.note_cancel, language)) } },
    )
}
