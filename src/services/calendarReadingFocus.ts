import type {BibleChapter,BibleVerse,CalendarReading,CalendarReadingPassage} from '@/api/contracts'
import {parsedCalendarPassages} from './calendarReading'
import {comparePoints} from './personalStudy'
export interface CalendarReadingFocus {title:string;passages:CalendarReadingPassage[]}
export function calendarFocus(reading:CalendarReading,title=reading.display_ref||reading.title):CalendarReadingFocus|undefined{
 const passages=parsedCalendarPassages(reading);return passages&&passages.length<=50?{title,passages}:undefined
}
export function decodeCalendarFocus(value:unknown):CalendarReadingFocus|undefined{
 if(typeof value!=='string'||value.length>16000)return
 try{const parsed=JSON.parse(value);if(!parsed||typeof parsed.title!=='string'||parsed.title.length>1000)return
   return calendarFocus({reading:{schemaVersion:1,parseStatus:'parsed',passages:parsed.passages}} as CalendarReading,parsed.title)
 }catch{return}
}
function matches(part:CalendarReadingPassage,verse:BibleVerse){
 const ref=/^([A-Za-z0-9]+)\.([1-9]\d*)\.([1-9]\d*)$/.exec(verse.osis_ref)
 if(!ref||ref[1]!==part.book)return false
 const point={chapter:Number(ref[2]),verse:Number(ref[3])}
 return comparePoints(point,{chapter:part.start.chapter,verse:part.start.verse??1})>=0&&
  (point.chapter<part.end.chapter||point.chapter===part.end.chapter&&(part.end.verse===null||point.verse<=part.end.verse))
}
export function calendarVerseFocused(focus:CalendarReadingFocus|undefined,verse:BibleVerse){return !!focus?.passages.some(part=>matches(part,verse))}
export function calendarVerseBoundary(focus:CalendarReadingFocus|undefined,source:BibleChapter,verse:BibleVerse,loaded:BibleChapter[]=[source]){
 let start=false,end=false
 for(const part of focus?.passages??[]){
   if(!matches(part,verse))continue
   start ||= verse.osis_ref===`${part.book}.${part.start.chapter}.${part.start.verse??1}`
   if(part.end.verse!==null)end ||= verse.osis_ref===`${part.book}.${part.end.chapter}.${part.end.verse}`
   else {
     const candidates=loaded.filter(c=>c.translation.code===source.translation.code&&c.book.slug===source.book.slug).flatMap(c=>c.verses)
     const later=candidates.some(v=>v.osis_ref.startsWith(`${part.book}.`)&&Number(v.osis_ref.split('.')[1])>part.end.chapter)
     const actualEnd=later||loaded.some(c=>c.translation.code===source.translation.code&&c.book.slug===source.book.slug&&c.chapter.number===c.book.chapters_count)
     const included=candidates.filter(v=>matches(part,v)).sort((a,b)=>{
       const x=a.osis_ref.split('.'),y=b.osis_ref.split('.')
       return comparePoints({chapter:Number(x[1]),verse:Number(x[2])},{chapter:Number(y[1]),verse:Number(y[2])})
     }),last=included.at(-1)
     end ||= actualEnd&&last?.osis_ref===verse.osis_ref
   }
 }
 return{start,end}
}
