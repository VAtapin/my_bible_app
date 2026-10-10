import type {BibleApi} from '@/api/client'
import type {TranslationSummary} from '@/api/contracts'
import {bibleApi} from '@/api'
import {apiBaseUrl} from '@/config/api'
import {createStudyPackageApi,type StudyPackage} from '@/api/studyPackages'
import {createIndexedDbChapterRepository} from '@/offline/indexedDbChapterRepository'
import {createIndexedDbLibraryRepository} from '@/offline/indexedDbLibraryRepository'
import type {ChapterRepository} from '@/offline/chapterRepository'
import type {LibraryRepository} from '@/offline/libraryRepository'
import {readCalendarState,writeCalendarState} from '@/offline/calendarMedia'
import {createOfflinePackageService,type PackageProgress} from './offlinePackageService'
import {installedStudyPackages,installStudyPackage,removeStudyPackage} from './studyPackages'
import {cachedBundleAtlas,ensureBundleAtlas,bundleAtlasCache,bundleAtlasUrl} from './bundleAtlas'
export interface OfflineBundle{id:string;name:string;items:string[]}
export interface BundleMember{id:string;name:string;kind:'bible'|StudyPackage['kind']|'atlas';bytes:number|null;estimate?:number;ready:boolean;state:'ready'|'not_downloaded'|'partial'|'updating';update:boolean;translation?:TranslationSummary;pack?:StudyPackage}
export interface BundleProgress{completed:number;total:number;member:string;fraction:number;chapter?:PackageProgress}
export function bundleSize(members:BundleMember[]){return{known:members.reduce((sum,m)=>sum+(m.bytes??0),0),estimated:members.reduce((sum,m)=>sum+(m.estimate??0),0),unknown:members.filter(m=>m.bytes===null&&!m.estimate).length}}
const catalogKey='offline-bundles:catalog:v1',bundleKey='offline-bundles:recipes:v1'
const validMemberId=(id:unknown):id is string=>typeof id==='string'&&/^(?:(?:bible|study):[A-Za-z0-9][A-Za-z0-9_.-]*|atlas:openbible-v1)$/.test(id)
export function validateBundleIds(ids:unknown):string[]{
 if(!Array.isArray(ids)||!ids.length||ids.length>100||!Array.from(ids).every(validMemberId)||new Set(ids).size!==ids.length)throw Error('Invalid bundle IDs')
 return [...ids] as string[]
}
function validBundle(value:unknown):value is OfflineBundle{
 if(!value||typeof value!=='object')return false
 const b=value as OfflineBundle
 if(typeof b.id!=='string'||!/^[A-Za-z0-9_-]{1,128}$/.test(b.id)||typeof b.name!=='string'||!b.name.trim()||b.name.length>80)return false
 try{validateBundleIds(b.items);return true}catch{return false}
}
export async function savedOfflineBundles(){
 const current:unknown=await readCalendarState(bundleKey)
 if(Array.isArray(current))return current.filter(validBundle)
 if(current!==undefined){const envelope=current as {schema?:unknown;bundles?:unknown};return envelope?.schema===1&&Array.isArray(envelope.bundles)?envelope.bundles.filter(validBundle):[]}
 let migrated:OfflineBundle[]=[]
 try{const old:unknown=JSON.parse(globalThis.localStorage?.getItem('bible-desktop:study-bundles')??'[]');if(Array.isArray(old))migrated=old.filter(b=>typeof b?.name==='string'&&Array.isArray(b.codes)&&b.codes.every((id:unknown)=>typeof id==='string')).map((b,i)=>({id:`legacy-study-${i}`,name:b.name,items:b.codes.map((id:string)=>`study:${id}`)}))}catch{}
 migrated=migrated.filter(validBundle);await writeCalendarState(bundleKey,{schema:1,bundles:migrated});return migrated
}
export async function saveOfflineBundle(bundle:OfflineBundle){
 if(!validBundle(bundle))throw Error('Invalid bundle')
 await writeCalendarState(bundleKey,{schema:1,bundles:[...(await savedOfflineBundles()).filter(b=>b.id!==bundle.id),{...bundle,name:bundle.name.trim(),items:[...new Set(bundle.items)]}]})
}
export async function forgetOfflineBundle(id:string){await writeCalendarState(bundleKey,{schema:1,bundles:(await savedOfflineBundles()).filter(b=>b.id!==id)})}
export function createOfflineBundleCoordinator(options:{api?:BibleApi;chapters?:ChapterRepository;library?:LibraryRepository;manifest?:()=>Promise<StudyPackage[]>;fetcher?:typeof fetch;cache?:CacheStorage;baseUrl?:string}={}){
 const api=options.api??bibleApi,chapters=options.chapters??createIndexedDbChapterRepository(),library=options.library??createIndexedDbLibraryRepository(),baseUrl=options.baseUrl??apiBaseUrl,fetcher=options.fetcher??fetch,cache=options.cache??globalThis.caches
 const bible=createOfflinePackageService(api,chapters,library)
 let unavailable=false,publishedStudyEmpty=false
 return{
 get unavailable(){return unavailable},
 get publishedStudyEmpty(){return publishedStudyEmpty},
 async catalog():Promise<BundleMember[]>{
  const saved=await readCalendarState<{translations:TranslationSummary[];packs:StudyPackage[]}>(catalogKey)
  const results=await Promise.allSettled([api.getTranslations(),options.manifest?.()??createStudyPackageApi({baseUrl,fetcher}).manifest().then(m=>m.packages)])
  unavailable=results.some(r=>r.status==='rejected')
  publishedStudyEmpty=results[1].status==='fulfilled'&&results[1].value.length===0
  const translations=results[0].status==='fulfilled'?results[0].value:saved?.translations??[],packs=results[1].status==='fulfilled'?results[1].value:saved?.packs??[]
  if(results.some(r=>r.status==='fulfilled'))await writeCalendarState(catalogKey,{translations,packs})
  const [stored,study]=await Promise.all([library.listPackages(),installedStudyPackages()])
  const readyBibles=new Set<string>()
  for(const p of stored){const audit=await bible.auditStored(p.translationCode);if(audit.stored?.complete&&!audit.refreshing)readyBibles.add(p.translationCode)}
  const allTranslations=new Map(translations.map(t=>[t.code,t]));for(const p of stored)if(p.translation&&!allTranslations.has(p.translationCode))allTranslations.set(p.translationCode,p.translation)
  const allPacks=new Map(packs.map(p=>[p.id,p]));for(const p of study)if(!allPacks.has(p.manifest.id))allPacks.set(p.manifest.id,p.manifest)
  const members:BundleMember[]=[...allTranslations.values()].map(translation=>{
   const p=stored.find(p=>p.translationCode===translation.code),previous=p?.translation?.content_revision,revision=translation.content_revision
   const ready=readyBibles.has(translation.code),state:BundleMember['state']=ready?'ready':p?.refreshing?'updating':p?'partial':'not_downloaded'
   return{id:`bible:${translation.code}`,name:translation.name,kind:'bible',bytes:null,estimate:translation.offline_size_estimate_bytes??undefined,ready,state,update:!!previous&&!!revision&&previous!==revision,translation}
  })
  for(const pack of allPacks.values()){const installed=study.find(p=>p.manifest.id===pack.id);members.push({id:`study:${pack.id}`,name:String(installed?.metadata.name??pack.id),kind:pack.kind,bytes:pack.bytes,ready:!!installed,state:installed?'ready':'not_downloaded',update:!!installed&&installed.manifest.version!==pack.version,pack})}
  const atlas=cache?await cachedBundleAtlas(cache):undefined
  members.push({id:'atlas:openbible-v1',name:'OpenBible / Natural Earth',kind:'atlas',bytes:atlas?(await atlas.clone().blob()).size:null,ready:!!atlas,state:atlas?'ready':'not_downloaded',update:false})
  return members
 },
 async run(ids:string[],onProgress:(value:BundleProgress)=>void,signal:AbortSignal,update=false){
  const wanted=validateBundleIds(ids)
  if(signal.aborted)throw new DOMException('Aborted','AbortError')
  const catalog=await this.catalog();let completed=0
  const resolved=wanted.map(id=>{const member=catalog.find(m=>m.id===id);if(!member)throw Error('Bundle source unavailable');return member})
  for(const member of resolved){if(signal.aborted)throw new DOMException('Aborted','AbortError');const id=member.id
   const progress=(fraction:number,chapter?:PackageProgress)=>onProgress({completed,total:wanted.length,member:id,fraction,chapter});progress(0)
   if(!member.ready||member.update||update){
    if(member.translation){const result=await bible.download(member.translation,p=>progress(p.total?p.current/p.total:0,p),signal,{forceRefresh:update||member.update});if(!result.complete)throw Error('Bible contains unavailable chapters or verses')}
    else if(member.pack)await installStudyPackage(member.pack,baseUrl,signal,(bytes,total)=>progress(total?bytes/total:0),fetcher)
    else{if(!cache)throw Error('Offline atlas cache unsupported');await ensureBundleAtlas(signal,fetcher,cache,update)}
   }
   completed++;onProgress({completed,total:wanted.length,member:id,fraction:0})
  }
  const fresh=await this.catalog();if(wanted.some(id=>!fresh.find(m=>m.id===id)?.ready))throw Error('Bundle incomplete')
 },
 async remove(ids:string[]){const wanted=validateBundleIds(ids);for(const id of wanted){
  if(id.startsWith('bible:')){const code=id.slice(6);for(const chapter of await chapters.list())if(chapter.data.translation.code===code)await chapters.delete(chapter.key);await library.deletePackage(code)}
  else if(id.startsWith('study:'))await removeStudyPackage(id.slice(6))
  else if(id==='atlas:openbible-v1'&&cache)await(await cache.open(bundleAtlasCache)).delete(bundleAtlasUrl())
 }},
 async inspectBible(code:string){return bible.inspect(code)},
 }
}
