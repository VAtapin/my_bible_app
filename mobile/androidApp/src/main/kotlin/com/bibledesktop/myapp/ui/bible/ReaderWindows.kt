package com.bibledesktop.myapp.ui.bible

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.bibledesktop.myapp.R
import com.bibledesktop.myapp.ui.setup.localized
import com.bibledesktop.shared.api.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

internal data class WindowPosition(val code: String, val book: String, val chapter: Int, val verse: Int, val offset: Int = 0)
internal data class WindowCommands(val choose: (Boolean) -> Unit, val move: (Int) -> Unit)
private fun position(chapter: BibleChapter, verse: Int, offset: Int = 0) = WindowPosition(chapter.translation.code, chapter.book.slug, chapter.chapter.number, verse, offset)

/** Each pane keeps its own anchor; only the active pane drives canonical verse synchronization. */
@Composable
internal fun ReaderWindows(language: String, primary: BibleChapter, secondCode: String, catalog: List<TranslationSummary>,
    client: BibleContentSource, fontSize: Float, initialVerse: Int, modifier: Modifier,
    bookmarks: Set<String>, onBookmark: (BibleChapter, BibleVerse) -> Unit,
    onShare: (BibleChapter, BibleVerse) -> Unit, onNote: (BibleChapter, BibleVerse) -> Unit,
    onStudy: ((BibleChapter, BibleVerse) -> Unit)?, onVisible: (BibleChapter, BibleVerse, BibleVerse, Int) -> Unit,
    onCommands: (WindowCommands?) -> Unit, preferencesName: String = "bible-desktop-reader-windows",
    onPair: (BibleChapter, String, Int) -> Unit = { _, _, _ -> }) {
    val preferences = LocalContext.current.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    val saved = remember { runCatching {
        val array = JSONArray(preferences.getString("places", "[]"))
        require(array.length() == 2)
        (0..1).map { id -> array.getJSONObject(id).let { WindowPosition(it.getString("code"), it.getString("book"), it.getInt("chapter"), it.getInt("verse"), it.getInt("offset")) }
            .also { require(it.code.isNotBlank() && it.book.isNotBlank() && it.chapter > 0 && it.verse >= 0 && it.offset >= 0) } }
    }.getOrNull() }
    val places = remember { mutableStateListOf(*(saved ?: listOf(position(primary, initialVerse.coerceAtLeast(1)), position(primary, initialVerse.coerceAtLeast(1)).copy(code = secondCode.ifBlank { primary.translation.code }))).toTypedArray()) }
    var active by remember { mutableIntStateOf(preferences.getInt("active", 0).coerceIn(0, 1)) }
    var synchronized by remember { mutableStateOf(preferences.getBoolean("sync", true)) }
    var ratio by remember { mutableFloatStateOf(preferences.getFloat("ratio", .5f).takeIf { it.isFinite() }?.coerceIn(.2f, .8f) ?: .5f) }
    val open = remember { mutableStateListOf(preferences.getBoolean("open0", true), preferences.getBoolean("open1", true)).also { if (!it.any { value -> value }) it[0] = true } }
    val anchors = remember { mutableStateListOf<BibleChapter?>(null, null) }
    val current = remember { mutableStateListOf<BibleChapter?>(null, null) }
    val errors = remember { mutableStateListOf<Int?>(null, null) }
    val follows = remember { mutableStateListOf<Int?>(null, null) }
    val followRequests = remember { mutableStateListOf(0, 0) }
    var epoch by remember { mutableIntStateOf(0) }
    val versions = remember { intArrayOf(0, 0) }
    val scope = rememberCoroutineScope()
    var picker by remember { mutableStateOf<Pair<Int, Boolean>?>(null) }
    var digital by remember { mutableStateOf<Int?>(null) }
    var draft by remember { mutableStateOf("") }
    fun persist() {
        val array = JSONArray()
        places.forEach { p -> array.put(JSONObject().put("code", p.code).put("book", p.book).put("chapter", p.chapter).put("verse", p.verse).put("offset", p.offset)) }
        preferences.edit().putString("places", array.toString()).putInt("active", active).putBoolean("sync", synchronized)
            .putFloat("ratio", ratio).putBoolean("open0", open[0]).putBoolean("open1", open[1]).apply()
    }
    suspend fun load(id: Int, place: WindowPosition) {
        val version = ++versions[id]
        errors[id] = null
        try {
            val value = if (place.code == primary.translation.code && place.book == primary.book.slug && place.chapter == primary.chapter.number) primary
                else client.getChapter(place.code, place.book, place.chapter)
            require(value.translation.code == place.code && value.book.slug == place.book && value.chapter.number == place.chapter)
            require(value.verses.any { it.number == place.verse && it.plainText.isNotBlank() })
            if (versions[id] != version) return
            val old = anchors[id]
            follows[id] = if (old?.translation?.code == value.translation.code && old.book.slug == value.book.slug && old.chapter.number == value.chapter.number) place.verse else null
            places[id] = place; anchors[id] = value; current[id] = value; persist()
            followRequests[id]++
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { if (versions[id] == version) errors[id] = R.string.windows_error }
    }
    fun activate(id: Int) {
        if (active == id) return
        active = id; persist()
        val value = current[id]
        value?.verses?.find { it.number == places[id].verse }?.let { onVisible(value, it, it, places[id].offset) }
    }
    LaunchedEffect(Unit) {
        if (saved != null && places.none { it.code == primary.translation.code && it.book == primary.book.slug && it.chapter == primary.chapter.number }) places[active] = position(primary, initialVerse.coerceAtLeast(1))
        if (!open[active]) active = 1 - active
        for (id in 0..1) if (open[id]) {
            if (saved == null && id == 1 && places[id].code != primary.translation.code) {
                try { val other = loadComparison(primary, places[id].code, client); places[id] = position(other, initialVerse.coerceAtLeast(1)) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { errors[id] = R.string.compare_unavailable; continue }
            }
            load(id, places[id])
        }
    }
    val activeChapter = current[active]
    val activeVerse = activeChapter?.verses?.find { it.number == places[active].verse }
    LaunchedEffect(synchronized, active, activeVerse?.osisRef, places[1 - active].code, open.toList()) {
        if (!synchronized || !open.all { it } || activeChapter == null || activeVerse == null) return@LaunchedEffect
        val id = 1 - active
        val version = ++versions[id]
        try {
            val code = places[id].code
            val existing = anchors[id]
            val value = if (existing?.translation?.code == code && existing.verses.any { it.osisRef == activeVerse.osisRef }) existing
                else if (code == activeChapter.translation.code) activeChapter else loadComparison(activeChapter, code, client)
            val target = value.verses.find { it.osisRef == activeVerse.osisRef && it.plainText.isNotBlank() } ?: throw ComparisonUnavailable()
            if (versions[id] != version) return@LaunchedEffect
            follows[id] = if (anchors[id] == value) target.number else null
            places[id] = position(value, target.number); anchors[id] = value; current[id] = value; errors[id] = null; persist()
            followRequests[id]++
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { if (versions[id] == version) errors[id] = R.string.windows_missing }
    }
    DisposableEffect(Unit) { onDispose { onCommands(null) } }
    val chooseAction by rememberUpdatedState<(Boolean) -> Unit>({ picker = active to it })
    val moveAction by rememberUpdatedState<(Int) -> Unit>({ delta ->
        val id = active; val place = places[id]; val chapter = place.chapter + delta
        if (chapter in 1..(current[id]?.book?.chaptersCount ?: 0)) scope.launch { load(id, place.copy(chapter = chapter, verse = 1, offset = 0)) }
    })
    val commands = remember { WindowCommands({ chooseAction(it) }, { moveAction(it) }) }
    SideEffect { onCommands(commands) }
    if (activeChapter != null && activeVerse != null) SideEffect { onPair(activeChapter, places[1 - active].code, activeVerse.number) }
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton(onClick = { synchronized = !synchronized; persist() }, modifier = Modifier.testTag("windows-sync")) { Text(localized(if (synchronized) R.string.windows_sync else R.string.windows_independent, language)) }
            TextButton(onClick = {
                epoch++; follows[0] = null; follows[1] = null
                val p = places[0]; places[0] = places[1]; places[1] = p
                val a = anchors[0]; anchors[0] = anchors[1]; anchors[1] = a
                val c = current[0]; current[0] = current[1]; current[1] = c
                val f = follows[0]; follows[0] = follows[1]; follows[1] = f
                val e = errors[0]; errors[0] = errors[1]; errors[1] = e
                if (open[1 - active]) active = 1 - active; persist()
            }, modifier = Modifier.testTag("windows-swap")) { Text(localized(R.string.windows_swap, language)) }
            (0..1).filter { !open[it] }.forEach { id -> TextButton(onClick = { open[id] = true; scope.launch { load(id, places[id]) }; persist() }, modifier = Modifier.testTag("windows-reopen-$id")) { Text("${localized(R.string.windows_reopen, language)} ${id + 1}") } }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val total = constraints.maxHeight.toFloat().coerceAtLeast(1f)
            Column(Modifier.fillMaxSize()) {
                (0..1).filter { open[it] }.forEach { id ->
                    if (id == 1 && open.all { it }) {
                        Box(Modifier.fillMaxWidth().height(24.dp).background(MaterialTheme.colorScheme.secondaryContainer).testTag("windows-divider")
                            .pointerInput(Unit) { detectVerticalDragGestures { change, amount -> change.consume(); ratio = (ratio + amount / total).coerceIn(.2f, .8f); persist() } }) {
                            Slider(ratio, { ratio = it.coerceIn(.2f, .8f); persist() }, valueRange = .2f.. .8f, modifier = Modifier.fillMaxWidth().height(24.dp).testTag("windows-slider"))
                        }
                    }
                    Card(Modifier.weight(if (open.all { it }) if (id == 0) ratio else 1 - ratio else 1f).fillMaxWidth().testTag("comparison-pane-$id")
                        .pointerInput(id) { awaitPointerEventScope { while (true) { val event = awaitPointerEvent(); if (event.changes.any { it.pressed && !it.previousPressed }) activate(id) } } },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                            Text("${current[id]?.translation?.name ?: places[id].code} · ${current[id]?.book?.name.orEmpty()} ${places[id].chapter}:${places[id].verse}${if (active == id) " · ${localized(R.string.windows_active, language)}" else ""}", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                            TextButton(onClick = { activate(id); picker = id to true }) { Text(localized(R.string.windows_place, language)) }
                            TextButton(onClick = { activate(id); draft = "${places[id].chapter}:${places[id].verse}"; digital = id }, modifier = Modifier.testTag("windows-digital-$id")) { Text("#") }
                            if (open[1 - id]) TextButton(onClick = { open[id] = false; if (active == id) activate(1 - id); persist() }, modifier = Modifier.testTag("windows-close-$id")) { Text("×") }
                        }
                        errors[id]?.let { Text(localized(it, language), Modifier.padding(8.dp)) }
                        val anchor = anchors[id]
                        if (anchor == null) { if (errors[id] == null) CircularProgressIndicator(Modifier.padding(12.dp)) }
                        else key(epoch, anchor.translation.code, anchor.book.slug, anchor.chapter.number) {
                            ContinuousChapterContent(language, anchor, client, fontSize, bookmarks, onBookmark, onShare, onNote, onStudy,
                                places[id].verse, Modifier.weight(1f), onVisiblePlace = { value, first, last, offset ->
                                    current[id] = value; places[id] = position(value, first.number, offset); persist()
                                    if (active == id) onVisible(value, first, last, offset)
                                }, followVerse = follows[id], initialOffset = places[id].offset, listTag = "comparison-list-$id", followRequest = followRequests[id])
                        }
                    }
                }
            }
        }
    }
    picker?.let { (id, books) -> PassagePicker(language, catalog, client, places[id].code, current[id]?.book, places[id].chapter, books,
        onSelect = { code, book, chapter -> active = id; picker = null; scope.launch { load(id, WindowPosition(code, book.slug, chapter, 1)) } },
        onClose = { picker = null }, onHome = { picker = null }) }
    digital?.let { id -> AlertDialog(onDismissRequest = { digital = null }, title = { Text(localized(R.string.windows_place, language)) },
        text = { OutlinedTextField(draft, { draft = it }, singleLine = true, modifier = Modifier.testTag("windows-position"), label = { Text(localized(R.string.windows_position, language)) }) },
        confirmButton = { TextButton(onClick = {
            val parts = Regex("^(\\d+)(?::(\\d+))?$").matchEntire(draft.trim())
            val chapter = parts?.groupValues?.get(1)?.toIntOrNull(); val verse = parts?.groupValues?.get(2)?.takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 1
            if (chapter != null && chapter in 1..(current[id]?.book?.chaptersCount ?: 0) && verse > 0) { digital = null; scope.launch { load(id, places[id].copy(chapter = chapter, verse = verse, offset = 0)) } }
        }) { Text(localized(R.string.windows_go, language)) } }, dismissButton = { TextButton(onClick = { digital = null }) { Text(localized(R.string.note_cancel, language)) } }) }
}
