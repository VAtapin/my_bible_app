package com.bibledesktop.myapp.ui.study

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.bibledesktop.shared.api.ReferenceTarget
internal data class NumberingTexts(val statuses:List<String>,val warning:String,val source:String,val edition:String,val version:String)
internal fun numberingTexts(language:String)=when(language){
 "ru"->NumberingTexts(listOf("Нумерация неизвестна","Исходная нумерация","Неоднозначное соответствие","Соответствие подтверждено"),"Соответствие источника нумерации редакции не подтверждено. Предпросмотр скрыт. Сохранённую ссылку можно открыть вручную; тот же библейский отрывок не гарантирован.","Система источника","Система редакции","Версия сопоставления")
 "de"->NumberingTexts(listOf("Nummerierung unbekannt","Originalnummerierung","Mehrdeutige Entsprechung","Entsprechung bestätigt"),"Entsprechung zwischen Quelle und Ausgabe unbestätigt. Vorschau verborgen. Der gespeicherte Verweis kann manuell geöffnet werden; derselbe Bibelabschnitt ist nicht garantiert.","Quellensystem","Ausgabensystem","Zuordnungsversion")
 "uk"->NumberingTexts(listOf("Нумерація невідома","Вихідна нумерація","Неоднозначна відповідність","Відповідність підтверджено"),"Відповідність нумерації джерела редакції не підтверджено. Попередній перегляд приховано. Збережене посилання можна відкрити вручну; той самий біблійний уривок не гарантовано.","Система джерела","Система редакції","Версія зіставлення")
 else->NumberingTexts(listOf("Unknown numbering","Original numbering","Ambiguous correspondence","Verified correspondence"),"Source-to-edition correspondence is unverified. Preview hidden. The stored reference may be opened manually; the same biblical passage is not guaranteed.","Source system","Edition system","Mapping version")
}
@Composable internal fun ReferenceNumbering(language:String,targets:List<ReferenceTarget>){val text=numberingTexts(language);targets.map{it.versification}.distinct().forEach{status->val index=if(status.verified)3 else listOf("unknown","raw","ambiguous").indexOf(status.status).coerceAtLeast(0);Text("${text.statuses[index]} · ${text.source}: ${status.sourceProfile?:"—"} · ${text.edition}: ${status.editionProfile?:"—"}${status.mapVersion?.let{" · ${text.version}: $it"}.orEmpty()}");if(!status.verified)Text(text.warning)}}
