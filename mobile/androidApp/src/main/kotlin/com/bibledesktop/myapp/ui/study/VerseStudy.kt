package com.bibledesktop.myapp.ui.study

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bibledesktop.myapp.R
import com.bibledesktop.myapp.ui.reading.readingText
import com.bibledesktop.myapp.ui.setup.localized
import com.bibledesktop.myapp.ui.theme.*
import com.bibledesktop.shared.api.*
import kotlinx.coroutines.CancellationException

@Composable
internal fun VerseStudyDialog(language: String, chapter: BibleChapter, verse: BibleVerse, client: BibleContentSource,
    onOpen: (ReferenceTarget) -> Unit, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.94f).heightIn(max = 720.dp), shape = MaterialTheme.shapes.large) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${chapter.book.name} ${chapter.chapter.number}:${verse.number}", style = MaterialTheme.typography.titleLarge)
                VerseStudy(language, chapter, verse, client, onOpen)
                TextButton(onClick = onClose) { Text(localized(R.string.study_close, language)) }
            }
        }
    }
}

@Composable
internal fun VerseStudy(language: String, chapter: BibleChapter, verse: BibleVerse, client: BibleContentSource,
    onOpen: (ReferenceTarget) -> Unit) {
    var references by remember(verse.id, chapter.translation.code) { mutableStateOf<CrossReferences?>(null) }
    var referenceError by remember(verse.id, chapter.translation.code) { mutableStateOf(false) }
    var tokens by remember(verse.id, chapter.translation.code) { mutableStateOf<StrongTokens?>(null) }
    var tokenError by remember(verse.id, chapter.translation.code) { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    val hasStrong = chapter.translation.hasStrong || verse.hasStrongMarkup
    LaunchedEffect(verse.id, chapter.translation.code, retry) {
        references = null; referenceError = false
        try { references = client.getCrossReferences(verse.id, chapter.translation.code) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { referenceError = true }
    }
    LaunchedEffect(verse.id, chapter.translation.code, retry, hasStrong) {
        tokens = null; tokenError = false
        if (hasStrong) try { tokens = client.getStrongTokens(verse.id, chapter.translation.code) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { tokenError = true }
    }
    Column(Modifier.fillMaxWidth().testTag("verse-study"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SelectionContainer { Text(verse.plainText, fontFamily = readingFont(chapter.translation.language.code), fontSize = 19.sp, lineHeight = 29.sp) }
        Text(chapter.translation.name, color = PrimaryBlue)
        Text(localized(R.string.study_references, language), style = MaterialTheme.typography.titleMedium)
        if (referenceError) StudyError(language) { retry++ }
        else if (references == null) CircularProgressIndicator(Modifier.size(24.dp))
        else if (references!!.references.isEmpty()) Text(localized(R.string.study_no_references, language))
        references?.references?.distinctBy { it.target.osisRef }?.forEach { reference ->
            Card {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(reference.target.reference, style = MaterialTheme.typography.titleMedium)
                    SelectionContainer { Text(reference.target.text ?: localized(R.string.study_missing_text, language), fontFamily = readingFont(chapter.translation.language.code), fontSize = 18.sp, lineHeight = 27.sp) }
                    TextButton(onClick = { onOpen(reference.target) }, modifier = Modifier.testTag("reference-${reference.target.osisRef}")) {
                        Text(localized(R.string.bookmark_open, language))
                    }
                }
            }
        }
        Text(localized(R.string.study_strong, language), style = MaterialTheme.typography.titleMedium)
        if (!hasStrong) Text(localized(R.string.study_no_strong, language))
        else if (tokenError) StudyError(language) { retry++ }
        else if (tokens == null) CircularProgressIndicator(Modifier.size(24.dp))
        else if (tokens!!.tokens.isEmpty()) Text(localized(R.string.study_no_strong, language))
        else StrongWords(language, verse.id, tokens!!.tokens.map { it.number }.distinct(), client)
        Text(localized(R.string.study_offline_hint, language), color = PrimaryBlue, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun StrongWords(language: String, verseId: Long, numbers: List<String>, client: BibleContentSource) {
    var number by remember(verseId) { mutableStateOf<String?>(null) }
    var entry by remember(verseId, number) { mutableStateOf<StrongEntry?>(null) }
    var error by remember(verseId, number) { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(verseId, number, retry) {
        entry = null; error = false
        val selected = number ?: return@LaunchedEffect
        try { entry = client.getStrongEntry(selected, verseId) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = true }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        numbers.forEach { value -> FilterChip(selected = value == number, onClick = { number = value }, label = { Text(value) }) }
    }
    if (number != null) {
        if (error) StudyError(language) { retry++ }
        else if (entry == null) CircularProgressIndicator(Modifier.size(24.dp))
        else Card {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(listOfNotNull(entry!!.number, entry!!.word, entry!!.transliteration).joinToString(" · "), style = MaterialTheme.typography.titleMedium)
                Text("${entry!!.lexicon.name} · ${entry!!.lexicon.language.uppercase()}", color = PrimaryBlue)
                SelectionContainer { Text(readingText(entry!!.content.orEmpty()).ifBlank { localized(R.string.study_missing_text, language) }, fontSize = 18.sp, lineHeight = 27.sp) }
            }
        }
    }
}

@Composable
internal fun StudyError(language: String, retry: () -> Unit) {
    Text(localized(R.string.study_error, language))
    TextButton(onClick = retry) { Text(localized(R.string.retry, language)) }
}
