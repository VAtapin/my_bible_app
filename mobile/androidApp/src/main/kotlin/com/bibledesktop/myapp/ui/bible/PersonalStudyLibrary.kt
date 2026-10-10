package com.bibledesktop.myapp.ui.bible

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bibledesktop.shared.api.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun PersonalStudyLibrary(language: String, onBack: () -> Unit, onOpen: (SavedPassage) -> Unit, onReminders: (() -> Unit)? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    fun t(key: String) = personalStudyText(language, key)
    var data by remember { mutableStateOf(PersonalStudy()) }
    var error by remember { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var collection by rememberSaveable { mutableStateOf("") }
    var shown by remember { mutableStateOf(emptySet<String>()) }
    var editing by remember { mutableStateOf<StudyBookmark?>(null) }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var draftCollection by remember { mutableStateOf("") }
    var draftColor by remember { mutableStateOf("yellow") }
    var editingWord by remember { mutableStateOf<WordMark?>(null) }
    var wordNote by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { try { data = PersonalStudyStore.read(context) } catch (_: Exception) { error = t("failed") } }
    BackHandler(onBack = onBack)
    fun update(transform: (PersonalStudy) -> PersonalStudy) { scope.launch { try { data = PersonalStudyStore.update(context, transform) } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { error = t("failed") } } }
    fun toggle(id: String) { shown = if (id in shown) shown - id else shown + id }
    fun move(bookmark: StudyBookmark, direction: Int) {
        update { current ->
            val entries = current.bookmarks.filter { it.collection == bookmark.collection }.sortedBy { it.order }.toMutableList()
            val index = entries.indexOfFirst { it.id == bookmark.id }; val next = index + direction
            if (index >= 0 && next in entries.indices) { val other = entries[next]; entries[next] = entries[index]; entries[index] = other }
            val orders = entries.mapIndexed { i, value -> value.id to i }.toMap()
            current.copy(bookmarks = current.bookmarks.map { if (it.id in orders) it.copy(order = orders.getValue(it.id)) else it })
        }
    }
    val entries = data.bookmarks.filter { (collection.isBlank() || it.collection == collection) && (it.title + " " + it.description).contains(query, ignoreCase = true) }.sortedWith(compareBy({ it.collection }, { it.order }))
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(t("library"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f)); TextButton(onClick = onBack) { Text(t("close")) } }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { OutlinedTextField(query, { query = it }, label = { Text(t("search")) }, modifier = Modifier.fillMaxWidth()); OutlinedTextField(collection, { collection = it }, label = { Text(t("collection")) }, supportingText = { Text(data.bookmarks.map { it.collection }.distinct().filter { it.isNotBlank() }.joinToString(" · ")) }, modifier = Modifier.fillMaxWidth()); if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error) }
            items(entries, key = { "bookmark:${it.id}" }) { bookmark ->
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = { onOpen(bookmark.passage) }) { Text(bookmark.title + " · " + bookmark.passage.reference) }
                    Text(bookmark.passage.translationName + " · " + bookmark.collection)
                    if (bookmark.description.isNotBlank()) Text(bookmark.description)
                    TextButton(onClick = { toggle(bookmark.id) }) { Text(t(if (bookmark.id in shown) "hide" else "show")) }
                    if (bookmark.id in shown) Text(formatPassage(bookmark.passage, reference = false, translation = false))
                    Row { TextButton(onClick = { editing = bookmark; title = bookmark.title; description = bookmark.description; draftCollection = bookmark.collection; draftColor = bookmark.color }) { Text(t("edit")) }; TextButton(onClick = { move(bookmark, -1) }) { Text("↑") }; TextButton(onClick = { move(bookmark, 1) }) { Text("↓") }; TextButton(onClick = { update { current -> current.copy(bookmarks = current.bookmarks.filterNot { it.id == bookmark.id }) } }) { Text(t("remove")) } }
                } }
            }
            if (entries.isEmpty()) item { Text(t("empty")) }
            item { Text(t("memory"), style = MaterialTheme.typography.titleLarge) }
            items(data.cards, key = { "memory:${it.id}" }) { card -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                TextButton(onClick = { onOpen(card.passage) }) { Text(card.passage.reference + " · " + card.passage.translationName) }
                TextButton(onClick = { toggle(card.id) }) { Text(t(if (card.id in shown) "hide" else "show")) }
                if (card.id in shown) Text(formatPassage(card.passage, reference = false, translation = false, numbers = false))
                StudyCheck(t(if (card.learned) "learned" else "learning"), card.learned) { learned -> update { current -> current.copy(cards = current.cards.map { if (it.id == card.id) it.copy(learned = learned) else it }) } }
                TextButton(onClick = { update { current -> current.copy(cards = current.cards.filterNot { it.id == card.id }) } }) { Text(t("remove")) }
            } } }
            if (onReminders != null) item { TextButton(onClick = onReminders) { Text(t("reminder")) } }
            item { Text(t("wordNotes"), style = MaterialTheme.typography.titleLarge) }
            items(data.marks.filter { it.note.isNotBlank() }, key = { "note:${it.id}" }) { mark -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) { Text("${mark.osis} · ${mark.code} · ${mark.quote}"); Text(mark.note); TextButton(onClick = { editingWord=mark;wordNote=mark.note }){Text(t("edit"))}; TextButton(onClick = { update { current -> current.copy(marks = current.marks.filterNot { it.id == mark.id }) } }) { Text(t("remove")) } } } }
            item { Text(t("palette"), style = MaterialTheme.typography.titleLarge) }
            items(data.palette.keys.toList(), key = { "palette:$it" }) { name ->
                val colors = data.palette.getValue(name)
                var day by remember(colors.day) { mutableStateOf(colors.day) }
                var night by remember(colors.night) { mutableStateOf(colors.night) }
                Column { Text(personalColorName(language,name)); OutlinedTextField(day, { day = it }, label = { Text(t("day") + " #RRGGBB") }, modifier = Modifier.fillMaxWidth()); OutlinedTextField(night, { night = it }, label = { Text(t("night") + " #RRGGBB") }, modifier = Modifier.fillMaxWidth()); TextButton(onClick = { update { current -> current.copy(palette = current.palette + (name to StudyColor(day, night))) } }, enabled = Regex("#[0-9a-fA-F]{6}").matches(day) && Regex("#[0-9a-fA-F]{6}").matches(night)) { Text(t("save")) } }
            }
        }
    }
    editing?.let { bookmark -> AlertDialog(onDismissRequest = { editing = null }, title = { Text(t("edit")) }, text = { Column { OutlinedTextField(title, { if (it.length <= 200) title = it }, label = { Text(t("heading")) }); OutlinedTextField(description, { if (it.length <= 10_000) description = it }, label = { Text(t("description")) }); OutlinedTextField(draftCollection, { if (it.length <= 100) draftCollection = it }, label = { Text(t("collection")) }); PersonalColorPicker(data, draftColor, language) { draftColor = it } } }, confirmButton = { TextButton(onClick = { update { current -> current.copy(bookmarks = current.bookmarks.map { if (it.id == bookmark.id) it.copy(title = title.trim(), description = description.trim(), collection = draftCollection.trim(), color = draftColor, order = if (it.collection == draftCollection.trim()) it.order else current.bookmarks.count { b -> b.collection == draftCollection.trim() }) else it }) }; editing = null }, enabled = title.isNotBlank()) { Text(t("save")) } }, dismissButton = { TextButton(onClick = { editing = null }) { Text(t("close")) } }) }
    editingWord?.let { mark -> AlertDialog(onDismissRequest={editingWord=null},title={Text(t("note"))},text={Column{Text(mark.quote);OutlinedTextField(wordNote,{if(it.length<=10_000)wordNote=it},modifier=Modifier.fillMaxWidth())}},confirmButton={TextButton(onClick={update{current->current.copy(marks=current.marks.map{if(it.id==mark.id)it.copy(note=wordNote.trim())else it})};editingWord=null}){Text(t("save"))}},dismissButton={TextButton(onClick={editingWord=null}){Text(t("close"))}}) }
}
