import type {ReferenceGroup} from './verseStudy'
import type {ChapterService} from './chapterService'
import {resolveVerseLocations} from './verseLocations'
import {readWebChapter} from './webBibleLibrary'
/** Read explicitly stored addresses in the chosen edition; this is not a semantic mapping claim. */
export async function readReferenceText(group:ReferenceGroup,code:string,service:ChapterService,resolve=resolveVerseLocations,read=readWebChapter){
 const refs=[...new Set(group.targets.map(t=>t.osis_ref))],text:Record<string,string>={},chapters=new Map<string,Awaited<ReturnType<typeof read>>>()
 for(let offset=0;offset<refs.length;offset+=200){
  const requested=refs.slice(offset,offset+200),locations=await resolve(code,requested,service)
  for(const location of locations){
   if(!requested.includes(location.osis_ref)||text[location.osis_ref]!==undefined)throw Error('Conflicting reference location')
   const key=`${location.book_slug}:${location.chapter_number}`
   const chapter=chapters.get(key)??await read(service,code,location.book_slug,location.chapter_number)
   if(chapter.translation.code!==code||chapter.book.slug!==location.book_slug||chapter.chapter.number!==location.chapter_number)throw Error('Conflicting reference chapter')
   chapters.set(key,chapter)
   const verse=chapter.verses.find(v=>v.osis_ref===location.osis_ref&&v.id===location.verse_id&&v.number===location.verse_number)
   if(!verse?.plain_text.trim())throw Error('Reference text unavailable')
   text[location.osis_ref]=verse.plain_text
  }
 }
 return text
}
