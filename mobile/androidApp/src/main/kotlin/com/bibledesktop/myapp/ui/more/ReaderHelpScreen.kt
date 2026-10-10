package com.bibledesktop.myapp.ui.more

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.json.JSONObject

internal fun readerHelpTitle(language:String)=when(language){"ru"->"Справка по читалке и изучению";"de"->"Hilfe zum Lesen und Studieren";"uk"->"Довідка з читання та вивчення";else->"Reader and study help"}
@Composable internal fun ReaderHelpScreen(language:String,onBack:()->Unit){
    val context=LocalContext.current
    val text=remember(language){JSONObject(context.assets.open("reader-help.json").bufferedReader().use{it.readText()}).getJSONObject(language.takeIf{it in setOf("ru","de","uk","en")}?:"en")}
    val sections=remember(text){val values=text.getJSONArray("sections");(0 until values.length()).map{val entry=values.getJSONArray(it);entry.getString(0) to entry.getString(1)}}
    var horizontal by remember{mutableStateOf(true)}
    var demonstrated by remember{mutableStateOf(false)}
    val progress by animateFloatAsState(if(demonstrated)1f else 0f,label="gesture-demonstration")
    BackHandler(onBack=onBack)
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{Row{TextButton(onClick=onBack){Text("←")};Text(text.getString("title"),style=MaterialTheme.typography.titleLarge)}}
        items(sections){(title,body)->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text(title,style=MaterialTheme.typography.titleMedium);Text(body)}}}
        item{Text(text.getString("demo"),style=MaterialTheme.typography.titleLarge);Row{FilterChip(selected=horizontal,onClick={horizontal=true;demonstrated=!demonstrated},label={Text(text.getString("horizontal"))});FilterChip(selected=!horizontal,onClick={horizontal=false;demonstrated=!demonstrated},label={Text(text.getString("vertical"))})};Box(Modifier.fillMaxWidth().height(180.dp).background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp)){if(horizontal)Box(Modifier.offset(y=8.dp).width((190*progress).dp).height(25.dp).background(Color(0xffffe49a)));Text(text.getString("sample"),Modifier.offset(y=if(horizontal)0.dp else (-40*progress).dp),lineHeight=MaterialTheme.typography.bodyLarge.lineHeight*2);Text("●",Modifier.offset(x=if(horizontal)(190*progress).dp else 0.dp,y=if(horizontal)8.dp else (-40*progress+40).dp),color=MaterialTheme.colorScheme.primary)} }
    }
}
