import type { BibleChapter, BibleVerse } from '@/api/contracts'
import type { BibleApi } from '@/api/client'
import type { ChapterService } from './chapterService'
import { readWebChapter, webBibleBooks } from './webBibleLibrary'
import { resolveVerseLocations } from './verseLocations'

export function canonicalReferenceOrder(a:string,b:string){const x=a.split('.'),y=b.split('.');return (x[0]??'').localeCompare(y[0]??'')||Number(x[1])-Number(y[1])||Number(x[2])-Number(y[2])||a.localeCompare(b)}
export function compareVerses(primary: BibleChapter, secondary: BibleChapter|BibleChapter[]) {
  const index = (verses: BibleVerse[]) => {
    const result = new Map(verses.map(verse => [verse.osis_ref, verse]))
    if (result.size !== verses.length || verses.some(verse => !verse.osis_ref)) throw new Error('Invalid verse references')
    return result
  }
  const chapters=Array.isArray(secondary)?secondary:[secondary]
  const first=index(primary.verses),second=index(chapters.flatMap(c=>c.verses)),sources=new Map(chapters.flatMap(c=>c.verses.map(v=>[v.osis_ref,c]as const)))
  return [...new Set([...first.keys(),...second.keys()])].sort(canonicalReferenceOrder)
    .map(reference=>({reference,primary:first.get(reference),secondary:second.get(reference),secondaryChapter:sources.get(reference)}))
}
export interface ComparisonFrame{primary:BibleChapter;secondary?:BibleChapter;secondaryChapters?:BibleChapter[]}
export function comparisonFrameRows(frames:ComparisonFrame[]){
 const primaryRefs=new Set(frames.flatMap(f=>f.primary.verses.map(v=>v.osis_ref))),seen=new Set<string>()
 return frames.flatMap(frame=>compareVerses(frame.primary,frame.secondaryChapters??(frame.secondary?[frame.secondary]:[])).filter(row=>{
   if(!row.primary&&primaryRefs.has(row.reference))return false
   if(seen.has(row.reference))return false;seen.add(row.reference);return true
 }).map(row=>({...row,frame}))).sort((a,b)=>canonicalReferenceOrder(a.reference,b.reference))
}
export async function loadComparisonChapters(primary:BibleChapter,code:string,api:BibleApi,service:ChapterService):Promise<BibleChapter[]>{
 if(!code||code===primary.translation.code)throw Error('Invalid translation')
 const refs=primary.verses.map(v=>v.osis_ref)
 const locations=[] as Awaited<ReturnType<typeof resolveVerseLocations>>
 for(let index=0;index<refs.length;index+=200)locations.push(...await resolveVerseLocations(code,refs.slice(index,index+200),service,api.getVerseLocations))
 if(!locations.length)throw Error('Exact comparison unavailable')
 if(locations.some(l=>!refs.includes(l.osis_ref))||new Set(locations.map(l=>l.osis_ref)).size!==locations.length)throw Error('Conflicting comparison identity')
 const groups=new Map<string,typeof locations>()
 for(const l of locations){const key=l.book_slug+':'+l.chapter_number;groups.set(key,[...(groups.get(key)??[]),l])}
 const chapters=await Promise.all([...groups.values()].map(async group=>{
  const l=group[0]!,value=await readWebChapter(service,code,l.book_slug,l.chapter_number)
  if(value.translation.code!==code||value.book.slug!==l.book_slug||value.chapter.number!==l.chapter_number||group.some(location=>!value.verses.some(v=>v.osis_ref===location.osis_ref&&v.id===location.verse_id&&v.number===location.verse_number&&v.plain_text.trim())))throw Error('Conflicting comparison identity')
  return value
 }))
 compareVerses(primary,chapters);return chapters
}

export async function loadComparison(primary: BibleChapter, code: string, api: BibleApi, service: ChapterService): Promise<BibleChapter> {
  if (!code || code === primary.translation.code) throw new Error('Invalid translation')
  // OSIS comes from actual verse references; titles and slugs are not book identities.
  const canonical = primary.verses[0]?.osis_ref.split('.')[0]
  if (!canonical) throw new Error('Unavailable canonical book')
  if(api.getVerseLocations){
    const locations=await resolveVerseLocations(code,primary.verses.map(v=>v.osis_ref),service,api.getVerseLocations)
    const location=locations.find(l=>l.osis_ref===primary.verses[0]?.osis_ref)??locations[0]
    if(!location)throw new Error('Exact comparison unavailable')
    const value=await readWebChapter(service,code,location.book_slug,location.chapter_number)
    if(value.translation.code!==code||value.chapter.number!==location.chapter_number||!value.verses.some(v=>v.osis_ref===location.osis_ref&&v.id===location.verse_id))throw new Error('Conflicting comparison identity')
    compareVerses(primary,value);return value
  }
  const stored = (await service.listStored()).find(({ data }) => data.translation.code === code
    && data.chapter.number === primary.chapter.number && data.verses[0]?.osis_ref.split('.')[0] === canonical)?.data
  const validate = (value: BibleChapter) => {
    if (value.translation.code !== code || value.chapter.number !== primary.chapter.number
      || value.verses.some(verse => verse.osis_ref.split('.')[0] !== canonical)) throw new Error('Invalid comparison chapter')
    compareVerses(primary, value)
    return value
  }
  if (stored) return validate(stored)
  const books = await webBibleBooks(api, code)
  const book = books.find(item => item.canonical_book?.osis_code === canonical)
  if (!book || book.chapters_count < primary.chapter.number) throw new Error('Unavailable canonical book')
  return validate(await readWebChapter(service, code, book.slug, primary.chapter.number))
}
