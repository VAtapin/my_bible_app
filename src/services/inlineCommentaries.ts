import type { CommentaryEntry } from '@/api/study'
import { canonicalStudyPosition,loadCommentaryRange,overlapsStudyRange } from './commentaryRange'
import { createStudyApi } from '@/api/study'
import { createStudyService } from './studyService'
import { apiBaseUrl } from '@/config/api'
import { installedStudyPackages } from './studyPackages'
import { studyPackageRows } from './studyPackageLookup'
import { sourcesAtOsis } from './commentarySourceRules'
import { bibleApi } from '@/api'
import { loadBibleCatalog } from './bibleCatalog'
const service=createStudyService(createStudyApi({baseUrl:apiBaseUrl}))
const pending=new Map<string,{created:number;data:Promise<CommentaryEntry[]>}>()
export function commentarySources(osis:string):string[]{try{const raw=localStorage.getItem(`bible-desktop:commentary-book-sources:${osis.split('.')[0]}`)??localStorage.getItem('bible-desktop:commentary-sources');const value:unknown=JSON.parse(raw??'[]');return sourcesAtOsis(osis,Array.isArray(value)?value.filter((v):v is string=>typeof v==='string').slice(0,30):[])}catch{return[]}}
export async function inlineCommentaries(osis:string,canon:string|null|undefined,codes:string[],translation?:string):Promise<CommentaryEntry[]>{
 if(!codes.length)return[]
 const position=canonicalStudyPosition(osis),book=osis.split('.')[0]!,key=JSON.stringify([canon,translation,book,position.chapter,[...codes].sort()])
 let cached=pending.get(key)
 if(!cached||Date.now()-cached.created>60000){const data=(async()=>{let slug:string|undefined
  for(const pack of await installedStudyPackages()){const table=pack.manifest.kind==='dictionary'?'references.jsonl':pack.manifest.kind==='commentary'?'entries.jsonl':undefined;if(!table)continue;const row=(await studyPackageRows(pack,table,{bookOsis:book,limit:1})).rows[0];if(typeof row?.book_slug==='string'){slug=row.book_slug;break}}
  if(!slug){const selectedCanon=canon??(translation?(await loadBibleCatalog(bibleApi)).find(item=>item.code===translation)?.canon_code:undefined);if(!selectedCanon)throw Error('Canonical identity unavailable');slug=await service.canonicalSlug(selectedCanon,book)}
  return loadCommentaryRange({chapter:position.chapter,verse:1},{chapter:position.chapter,verse:Number.MAX_SAFE_INTEGER},(chapter,offset)=>service.commentaries(slug!,chapter,codes,offset))
 })();cached={created:Date.now(),data};pending.set(key,cached);void data.catch(()=>pending.delete(key));if(pending.size>24)pending.delete(pending.keys().next().value!)}
 return(await cached.data).filter(entry=>overlapsStudyRange(entry,position,position))
}
