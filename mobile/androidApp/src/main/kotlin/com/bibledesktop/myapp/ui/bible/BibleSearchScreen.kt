package com.bibledesktop.myapp.ui.bible

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.bibledesktop.myapp.R
import com.bibledesktop.myapp.data.*
import com.bibledesktop.myapp.ui.setup.localized
import com.bibledesktop.shared.api.*
import kotlinx.coroutines.*
import java.io.File

@Composable
internal fun BibleSearchScreen(language: String, client: BibleContentSource, initialCode: String, onBack: () -> Unit, onOpen: (VerseSearchHit) -> Unit,
    preferencesName: String = "bible-desktop-search", indexFile: File? = null) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE) }
    val repository = client as? OfflineContentRepository
    val index = remember(client) { repository?.let { LocalBibleSearch(it.store, indexFile ?: File(context.noBackupFilesDir, "verse-search-v1.sqlite")) } }
    var query by rememberSaveable { mutableStateOf(prefs.getString("query", "").orEmpty()) }
    val restoreOnOpen=remember{query.trim().length>=2}
    var match by rememberSaveable { mutableStateOf(runCatching { VerseSearchMatch.valueOf(prefs.getString("match", "EXACT")!!) }.getOrDefault(VerseSearchMatch.EXACT)) }
    var scope by rememberSaveable { mutableStateOf(runCatching { VerseSearchScope.valueOf(prefs.getString("scope", "ALL")!!) }.getOrDefault(VerseSearchScope.ALL)) }
    var codes by remember { mutableStateOf(prefs.getStringSet("codes", setOf(initialCode)).orEmpty().toSet()) }
    var editions by remember { mutableStateOf<List<TranslationSummary>>(emptyList()) }
    var books by remember { mutableStateOf<List<BibleBook>>(emptyList()) }
    var book by rememberSaveable { mutableStateOf(prefs.getString("book", null)) }
    var page by remember { mutableStateOf<LocalVerseSearchPage?>(null) }
    var offset by rememberSaveable { mutableIntStateOf(prefs.getInt("offset", 0)) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var invalid by remember { mutableStateOf(false) }
    var indexed by remember { mutableIntStateOf(0) }
    var indexTotal by remember { mutableIntStateOf(0) }
    var run by remember { mutableStateOf<Job?>(null) }
    var generation by remember { mutableIntStateOf(0) }
    var resultQuery by remember { mutableStateOf(query) }
    var resultMatch by remember { mutableStateOf(match) }
    val coroutine = rememberCoroutineScope()
    val listState = rememberLazyListState(prefs.getInt("row",0), prefs.getInt("rowOffset",0))
    val strings = listOf(R.string.action_back,R.string.note_cancel,R.string.study_previous,R.string.study_next,
        R.string.verse_search_title,R.string.verse_search_exact,R.string.verse_search_phrase,R.string.verse_search_partial,R.string.verse_search_strong,
        R.string.verse_search_all,R.string.verse_search_old,R.string.verse_search_new,R.string.verse_search_psalms,R.string.verse_search_mode,
        R.string.verse_search_scope,R.string.verse_search_book,R.string.verse_search_translations,R.string.verse_search_find,R.string.verse_search_total,
        R.string.verse_search_missing,R.string.verse_search_guide,R.string.verse_search_invalid,R.string.verse_search_error,R.string.verse_search_morphology,R.string.verse_search_morphology_hint).associateWith { localized(it, language) }
    val l: (Int) -> String = { strings.getValue(it) }
    fun invalidate() { page=null;offset=0 }
    fun search(position: Int, restore: Boolean = false) {
        if (query.trim().length !in 2..500 || codes.isEmpty() || (match == VerseSearchMatch.STRONG && !Regex("[HG]\\d{1,5}",RegexOption.IGNORE_CASE).matches(query.trim()))) { invalid=true; return }
        if(match==VerseSearchMatch.MORPHOLOGY && editions.filter{it.code in codes}.any{it.language.code !in stemmingLanguages}) { invalid=true;return }
        invalid=false; generation++; val current=generation; run?.cancel(); busy=true; error=false
        val selected=codes.toSet(); val searching=query.trim(); val mode=match; val area=scope; val chosenBook=book
        prefs.edit().putString("query", searching).putString("match",mode.name).putString("scope",area.name).putStringSet("codes",selected).putString("book",chosenBook).putInt("offset",position).apply()
        run=coroutine.launch {
            try {
                page = index?.search(selected, searching, mode, area, chosenBook, position) { done, total -> indexed=done;indexTotal=total }
                    ?: error("Search needs installed storage")
                resultQuery=searching;resultMatch=mode
                offset=position
                if(restore) listState.scrollToItem(prefs.getInt("row",0),prefs.getInt("rowOffset",0)) else listState.scrollToItem(0)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if(current==generation)error=true }
            finally { if(current==generation)busy=false }
        }
    }
    LaunchedEffect(client) {
        try {
            editions=repository?.installedTranslations().orEmpty()
            codes=codes.filter { code -> editions.any { it.code==code } }.toSet().ifEmpty { editions.firstOrNull()?.let { setOf(it.code) }.orEmpty() }
            books=editions.firstOrNull()?.let { client.getBooks(it.code) }.orEmpty()
            if (restoreOnOpen && codes.isNotEmpty()) search(offset,true)
        } catch (_: Exception) { error=true }
    }
    DisposableEffect(Unit) { onDispose { prefs.edit().putInt("row",listState.firstVisibleItemIndex).putInt("rowOffset",listState.firstVisibleItemScrollOffset).apply() } }
    BackHandler(onBack=onBack)
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(12.dp)) {
        Row(Modifier.fillMaxWidth()) { TextButton(onClick=onBack) { Text(l(R.string.action_back)) }; Text(l(R.string.verse_search_title), style=MaterialTheme.typography.titleLarge) }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state=listState, verticalArrangement=Arrangement.spacedBy(10.dp)) {
            item {
                OutlinedTextField(query,{query=it.take(500);invalidate()},label={Text(l(R.string.verse_search_title))},singleLine=true,enabled=!busy,modifier=Modifier.fillMaxWidth().testTag("verse-search-query"))
                SearchChoice(l(R.string.verse_search_mode),match,VerseSearchMatch.entries,{l(when(it){VerseSearchMatch.EXACT->R.string.verse_search_exact;VerseSearchMatch.PHRASE->R.string.verse_search_phrase;VerseSearchMatch.PARTIAL->R.string.verse_search_partial;VerseSearchMatch.STRONG->R.string.verse_search_strong;VerseSearchMatch.MORPHOLOGY->R.string.verse_search_morphology})},!busy){match=it;invalidate()}
                SearchChoice(l(R.string.verse_search_scope),scope,VerseSearchScope.entries,{l(when(it){VerseSearchScope.ALL->R.string.verse_search_all;VerseSearchScope.OLD->R.string.verse_search_old;VerseSearchScope.NEW->R.string.verse_search_new;VerseSearchScope.PSALMS->R.string.verse_search_psalms})},!busy){scope=it;invalidate()}
                SearchChoice(l(R.string.verse_search_book),book,listOf<String?>(null)+books.mapNotNull{it.canonicalBook?.osisCode}.distinct(),{value->if(value==null) l(R.string.verse_search_all) else books.firstOrNull{it.canonicalBook?.osisCode==value}?.name?:value},!busy){book=it;invalidate()}
                Text(l(R.string.verse_search_translations),style=MaterialTheme.typography.labelLarge)
                Column(Modifier.heightIn(max=180.dp).fillMaxWidth()) { androidx.compose.foundation.lazy.LazyColumn {
                    items(editions,key={it.code}) { edition -> Row(Modifier.fillMaxWidth()) {
                        Checkbox(edition.code in codes,{checked-> codes=if(checked) codes+edition.code else codes-edition.code;invalidate()},enabled=!busy)
                        Text(edition.name,Modifier.padding(top=12.dp))
                    } }
                } }
                if (busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("$indexed / $indexTotal"); TextButton(onClick={run?.cancel()}){Text(l(R.string.note_cancel))} }
                else Button(onClick={search(0)},modifier=Modifier.testTag("verse-search-submit")){Text(l(R.string.verse_search_find))}
                Text(l(R.string.verse_search_guide),style=MaterialTheme.typography.bodySmall)
                Text(l(R.string.verse_search_morphology_hint),style=MaterialTheme.typography.bodySmall)
                if (invalid) Text(l(R.string.verse_search_invalid),color=MaterialTheme.colorScheme.error)
                if (error) Text(l(R.string.verse_search_error),color=MaterialTheme.colorScheme.error)
                page?.let { value ->
                    Text("${l(R.string.verse_search_total)}: ${value.total}",Modifier.testTag("verse-search-count"))
                    if(value.unavailableChapters>0) Text("${l(R.string.verse_search_missing)}: ${value.unavailableChapters}")
                }
            }
            items(page?.results.orEmpty(),key={"${it.translation}:${it.reference}"}) { hit -> Card(Modifier.fillMaxWidth().clickable{onOpen(hit)}.testTag("search-hit-${hit.reference}")) {
                Column(Modifier.padding(12.dp)) {
                    Text("${hit.bookName} ${hit.chapter}:${hit.verse}",style=MaterialTheme.typography.titleMedium)
                    if(codes.size>1) Text(editions.firstOrNull{it.code==hit.translation}?.name?:hit.translation,style=MaterialTheme.typography.labelSmall)
                    Text(highlightSearch(hit.text,resultQuery,resultMatch,editions.firstOrNull{it.code==hit.translation}?.language?.code))
                }
            } }
            item { page?.let { value -> Row {
                TextButton(onClick={search((offset-50).coerceAtLeast(0))},enabled=!busy&&offset>0){Text(l(R.string.study_previous))}
                Text("${if(value.total==0)0 else offset+1}–${offset+value.results.size} / ${value.total}")
                TextButton(onClick={search(offset+50)},enabled=!busy&&offset+50<value.total){Text(l(R.string.study_next))}
            } } }
        }
    }
}
@Composable private fun <T> SearchChoice(label:String, selected:T, values:List<T>, name:(T)->String, enabled:Boolean, onSelect:(T)->Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box { TextButton(onClick={expanded=true},enabled=enabled){Text("$label: ${name(selected)}")}
        DropdownMenu(expanded,{expanded=false},Modifier.heightIn(max=300.dp)) { values.forEach { value -> DropdownMenuItem(text={Text(name(value))},onClick={onSelect(value);expanded=false}) } }
    }
}
private fun highlightSearch(text:String,query:String,match:VerseSearchMatch,language:String?): AnnotatedString = buildAnnotatedString {
    val stemming=if(match==VerseSearchMatch.MORPHOLOGY&&language in stemmingLanguages) SearchStemming(language!!) else null
    val stems=stemming?.tokens(query).orEmpty()
    verseHighlights(text,query,if(stemming!=null)VerseSearchMatch.EXACT else match).forEach { part ->
        val matched=if(stemming!=null)stemming.word(part.text) in stems else part.match
        if(matched) withStyle(SpanStyle(background=Color(0xFFFFEB9C),color=Color(0xFF242424))){append(part.text)} else append(part.text)
    }
}
