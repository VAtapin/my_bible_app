import defaultApiBaseUrl from '../../../config/api-base-url.txt?raw'
export const dictionaryModule='AZBYKA_CU_FULL'
export const dictionaryApiBase=(import.meta.env.VITE_API_BASE_URL?.trim()||defaultApiBaseUrl.trim()).replace(/\/+$/,'')
export type DictionarySearchMode='word'|'meaning'
export interface DictionaryEntry {key:string;headword:string;civil:string;definition:string;source_url:string;source_chapter:number;content_hash:string}
export interface DictionaryLetter {chapter:number;label:string;count:number}
export interface DictionaryResults {data:DictionaryEntry[];total:number;offset:number;limit:number;content_version:string}
export class DictionaryFailure extends Error {
 constructor(public readonly kind:'network'|'http'|'contract',public readonly status?:number,public readonly retryAfter?:number){super(kind)}
}
const record=(value:unknown):value is Record<string,unknown>=>typeof value==='object'&&value!==null
const count=(value:unknown):value is number=>Number.isSafeInteger(value)&&Number(value)>=0
export function isDictionaryEntry(value:unknown):value is DictionaryEntry {
 if(!record(value)||typeof value.key!=='string'||!/^[a-f0-9]{40}$/.test(value.key)||
 typeof value.headword!=='string'||typeof value.civil!=='string'||typeof value.definition!=='string'||
 value.body_format!=='plain-text'||typeof value.content_hash!=='string'||!/^[a-f0-9]{64}$/.test(value.content_hash)||
 !count(value.source_chapter)||value.source_chapter<1||value.source_chapter>36)return false
 return value.source_url===`https://azbyka.ru/otechnik/Spravochniki/polnyj-pravoslavnyj-tserkovnoslavjanskij-slovar/${value.source_chapter}`
}
export function createSlavonicDictionary(fetcher:typeof fetch=fetch,base=dictionaryApiBase){
 let blockedUntil=0
 async function request(path:string,signal?:AbortSignal):Promise<unknown>{
  if(Date.now()<blockedUntil)throw new DictionaryFailure('http',429,Math.ceil((blockedUntil-Date.now())/1000))
  const controller=new AbortController(),abort=()=>controller.abort(),timer=setTimeout(abort,15_000)
  if(signal?.aborted)controller.abort();signal?.addEventListener('abort',abort,{once:true})
  try{
   const response=await fetcher(`${base}${path}`,{signal:controller.signal,headers:{Accept:'application/json'},credentials:'omit'})
   if(!response.ok){const header=response.headers.get('Retry-After'),seconds=Number(header),date=header?Date.parse(header):NaN
    const delay=Number.isFinite(seconds)&&seconds>0?seconds:Number.isFinite(date)?Math.max(1,Math.ceil((date-Date.now())/1000)):undefined
    if(response.status===429)blockedUntil=Date.now()+(delay??60)*1000
    throw new DictionaryFailure('http',response.status,response.status===429?delay??60:undefined)}
   try{return await response.json()}catch{throw new DictionaryFailure('contract')}
  }catch(error){if(error instanceof DictionaryFailure)throw error;throw new DictionaryFailure('network')}
  finally{clearTimeout(timer);signal?.removeEventListener('abort',abort)}
 }
 return {
  async search(q:string,mode:DictionarySearchMode,offset=0,letter?:number,signal?:AbortSignal):Promise<DictionaryResults>{
   if(!count(offset)||q.length>120||!['word','meaning'].includes(mode)||(letter!==undefined&&(!count(letter)||letter<1||letter>36)))throw new DictionaryFailure('contract')
   const params=new URLSearchParams({q:q.trim(),mode,offset:String(offset),limit:'30'});if(letter!==undefined)params.set('letter',String(letter))
   const value=await request(`/dictionaries/${dictionaryModule}/search?${params}`,signal)
   if(!record(value)||value.module_code!==dictionaryModule||!Array.isArray(value.data)||!value.data.every(isDictionaryEntry)||
    !count(value.total)||value.offset!==offset||value.limit!==30||typeof value.content_version!=='string'||!/^[a-f0-9]{64}$/.test(value.content_version))throw new DictionaryFailure('contract')
   return value as unknown as DictionaryResults
  },
  async article(key:string,signal?:AbortSignal):Promise<DictionaryEntry>{
   if(!/^[a-f0-9]{40}$/.test(key))throw new DictionaryFailure('contract')
   const response=await request(`/dictionaries/${dictionaryModule}/entries/${key}`,signal)
   if(!record(response)||!record(response.data))throw new DictionaryFailure('contract')
   if(response.data.module_code!==dictionaryModule||response.data.key!==key)throw new DictionaryFailure('contract')
   const value=response.data.lexical
   if(!isDictionaryEntry(value)||value.key!==key)throw new DictionaryFailure('contract')
   return value
  },
  async alphabet(signal?:AbortSignal):Promise<DictionaryLetter[]>{
   const response=await request(`/dictionaries/${dictionaryModule}/alphabet`,signal)
   if(!record(response)||response.module_code!==dictionaryModule||!Array.isArray(response.data)||!response.data.every(value=>record(value)&&count(value.chapter)&&value.chapter>=1&&value.chapter<=36&&typeof value.label==='string'&&count(value.count)))throw new DictionaryFailure('contract')
   return response.data as DictionaryLetter[]
  },
 }
}
