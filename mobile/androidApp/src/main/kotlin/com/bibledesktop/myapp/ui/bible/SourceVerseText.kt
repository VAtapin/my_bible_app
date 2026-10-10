package com.bibledesktop.myapp.ui.bible

import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.dp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.bibledesktop.shared.api.*

internal fun annotatedSourceVerse(verse:BibleVerse,body:AnnotatedString,display:ReaderPreferences,wordNotes:List<WordMark> = emptyList()):AnnotatedString {
 val source=verse.annotations?.takeIf{it.status=="available"&&it.validFor(verse.plainText)&&body.text==verse.plainText}
 val strong=if(source!=null)verse.explicitSourceStrongTokens()else emptyList()
 val validNotes=wordNotes.filter{!display.clean&&it.sourceText==verse.plainText&&it.start>=0&&it.end<=verse.plainText.length&&it.end>it.start&&it.note.isNotBlank()&&verse.plainText.substring(it.start,it.end)==it.quote&&body.text==verse.plainText}
 val points=(listOf(0,body.length)+source?.let{a->a.headings.map{it.offset}+a.footnotes.map{it.offset}+a.paragraphBreaks.map{it.offset}+a.lineBreaks.map{it.offset}+(a.addedWords+a.emphasis+a.quotations+a.redLetters).flatMap{listOf(it.start,it.end)}}.orEmpty()+strong.map{it.offset}+validNotes.map{it.end}).distinct().sorted()
 return buildAnnotatedString{
  points.forEachIndexed{index,offset->
   if(!display.clean&&source!=null){
    if(body.text.getOrNull(offset)!='\n'){if(display.paragraphs&&(offset==0&&source.paragraphBefore==true||source.paragraphBreaks.any{it.offset==offset}))append("\n\n")else if(display.paragraphs&&source.lineBreaks.any{it.offset==offset})append("\n")}
    if(display.headings)source.headings.filter{it.offset==offset}.forEach{heading->append("\n");withStyle(SpanStyle(fontWeight=FontWeight.Bold)){append(heading.text)};append("\n")}
    if(display.footnotes)source.footnotes.filter{it.offset==offset}.forEach{note->pushStringAnnotation("source-footnote",note.id);withStyle(SpanStyle(color=Color(0xff1e5aa8),fontWeight=FontWeight.Bold)){append(note.marker?:"*")};pop()}
   }
   if(display.strongNumbers&&!display.clean)strong.filter{it.offset==offset}.forEach{token->pushStringAnnotation("source-strong",token.number);withStyle(SpanStyle(color=Color(0xff1e5aa8),fontWeight=FontWeight.Bold)){append(token.number)};pop()}
   validNotes.filter{it.end==offset}.forEach{note->pushStringAnnotation("word-note",note.id);withStyle(SpanStyle(fontWeight=FontWeight.Bold)){append("✎")};pop()}
   val end=points.getOrNull(index+1)?:offset
   if(end>offset){val start=length;append((if(display.paragraphs&&!display.clean)body else AnnotatedString(body.text.replace('\n',' '),body.spanStyles,body.paragraphStyles)).subSequence(offset,end));if(!display.clean&&source!=null){if(display.addedWords&&source.addedWords.any{it.start<=offset&&it.end>=end})addStyle(SpanStyle(fontStyle=FontStyle.Italic,textDecoration=TextDecoration.Underline),start,length);if(source.redLetters.any{it.start<=offset&&it.end>=end})addStyle(SpanStyle(color=if(display.night)Color(0xfff4a1a1)else Color(0xff9c2636)),start,length);if((source.emphasis+source.quotations).any{it.start<=offset&&it.end>=end})addStyle(SpanStyle(fontStyle=FontStyle.Italic),start,length)}}
  }
 }
}
private fun sourceNoteLabels(language:String)=when(language){"ru"->listOf("Сноска источника","Тело сноски не сохранено в доступном источнике.","Вернуться к чтению","Источник");"de"->listOf("Quellenfußnote","Der Fußnotentext ist in der verfügbaren Quelle nicht gespeichert.","Zurück zum Lesen","Quelle");"uk"->listOf("Виноска джерела","Текст виноски не збережено в доступному джерелі.","Повернутися до читання","Джерело");else->listOf("Source footnote","The note body is not preserved in the available source.","Return to reading","Source")}
@Composable internal fun SourceVerseText(verse:BibleVerse,body:AnnotatedString,display:ReaderPreferences,style:TextStyle,wordNotes:List<WordMark> = emptyList(),onWordNote:((WordMark)->Unit)?=null,onStrong:((String)->Unit)?=null){
 val language=com.bibledesktop.myapp.ui.bible.LocalSourceAnnotationLanguage.current
 val labels=sourceNoteLabels(language);var note by remember(verse.id){mutableStateOf<FootnoteAnnotation?>(null)}
 val annotated=remember(verse,body,display,wordNotes){annotatedSourceVerse(verse,body,display,wordNotes)}
 val (measurementModifier,onMeasuredLayout)=measuredReadingText()
 ClickableText(annotated,style=style,onTextLayout=onMeasuredLayout,modifier=measurementModifier.semantics{customActions=wordNotes.filter{!display.clean&&it.note.isNotBlank()&&it.sourceText==verse.plainText}.map{mark->CustomAccessibilityAction(personalStudyText(language,"note")+": "+mark.quote){onWordNote?.invoke(mark);onWordNote!=null}}+annotated.getStringAnnotations("source-strong",0,annotated.length).map{it.item}.distinct().map{number->CustomAccessibilityAction(number){onStrong?.invoke(number);onStrong!=null}}+annotated.getStringAnnotations("source-footnote",0,annotated.length).map{it.item}.distinct().mapNotNull{id->verse.annotations?.footnotes?.find{it.id==id}?.let{item->CustomAccessibilityAction("${labels[0]} ${item.marker?:"*"}"){note=item;true}}}},onClick={offset->annotated.getStringAnnotations("source-strong",offset,offset).firstOrNull()?.let{onStrong?.invoke(it.item)};annotated.getStringAnnotations("source-footnote",offset,offset).firstOrNull()?.let{annotation->note=verse.annotations?.footnotes?.find{it.id==annotation.item}};annotated.getStringAnnotations("word-note",offset,offset).firstOrNull()?.let{annotation->wordNotes.find{it.id==annotation.item}?.let{onWordNote?.invoke(it)}}})
 note?.let{item->val actualBody=item.text;AlertDialog(onDismissRequest={note=null},title={Text("${labels[0]} ${item.marker?:"*"}")},text={Column(Modifier.heightIn(max=400.dp).verticalScroll(rememberScrollState())){Text(verse.osisRef);Text("${if(item.bodyAvailable&&actualBody!=null)actualBody.replace(Regex("<[^>]*>"),"")else labels[1]}\n${labels[3]}: ${item.source.kind} · ${item.source.sha256?:""}")}},confirmButton={TextButton(onClick={note=null}){Text(labels[2])}})}
}
internal val LocalSourceAnnotationLanguage=staticCompositionLocalOf{"ru"}
