package com.bibledesktop.myapp.ui.study

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bibledesktop.myapp.R
import com.bibledesktop.myapp.ui.setup.localized
import com.bibledesktop.myapp.ui.theme.*
import com.bibledesktop.shared.api.*
import kotlinx.coroutines.CancellationException

@Composable
internal fun CommentaryPanel(language: String, chapter: BibleChapter, verse: BibleVerse, client: BibleContentSource, visibleLast: Int = verse.number, preferencesName: String = "bible-desktop-study") {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE) }
    var sources by remember { mutableStateOf(preferences.getStringSet("sources", emptySet()).orEmpty().toList().take(30)) }
    var modules by remember { mutableStateOf<List<CommentaryModule>>(emptyList()) }
    var choose by remember { mutableStateOf(false) }
    var sourceQuery by rememberSaveable { mutableStateOf("") }
    var mode by rememberSaveable { mutableIntStateOf(2) }
    var first by rememberSaveable(verse.id) { mutableStateOf(verse.number.toString()) }
    var last by rememberSaveable(verse.id) { mutableStateOf(verse.number.toString()) }
    var page by remember { mutableStateOf<CommentaryPage?>(null) }
    var entries by remember { mutableStateOf<List<CommentaryEntry>>(emptyList()) }
    var offset by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    var expanded by remember { mutableStateOf(setOf<Long>()) }
    val identity = "${chapter.translation.code}:${chapter.book.slug}:${chapter.chapter.number}:${mode == 0}:${sources.sorted().joinToString(",")}:$retry"
    LaunchedEffect(identity) { offset = 0; entries = emptyList(); page = null; expanded = emptySet() }
    LaunchedEffect(identity, offset) {
        loading = true; failed = false
        try {
            modules = client.getCommentaryModules()
            if (sources.isNotEmpty()) {
                val canon = chapter.translation.canonCode ?: client.getTranslations().firstOrNull { it.code == chapter.translation.code }?.canonCode
                    ?: error("Canonical identity unavailable")
                val osis = chapter.verses.firstOrNull()?.osisRef?.substringBefore('.') ?: error("Canonical identity unavailable")
                val slug = client.getCanonicalSlug(canon, osis)
                val data = client.getCommentaries(slug, if (mode == 0) null else chapter.chapter.number, sources, offset)
                page = data
                entries = if (offset == 0) data.entries else (entries + data.entries).distinctBy { it.id }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
        finally { loading = false }
    }
    val start = if (mode == 2 || mode == 4) verse.number else first.toIntOrNull()
    val end = if (mode == 2) verse.number else if (mode == 4) visibleLast else last.toIntOrNull()
    val valid = mode < 2 || (start != null && end != null && start <= end && chapter.verses.any { it.number == start } && chapter.verses.any { it.number == end })
    Column(Modifier.fillMaxWidth().testTag("commentary-panel"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(localized(R.string.study_commentaries, language), style = MaterialTheme.typography.titleLarge)
        TextButton(onClick = { choose = !choose }) { Text(localized(R.string.study_sources, language)) }
        if (choose) {
            Text(localized(R.string.study_choose_sources, language))
            OutlinedTextField(sourceQuery, { sourceQuery = it }, label = { Text(localized(R.string.study_sources, language)) }, singleLine = true)
            Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
            modules.filter { it.name.contains(sourceQuery.trim(), ignoreCase = true) || it.shortName.orEmpty().contains(sourceQuery.trim(), ignoreCase = true) }.forEach { source -> Row(Modifier.fillMaxWidth()) {
                Checkbox(checked = source.code in sources, enabled = source.code in sources || sources.size < 30, modifier = Modifier.semantics { contentDescription = source.name }, onCheckedChange = { checked ->
                    sources = if (checked) sources + source.code else sources - source.code
                    preferences.edit().putStringSet("sources", sources.toSet()).apply()
                })
                Text(source.name, Modifier.padding(top = 12.dp).weight(1f))
            } }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(R.string.study_scope_book, R.string.study_scope_chapter, R.string.study_scope_verse, R.string.study_scope_range, R.string.study_scope_visible).forEachIndexed { index, label ->
                FilterChip(selected = mode == index, onClick = { mode = index }, label = { Text(localized(label, language)) })
            }
        }
        Text("${chapter.book.name} ${chapter.chapter.number}" + if (mode >= 2) ":${start ?: "?"}" + if (end != start) "–${end ?: "?"}" else "" else "")
        if (mode == 3) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(first, { first = it.take(4) }, Modifier.weight(1f), label = { Text(localized(R.string.study_first_verse, language)) }, singleLine = true)
            OutlinedTextField(last, { last = it.take(4) }, Modifier.weight(1f), label = { Text(localized(R.string.study_last_verse, language)) }, singleLine = true)
        }
        if (!valid) Text(localized(R.string.study_invalid_range, language))
        if (sources.isEmpty()) Text(localized(R.string.study_choose_sources, language))
        if (loading) CircularProgressIndicator(Modifier.size(24.dp))
        if (failed) { Text(localized(R.string.study_material_error, language)); TextButton(onClick = { retry++ }) { Text(localized(R.string.retry, language)) } }
        val filtered = if (mode < 2) entries else entries.filter { valid && commentaryOverlaps(it, chapter.chapter.number, start!!, end!!) }
        if (valid && !loading && !failed && sources.isNotEmpty() && filtered.isEmpty() && entries.size >= (page?.total ?: 0)) Text(localized(R.string.study_material_empty, language))
        filtered.forEach { entry -> Card(Modifier.fillMaxWidth().testTag("commentary-${entry.id}")) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(entry.title ?: localized(R.string.study_section, language), style = MaterialTheme.typography.titleMedium)
                Text(listOfNotNull(entry.author, entry.moduleName).joinToString(" · "), color = PrimaryBlue)
                if (entry.chapterFrom > 0) Text("${chapter.book.name} ${entry.chapterFrom}" + (if (entry.verseFrom > 0) ":${entry.verseFrom}" else "") +
                    (if (entry.chapterTo != null || entry.verseTo != null) "–${entry.chapterTo ?: entry.chapterFrom}" + (entry.verseTo?.let { ":$it" } ?: "") else ""))
                SelectionContainer { Text(studyReadingText(entry.body).let { if (entry.id in expanded) it else it.take(220) + "…" }, fontSize = 18.sp, lineHeight = 27.sp) }
                TextButton(onClick = { expanded = if (entry.id in expanded) expanded - entry.id else expanded + entry.id }) {
                    Text(localized(if (entry.id in expanded) R.string.study_close else R.string.study_full, language))
                }
            }
        } }
        if (entries.size < (page?.total ?: 0)) TextButton(onClick = { offset += 10 }, enabled = !loading) { Text("${localized(R.string.study_more, language)} (${entries.size}/${page?.total})") }
        TextButton(onClick = { retry++ }, enabled = !loading) { Text(localized(R.string.retry, language)) }
    }
}
