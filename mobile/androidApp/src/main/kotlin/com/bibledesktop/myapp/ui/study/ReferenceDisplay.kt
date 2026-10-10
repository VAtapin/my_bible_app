package com.bibledesktop.myapp.ui.study

import android.content.Context
import com.bibledesktop.shared.api.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray

internal data class ReferenceDisplaySettings(val list:Boolean=false,val inlineSources:List<String>?=null,val detailSources:List<String>?=null,val bySource:Boolean=false)
internal class ReferenceDisplayStore(context:Context){
 private val prefs=context.getSharedPreferences("bible-desktop-reference-display",Context.MODE_PRIVATE)
 private fun sources(key:String):List<String>?=prefs.getString(key,null)?.let{runCatching{val array=JSONArray(it);(0 until array.length()).map{n->array.getString(n)}.distinct().take(100)}.getOrNull()}
 fun load()=ReferenceDisplaySettings(prefs.getBoolean("list",false),sources("inlineSources"),sources("detailSources"),prefs.getBoolean("bySource",false))
 fun save(value:ReferenceDisplaySettings){prefs.edit().putBoolean("list",value.list).putBoolean("bySource",value.bySource).apply{if(value.inlineSources==null)remove("inlineSources")else putString("inlineSources",JSONArray(value.inlineSources).toString());if(value.detailSources==null)remove("detailSources")else putString("detailSources",JSONArray(value.detailSources).toString())}.apply();changes.value++}
 companion object{val changes=MutableStateFlow(0)}
}
internal fun toggledReferenceSources(selected:List<String>?,source:String,all:List<String>):List<String>{val values=selected?:all;return if(source in values)values-source else values+source}
internal fun referencePreviewText(target:ReferenceTarget,missing:String)=if(target.versification.verified)target.text?.let{com.bibledesktop.myapp.ui.reading.readingText(it)}?.takeIf{it.isNotBlank()}?:missing else ""
internal fun displayReferenceGroups(references:List<CrossReference>,sources:List<String>?=null,order:Map<String,Int> = emptyMap()):List<ReferenceGroup>{
 val map=linkedMapOf<String,ReferenceGroup>()
 referenceGroups(references.filter{sources==null||it.source.orEmpty() in sources},order).forEach{group->
  val key=group.targets.joinToString("|"){"${it.osisRef}:${it.versification}"};val existing=map[key]
  map[key]=if(existing==null)group else existing.copy(source=(existing.source.split(" · ")+group.source).filter{it.isNotBlank()}.distinct().joinToString(" · "),type=listOf(existing.type,group.type).filter{it.isNotBlank()}.distinct().joinToString(" · "))
 };return map.values.toList()
}
internal fun referenceCopyText(group:ReferenceGroup,translation:String,actualText:Map<String,String> = emptyMap()):String {
 val body=group.targets.mapNotNull{target->
  (actualText[target.osisRef] ?: referencePreviewText(target,""))
   .takeIf{it.isNotBlank()}?.let{"${target.verseNumber} $it"}
 }.joinToString("\n")
 return "${group.label} · $translation" + if(body.isNotEmpty()) "\n$body" else ""
}
internal fun referenceSourceLabel(value:String)=value.split(" · ").filter { it.isNotBlank() && !Regex("(?:legacy_|[A-Za-z0-9]+_)[A-Za-z0-9_.-]*").matches(it) }.joinToString(" · ")
internal data class ReferenceDisplayTexts(val compact:String,val list:String,val normal:String,val detail:String,val count:String,val copy:String,val copied:String)
internal fun referenceDisplayTexts(language:String)=when(language){
 "ru"->ReferenceDisplayTexts("Только текст стиха","Ссылки под стихом","Источники при чтении","Источники изучения","Связанные места","Копировать отрывок","Отрывок скопирован")
 "de"->ReferenceDisplayTexts("Nur Verstext","Verweise unter dem Vers","Quellen beim Lesen","Studienquellen","Verknüpfte Stellen","Abschnitt kopieren","Abschnitt kopiert")
 "uk"->ReferenceDisplayTexts("Лише текст вірша","Посилання під віршем","Джерела під час читання","Джерела вивчення","Пов’язані місця","Копіювати уривок","Уривок скопійовано")
 else->ReferenceDisplayTexts("Verse text only","References below verses","Reading sources","Study sources","Linked passages","Copy passage","Passage copied")
}
