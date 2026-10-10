package com.bibledesktop.myapp.ui.bible

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bibledesktop.shared.api.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.UUID
import com.bibledesktop.myapp.ui.study.DictionaryLibrary
import com.bibledesktop.myapp.ui.study.dictionaryTexts
import com.bibledesktop.myapp.ui.study.GeoAtlasScreen
import com.bibledesktop.myapp.ui.study.geoTexts

@Composable
internal fun PersonalStudyPanel(language: String, chapter: BibleChapter, verse: BibleVerse, client: BibleContentSource,
    onClose: () -> Unit, onSelection: (SavedPassage) -> Unit = {}, onLibrary: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    fun t(key: String) = personalStudyText(language, key)
    var start by remember(chapter, verse.osisRef) { mutableStateOf("${chapter.chapter.number}:${verse.number}") }
    var end by remember(chapter, verse.osisRef) { mutableStateOf(start) }
    var passage by remember(chapter, verse.osisRef) { mutableStateOf<SavedPassage?>(null) }
    var data by remember { mutableStateOf(PersonalStudy()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var collection by remember { mutableStateOf("") }
    var color by remember { mutableStateOf("yellow") }
    var note by remember { mutableStateOf("") }
    var reference by remember { mutableStateOf(true) }
    var translation by remember { mutableStateOf(true) }
    var numbers by remember { mutableStateOf(true) }
    var selectedWords by remember(verse) { mutableStateOf(TextFieldValue(verse.plainText, TextRange.Zero)) }
    var marker by remember { mutableStateOf(false) }
    var dictionaryQuery by remember {mutableStateOf<String?>(null)}
    var geoQuery by remember {mutableStateOf<String?>(null)}
    val selectedRange=trimWordSelection(verse.plainText,selectedWords.selection.start,selectedWords.selection.end)
    fun updateWords(value:TextRange) {
        val range=trimWordSelection(verse.plainText,value.start,value.end)
        selectedWords=selectedWords.copy(selection=range?.let{TextRange(it.first,it.second)}?:TextRange.Zero)
        note=range?.let{selected->data.marks.find{it.matches(chapter.translation.code,verse)&&it.start==selected.first&&it.end==selected.second}?.note}.orEmpty()
    }
    val selectedDictionaryQuery=dictionarySelectionQuery(verse.plainText,selectedWords.selection.start,selectedWords.selection.end)
    LaunchedEffect(Unit) { try { data = PersonalStudyStore.read(context) } catch (_: Exception) { error = t("failed") } }
    suspend fun selection(): SavedPassage {
        return collectPassage(chapter, parsePassagePoint(start), parsePassagePoint(end)) { number -> client.getChapter(chapter.translation.code, chapter.book.slug, number) }.also { passage = it; onSelection(it) }
    }
    fun work(block: suspend () -> Unit) { scope.launch { busy = true; error = ""; status = ""; try { block() } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { error = t("invalid") } finally { busy = false } } }
    fun share(copy: Boolean) = work {
        val selected = passage ?: selection()
        val text = formatPassage(selected, reference, translation, numbers)
        if (copy) { (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(selected.reference, text)); status = t("saved") }
        else context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text); putExtra(Intent.EXTRA_SUBJECT, selected.reference) }, t("share")))
    }
    fun mark(kind: String) = work {
        val selected = requireNotNull(selectedRange)
        val from = selected.first; val to = selected.second
        data = PersonalStudyStore.update(context) { current ->
            val marks = eraseWordMarks(current.marks, chapter.translation.code, verse, from, to) { UUID.randomUUID().toString() }.toMutableList()
            if (kind != "erase") marks += WordMark(UUID.randomUUID().toString(), chapter.translation.code, verse.osisRef, verse.plainText, from, to, verse.plainText.substring(from, to), color, kind == "underline", note.trim())
            current.copy(marks = marks)
        }
        status = t("saved")
    }
    Dialog(onDismissRequest = { if (!busy) onClose() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(t("title"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f)); TextButton(onClick = onClose, enabled = !busy) { Text(t("close")) } }
                Text("${chapter.book.name} ${chapter.chapter.number}:${verse.number} · ${chapter.translation.name}")
                OutlinedTextField(start, { start = it; passage = null }, label = { Text(t("start")) }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(end, { end = it; passage = null }, label = { Text(t("end")) }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
                Button(onClick = { work { selection() } }, enabled = !busy) { Text(t("select")) }
                passage?.let { Text("${it.reference} · ${it.verses.size}") }
                StudyCheck(t("reference"), reference) { reference = it }; StudyCheck(t("translation"), translation) { translation = it }; StudyCheck(t("numbers"), numbers) { numbers = it }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton(onClick = { share(true) }, enabled = !busy) { Text(t("copy")) }; OutlinedButton(onClick = { share(false) }, enabled = !busy) { Text(t("share")) } }
                OutlinedTextField(title, { if (it.length <= 200) title = it }, label = { Text(t("heading")) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(description, { if (it.length <= 10_000) description = it }, label = { Text(t("description")) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(collection, { if (it.length <= 100) collection = it }, label = { Text(t("collection")) }, modifier = Modifier.fillMaxWidth())
                PersonalColorPicker(data, color, language) { color = it }
                Button(onClick = { work { val selected = passage ?: selection(); data = PersonalStudyStore.update(context) { current -> current.copy(bookmarks = current.bookmarks + StudyBookmark(UUID.randomUUID().toString(), selected, title.trim().ifBlank { selected.reference }, description.trim(), color, collection.trim(), current.bookmarks.count { it.collection == collection.trim() })) }; status = t("saved") } }, enabled = !busy) { Text(t("bookmark")) }
                Button(onClick = { work { val selected = passage ?: selection(); data = PersonalStudyStore.update(context) { it.copy(cards = it.cards + MemoryCard(UUID.randomUUID().toString(), selected)) }; status = t("saved") } }, enabled = !busy) { Text(t("memory")) }
                HorizontalDivider(); Text(t("word"), style = MaterialTheme.typography.titleMedium); Text(t("wordHint"))
                FilterChip(selected = marker, onClick = { marker = !marker }, label = { Text(t("highlight") + " ↔") })
                if (marker) WordMarkText(verse.plainText, true, selectedWords.selection) { selected -> updateWords(selected) }
                OutlinedTextField(selectedWords, { value -> updateWords(value.selection) }, readOnly = true, modifier = Modifier.fillMaxWidth())
                TextButton(onClick={dictionaryQuery=selectedDictionaryQuery},enabled=selectedDictionaryQuery!=null&&!busy) {Text(dictionaryTexts(language).title)}
                TextButton(onClick={geoQuery=selectedDictionaryQuery},enabled=selectedDictionaryQuery!=null&&!busy) {Text(geoTexts(language).title)}
                OutlinedTextField(note, { if (it.length <= 10_000) note = it }, label = { Text(t("note")) }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { TextButton(onClick = { mark("highlight") }, enabled = selectedRange != null && !busy) { Text(t("highlight")) }; TextButton(onClick = { mark("underline") }, enabled = selectedRange != null && !busy) { Text(t("underline")) } }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { TextButton(onClick = { mark("note") }, enabled = selectedRange != null && !busy) { Text(t("save")) }; TextButton(onClick = { mark("erase") }, enabled = selectedRange != null && !busy) { Text(t("erase")) } }
                if (data.marks.any { it.code == chapter.translation.code && it.osis == verse.osisRef && !it.matches(it.code, verse) }) Text(t("stale"))
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                if (status.isNotBlank()) Text(status)
                TextButton(onClick = onLibrary) { Text(t("library")) }
            }
        }
    }
    dictionaryQuery?.let{query->Dialog(onDismissRequest={dictionaryQuery=null},properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize()){DictionaryLibrary(language,client,chapter.translation.code,{dictionaryQuery=null},initialQuery=query)}
    }}
    geoQuery?.let{query->Dialog(onDismissRequest={geoQuery=null},properties=DialogProperties(usePlatformDefaultWidth=false)){Surface(Modifier.fillMaxSize()){GeoAtlasScreen(language,{geoQuery=null},references=listOf(verse.osisRef),initialQuery=query)}}}
}
@Composable internal fun StudyCheck(label: String, checked: Boolean, onChange: (Boolean) -> Unit) { Row { Checkbox(checked, onChange); Text(label, Modifier.padding(top = 12.dp)) } }
@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun PersonalColorPicker(data: PersonalStudy, color: String, language:String="en", onChange: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { data.palette.forEach { (name, value) ->
        FilterChip(selected = color == name, onClick = { onChange(name) }, label = { Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) { Text("●", color = androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(value.day))); Text(personalColorName(language,name)) } })
    } }
}
