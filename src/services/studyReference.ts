import { bibleApi } from '@/api'
import { createStudyApi } from '@/api/study'
import { apiBaseUrl } from '@/config/api'
import { createIndexedDbLibraryRepository } from '@/offline/indexedDbLibraryRepository'
import { loadBibleCatalog } from './bibleCatalog'
import type { ChapterService } from './chapterService'
import type { ReferenceGroup } from './verseStudy'
import { createStudyService } from './studyService'
import { readWebChapter,webBibleBooks } from './webBibleLibrary'
import { resolveVerseLocations, resolvedVerseChapter } from './verseLocations'
export interface StudyScriptureReference { book_slug:string;book_osis?:string|null;chapter_number:number|null;verse_from:number|null;verse_to:number|null }
/** Canonical source metadata plus actual edition verses; never guess names or IDs. */
export async function resolveStudyReference(value:StudyScriptureReference,service:ChapterService,source:string,providedOsis?:string,translationCode?:string):Promise<{code:string;group:ReferenceGroup}> {
 const place=await createIndexedDbLibraryRepository().getReadingLocation(),editions=await loadBibleCatalog(bibleApi)
 const edition=translationCode?editions.find(e=>e.code===translationCode):place?editions.find(e=>e.code===place.translationCode):editions.find(e=>e.code==='BQ_RUSSIAN_RST_STRONG')
 if(!edition?.canon_code)throw new Error('Canonical identity unavailable')
 const osis=providedOsis??value.book_osis??(await createStudyService(createStudyApi({baseUrl:apiBaseUrl})).canonicalBooks(edition.canon_code)).find(b=>b.slug===value.book_slug)?.osis_code
 if(!osis)throw new Error('Canonical book unavailable')
 const chapter=value.chapter_number??1,book=(await webBibleBooks(bibleApi,edition.code)).find(b=>b.canonical_book?.osis_code===osis)
 if(!book)throw new Error('Book unavailable')
 const passage=await resolvedVerseChapter(edition.code,`${osis}.${chapter}.${value.verse_from??1}`,service),verse=value.verse_from??1
 if(!verse||value.verse_to!==null&&value.verse_to<verse)throw new Error('Invalid reference')
 const targets=[],references=Array.from({length:(value.verse_to??verse)-verse+1},(_,index)=>`${osis}.${chapter}.${verse+index}`),locations=await resolveVerseLocations(edition.code,references,service)
 for(const ref of references){const location=locations.find(item=>item.osis_ref===ref);if(!location)throw new Error('Exact verse unavailable');const target=location.chapter_number===passage.chapter.number&&location.book_slug===passage.book.slug?passage:await readWebChapter(service,edition.code,location.book_slug,location.chapter_number),item=target.verses.find(v=>v.osis_ref===ref&&v.id===location.verse_id);if(!item?.plain_text.trim())throw new Error('Exact verse unavailable');targets.push({verse_id:item.id,osis_ref:item.osis_ref,reference:`${book.name} ${location.chapter_number}:${location.verse_number}`,book_slug:location.book_slug,chapter_number:location.chapter_number,verse_number:location.verse_number,text:item.plain_text})}
 return{code:edition.code,group:{key:JSON.stringify(value),source,type:'dictionary',label:`${book.name} ${chapter}:${verse}${targets.length>1?`–${value.verse_to}`:''}`,targets}}
}
