package com.bibledesktop.myapp.ui.study

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bibledesktop.shared.api.*
import com.bibledesktop.myapp.R
import com.bibledesktop.myapp.ui.setup.localized
import kotlinx.coroutines.*

private object InlineCommentaryPages {
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
 private val requests=linkedMapOf<String,Pair<Long,Deferred<List<CommentaryEntry>>>>()
 suspend fun entries(client:BibleContentSource,chapter:BibleChapter,verse:BibleVerse,sources:List<String>):List<CommentaryEntry>{
  val position=canonicalStudyPosition(verse.osisRef);val osis=verse.osisRef.substringBefore('.')
  val key="${System.identityHashCode(client)}:${chapter.translation.canonCode}:$osis:${position.chapter}:${sources.sorted()}"
  val pending=synchronized(requests){val existing=requests[key];if(existing!=null&&System.currentTimeMillis()-existing.first<60000)existing.second else {
   val task=scope.async{val canon=chapter.translation.canonCode?:client.getTranslations().firstOrNull{it.code==chapter.translation.code}?.canonCode?:error("Canonical identity unavailable");val slug=client.getCanonicalSlug(canon,osis)
    loadCommentaryRange(StudyPosition(position.chapter,1),StudyPosition(position.chapter,Int.MAX_VALUE)){number,offset->client.getCommentaries(slug,number,sources,offset)} }
   requests[key]=System.currentTimeMillis() to task;if(requests.size>24)requests.remove(requests.keys.first());task
  }}
  try{return pending.await().filter{commentaryOverlapsRange(it,position,position)}}catch(error:Exception){synchronized(requests){if(requests[key]?.second===pending)requests.remove(key)};throw error}
 }
}
@Composable internal fun InlineVerseCommentaries(language:String,chapter:BibleChapter,verse:BibleVerse,onStudy:()->Unit){
 val client=LocalVerseStudyClient.current?:return;val context=LocalContext.current
 val preferences=remember(context){context.getSharedPreferences("bible-desktop-study",Context.MODE_PRIVATE)}
 var revision by remember{mutableIntStateOf(0)}
 DisposableEffect(preferences){val listener=SharedPreferences.OnSharedPreferenceChangeListener{_,_->revision++};preferences.registerOnSharedPreferenceChangeListener(listener);onDispose{preferences.unregisterOnSharedPreferenceChangeListener(listener)}}
 val rule="sources-book:${verse.osisRef.substringBefore('.')}"
 val sources=remember(revision,rule){commentarySourcesAt(verse.osisRef.substringBefore('.'),canonicalStudyPosition(verse.osisRef),preferences.getStringSet(if(preferences.contains(rule))rule else "sources",emptySet()).orEmpty().toList().take(30),readCommentarySourceRules(preferences))}
 var visible by remember(verse.id,chapter.translation.code){mutableStateOf(false)};var entries by remember(verse.id,chapter.translation.code){mutableStateOf(emptyList<CommentaryEntry>())};var failed by remember{mutableStateOf(false)};var retry by remember{mutableIntStateOf(0)};var showing by remember{mutableStateOf(false)};var expanded by remember{mutableStateOf(emptySet<Long>())}
 LaunchedEffect(visible,verse.osisRef,chapter.translation.code,sources,retry){if(!visible)return@LaunchedEffect;entries=emptyList();failed=false;if(sources.isEmpty())return@LaunchedEffect
  try{entries=InlineCommentaryPages.entries(client,chapter,verse,sources)}catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){failed=true}
 }
 Column(Modifier.fillMaxWidth().heightIn(min=1.dp).onGloballyPositioned{if(it.boundsInWindow().height>0)visible=true}){
  if(entries.isNotEmpty())TextButton(onClick={showing=true}){Text("▤ ${localized(R.string.study_commentaries,language)} · ${entries.size}")}
  else if(failed)TextButton(onClick={retry++}){Text("▤ ${localized(R.string.retry,language)}")}
 }
 if(showing)AlertDialog(onDismissRequest={showing=false},confirmButton={TextButton(onClick={showing=false}){Text(localized(R.string.study_close,language))}},title={Text("${chapter.book.name} ${chapter.chapter.number}:${verse.number}")},text={Column(Modifier.heightIn(max=460.dp).verticalScroll(rememberScrollState())){
  entries.forEach{entry->Text(studySourceName(entry.title?:localized(R.string.study_section,language)),style=MaterialTheme.typography.titleSmall);Text(studySourceName(entry.moduleName),style=MaterialTheme.typography.labelSmall)
   if(entry.body.length>=largeStudyBodyThreshold)LargeStudyBody(entry.body,Modifier.fillMaxWidth().heightIn(max=320.dp),expanded=entry.id in expanded,preferences=preferences,positionKey="commentary-body:${entry.moduleCode}:${entry.id}")
   else SelectionContainer{Text(studyReadingText(entry.body).let{if(entry.id in expanded)it else it.take(220)})}
   if(entry.id in expanded)CommentaryAnnotationsContent(language,entry.annotations,client,chapter.translation.code,entry.moduleCode)
   if(entry.id !in expanded)TextButton(onClick={expanded=expanded+entry.id}){Text(localized(R.string.study_full,language))}
  };TextButton(onClick={showing=false;onStudy()}){Text(localized(R.string.study_commentaries,language))}
 }})
}
