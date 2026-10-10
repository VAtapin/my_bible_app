package com.bibledesktop.shared.api
import kotlinx.serialization.Serializable

@Serializable data class CommentarySourceRule(val id:String,val book:String,val first:StudyPosition,val last:StudyPosition,val sources:List<String>)
data class CommentarySourceSegment(val first:StudyPosition,val last:StudyPosition,val sources:List<String>)
fun commentarySourcesAt(book:String,position:StudyPosition,base:List<String>,rules:List<CommentarySourceRule>):List<String> = rules.lastOrNull{it.book==book&&it.first<=position&&it.last>=position}?.sources?:base
fun commentarySourceSegments(book:String,first:StudyPosition,last:StudyPosition,base:List<String>,rules:List<CommentarySourceRule>):List<CommentarySourceSegment>{
 val points=mutableListOf(first)
 rules.filter{it.book==book}.forEach{rule->listOf(rule.first,StudyPosition(rule.last.chapter,rule.last.verse+1)).filter{it>first&&it<=last}.forEach{points+=it}}
 val ordered=points.distinct().sorted()
 return ordered.mapIndexed{index,start->val next=ordered.getOrNull(index+1);val end=if(next==null)last else if(next.verse>1)StudyPosition(next.chapter,next.verse-1)else StudyPosition(next.chapter-1,Int.MAX_VALUE);CommentarySourceSegment(start,end,commentarySourcesAt(book,start,base,rules))}
}
suspend fun loadRuleCommentaries(book:String,first:StudyPosition,last:StudyPosition,base:List<String>,rules:List<CommentarySourceRule>,fetch:suspend (Int,Int,List<String>)->CommentaryPage):List<CommentaryEntry>{
 val data=linkedMapOf<Long,CommentaryEntry>()
 for(segment in commentarySourceSegments(book,first,last,base,rules)){if(segment.sources.isEmpty())continue;loadCommentaryRange(segment.first,segment.last){chapter,offset->fetch(chapter,offset,segment.sources)}.filter{it.moduleCode in segment.sources}.forEach{data[it.id]=it}}
 return data.values.toList()
}
