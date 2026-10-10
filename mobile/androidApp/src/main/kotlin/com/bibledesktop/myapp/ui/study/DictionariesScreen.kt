package com.bibledesktop.myapp.ui.study

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bibledesktop.myapp.data.DictionaryRepository
import com.bibledesktop.shared.api.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

@Composable internal fun DictionariesScreen(language: String, onBack: () -> Unit, initialModule: String? = null, initialEntry: String? = null, initialQuery:String="", onReference: ((DictionaryReference) -> Unit)? = null) {
    val context = LocalContext.current; val text = dictionaryTexts(language)
    val preferences = remember { context.getSharedPreferences("dictionary-reading", Context.MODE_PRIVATE) }
    val repository = remember { DictionaryRepository(context) }
    DisposableEffect(repository) { onDispose { repository.close() } }
    var code by rememberSaveable { mutableStateOf(initialModule ?: preferences.getString("module", "") ?: "") }
    var key by rememberSaveable { mutableStateOf(initialEntry ?: preferences.getString("entry", "") ?: "") }
    var query by rememberSaveable { mutableStateOf(initialQuery) }; var submitted by rememberSaveable { mutableStateOf(initialQuery) }
    var offset by rememberSaveable { mutableIntStateOf(0) }; var maps by rememberSaveable { mutableStateOf(false) }
    var articleHistory by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var modules by remember { mutableStateOf(emptyList<DictionaryModule>()) }; var page by remember { mutableStateOf<DictionaryPage?>(null) }; var article by remember { mutableStateOf<DictionaryArticle?>(null) }
    var forms by remember { mutableStateOf(emptyList<DictionaryWordForm>()) }; var images by remember { mutableStateOf(emptyMap<Long, File>()) }
    var busy by remember { mutableStateOf(false) }; var failed by remember { mutableStateOf(false) }; var retry by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    fun positionKey() = "$code:${modules.firstOrNull { it.code == code }?.version}:$key:$submitted:$offset"
    fun openArticle(target: String) { if(key.isNotEmpty()) articleHistory = articleHistory + key; key = target }
    fun back() { when { articleHistory.isNotEmpty() -> { key = articleHistory.last(); articleHistory = articleHistory.dropLast(1) }; key.isNotEmpty() -> key = ""; code.isNotEmpty() -> { code = ""; offset = 0 }; else -> onBack() } }
    BackHandler { back() }
    LaunchedEffect(code, key, offset, submitted, retry) {
        busy = true; failed = false; article = null; page = null; images = emptyMap(); forms = emptyList()
        try {
            modules = repository.modules()
            val module = modules.firstOrNull { it.code == code }
            if (module != null) {
                if (key.isEmpty()) { page = repository.entries(code, submitted, offset); if (submitted.isNotBlank() && module.forms > 0) forms = repository.lookup(submitted, code) }
                else { article = repository.article(module, key); preferences.edit().putString("module", code).putString("entry", key).apply() }
            }
        } catch (e: CancellationException) { throw e } catch (_: Exception) { failed = true } finally { busy = false }
    }
    LaunchedEffect(article) { article?.media?.forEach { media -> try { images = images + (media.id to repository.image(code, media, modules.firstOrNull { it.code == code }?.version)) } catch (e: CancellationException) { throw e } catch (_: Exception) { /* Text remains available when its image cannot load. */ } } }
    LaunchedEffect(article, page) { if(article != null || page != null) listState.scrollToItem(preferences.getInt("row:${positionKey()}", 0).coerceAtLeast(0), preferences.getInt("scroll:${positionKey()}", 0).coerceAtLeast(0)) }
    LaunchedEffect(code, key, submitted, offset, modules) { snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }.collectLatest { (row, scroll) -> delay(180); if(!busy) preferences.edit().putInt("row:${positionKey()}", row).putInt("scroll:${positionKey()}", scroll).apply() } }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Row { TextButton(onClick = { back() }) { Text(text.back) }; Text(text.title, style = MaterialTheme.typography.titleLarge) } }
        if(busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if(failed) item { Text(text.error); Button(onClick = { retry++ }) { Text(text.retry) } }
        if(code.isEmpty()) {
            item { DictionarySearchPanel(language, initialQuery, repository) { module, entry -> code=module;key=entry;offset=0 } }
            item { FilterChip(selected = maps, onClick = { maps = !maps }, label = { Text(if(maps) text.maps else text.all) }) }
            items(modules.filter { !maps || it.media > 0 }, key = { it.code }) { module -> OutlinedButton(onClick = { code = module.code; key = ""; offset = 0; submitted = query }, modifier = Modifier.fillMaxWidth()) { Text("${studySourceName(module.name)} · ${module.entries}") } }
        } else {
            item { Text(studySourceName(modules.firstOrNull { it.code == code }?.name.orEmpty()), style = MaterialTheme.typography.titleMedium) }
            modules.firstOrNull { it.code == code }?.let { module -> item { ModuleSourceCard(language,Json.encodeToJsonElement(DictionaryModule.serializer(),module).jsonObject,module.version) } }
            if(article != null) {
                val body = article!!
                item { Text(studySourceName(body.topic), style = MaterialTheme.typography.titleLarge); Text(studyReadingText(body.body)) }
                items(body.media, key = { it.id }) { media -> images[media.id]?.let { AtlasImage(it, body.topic, language) } }
                if(body.links.isNotEmpty()) item { Text(text.links) }
                itemsIndexed(body.links, key = { index, link -> "${link.key}:$index" }) { _, link -> TextButton(onClick = { openArticle(link.key) }) { Text(link.label.ifBlank { link.topic }) } }
                items(body.references) { ref ->
                    val label = "${ref.book} ${ref.chapter ?: ""}${ref.first?.let { ":$it${ref.last?.takeIf { n -> n != ref.first }?.let { n -> "–$n" } ?: ""}" } ?: ""}"
                    if(onReference != null) TextButton(onClick = { onReference(ref) }) { Text(label) } else Text(label)
                }
            } else {
                item { OutlinedTextField(query, { query = it.take(120) }, label = { Text(text.search) }); Button(onClick = { submitted = query; offset = 0; retry++ }) { Text(text.search) } }
                items(forms) { form -> TextButton(onClick={query=form.standard;submitted=form.standard;offset=0;retry++}) { Text(form.standard) } }
                items(page?.data.orEmpty(), key = { it.key }) { topic -> OutlinedButton(onClick = { openArticle(topic.key) }, modifier = Modifier.fillMaxWidth()) { Text(studySourceName(topic.topic)) } }
                item { Row { TextButton(onClick = { offset = (offset - 30).coerceAtLeast(0) }, enabled = offset > 0) { Text(text.back) }; Text("${offset + (page?.data?.size ?: 0)} / ${page?.total ?: 0}"); TextButton(onClick = { offset += page?.data?.size ?: 0 }, enabled = page?.let { offset + it.data.size < it.total } == true) { Text(text.next) } } }
            }
        }
        item { Text(text.offline, style = MaterialTheme.typography.bodySmall) }
    }
}
