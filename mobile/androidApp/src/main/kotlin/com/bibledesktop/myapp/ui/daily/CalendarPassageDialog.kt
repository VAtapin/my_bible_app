package com.bibledesktop.myapp.ui.daily

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bibledesktop.myapp.R
import com.bibledesktop.myapp.ui.bible.*
import com.bibledesktop.myapp.ui.setup.localized
import com.bibledesktop.myapp.ui.study.*
import com.bibledesktop.shared.api.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Temporary calendar reading never updates the permanent Bible windows or their history. */
@Composable internal fun CalendarPassageDialog(language:String,reading:CalendarReading,client:BibleContentSource,onClose:()->Unit){
 val context=LocalContext.current;val scope=rememberCoroutineScope()
 val preferences=remember(context){ReaderPreferencesStore(context).load()}
 var translations by remember{mutableStateOf(emptyList<TranslationSummary>())}
 var code by remember(reading.id){mutableStateOf<String?>(null)}
 var choosing by remember{mutableStateOf(false)}
 var rows by remember(reading,code){mutableStateOf<List<CalendarReadingRow>?>(null)}
 var error by remember(reading,code){mutableStateOf(false)}
 var retry by remember{mutableIntStateOf(0)}
 var bookmarks by remember{mutableStateOf(BookmarkStore.load(context))}
 var note by remember{mutableStateOf<BookmarkEntry?>(null)};var noteInitial by remember{mutableStateOf("")}
 var strong by remember{mutableStateOf<Triple<BibleChapter,BibleVerse,String>?>(null)}
 LaunchedEffect(client,retry){
  try{
   translations=client.getTranslations()
   if(code==null){val stored=context.getSharedPreferences("bible-desktop-native-profile",Context.MODE_PRIVATE).getString("lastTranslation",null)
    code=translations.firstOrNull{it.code==stored}?.code?:translations.firstOrNull{it.language.code=="ru"}?.code?:translations.firstOrNull()?.code
    require(code!=null)
   }
  }catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){error=true}
 }
 LaunchedEffect(client,reading,code,retry,translations){
  val translation=translations.firstOrNull{it.code==code}?:return@LaunchedEffect
  rows=null;error=false
  try{rows=loadCalendarReading(client,translation,reading)}catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){error=true}
 }
 Dialog(onDismissRequest=onClose,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)){
  BackHandler(onBack=onClose)
  ReaderTheme(preferences){CompositionLocalProvider(LocalVerseStudyClient provides client){
   Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().testTag("calendar-reading-dialog")){
    Row(Modifier.fillMaxWidth().padding(horizontal=10.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
     Text(reading.displayRef.ifBlank{reading.title},Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
     TextButton(onClick=onClose,modifier=Modifier.testTag("calendar-reading-close")){Text(localized(R.string.study_close,language))}
    }
    Box{TextButton(onClick={choosing=true}){Text(translations.firstOrNull{it.code==code}?.name.orEmpty())}
     DropdownMenu(choosing,{choosing=false}){translations.forEach{translation->DropdownMenuItem(text={Text(translation.name)},onClick={code=translation.code;choosing=false})}}
    }
    if(error)Column(Modifier.padding(10.dp)){Text(localized(R.string.study_missing_text,language));TextButton(onClick={retry++}){Text(localized(R.string.retry,language))}}
    else if(rows==null)LinearProgressIndicator(Modifier.fillMaxWidth())
    rows?.let{loaded->LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal=10.dp).testTag("calendar-reading-rows")){
     itemsIndexed(loaded,key={index,row->"$index:${row.chapter.translation.code}:${row.verse.osisRef}"}){index,row->
      if(index==0||loaded[index-1].chapter.book.slug!=row.chapter.book.slug||loaded[index-1].chapter.chapter.number!=row.chapter.chapter.number)Text("${row.chapter.book.name} ${row.chapter.chapter.number}",style=MaterialTheme.typography.titleMedium)
      Box(Modifier.testTag("calendar-reading-${row.verse.osisRef}")){VerseRow(language,row.chapter,row.verse,preferences.fontSize,
       bookmarks.any{it.translationCode==row.chapter.translation.code&&it.reference==row.verse.osisRef},
       onBookmark={bookmarks=BookmarkStore.toggle(context,bookmarks,row.chapter,row.verse)},
       onShare={shareBiblePassage(context,versePassage(row.chapter,row.verse))},
       onNote={val passage=versePassage(row.chapter,row.verse);scope.launch{try{noteInitial=NoteStore.read(context).firstOrNull{it.passage.translationCode==passage.translationCode&&it.passage.reference==passage.reference}?.body.orEmpty();note=passage}catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){error=true}}},
       onStrong={number->strong=Triple(row.chapter,row.verse,number)})}
     }
    }}
   }
  }}
 }
 note?.let{NoteEditor(language,it,noteInitial,onDismiss={note=null},onSaved={note=null})}
 strong?.let{value->Dialog(onDismissRequest={strong=null},properties=DialogProperties(usePlatformDefaultWidth=false)){
  Surface(Modifier.fillMaxWidth()){Column(Modifier.padding(10.dp)){
   TextButton(onClick={strong=null}){Text(localized(R.string.study_close,language))}
   StrongArticle(language,value.first,value.second.id,client,value.third)
  }}
 }}
}
