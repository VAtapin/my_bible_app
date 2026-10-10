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
import androidx.compose.ui.text.TextStyle
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
    val rulesText = commentaryRuleTexts(language)
    var rangeRules by remember { mutableStateOf(readCommentarySourceRules(preferences)) }
    var canonicalBounds by remember { mutableStateOf<Pair<StudyPosition,StudyPosition>?>(null) }
    val ruleKey = "sources-book:${chapter.verses.firstOrNull()?.osisRef?.substringBefore('.')}"
    var bookRule by remember(ruleKey) { mutableStateOf(preferences.contains(ruleKey)) }
    var sources by remember { mutableStateOf(preferences.getStringSet("sources", emptySet()).orEmpty().toList().take(30)) }
    LaunchedEffect(ruleKey) { sources = preferences.getStringSet(if(bookRule) ruleKey else "sources", emptySet()).orEmpty().toList().take(30) }
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
    var canonicalLoaded by remember { mutableStateOf(false) }
    val canonicalChapter = canonicalStudyPosition(verse.osisRef).chapter
    val rangeFirst = studyPosition(first, chapter.chapter.number)
    val rangeLast = studyPosition(last, chapter.chapter.number)
    val rangeValid = rangeFirst != null && rangeLast != null && rangeFirst <= rangeLast && rangeLast.chapter <= chapter.book.chaptersCount && listOf(rangeFirst,rangeLast).all { p -> p.chapter != chapter.chapter.number || chapter.verses.any { it.number == p.verse } }
    val identity = "$mode:${verse.number}:$visibleLast:$rangeRules:${chapter.translation.code}:${chapter.book.slug}:${chapter.chapter.number}:$canonicalChapter:${mode == 0}:${sources.sorted().joinToString(",")}:$retry:${if(mode==3) "$first:$last" else ""}:${if(chapter.verses.map{canonicalStudyPosition(it.osisRef).chapter}.distinct().size>1) "$mode:${verse.number}:$visibleLast" else ""}"
    LaunchedEffect(identity) { offset = 0; entries = emptyList(); page = null; expanded = emptySet(); canonicalLoaded = false; canonicalBounds=null }
    LaunchedEffect(identity, offset) {
        loading = true; failed = false
        try {
            modules = client.getCommentaryModules()
            if (sources.isNotEmpty() || mode!=0 && rangeRules.any{it.book==verse.osisRef.substringBefore('.')}) {
                val canon = chapter.translation.canonCode ?: client.getTranslations().firstOrNull { it.code == chapter.translation.code }?.canonCode
                    ?: error("Canonical identity unavailable")
                val osis = chapter.verses.firstOrNull()?.osisRef?.substringBefore('.') ?: error("Canonical identity unavailable")
                val slug = client.getCanonicalSlug(canon, osis)
                if(mode == 3) {
                    if(!rangeValid) return@LaunchedEffect
                    val mapped=listOf(rangeFirst!!,rangeLast!!).map { position -> val target = if(position.chapter == chapter.chapter.number) chapter else client.getChapter(chapter.translation.code, chapter.book.slug, position.chapter); val selected=target.verses.firstOrNull { it.number == position.verse } ?: error("Missing range endpoint");canonicalStudyPosition(selected.osisRef) }
                    require(mapped.first()<=mapped.last())
                    entries = loadRuleCommentaries(osis,mapped.first(),mapped.last(),sources,rangeRules) { number, pageOffset,selected -> client.getCommentaries(slug, number, selected, pageOffset) }
                    canonicalBounds=mapped.first() to mapped.last()
                    page = CommentaryPage(slug, null, entries, entries.size)
                    canonicalLoaded = true
                    return@LaunchedEffect
                }
                val represented=chapter.verses.filter { mode==1||it.number in verse.number..(if(mode==4)visibleLast else verse.number) }.map { canonicalStudyPosition(it.osisRef) }.sorted()
                if(mode!=0 && (represented.map { it.chapter }.distinct().size>1||rangeRules.any{it.book==osis})){entries=loadRuleCommentaries(osis,represented.first(),represented.last(),sources,rangeRules){number,pageOffset,selected->client.getCommentaries(slug,number,selected,pageOffset)};canonicalBounds=represented.first() to represented.last();page=CommentaryPage(slug,null,entries,entries.size);canonicalLoaded=true;return@LaunchedEffect}
                val data = client.getCommentaries(slug, if (mode == 0) null else canonicalChapter, sources, offset)
                page = data
                entries = if (offset == 0) data.entries else (entries + data.entries).distinctBy { it.id }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
        finally { loading = false }
    }
    val start = if (mode == 2 || mode == 4) verse.number else first.toIntOrNull()
    val end = if (mode == 2) verse.number else if (mode == 4) visibleLast else last.toIntOrNull()
    val valid = mode < 2 || if(mode == 3) rangeValid else (start != null && end != null && start <= end && chapter.verses.any { it.number == start } && chapter.verses.any { it.number == end })
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
                    preferences.edit().putStringSet(if(bookRule) ruleKey else "sources", sources.toSet()).apply()
                })
                Text(studySourceName(source.name), Modifier.padding(top = 12.dp).weight(1f))
            } }
            }
        }
        if(bookRule) Text(rulesText.book)
        Row { TextButton(onClick = { bookRule = true; preferences.edit().putStringSet(ruleKey, sources.toSet()).apply() }) { Text(rulesText.save) }; if(bookRule) TextButton(onClick = { preferences.edit().remove(ruleKey).apply(); bookRule = false; sources = preferences.getStringSet("sources", emptySet()).orEmpty().toList().take(30) }) { Text(rulesText.general) } }
        if(mode!=0)TextButton(enabled=valid&&!loading&&sources.isNotEmpty()&&(mode!=3||canonicalBounds!=null),onClick={
            val represented=chapter.verses.filter{mode==1||it.number in (start?:verse.number)..(end?:verse.number)}.map{canonicalStudyPosition(it.osisRef)}.sorted()
            val interval=canonicalBounds?:represented.takeIf{it.isNotEmpty()}?.let{it.first() to it.last()}
            if(interval!=null){rangeRules=rangeRules+CommentarySourceRule(java.util.UUID.randomUUID().toString(),verse.osisRef.substringBefore('.'),interval.first,interval.second,sources.toList());writeCommentarySourceRules(preferences,rangeRules)}
        }){Text(rulesText.saveRange)}
        var showRanges by remember { mutableStateOf(false) }
        if(rangeRules.any{it.book==verse.osisRef.substringBefore('.')})TextButton(onClick={showRanges=!showRanges}){Text(rulesText.ranges)}
        if(showRanges)rangeRules.filter{it.book==verse.osisRef.substringBefore('.')}.forEach{rule->Column{
            Text("${rule.book} ${rule.first.chapter}:${rule.first.verse}–${rule.last.chapter}:${rule.last.verse} · ${rule.sources.joinToString { code->studySourceName(modules.firstOrNull{it.code==code}?.name?:code) }}")
            TextButton(onClick={rangeRules=rangeRules.filter{it.id!=rule.id};writeCommentarySourceRules(preferences,rangeRules)}){Text(rulesText.removeRange)}
        }}
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(R.string.study_scope_book, R.string.study_scope_chapter, R.string.study_scope_verse, R.string.study_scope_range, R.string.study_scope_visible).forEachIndexed { index, label ->
                FilterChip(selected = mode == index, onClick = { mode = index }, label = { Text(localized(label, language)) })
            }
        }
        if(mode == 3) Text("${chapter.book.name} ${rangeFirst?.chapter ?: "?"}:${rangeFirst?.verse ?: "?"}–${rangeLast?.chapter ?: "?"}:${rangeLast?.verse ?: "?"}")
        else Text("${chapter.book.name} ${chapter.chapter.number}" + if (mode >= 2) ":${start ?: "?"}" + if (end != start) "–${end ?: "?"}" else "" else "")
        if (mode == 3) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(first, { first = it.take(9) }, Modifier.weight(1f), label = { Text(localized(R.string.study_first_verse, language)) }, placeholder = { Text(rulesText.position) }, singleLine = true)
            OutlinedTextField(last, { last = it.take(9) }, Modifier.weight(1f), label = { Text(localized(R.string.study_last_verse, language)) }, placeholder = { Text(rulesText.position) }, singleLine = true)
        }
        if (!valid) Text(localized(R.string.study_invalid_range, language))
        if (sources.isEmpty()&&entries.isEmpty()) Text(localized(R.string.study_choose_sources, language))
        if (loading) CircularProgressIndicator(Modifier.size(24.dp))
        if (failed) { Text(localized(R.string.study_material_error, language)); TextButton(onClick = { retry++ }) { Text(localized(R.string.retry, language)) } }
        val filtered = if (mode < 2 || mode == 3 || canonicalLoaded) entries else entries.filter { valid && commentaryOverlaps(it, canonicalChapter, start!!, end!!) }
        if (valid && !loading && !failed && sources.isNotEmpty() && filtered.isEmpty() && entries.size >= (page?.total ?: 0)) Text(localized(R.string.study_material_empty, language))
        filtered.forEach { entry -> Card(Modifier.fillMaxWidth().testTag("commentary-${entry.id}")) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(studySourceName(entry.title ?: localized(R.string.study_section, language)), style = MaterialTheme.typography.titleMedium)
                Text(studySourceName(listOfNotNull(entry.author, entry.moduleName).joinToString(" · ")), color = PrimaryBlue)
                if (entry.chapterFrom > 0) Text("${chapter.book.name} ${entry.chapterFrom}" + (if (entry.verseFrom > 0) ":${entry.verseFrom}" else "") +
                    (if (entry.chapterTo != null || entry.verseTo != null) "–${entry.chapterTo ?: entry.chapterFrom}" + (entry.verseTo?.let { ":$it" } ?: "") else ""))
                if(entry.body.length>=largeStudyBodyThreshold)LargeStudyBody(entry.body,Modifier.fillMaxWidth().heightIn(max=440.dp),expanded=entry.id in expanded,
                    preferences=preferences,positionKey="commentary-body:${entry.moduleCode}:${entry.id}",style=TextStyle(fontSize=18.sp,lineHeight=27.sp))
                else SelectionContainer { Text(studyReadingText(entry.body).let { if (entry.id in expanded) it else it.take(220) + "…" }, fontSize = 18.sp, lineHeight = 27.sp) }
                if(entry.id in expanded)CommentaryAnnotationsContent(language,entry.annotations,client,chapter.translation.code,entry.moduleCode)
                TextButton(onClick = { expanded = if (entry.id in expanded) expanded - entry.id else expanded + entry.id }) {
                    Text(localized(if (entry.id in expanded) R.string.study_close else R.string.study_full, language))
                }
            }
        } }
        if (entries.size < (page?.total ?: 0)) TextButton(onClick = { offset += 10 }, enabled = !loading) { Text("${localized(R.string.study_more, language)} (${entries.size}/${page?.total})") }
        TextButton(onClick = { retry++ }, enabled = !loading) { Text(localized(R.string.retry, language)) }
    }
}
