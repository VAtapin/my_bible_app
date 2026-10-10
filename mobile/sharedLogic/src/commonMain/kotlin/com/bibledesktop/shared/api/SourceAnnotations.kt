package com.bibledesktop.shared.api
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable data class AnnotationOffset(@SerialName("offset_utf16")val offset:Int)
@Serializable data class AnnotationRange(@SerialName("start_utf16")val start:Int,@SerialName("end_utf16")val end:Int)
@Serializable data class HeadingAnnotation(val id:String,val text:String,@SerialName("offset_utf16")val offset:Int,val source:AnnotationSource)
@Serializable data class FootnoteAnnotation(val id:String,val marker:String?,val text:String?,@SerialName("offset_utf16")val offset:Int,val kind:String,@SerialName("body_available")val bodyAvailable:Boolean,val source:AnnotationSource)
@Serializable data class SourceStrongAnnotation(@SerialName("strong_number")val number:String,@SerialName("token_order")val order:Int,@SerialName("offset_utf16")val offset:Int,@SerialName("grammar_code")val grammar:String?=null,@SerialName("surface_text")val surface:String?=null,@SerialName("raw_number")val rawNumber:String?=null,@SerialName("scope_source")val scopeSource:String?=null)
@Serializable data class AnnotationSource(val kind:String,val sha256:String?=null)
fun BibleVerse.explicitSourceStrongTokens():List<SourceStrongAnnotation> = annotations?.takeIf{it.status=="available"&&it.validFor(plainText)}?.strongTokens?.filter{Regex("[HG][0-9]{1,5}",RegexOption.IGNORE_CASE).matches(it.number)&&it.number.substring(1).toInt()>0}?.map{it.copy(number=it.number.first().uppercase()+it.number.substring(1).toInt())}.orEmpty()
fun BibleVerse.sourceStudyStrongTokens():List<StrongToken> {
 val positioned=explicitSourceStrongTokens().map{StrongToken(it.number,it.order,it.surface,it.grammar)}
 val numbers=positioned.map{it.number}.toSet()
 return positioned+sourceStrongNumbers(text,hasStrongMarkup).filter{it !in numbers}.map{StrongToken(it)}
}
@Serializable data class SourceAnnotations(val status:String,@SerialName("paragraph_before")val paragraphBefore:Boolean?=null,@SerialName("paragraph_breaks")val paragraphBreaks:List<AnnotationOffset> = emptyList(),@SerialName("line_breaks")val lineBreaks:List<AnnotationOffset> = emptyList(),val headings:List<HeadingAnnotation> = emptyList(),val footnotes:List<FootnoteAnnotation> = emptyList(),@SerialName("added_words")val addedWords:List<AnnotationRange> = emptyList(),val emphasis:List<AnnotationRange> = emptyList(),@SerialName("red_letters")val redLetters:List<AnnotationRange> = emptyList(),val quotations:List<AnnotationRange> = emptyList(),@SerialName("strong_tokens")val strongTokens:List<SourceStrongAnnotation> = emptyList(),val source:AnnotationSource?=null,val features:Map<String,String> = emptyMap()){
 fun validFor(text:String):Boolean{
  fun position(n:Int)=n in 0..text.length&&!(n>0&&n<text.length&&text[n-1].code in 0xd800..0xdbff&&text[n].code in 0xdc00..0xdfff)
  fun range(r:AnnotationRange)=position(r.start)&&position(r.end)&&r.end>r.start
  fun provenance(s:AnnotationSource)=s.kind in setOf("mybible","biblequote","legacy_html","unknown","mybible_stories","biblequote_heading","mybible_footnotes")&&(s.sha256==null||Regex("[a-fA-F0-9]{64}").matches(s.sha256))
  return status in setOf("available","not_preserved")&&(status!="available"||source!=null)&&(source==null||provenance(source))&&(paragraphBreaks+lineBreaks).all{position(it.offset)}&&(addedWords+emphasis+redLetters+quotations).all(::range)&&headings.all{position(it.offset)&&provenance(it.source)}&&footnotes.all{position(it.offset)&&it.kind in setOf("inline_note","footnote")&&provenance(it.source)}&&strongTokens.all{position(it.offset)&&Regex("[HG]?[0-9]{1,5}",RegexOption.IGNORE_CASE).matches(it.number)&&it.number.replace(Regex("^[HG]",RegexOption.IGNORE_CASE),"").toInt()>0&&it.order>=0&&(it.scopeSource==null||it.scopeSource in setOf("marker","source_metadata","catalog_testament","unknown"))}&&listOf("headings","footnotes","added_words","paragraphs").all{features[it] in setOf("present","absent","unknown")}
 }
}
