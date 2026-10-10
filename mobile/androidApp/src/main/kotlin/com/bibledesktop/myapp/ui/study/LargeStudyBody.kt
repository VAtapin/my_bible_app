package com.bibledesktop.myapp.ui.study

import android.content.SharedPreferences
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext

internal const val largeStudyBodyThreshold=64*1024
/** Keeps every character and splits only between Unicode code points. */
internal fun splitStudyText(text:String,maximum:Int=2048):List<String>{
 require(maximum>=2);val result=mutableListOf<String>();var start=0
 while(start<text.length){var end=(start+maximum).coerceAtMost(text.length)
  if(end<text.length){for(boundary in end-1 downTo start+maximum/2){if(text[boundary]=='\n'||text[boundary]==' '){end=boundary+1;break}}
   if(end>start&&text[end-1].isHighSurrogate()&&text[end].isLowSurrogate())end--
  };result+=text.substring(start,end);start=end
 };return result
}
internal suspend fun prepareStudyBody(body:String)=withContext(Dispatchers.Default){splitStudyText(studyReadingText(body))}

/** A bounded, virtualized body; callers must supply a finite viewport. */
@Composable internal fun LargeStudyBody(body:String,modifier:Modifier=Modifier,expanded:Boolean=true,
 preferences:SharedPreferences?=null,positionKey:String?=null,style:TextStyle=MaterialTheme.typography.bodyLarge,
 leading:@Composable ()->Unit={},trailing:@Composable ()->Unit={}){
 val chunks by produceState<List<String>?>(null,body){value=null;value=prepareStudyBody(body)}
 val parts=chunks
 if(parts==null){Box(modifier){LinearProgressIndicator(Modifier.fillMaxWidth())};return}
 if(!expanded){SelectionContainer{Text(parts.firstOrNull().orEmpty().take(220)+"…",style=style)};return}
 val savedRow=positionKey?.let{preferences?.getInt("$it:row",0)}?:0
 val savedOffset=positionKey?.let{preferences?.getInt("$it:offset",0)}?:0
 val state=rememberLazyListState(savedRow.coerceIn(0,parts.size+1),savedOffset.coerceAtLeast(0))
 var restored by remember(positionKey){mutableStateOf(false)}
 LaunchedEffect(body,positionKey){
  if(positionKey!=null&&preferences!=null&&!preferences.contains("$positionKey:row")){
   val legacy=preferences.getInt(positionKey,0);if(legacy>0)state.scrollBy(legacy.toFloat())
  };restored=true
 }
 LaunchedEffect(state,restored,positionKey){if(!restored||preferences==null||positionKey==null)return@LaunchedEffect
  snapshotFlow{state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset}.collectLatest{(row,offset)->delay(180);preferences.edit().putInt("$positionKey:row",row).putInt("$positionKey:offset",offset).apply()}
 }
 LazyColumn(modifier,state=state){item(key="heading"){leading()};itemsIndexed(parts,key={index,_->index}){_,part->SelectionContainer{Text(part,style=style)}};item(key="annotations"){trailing()}}
}
