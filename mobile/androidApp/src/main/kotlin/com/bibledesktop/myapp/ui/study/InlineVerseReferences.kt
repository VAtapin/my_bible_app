package com.bibledesktop.myapp.ui.study

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.bibledesktop.shared.api.*
import com.bibledesktop.myapp.R
import com.bibledesktop.myapp.ui.setup.localized
import com.bibledesktop.myapp.ui.reading.readingText
import com.bibledesktop.myapp.data.OfflineContentRepository
import kotlinx.coroutines.CancellationException

internal val LocalVerseStudyClient=staticCompositionLocalOf<BibleContentSource?>{null}
@Composable internal fun InlineVerseReferences(language:String,chapter:BibleChapter,verse:BibleVerse,onStudy:()->Unit){
 val client=LocalVerseStudyClient.current?:return
 val loader=LocalInlineReferenceLoader.current;val view=LocalView.current
 val context=LocalContext.current;val labels=studyTexts(language);val text=referenceDisplayTexts(language)
 val store=remember(context){ReferenceDisplayStore(context)};val revision by ReferenceDisplayStore.changes.collectAsState();val settings=remember(revision){store.load()}
 var visible by remember(client,verse.id,verse.osisRef,chapter.translation.code){mutableStateOf(false)}
 var data by remember(client,verse.id,verse.osisRef,chapter.translation.code){mutableStateOf<CrossReferences?>(null)}
 var showing by remember(client,verse.id,verse.osisRef,chapter.translation.code){mutableStateOf(false)}
 var books by remember(client,chapter.translation.code){mutableStateOf(emptyList<BibleBook>())};var temporary by remember{mutableStateOf<List<ReferenceTarget>?>(null)}
 LaunchedEffect(client,loader,visible,verse.id,verse.osisRef,chapter.translation.code){
  if(!visible||data!=null)return@LaunchedEffect
  data=loader.load(attempt={
   ((client as? OfflineContentRepository)?.getCrossReferencesAt(verse.id,chapter.translation.code,verse.osisRef)?:client.getCrossReferences(verse.id,chapter.translation.code))
    .also{require(it.verse.osisRef==verse.osisRef&&it.translationCode==chapter.translation.code)}
  })

 }

 LaunchedEffect(showing,client,chapter.translation.code){
  if(!showing||books.isNotEmpty())return@LaunchedEffect
  // Catalogue metadata is needed only in the explicitly opened study popup.
  books=try{client.getBooks(chapter.translation.code)}catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){emptyList()}
 }
 val groups=displayReferenceGroups(data?.references.orEmpty(),settings.inlineSources,books.mapNotNull{b->b.canonicalBook?.osisCode?.let{it to b.order}}.toMap())
 val sources=data?.references?.map{it.source.orEmpty()}?.distinct().orEmpty()
 Column(Modifier.fillMaxWidth().heightIn(min=1.dp).onGloballyPositioned{
  val bounds=it.boundsInWindow()
  visible=bounds.height>0&&bounds.bottom>0&&bounds.top<view.height
 }){
  if(groups.isNotEmpty()){TextButton(onClick={showing=true}){Text("↗ ${groups.size}")};if(settings.list)FlowRow{groups.forEach{group->TextButton(onClick={temporary=group.targets}){Text(group.label)}}}}
 }

 if(showing)AlertDialog(onDismissRequest={showing=false},title={Text("${chapter.book.name} ${chapter.chapter.number}:${verse.number} · ${chapter.translation.name}")},
  confirmButton={TextButton(onClick={showing=false}){Text(localized(R.string.study_close,language))}},text={Column(Modifier.heightIn(max=430.dp).verticalScroll(rememberScrollState())){
   Text("${text.count}: ${groups.size}");Text(text.normal)
   TextButton(onClick={store.save(settings.copy(inlineSources=null))}){Text(labels.all)}
   sources.forEach{source->Row{Checkbox(settings.inlineSources==null||source in settings.inlineSources,{store.save(settings.copy(inlineSources=toggledReferenceSources(settings.inlineSources,source,sources)))});Text(source.ifBlank{"—"})}}
   groups.forEach{group->Text(group.label,style=MaterialTheme.typography.titleSmall);ReferenceNumbering(language,group.targets);Text("${group.source.ifBlank{"—"}} · ${chapter.translation.name}");group.targets.forEach{target->Text("${target.verseNumber} ${referencePreviewText(target,labels.missing)}")}
    Row{TextButton(onClick={temporary=group.targets}){Text(localized(R.string.bookmark_open,language))};TextButton(onClick={(context.getSystemService(Context.CLIPBOARD_SERVICE)as ClipboardManager).setPrimaryClip(ClipData.newPlainText(group.label,referenceCopyText(group,chapter.translation.name)))}){Text(text.copy)}}
   };TextButton(onClick={showing=false;onStudy()}){Text(localized(R.string.study_verse,language))}
  }})
 temporary?.let{TemporaryStudyPassage(language,chapter.translation.code,it,client){temporary=null}}
}
