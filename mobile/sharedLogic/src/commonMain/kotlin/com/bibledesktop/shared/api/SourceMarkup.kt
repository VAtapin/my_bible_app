package com.bibledesktop.shared.api

/** UTF-16 offsets into the returned text; source semantics require a declared format. */
data class SourceTextRange(val start:Int,val end:Int)
data class SourceHeading(val text:String,val offset:Int)
data class SourceFootnote(val marker:String,val text:String?,val offset:Int)
data class SourceMarkup(val text:String,val addedWords:List<SourceTextRange> = emptyList(),val headings:List<SourceHeading> = emptyList(),val footnotes:List<SourceFootnote> = emptyList(),val paragraphs:List<Int> = emptyList())
private fun decodeSourceEntities(value:String)=Regex("&(#x[0-9a-f]+|#\\d+|amp|lt|gt|quot|apos|nbsp);",RegexOption.IGNORE_CASE).replace(value){match->
 val entity=match.groupValues[1].lowercase()
 if(entity.startsWith("#")){val hex=entity.startsWith("#x");val point=entity.drop(if(hex)2 else 1).toIntOrNull(if(hex)16 else 10)?:0;when{point in 1..0xffff->point.toChar().toString();point in 0x10000..0x10ffff->{val n=point-0x10000;"${(0xd800+(n shr 10)).toChar()}${(0xdc00+(n and 0x3ff)).toChar()}"};else->""}}else mapOf("amp" to "&","lt" to "<","gt" to ">","quot" to "\"","apos" to "'","nbsp" to " ")[entity].orEmpty()
}
private fun compactSource(value:String)=decodeSourceEntities(value.replace(Regex("<[^>]*>"),"")).replace(Regex("\\s+")," ").trim()
fun parseSourceMarkup(raw:String,format:String?):SourceMarkup{
 val safe=raw.replace(Regex("<(script|style|iframe|object)\\b[^>]*>[\\s\\S]*?</\\1\\s*>",RegexOption.IGNORE_CASE),"")
 if(format!="mybible")return SourceMarkup(compactSource(safe))
 val body=StringBuilder();val added=mutableListOf<SourceTextRange>();val headings=mutableListOf<SourceHeading>();val notes=mutableListOf<SourceFootnote>();val paragraphs=mutableListOf<Int>()
 var addedStart:Int?=null;var captureTag:String?=null;var captureOffset=0;var captureBody=StringBuilder()
 for(token in Regex("<[^>]*>|[^<]+|<").findAll(safe).map{it.value}){
  val tagMatch=Regex("^<\\s*(/?)\\s*([a-z]+)\\b[^>]*>$",RegexOption.IGNORE_CASE).matchEntire(token)
  if(tagMatch==null){val value=decodeSourceEntities(token);if(captureTag!=null)captureBody.append(value)else body.append(value);continue}
  val closing=tagMatch.groupValues[1].isNotEmpty();val tag=tagMatch.groupValues[2].lowercase()
  if(captureTag!=null){if(closing&&tag==captureTag){val value=compactSource(captureBody.toString());if(value.isNotBlank())when(tag){"h"->headings+=SourceHeading(value,captureOffset);"f"->notes+=SourceFootnote(value,null,captureOffset);"n"->notes+=SourceFootnote("*",value,captureOffset)};captureTag=null};continue}
  if(!closing&&tag in setOf("h","f","s","n")){captureTag=tag;captureOffset=body.length;captureBody=StringBuilder();continue}
  if(tag=="i"){if(!closing)addedStart=body.length else{addedStart?.let{added+=SourceTextRange(it,body.length)};addedStart=null}}
  if(!closing&&tag in setOf("p","pb","br")&&body.isNotEmpty()){body.append(' ');paragraphs+=body.length}
 }
 val offsets=IntArray(body.length+1);val plain=StringBuilder();var pending=false
 for(index in body.indices){offsets[index]=plain.length;val char=body[index];if(char.isWhitespace())pending=plain.isNotEmpty()else{if(pending){plain.append(' ');pending=false};offsets[index]=plain.length;plain.append(char)}}
 offsets[body.length]=plain.length
 return SourceMarkup(plain.toString(),added.map{SourceTextRange(offsets[it.start],offsets[it.end])}.filter{it.end>it.start},headings.map{it.copy(offset=offsets[it.offset])},notes.map{it.copy(offset=offsets[it.offset])},paragraphs.map{offsets[it]}.distinct().filter{it>0&&it<plain.length})
}
