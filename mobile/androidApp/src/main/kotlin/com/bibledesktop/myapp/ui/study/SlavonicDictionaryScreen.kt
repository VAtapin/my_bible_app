package com.bibledesktop.myapp.ui.study

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bibledesktop.myapp.data.SlavonicNotebook
import com.bibledesktop.myapp.data.SlavonicNotebookData
import com.bibledesktop.myapp.ui.theme.readingFont
import com.bibledesktop.shared.api.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Native catalog and lexical search; the original Russian definition is never translated. */
@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun SlavonicDictionaryScreen(language: String, onBack: () -> Unit,
    sourceOverride: SlavonicDictionarySource? = null, preferencesName: String = "bible-desktop-native-profile",
    initialQuery: String = "", initialEntry: String = "") {
    val context = LocalContext.current
    val text = slavonicDictionaryTexts(language)
    val uri = LocalUriHandler.current
    val source = remember(sourceOverride) { sourceOverride ?: DictionaryApi() }
    DisposableEffect(source) { onDispose { if (sourceOverride == null) (source as DictionaryApi).close() } }
    val notebook = remember(preferencesName) { SlavonicNotebook(context.applicationContext, preferencesName) }
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableIntStateOf(if (initialQuery.isBlank()) 0 else 1) }
    var catalog by rememberSaveable { mutableStateOf(initialQuery.isBlank()) }
    var query by rememberSaveable { mutableStateOf(initialQuery.take(120)) }
    var submitted by rememberSaveable { mutableStateOf(initialQuery.take(120)) }
    var meaning by rememberSaveable { mutableStateOf(false) }
    var letter by rememberSaveable { mutableIntStateOf(0) }
    var pageCount by rememberSaveable { mutableIntStateOf(1) }
    var refresh by remember { mutableIntStateOf(0) }
    val browseKey = Triple(catalog, if (catalog) "" else submitted, (if (catalog) false else meaning) to letter)
    var entries by remember(browseKey) { mutableStateOf<List<SlavonicWord>>(emptyList()) }
    var total by remember(browseKey) { mutableIntStateOf(0) }
    var loaded by remember(browseKey) { mutableStateOf(false) }
    var currentVersion by remember(browseKey) { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    var alphabet by remember { mutableStateOf<List<SlavonicLetter>>(emptyList()) }
    var alphabetFailure by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(SlavonicNotebookData()) }
    var saveFailed by remember { mutableStateOf(false) }
    var articleKey by rememberSaveable { mutableStateOf(initialEntry) }
    var article by remember { mutableStateOf<SlavonicWord?>(null) }
    var articleFailure by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    fun errorMessage(error: Exception) = if (error is SlavonicRateLimitException) text.rate else text.unavailable
    fun search() { catalog = false; tab = 1; submitted = query.trim(); pageCount = 1; refresh++ }
    fun browse(section: Int = 0) { catalog = true; tab = 0; letter = section; submitted = ""; query = ""; pageCount = 1; refresh++ }
    LaunchedEffect(source) {
        try { alphabet = source.slavonicAlphabet() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { alphabetFailure = true }
    }
    LaunchedEffect(notebook) {
        try { saved = notebook.read() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { saveFailed = true }
    }
    var previousBrowseKey by remember { mutableStateOf(browseKey) }
    LaunchedEffect(browseKey) {
        if (previousBrowseKey != browseKey) { listState.scrollToItem(0); previousBrowseKey = browseKey }
    }
    LaunchedEffect(browseKey, pageCount, refresh) {
        loading = true; failure = null
        try {
            // On activity recreation restore all previously requested pages. Normal "more" fetches only the next one.
            while ((!loaded || entries.size < pageCount * 30) && (!loaded || entries.size < total)) {
                val page = source.slavonicSearch(if (catalog) "" else submitted, !catalog && meaning,
                    letter.takeIf { it > 0 }, entries.size)
                if (entries.isNotEmpty()) check(page.version == currentVersion)
                currentVersion = page.version
                entries = entries + page.data; total = page.total; loaded = true
                if (page.data.isEmpty()) break
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { failure = errorMessage(error) }
        finally { loading = false }
    }
    LaunchedEffect(articleKey) {
        article = null; articleFailure = null
        if (articleKey.isNotEmpty()) {
            try {
                val value = entries.firstOrNull { it.key == articleKey } ?: source.slavonicArticle(articleKey)
                article = value
                try { saved = notebook.remember(value) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { saveFailed = true }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { articleFailure = errorMessage(error) }
        }
    }
    BackHandler { if (articleKey.isNotEmpty()) articleKey = "" else onBack() }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, text.back) }
                Text(text.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(end = 8.dp))
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                listOf(text.catalog, text.search, text.favorites, text.recent).forEachIndexed { index, label ->
                    TextButton(modifier = Modifier.weight(1f).testTag("slavonic-tab-$index"),
                        contentPadding = PaddingValues(2.dp), onClick = {
                            if (index == 0) browse()
                            else if (index == 1) { letter = 0; catalog = false; tab = 1; pageCount = 1 }
                            else tab = index
                        }) { Text(label, fontSize = 12.sp, color = if (tab == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) }
                }
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("slavonic-list").semantics { stateDescription = "${text.words}: ${entries.size} / $total" }, state = listState,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) {
                if (tab == 0) {
                    item { FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilterChip(selected = letter == 0, onClick = { browse() }, label = { Text(text.all) })
                        alphabet.forEach { section -> FilterChip(selected = letter == section.chapter,
                            onClick = { browse(section.chapter) }, modifier = Modifier.testTag("slavonic-letter-${section.chapter}"),
                            label = { Text(section.label, fontFamily = readingFont("cu"), fontSize = 22.sp) }) }
                    } }
                    if (alphabetFailure) item { Text(text.unavailable) }
                }
                if (tab == 1) item {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = !meaning, onClick = { meaning = false; search() }, label = { Text(text.word) })
                        FilterChip(selected = meaning, onClick = { meaning = true; search() }, label = { Text(text.meaning) }, modifier = Modifier.testTag("slavonic-meaning"))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(query, { query = it.take(120) }, singleLine = true, label = { Text(text.word) },
                            modifier = Modifier.weight(1f).testTag("slavonic-query"))
                        TextButton(onClick = { search() }, enabled = !loading, modifier = Modifier.testTag("slavonic-search")) { Text(text.search) }
                    }
                }
                if (tab < 2) {
                    if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth().testTag("slavonic-loading")) }
                    failure?.let { item { Text(it) } }
                    if (loaded) item { Text("${text.words}: $total", style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("slavonic-count")) }
                    items(entries, key = { it.key }) { value ->
                        Column(Modifier.fillMaxWidth().clickable { articleKey = value.key }.testTag("slavonic-word-${value.key}").padding(vertical = 7.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(value.headword, fontFamily = readingFont("cu"), fontSize = 25.sp, modifier = Modifier.weight(1f))
                                Text(value.civil, fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp))
                            }
                            if (tab == 1) Text(value.definition.take(200) + if (value.definition.length > 200) "…" else "", fontFamily = readingFont("ru"))
                        }
                        HorizontalDivider()
                    }
                    if (loaded && entries.isEmpty() && failure == null) item { Text(text.empty) }
                    if (entries.size < total) item { TextButton(onClick = { pageCount++; refresh++ }, enabled = !loading,
                        modifier = Modifier.testTag("slavonic-more")) { Text(text.more) } }
                } else {
                    val words = if (tab == 2) saved.favorites else saved.recent
                    if (words.isEmpty()) item { Text(text.empty) }
                    items(words, key = { it.key }) { value -> TextButton(onClick = { articleKey = value.key }, modifier = Modifier.fillMaxWidth()) {
                        Text(value.word, fontFamily = readingFont("cu"), fontSize = 25.sp, modifier = Modifier.weight(1f)); Text(value.civil)
                    } }
                }
                if (saveFailed) item { Text(text.saved) }
                item { Text(text.offline, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp)) }
            }
        }
    }
    if (articleKey.isNotEmpty()) Dialog(onDismissRequest = { articleKey = "" }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().testTag("slavonic-article")) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { articleKey = "" }, modifier = Modifier.weight(1f)) { Text(text.back) }
                    IconButton(onClick = { articleKey = "" }, modifier = Modifier.testTag("slavonic-close")) { Icon(Icons.Outlined.Close, text.close) }
                }
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp)) {
                    articleFailure?.let { item { Text(it); Text(text.offline) } }
                    if (article == null && articleFailure == null) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                    article?.let { value ->
                        item { Text(value.headword, fontFamily = readingFont("cu"), fontSize = 32.sp); Text(value.civil) }
                        item { TextButton(onClick = { scope.launch {
                            try { saved = notebook.toggle(value); saveFailed = false }
                            catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: Exception) { saveFailed = true }
                        } }, modifier = Modifier.testTag("slavonic-save")) { Text(if (saved.favorites.any { it.key == value.key }) "★ ${text.remove}" else "☆ ${text.save}") } }
                        item { SelectionContainer { Text(value.definition, fontFamily = readingFont("ru"), fontSize = 19.sp,
                            lineHeight = 30.sp, modifier = Modifier.testTag("slavonic-definition")) } }
                        item { TextButton(onClick = { uri.openUri(value.sourceUrl) }) { Text(text.source) } }
                        if (saveFailed) item { Text(text.saved) }
                    }
                }
            }
        }
    }
}
