package com.bibledesktop.myapp.ui.bible

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.bibledesktop.myapp.R
import com.bibledesktop.myapp.ui.setup.localized
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun NoteEditor(language: String, passage: BookmarkEntry, initial: String, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var body by rememberSaveable(passage.reference, passage.translationCode) { mutableStateOf(initial) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(localized(R.string.note_edit, language)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${passage.bookName} ${passage.chapter}:${passage.verse} · ${passage.translationName}")
                OutlinedTextField(value = body, onValueChange = { if (it.length <= 20_000) body = it },
                    enabled = !saving, minLines = 4, maxLines = 8,
                    label = { Text(localized(R.string.note_body, language)) },
                    modifier = Modifier.fillMaxWidth().testTag("note-body"))
                if (error) Text(localized(R.string.notes_error, language), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(enabled = body.isNotBlank() && !saving, onClick = {
                saving = true
                error = false
                scope.launch {
                    try { NoteStore.save(context, passage, body); onSaved() }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { error = true }
                    finally { saving = false }
                }
            }) { Text(localized(R.string.note_save, language)) }
        },
        dismissButton = {
            TextButton(enabled = !saving, onClick = onDismiss) { Text(localized(R.string.note_cancel, language)) }
        },
    )
}
