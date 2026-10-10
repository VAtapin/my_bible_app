import type { BibleChapter } from '@/api/contracts'
import { ApiError } from '@/api/client'
import { bibleApi } from '@/api'
import { apiBaseUrl } from '@/config/api'
import type { ChapterService } from './chapterService'
import { readWebChapter, webBibleBooks } from './webBibleLibrary'
import { createVerseLocationApi, type VerseLocation } from '@/api/verseLocations'
const remote=createVerseLocationApi({baseUrl:apiBaseUrl})
export async function resolveVerseLocations(code:string,osis:string[],service:ChapterService,lookup=remote):Promise<VerseLocation[]>{
 try{return await lookup(code,osis)}catch(error){
  if(!(error instanceof ApiError)||!(['offline','timeout'].includes(error.kind)||error.kind==='http'&&error.status===404))throw error
  const stored=await service.listStored(),found:VerseLocation[]=[]
  for(const ref of osis){const chapter=stored.find(c=>c.data.translation.code===code&&c.data.verses.some(v=>v.osis_ref===ref&&v.plain_text.trim()))?.data
   const verse=chapter?.verses.find(v=>v.osis_ref===ref);if(chapter&&verse)found.push({verse_id:verse.id,osis_ref:ref,book_slug:chapter.book.slug,chapter_number:chapter.chapter.number,verse_number:verse.number})}
  if(error.kind!=='http'||found.length===osis.length)return found
  // An old server may lack the resolver: only an explicitly verified identical OSIS is acceptable.
  const books=await webBibleBooks(bibleApi,code)
  for(const ref of osis.filter(r=>!found.some(f=>f.osis_ref===r))){const [bookCode,number]=ref.split('.'),book=books.find(b=>b.canonical_book?.osis_code===bookCode);if(!book||Number(number)>book.chapters_count)continue
   const chapter=await readWebChapter(service,code,book.slug,Number(number)),verse=chapter.verses.find(v=>v.osis_ref===ref&&v.plain_text.trim());if(verse)found.push({verse_id:verse.id,osis_ref:ref,book_slug:book.slug,chapter_number:chapter.chapter.number,verse_number:verse.number})}
  return found
 }
}
export async function resolvedVerseChapter(code:string,osis:string,service:ChapterService):Promise<BibleChapter>{
 const location=(await resolveVerseLocations(code,[osis],service))[0];if(!location)throw new Error('Exact verse unavailable')
 const chapter=await readWebChapter(service,code,location.book_slug,location.chapter_number)
 if(!chapter.verses.some(v=>v.osis_ref===osis&&v.id===location.verse_id&&v.number===location.verse_number&&v.plain_text.trim()))throw new Error('Conflicting verse location')
 return chapter
}
