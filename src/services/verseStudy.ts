import { ApiError } from '@/api/client'
import type { VerseStudyApi,StudyCrossReference,StudyReferenceTarget,StudyStrongEntry,StudyReferences } from '@/api/verseStudy'
import { studyCache,type StudyCache } from './studyService'
import { installedStrongEntries } from './studyPackageLookup'
import { installedCrossReferences } from './installedCrossReferences'
import {referenceVersification,verifiedReference}from'@/api/verseStudy'

export type InstalledStrongReader = (number:string)=>Promise<{entries:Record<string,unknown>[];lexicons:Record<string,unknown>[]} | undefined>
export function guardedReferences(value:StudyReferences):StudyReferences{return{...value,references:value.references.map(item=>{const status=referenceVersification(item.versification);return{...item,versification:status,target:{...item.target,versification:status,text:verifiedReference(status)?item.target.text:null}}})}}
export function installedStrongArticle(number:string,data:NonNullable<Awaited<ReturnType<InstalledStrongReader>>>,lexiconCode?:string):StudyStrongEntry {
  const entry=data.entries.find(e=>e.id===number&&(!lexiconCode||e.lexicon_code===lexiconCode)),lexicon=entry&&data.lexicons.find(l=>l.code===entry.lexicon_code)
  if(!entry||!lexicon||typeof lexicon.code!=='string'||typeof lexicon.name!=='string'||typeof lexicon.language!=='string'||[entry.word,entry.transliteration,entry.pronunciation,entry.content].some(v=>v!==null&&typeof v!=='string'))throw new ApiError('invalid-response','Strong entry absent or invalid in installed lexicon')
  return{number,word:entry.word as string|null,transliteration:entry.transliteration as string|null,pronunciation:entry.pronunciation as string|null,content:entry.content as string|null,lexicon:{code:lexicon.code,name:lexicon.name,language:lexicon.language}}
}

export function verseStudyService(api:VerseStudyApi,cache:StudyCache=studyCache,installed:InstalledStrongReader=installedStrongEntries){
  async function saved<T>(key:string,read:()=>Promise<T>):Promise<T>{
    try{const value=await read();await cache.write(key,value).catch(()=>{});return value}
    catch(error){if(!(error instanceof ApiError)||!['offline','timeout'].includes(error.kind))throw error;const value=await cache.read<T>(key);if(value===undefined)throw error;return value}
  }
  return {references:async(id:number,code:string,osis?:string)=>guardedReferences(await installedCrossReferences(id,code,osis)??await saved(`verse-study:references:${code}:${id}`,()=>api.references(id,code))),tokens:(id:number,code:string)=>saved(`verse-study:tokens:${code}:${id}`,()=>api.tokens(id,code)),strong:async(number:string,id?:number,lexiconCode?:string)=>{
    if(!/^[HG][1-9]\d{0,4}$/.test(number))throw new Error('Explicit Hebrew/Greek Strong number required')
    const data=await installed(number);if(data){const previous=await cache.read<StudyStrongEntry>(`verse-study:strong:${number}:${id??''}`);return installedStrongArticle(number,data,lexiconCode??previous?.lexicon.code)}
    return saved(`verse-study:strong:${number}:${id??''}`,()=>api.strong(number,id))
  }}
}
export interface ReferenceGroup { key:string;source:string;type:string;targets:StudyReferenceTarget[];label:string }
/** Consecutive, explicitly published targets form a range; gaps and sources stay separate. */
export function referenceGroups(references:StudyCrossReference[],bookOrder:Record<string,number>={}):ReferenceGroup[]{
  references=references.map(item=>{const status=referenceVersification(item.versification);return{...item,target:{...item.target,versification:status,text:verifiedReference(status)?item.target.text:null}}})
  const groups:ReferenceGroup[]=[]
  const buckets=new Map<string,StudyCrossReference[]>()
  for(const item of references){const osis=item.target.osis_ref.split('.');const key=JSON.stringify([item.source??'',item.type??'',item.metadata?.legacy_quote_id??'',item.metadata?.raw_ref??'',osis[0],osis[1],item.target.chapter_number,item.target.versification]);const bucket=buckets.get(key)??[];if(!bucket.some(r=>r.target.osis_ref===item.target.osis_ref))bucket.push(item);buckets.set(key,bucket)}
  for(const [key,bucket]of buckets){bucket.sort((a,b)=>a.target.verse_number-b.target.verse_number);let current:ReferenceGroup|undefined
    for(const ref of bucket){if(!current||ref.target.verse_number!==current.targets.at(-1)!.verse_number+1){current={key:`${key}:${ref.target.verse_number}`,source:ref.source??'',type:ref.type??'',targets:[],label:ref.target.reference};groups.push(current)}current.targets.push(ref.target);if(current.targets.length>1)current.label=`${current.targets[0]!.reference}–${ref.target.verse_number}`}
  }
  return groups.sort((a,b)=>(bookOrder[a.targets[0]!.osis_ref.split('.')[0]!]??999)-(bookOrder[b.targets[0]!.osis_ref.split('.')[0]!]??999)||a.targets[0]!.chapter_number-b.targets[0]!.chapter_number||a.targets[0]!.verse_number-b.targets[0]!.verse_number||a.source.localeCompare(b.source))
}
export function explicitStrong(number:string,testament?:string):string|undefined{
  const match=/^([HG]?)(\d{1,5})$/iu.exec(number.trim());if(!match)return
  const prefix=match[1]?.toUpperCase()||(testament==='old'?'H':testament==='new'?'G':'')
  return prefix&&Number(match[2])>0?`${prefix}${Number(match[2])}`:undefined
}
