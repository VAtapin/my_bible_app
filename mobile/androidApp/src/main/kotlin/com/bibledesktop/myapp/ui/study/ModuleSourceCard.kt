package com.bibledesktop.myapp.ui.study

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.*

private val sourceLabels=mapOf("ru" to listOf("Об источнике","Полное название","Краткое название","Язык","Автор / переводчик / составитель","Издание","Электронный источник","Версия модуля","Дата обновления","Состав и разметка","Не указано источником"),"de" to listOf("Über die Quelle","Vollständiger Name","Kurzname","Sprache","Autor / Übersetzer / Herausgeber","Ausgabe","Elektronische Quelle","Modulversion","Aktualisierungsdatum","Inhalt und Auszeichnung","Von der Quelle nicht angegeben"),"uk" to listOf("Про джерело","Повна назва","Коротка назва","Мова","Автор / перекладач / упорядник","Видання","Електронне джерело","Версія модуля","Дата оновлення","Склад і розмітка","Не вказано джерелом"),"en" to listOf("About the source","Full name","Short name","Language","Author / translator / compiler","Edition","Electronic source","Module version","Update date","Contents and markup","Not supplied by the source"))
internal fun moduleSourceTitle(language:String)=(sourceLabels[language]?:sourceLabels.getValue("en"))[0]
internal fun publishedSourceFields(metadata:JsonObject,version:String?=null):List<String?>{
 fun value(vararg names:String)=names.mapNotNull{(metadata[it] as? JsonPrimitive)?.takeIf{value->value.isString}?.contentOrNull?.takeIf{value->value.isNotBlank()}}.firstOrNull()?.let(::studySourceName)?.takeIf{it.isNotBlank()}
 fun clean(value:String?)=value?.let(::studySourceName)?.takeIf{it.isNotBlank()}
 val language=metadata["language"] as? JsonObject
 return listOf(value("name","title"),value("short_name"),value("language_code","language")?:clean((language?.get("name") as? JsonPrimitive)?.contentOrNull)?:clean((language?.get("code") as? JsonPrimitive)?.contentOrNull),value("author","translator","compiler"),value("edition"),value("source_url","source","electronic_source"),clean(version)?:value("content_version","version"),value("updated_at","updated"))
}
@Composable internal fun ModuleSourceCard(language:String,metadata:JsonObject,version:String?=null,capabilities:List<String> = emptyList()){
 val labels=sourceLabels[language]?:sourceLabels.getValue("en")
 val fields=publishedSourceFields(metadata,version)
 val content=capabilities.map(::studySourceName).filter{it.isNotBlank()}.joinToString(" · ")
 if(fields.all{it==null}&&content.isBlank())return
 var expanded by remember(metadata,version){mutableStateOf(false)}
 Card(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
  TextButton(onClick={expanded=!expanded}){Text(labels[0])}
  if(expanded){
   fields.forEachIndexed{index,value->if(value!=null){Text(labels[index+1],style=MaterialTheme.typography.labelMedium);Text(value)}}
   if(content.isNotBlank()){Text(labels[9],style=MaterialTheme.typography.labelMedium);Text(content)}
  }
 }}
}
