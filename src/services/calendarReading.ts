import type {BibleChapter,CalendarReading,CalendarReadingPassage} from '@/api/contracts'
import type {ChapterService} from './chapterService'
import {resolvedVerseChapter} from './verseLocations'
import {readWebChapter} from './webBibleLibrary'
import {validateContinuation} from './continuousReading'

export function parsedCalendarPassages(reading:CalendarReading):CalendarReadingPassage[]|undefined{
 const data=reading.reading
 if(data?.schemaVersion!==1||data.parseStatus!=='parsed'||!Array.isArray(data.passages)||!data.passages.length)return
 const valid=data.passages.every(p=>p&&typeof p.book==='string'&&p.start&&p.end&&/^[A-Za-z0-9]+$/.test(p.book)&&[p.start.chapter,p.end.chapter].every(n=>Number.isSafeInteger(n)&&n>0)&&[p.start.verse,p.end.verse].every(n=>n===null||Number.isSafeInteger(n)&&n>0)&&
   (p.end.chapter>p.start.chapter||p.end.chapter===p.start.chapter&&(p.start.verse===null||p.end.verse===null||p.end.verse>=p.start.verse)))
 return valid?data.passages:undefined
}

export interface CalendarReadingPart {passage:CalendarReadingPassage;chapters:BibleChapter[]}
/** Published OSIS endpoints locate actual module chapters; null endpoints mean the actual whole chapter. */
export async function loadCalendarReading(reading:CalendarReading,code:string,service:ChapterService,
 resolve=resolvedVerseChapter,read=readWebChapter,signal?:AbortSignal):Promise<CalendarReadingPart[]>{
 const passages=parsedCalendarPassages(reading);if(!passages||!code)throw Error('Unparsed calendar reading')
 const result:CalendarReadingPart[]=[],cache=new Map<string,BibleChapter>()
 for(const passage of passages){
   signal?.throwIfAborted()
   const firstRef=`${passage.book}.${passage.start.chapter}.${passage.start.verse??1}`
   const lastRef=`${passage.book}.${passage.end.chapter}.${passage.end.verse??1}`
   const first=await resolve(code,firstRef,service)
   signal?.throwIfAborted()
   const last=lastRef===firstRef?first:await resolve(code,lastRef,service)
   signal?.throwIfAborted()
   if(first.translation.code!==code||last.translation.code!==code||first.book.slug!==last.book.slug||last.chapter.number<first.chapter.number||!first.verses.some(v=>v.osis_ref===firstRef&&v.plain_text.trim())||!last.verses.some(v=>v.osis_ref===lastRef&&v.plain_text.trim()))throw Error('Conflicting calendar source')
   cache.set(`${first.book.slug}:${first.chapter.number}`,first);cache.set(`${last.book.slug}:${last.chapter.number}`,last)
   const chapters:BibleChapter[]=[],seen=new Set<string>()
   let previous=first
   for(let number=first.chapter.number;number<=first.book.chapters_count;number++){
     signal?.throwIfAborted()
     const key=`${first.book.slug}:${number}`
     const source=cache.get(key)??await read(service,code,first.book.slug,number)
     signal?.throwIfAborted()
     validateContinuation(previous,source,number);cache.set(key,source);previous=source
     for(const verse of source.verses){if(verse.osis_ref.split('.')[0]!==passage.book)throw Error('Conflicting calendar book')}
     const verses=source.verses.filter(v=>{
       const [,c,n]=v.osis_ref.split('.'),chapter=Number(c),verse=Number(n)
       return chapter>=passage.start.chapter&&chapter<=passage.end.chapter&&
         (chapter!==passage.start.chapter||verse>=(passage.start.verse??1))&&
         (chapter!==passage.end.chapter||passage.end.verse===null||verse<=passage.end.verse)
     })
     for(const verse of verses){if(seen.has(verse.osis_ref))throw Error('Duplicate calendar verse');seen.add(verse.osis_ref)}
     if(verses.length)chapters.push({...source,verses})
     if(number>=last.chapter.number&&(passage.end.verse!==null||source.verses.some(v=>Number(v.osis_ref.split('.')[1])>passage.end.chapter)||number===first.book.chapters_count))break
   }
   if(!seen.has(firstRef)||passage.end.verse!==null&&!seen.has(lastRef)||!chapters.some(c=>c.verses.some(v=>v.plain_text.trim())))throw Error('Calendar passage unavailable')
   result.push({passage,chapters})
 }
 return result
}
