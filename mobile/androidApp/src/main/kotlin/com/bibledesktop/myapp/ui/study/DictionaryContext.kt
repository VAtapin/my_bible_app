package com.bibledesktop.myapp.ui.study

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bibledesktop.myapp.data.DictionaryRepository
import com.bibledesktop.shared.api.*
import kotlinx.coroutines.CancellationException

/** The caller supplies canonical book identity and actual verse IDs; no translation slug guessing. */
@Composable internal fun DictionaryContext(language: String, book: String, chapter: Int?, verseIds: List<Long> = emptyList(), verseNumbers: List<Int> = emptyList(), verseChapters: List<Int> = emptyList(), osis:String?=null, onOpen: (String, String) -> Unit) {
    val context = LocalContext.current; val text = dictionaryTexts(language)
    val repository = remember { DictionaryRepository(context) }
    val preferences = remember { context.getSharedPreferences("dictionary-context", Context.MODE_PRIVATE) }
    var modules by remember { mutableStateOf(emptyList<DictionaryModule>()) }
    var selected by remember { mutableStateOf(preferences.getStringSet("sources", emptySet()).orEmpty().toList().take(30)) }
    var entries by remember { mutableStateOf(emptyList<DictionaryTopic>()) }
    var choose by remember { mutableStateOf(false) }; var sourceQuery by remember { mutableStateOf("") }; var sourceLanguage by remember { mutableStateOf("") }; var languageMenu by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }; var failed by remember { mutableStateOf(false) }; var retry by remember { mutableIntStateOf(0) }
    DisposableEffect(repository) { onDispose { repository.close() } }
    LaunchedEffect(retry) { try { modules = repository.modules(); selected = selected.filter { code -> modules.any { it.code == code } } } catch(e: CancellationException) { throw e } catch(_: Exception) { failed = true } }
    LaunchedEffect(book, chapter, verseIds, verseNumbers, verseChapters, osis, selected, retry) {
        entries = emptyList(); if(selected.isEmpty()) return@LaunchedEffect
        busy = true; failed = false; preferences.edit().putStringSet("sources", selected.toSet()).apply()
        try {
            val unique = linkedMapOf<String, DictionaryTopic>()
            val places: List<Long?> = verseIds.distinct().ifEmpty { listOf(null) }
            for((index,id) in places.withIndex()) { var offset = 0; while(true) { val number=verseNumbers.getOrNull(index);val canonicalChapter=verseChapters.getOrNull(index)?:chapter;val page = if(id == null) repository.context(book, chapter, selected, offset,osis=osis) else if(canonicalChapter!=null&&number!=null)repository.verseAt(id,book,canonicalChapter,number,selected,offset,osis) else repository.verse(id, selected, offset); page.data.forEach { unique["${it.moduleCode}:${it.key}"] = it }; offset += page.data.size; if(page.data.isEmpty() || offset >= page.total) break } }
            entries = unique.values.toList()
        } catch(e: CancellationException) { throw e } catch(_: Exception) { failed = true } finally { busy = false }
    }
    Column {
        Text(text.title, style = MaterialTheme.typography.titleMedium)
        TextButton(onClick={choose=!choose}) { Text("${text.title} · ${selected.size}/30") }
        if(choose){
            OutlinedTextField(sourceQuery,{sourceQuery=it},label={Text(text.search)},singleLine=true)
            Box { TextButton(onClick={languageMenu=true}){Text(sourceLanguage.ifEmpty{text.all})};DropdownMenu(expanded=languageMenu,onDismissRequest={languageMenu=false},modifier=Modifier.heightIn(max=280.dp)){DropdownMenuItem(text={Text(text.all)},onClick={sourceLanguage="";languageMenu=false});modules.mapNotNull { it.language }.distinct().sorted().forEach { code -> DropdownMenuItem(text={Text(code)},onClick={sourceLanguage=code;languageMenu=false}) }} }
            Column(Modifier.heightIn(max=280.dp).verticalScroll(rememberScrollState())){modules.filter { (sourceLanguage.isEmpty()||it.language==sourceLanguage)&&studySourceName(it.name).contains(sourceQuery.trim(),ignoreCase=true) }.forEach { module -> Row { Checkbox(checked = module.code in selected, enabled = module.code in selected || selected.size < 30, onCheckedChange = { checked -> selected = if(checked) selected + module.code else selected - module.code }); Text(studySourceName(module.name),Modifier.weight(1f)) } }}
        }
        FlowRow { modules.filter { it.code in selected }.forEach { module -> InputChip(selected=true,onClick={selected=selected-module.code},label={Text(studySourceName(module.name))}) } }
        if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if(failed) { Text(text.error); TextButton(onClick = { retry++ }) { Text(text.retry) } }
        entries.forEach { entry -> entry.moduleCode?.let { code -> TextButton(onClick = { onOpen(code, entry.key) }) { Text("${entry.topic} · ${entry.moduleName.orEmpty()}") } } }
    }
}
