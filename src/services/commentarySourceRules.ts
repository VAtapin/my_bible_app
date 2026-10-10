import type { CommentaryEntry } from '@/api/study'
import { canonicalStudyPosition,loadCommentaryRange,positionCompare,type StudyPosition } from './commentaryRange'
export interface CommentarySourceRule {id:string;book:string;first:StudyPosition;last:StudyPosition;sources:string[]}
const storageKey='bible-desktop:commentary-range-sources'
export function readCommentarySourceRules():CommentarySourceRule[]{try{const value:unknown=JSON.parse(localStorage.getItem(storageKey)??'[]');if(!Array.isArray(value))return[];return value.filter((rule):rule is CommentarySourceRule=>Boolean(rule&&typeof rule==='object'&&typeof rule.id==='string'&&typeof rule.book==='string'&&rule.first&&rule.last&&[rule.first.chapter,rule.first.verse,rule.last.chapter,rule.last.verse].every(n=>Number.isSafeInteger(n)&&n>0)&&positionCompare(rule.first,rule.last)<=0&&Array.isArray(rule.sources)&&rule.sources.length<=30&&rule.sources.every((s:unknown)=>typeof s==='string')))}catch{return[]}}
export function writeCommentarySourceRules(rules:CommentarySourceRule[]){localStorage.setItem(storageKey,JSON.stringify(rules));window.dispatchEvent(new Event('commentary-sources-changed'))}
export function sourcesAtPosition(book:string,position:StudyPosition,base:string[],rules:CommentarySourceRule[]){return[...rules].reverse().find(rule=>rule.book===book&&positionCompare(rule.first,position)<=0&&positionCompare(rule.last,position)>=0)?.sources??base}
export function sourcesAtOsis(osis:string,base:string[],rules=readCommentarySourceRules()){return sourcesAtPosition(osis.split('.')[0]!,canonicalStudyPosition(osis),base,rules)}
export function sourceRuleSegments(book:string,first:StudyPosition,last:StudyPosition,base:string[],rules:CommentarySourceRule[]){
 const points=[first]
 for(const rule of rules.filter(r=>r.book===book)){for(const point of [rule.first,{chapter:rule.last.chapter,verse:rule.last.verse+1}])if(positionCompare(point,first)>0&&positionCompare(point,last)<=0)points.push(point)}
 const ordered=[...new Map(points.map(p=>[`${p.chapter}:${p.verse}`,p])).values()].sort(positionCompare)
 return ordered.map((start,index)=>{const next=ordered[index+1],end=next?(next.verse>1?{chapter:next.chapter,verse:next.verse-1}:{chapter:next.chapter-1,verse:Number.MAX_SAFE_INTEGER}):last;return{first:start,last:end,sources:sourcesAtPosition(book,start,base,rules)}})
}
export async function loadRuleCommentaries(book:string,first:StudyPosition,last:StudyPosition,base:string[],rules:CommentarySourceRule[],fetch:(chapter:number,offset:number,sources:string[])=>Promise<{entries:CommentaryEntry[];total:number}>,stale:()=>boolean=()=>false){
 const data=new Map<number,CommentaryEntry>()
 for(const segment of sourceRuleSegments(book,first,last,base,rules)){if(!segment.sources.length||stale())continue;const rows=await loadCommentaryRange(segment.first,segment.last,(chapter,offset)=>fetch(chapter,offset,segment.sources),stale);for(const row of rows)if(segment.sources.includes(row.module_code))data.set(row.id,row)}
 return[...data.values()]
}
