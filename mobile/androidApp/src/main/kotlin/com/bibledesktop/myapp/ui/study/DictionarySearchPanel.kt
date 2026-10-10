package com.bibledesktop.myapp.ui.study

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.bibledesktop.myapp.data.DictionaryReadingSource
import com.bibledesktop.shared.api.*
import kotlinx.coroutines.CancellationException

@Composable internal fun DictionarySearchPanel(language:String,initialQuery:String,repository:DictionaryReadingSource,onOpen:(String,String)->Unit) {
 val text=dictionaryTexts(language)
 val heading=when(language){"de"->"In installierten Wörterbüchern suchen";"uk"->"Пошук у встановлених словниках";"en"->"Search installed dictionaries";else->"Поиск по установленным словарям"}
 var modules by remember { mutableStateOf(emptyList<DictionaryModule>()) };var chosen by rememberSaveable { mutableStateOf(emptyList<String>()) }
 var query by rememberSaveable { mutableStateOf(initialQuery) };var submitted by rememberSaveable { mutableStateOf(initialQuery) };var offset by rememberSaveable { mutableIntStateOf(0) }
 var page by remember { mutableStateOf<DictionaryPage?>(null) };var busy by remember { mutableStateOf(false) };var failed by remember { mutableStateOf(false) };var expanded by rememberSaveable { mutableStateOf(false) }
 LaunchedEffect(repository){modules=repository.downloadedModules().filter { it.entries>0 };chosen=modules.map { it.code }}
 LaunchedEffect(submitted,offset,chosen){if(submitted.isBlank()||chosen.isEmpty()){page=null;return@LaunchedEffect};busy=true;failed=false
  try { page=repository.searchInstalled(submitted,chosen,offset) }catch(error:CancellationException){throw error}catch(_:Exception){failed=true}finally{busy=false}
 }
 if(modules.isEmpty())return
 Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
  Text(heading,style=MaterialTheme.typography.titleMedium)
  TextButton(onClick={expanded=!expanded}){Text("${text.all} · ${chosen.size}/${modules.size}")}
  if(expanded){TextButton(onClick={chosen=modules.map{it.code};offset=0}){Text(text.all)}
   LazyColumn(Modifier.heightIn(max=280.dp)){items(modules,key={it.code}) { module->Row{Checkbox(module.code in chosen,onCheckedChange={checked->chosen=if(checked)chosen+module.code else chosen-module.code;offset=0});Text(studySourceName(module.name))} } }
  }
  OutlinedTextField(query,{query=it},label={Text(text.search)},modifier=Modifier.fillMaxWidth())
  Button(onClick={offset=0;submitted=query},enabled=!busy&&query.isNotBlank()&&chosen.isNotEmpty()){Text(text.search)}
  if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
  if(failed)Text(text.error)
  page?.data?.forEach { entry->OutlinedButton(onClick={onOpen(entry.moduleCode!!,entry.key)},modifier=Modifier.fillMaxWidth()){
   Column{Text(highlightDictionary(studySourceName(entry.topic),submitted,SpanStyle(background=MaterialTheme.colorScheme.secondaryContainer)));Text(studySourceName(entry.moduleName.orEmpty()),style=MaterialTheme.typography.labelSmall)}
  } }
  page?.let{value->Row{TextButton(onClick={offset=(offset-50).coerceAtLeast(0)},enabled=!busy&&offset>0){Text(text.back)};Text("${offset+value.data.size}/${value.total}");TextButton(onClick={offset+=50},enabled=!busy&&offset+value.data.size<value.total){Text(text.next)}}}
 }
}
private fun highlightDictionary(text:String,query:String,style:SpanStyle):AnnotatedString=buildAnnotatedString {
 var start=0;while(start<text.length){val at=text.indexOf(query,start,ignoreCase=true);if(at<0||query.isEmpty()){append(text.substring(start));break};append(text.substring(start,at));withStyle(style){append(text.substring(at,at+query.length))};start=at+query.length}
}
