package com.bibledesktop.myapp.ui.study

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.bibledesktop.myapp.R
import com.bibledesktop.myapp.ui.bible.LocalReaderPreferences
import com.bibledesktop.myapp.ui.setup.localized
import com.bibledesktop.shared.api.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** Composed only at real book/chapter boundaries; loading never replaces or delays Bible text. */
@Composable internal fun BoundaryCommentaries(language:String,client:BibleContentSource,chapter:BibleChapter,verse:BibleVerse,boundary:CommentaryBoundary?) {
 val display=LocalReaderPreferences.current
 if(boundary==null||display.clean||!display.commentaryLinks)return
 val context=LocalContext.current
 val preferences=remember(context){context.getSharedPreferences("bible-desktop-study",Context.MODE_PRIVATE)}
 var revision by remember{mutableIntStateOf(0)}
 DisposableEffect(preferences){val listener=SharedPreferences.OnSharedPreferenceChangeListener{_,_->revision++};preferences.registerOnSharedPreferenceChangeListener(listener);onDispose{preferences.unregisterOnSharedPreferenceChangeListener(listener)}}
 val osis=verse.osisRef.substringBefore('.');val rule="sources-book:$osis"
 val sources=remember(revision,rule,verse.osisRef){commentarySourcesAt(osis,canonicalStudyPosition(verse.osisRef),preferences.getStringSet(if(preferences.contains(rule))rule else "sources",emptySet()).orEmpty().toList().take(30),readCommentarySourceRules(preferences))}
 var entries by remember(client,chapter.translation.code,verse.osisRef,boundary){mutableStateOf(emptyList<CommentaryEntry>())}
 var showing by remember{mutableStateOf(false)};var expanded by remember{mutableStateOf(emptySet<Long>())}
 LaunchedEffect(client,chapter.translation.code,verse.osisRef,boundary,sources){entries=emptyList();if(sources.isEmpty())return@LaunchedEffect
  var attempt=0
  val pauses=longArrayOf(3_000,9_000,30_000,60_000)
  while(true){
   try{entries=InlineCommentaryPages.boundary(client,chapter,verse,sources,boundary);break}
   catch(cancelled:CancellationException){throw cancelled}
   catch(error:Exception){
    if(!isCalendarTransportFailure(error)&&!isRetryableBibleFailure(error))break
    val rateLimitDelay=if(isRateLimitedBibleFailure(error))bibleRetryDelayMillis(error) else 0L
    delay(maxOf(pauses[attempt.coerceAtMost(pauses.lastIndex)],rateLimitDelay));attempt++
   }
  }
 }
 if(entries.isEmpty())return
 val labels=boundaryCommentaryLabels(language)
 val caption=if(boundary.after)labels.after else labels.materials
 val tag="boundary-commentaries:${chapter.translation.code}:${verse.osisRef}:${if(boundary.after)"after" else "start"}"
 TextButton(onClick={showing=true},modifier=Modifier.testTag(tag)){Text("▤ $caption · ${entries.size}",style=MaterialTheme.typography.labelMedium)}
 if(showing)AlertDialog(onDismissRequest={showing=false},confirmButton={TextButton(onClick={showing=false}){Text(localized(R.string.study_close,language))}},
  title={Text("$caption · ${chapter.book.name} ${boundary.chapter}")},text={Column(Modifier.heightIn(max=460.dp).verticalScroll(rememberScrollState())){
   entries.forEach{entry->
    val book=entry.chapterFrom==0
    Text(if(book)labels.book else "${labels.chapter} ${entry.chapterFrom}",style=MaterialTheme.typography.labelSmall)
    // Null placement stays neutral even when its convenient reading position is the chapter start.
    entry.placement?.let{Text(if(it==CommentaryPlacement.AFTER)labels.after else labels.before,style=MaterialTheme.typography.labelSmall)}
    Text(studySourceName(entry.title?:localized(R.string.study_section,language)),style=MaterialTheme.typography.titleSmall)
    entry.author?.takeIf{it.isNotBlank()}?.let{Text(it,style=MaterialTheme.typography.labelSmall)}
    Text(studySourceName(entry.moduleName),style=MaterialTheme.typography.labelSmall)
    if(entry.body.length>=largeStudyBodyThreshold)LargeStudyBody(entry.body,Modifier.fillMaxWidth().heightIn(max=320.dp),expanded=entry.id in expanded,preferences=preferences,positionKey="commentary-body:${entry.moduleCode}:${entry.id}")
    else SelectionContainer{Text(studyReadingText(entry.body).let{if(entry.id in expanded)it else it.take(220)})}
    if(entry.id in expanded)CommentaryAnnotationsContent(language,entry.annotations,client,chapter.translation.code,entry.moduleCode)
    if(entry.id !in expanded)TextButton(onClick={expanded=expanded+entry.id}){Text(localized(R.string.study_full,language))}
   }
  }})
}
