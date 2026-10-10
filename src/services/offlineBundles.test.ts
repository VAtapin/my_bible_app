import 'fake-indexeddb/auto'
import {describe,it,expect,vi} from 'vitest'
import {zipSync,strToU8} from 'fflate'
import type {BibleApi} from '@/api/client'
import type {BibleChapter,TranslationSummary} from '@/api/contracts'
import type {StudyPackage} from '@/api/studyPackages'
import {createIndexedDbChapterRepository} from '@/offline/indexedDbChapterRepository'
import {createIndexedDbLibraryRepository} from '@/offline/indexedDbLibraryRepository'
import {readCalendarState,writeCalendarState} from '@/offline/calendarMedia'
import {createOfflineBundleCoordinator,bundleSize,saveOfflineBundle,savedOfflineBundles,forgetOfflineBundle,validateBundleIds} from './offlineBundles'
import {cachedBundleAtlas,ensureBundleAtlas} from './bundleAtlas'
function cache(){const rows=new Map<string,Response>();return{open:async()=>({match:async(key:string)=>rows.get(key)?.clone(),put:async(key:string,response:Response)=>{rows.set(key,response.clone())},delete:async(key:string)=>rows.delete(key)})} as unknown as CacheStorage}
const atlas={schema:1,sources:[],land:[],places:[{id:'place',name:'Jerusalem',verses:['John.3.16'],locations:[{lon:35.2,lat:31.7}]}]}
const translation:TranslationSummary={code:'BUNDLE_TEST',name:'Fixture Bible',short_name:null,language:{code:'en',name:'English'},canon_code:'protestant',has_old_testament:true,has_new_testament:true,has_apocrypha:false,has_strong:false,is_default:false,content_revision:'published-v1'}
const books=[{slug:'john',name:'John',short_name:null,order:1,chapters_count:1}]
const chapter:BibleChapter={translation,book:books[0]!,chapter:{number:1,verses_count:1},verses:[{id:1,number:1,osis_ref:'John.1.1',text:'Actual fixture text',plain_text:'Actual fixture text',has_strong_markup:false}]}
async function study(){const zip=zipSync({'module.json':strToU8(JSON.stringify({schema:1,code:'BUNDLE_STRONG',kind:'strong',name:'Fixture Strong'})),'lexicons.jsonl':strToU8(''),'entries.jsonl':strToU8(JSON.stringify({id:'H1',content:'Actual lexicon text'})+'\n')});const sha=[...new Uint8Array(await crypto.subtle.digest('SHA-256',zip))].map(b=>b.toString(16).padStart(2,'0')).join('');return{zip,pack:{id:'BUNDLE_STRONG',kind:'strong',bytes:zip.length,version:sha,sha256:sha,url:'/api/offline/packages/BUNDLE_STRONG'} as StudyPackage}}
describe('unified offline bundles',()=>{
 it('downloads real chapters and verified ZIP and cached atlas, restores custom composition offline and deletes only content',async()=>{
  const {zip,pack}=await study(),storage=cache(),chapters=createIndexedDbChapterRepository(),library=createIndexedDbLibraryRepository()
  const api={getTranslations:vi.fn(async()=>[translation]),getBooks:vi.fn(async()=>books),getChapter:vi.fn(async()=>chapter)} as unknown as BibleApi
  const fetcher=vi.fn(async(url:RequestInfo|URL)=>String(url).includes('/offline/packages/')?new Response(zip as BodyInit):Response.json(atlas)) as unknown as typeof fetch
  const coordinator=createOfflineBundleCoordinator({api,chapters,library,cache:storage,manifest:async()=>[pack],fetcher,baseUrl:'https://example.test/api'})
  const items=['bible:BUNDLE_TEST','study:BUNDLE_STRONG','atlas:openbible-v1'],before=await coordinator.catalog()
  expect(before.find(m=>m.id===items[0])?.state).toBe('not_downloaded')
  expect(coordinator.publishedStudyEmpty).toBe(false)
  expect(bundleSize(before.filter(m=>items.includes(m.id)))).toEqual({known:pack.bytes,estimated:0,unknown:2})
  await writeCalendarState('bundle-test:personal','Keep personal note')
  await saveOfflineBundle({id:'bundle-test',name:'Actual bundle',items})
  const cancelled=new AbortController()
  await expect(coordinator.run(items,p=>{if(p.completed===1)cancelled.abort()},cancelled.signal)).rejects.toMatchObject({name:'AbortError'})
  const partial=await coordinator.catalog();expect(partial.find(m=>m.id===items[0])?.ready).toBe(true);expect(partial.find(m=>m.id===items[1])?.ready).toBe(false)
  const progress=vi.fn();await coordinator.run(items,progress,new AbortController().signal)
  expect((await coordinator.catalog()).filter(m=>items.includes(m.id)).every(m=>m.ready)).toBe(true)
  expect(progress).toHaveBeenLastCalledWith(expect.objectContaining({completed:3,total:3}))
  expect(await cachedBundleAtlas(storage)).toBeDefined();expect(api.getChapter).toHaveBeenCalledTimes(1)
  vi.mocked(api.getChapter).mockResolvedValue({...chapter,verses:[{...chapter.verses[0]!,plain_text:'Updated real text',text:'Updated real text'}]})
  await coordinator.run([items[0]!],()=>{},new AbortController().signal,true)
  expect((await chapters.get('BUNDLE_TEST:john:1'))?.data.verses[0]?.plain_text).toBe('Updated real text')
  expect(api.getChapter).toHaveBeenCalledTimes(2)
  vi.mocked(api.getTranslations).mockResolvedValue([{...translation,content_revision:'published-v2',offline_size_estimate_bytes:5000}])
  const revised=await coordinator.catalog(),bibleMember=revised.find(m=>m.id===items[0])!
  expect(bibleMember.update).toBe(true);expect(bundleSize([bibleMember])).toEqual({known:0,estimated:5000,unknown:0})
  await coordinator.run([items[0]!],()=>{},new AbortController().signal)
  expect(api.getChapter).toHaveBeenCalledTimes(3)
  expect((await coordinator.catalog()).find(m=>m.id===items[0])?.update).toBe(false)
  const offline=createOfflineBundleCoordinator({api:{getTranslations:vi.fn().mockRejectedValue(Error('offline'))} as unknown as BibleApi,chapters,library,cache:storage,manifest:async()=>{throw Error('offline')}})
  expect((await offline.catalog()).filter(m=>items.includes(m.id)).every(m=>m.ready)).toBe(true)
  expect(offline.unavailable).toBe(true);expect((await savedOfflineBundles()).find(b=>b.id==='bundle-test')?.items).toEqual(items)
  await offline.remove(items)
  expect((await offline.catalog()).filter(m=>items.includes(m.id)).every(m=>!m.ready)).toBe(true)
  expect(await readCalendarState('bundle-test:personal')).toBe('Keep personal note')
  await forgetOfflineBundle('bundle-test')
 })
 it('does not trust a complete marker with missing chapters or active refresh and refuses cancelled work',async()=>{
  const library=createIndexedDbLibraryRepository(),chapters=createIndexedDbChapterRepository()
  await library.putPackage({key:'package:BUNDLE_TEST',translationCode:translation.code,translationName:translation.name,translation,books,totalChapters:1,catalogVersion:'v1',chapterCount:1,approximateBytes:1,downloadedAt:'2026-10-10',complete:true,finished:true})
  const api={getTranslations:async()=>[translation]} as unknown as BibleApi,coordinator=createOfflineBundleCoordinator({api,chapters,library,cache:cache(),manifest:async()=>[]})
  const missing=await coordinator.catalog();expect(missing.find(m=>m.id==='bible:BUNDLE_TEST')).toMatchObject({ready:false,state:'partial'})
  expect(coordinator.publishedStudyEmpty).toBe(true)
  const controller=new AbortController();controller.abort();await expect(coordinator.run(['bible:BUNDLE_TEST'],()=>{},controller.signal)).rejects.toMatchObject({name:'AbortError'})
  await chapters.put({key:'BUNDLE_TEST:john:1',data:chapter,savedAt:'2026-10-10'})
  const old=await library.getPackage('BUNDLE_TEST');await library.putPackage({...old!,refreshing:true})
  expect((await coordinator.catalog()).find(m=>m.id==='bible:BUNDLE_TEST')).toMatchObject({ready:false,state:'updating'})
  await coordinator.remove(['bible:BUNDLE_TEST'])
 })
 it('rejects invalid geographic coordinates and never marks failed cache writes ready',async()=>{
  const storage=cache(),signal=new AbortController().signal
  await expect(ensureBundleAtlas(signal,async()=>Response.json({...atlas,places:[{...atlas.places[0],locations:[{lon:999,lat:31}]}]}),storage)).rejects.toThrow()
  expect(await cachedBundleAtlas(storage)).toBeUndefined()
  const broken={open:async()=>({match:async()=>undefined,put:async()=>{throw Error('quota')}})} as unknown as CacheStorage
  await expect(ensureBundleAtlas(signal,async()=>Response.json(atlas),broken)).rejects.toThrow('quota')
 })
 it('migrates legacy study-only compositions to typed durable records without deleting the legacy data',async()=>{
  const key='offline-bundles:recipes:v1',previous=await readCalendarState(key)
  await writeCalendarState(key,undefined)
  const legacy=JSON.stringify([{name:'Saved sources',codes:['BUNDLE_STRONG','OTHER_SOURCE']}]),storage={getItem:vi.fn(()=>legacy),removeItem:vi.fn()}
  vi.stubGlobal('localStorage',storage)
  try{
   expect(await savedOfflineBundles()).toEqual([{id:'legacy-study-0',name:'Saved sources',items:['study:BUNDLE_STRONG','study:OTHER_SOURCE']}])
   expect(await readCalendarState(key)).toMatchObject({schema:1})
   storage.getItem.mockImplementation(()=>{throw Error('Legacy must no longer be needed')})
   expect((await savedOfflineBundles())[0]?.items).toEqual(['study:BUNDLE_STRONG','study:OTHER_SOURCE'])
   expect(storage.removeItem).not.toHaveBeenCalled()
   await expect(saveOfflineBundle({id:'bad',name:'Bad',items:['study:']})).rejects.toThrow('Invalid bundle')
   await writeCalendarState(key,[{id:'valid',name:'Actual',items:['study:BUNDLE_STRONG']},{id:'invalid',name:'Ignored',items:[42]},{id:'x',name:'Wrong',items:'study:BUNDLE_STRONG'}])
   expect(await savedOfflineBundles()).toEqual([{id:'valid',name:'Actual',items:['study:BUNDLE_STRONG']}])
   await writeCalendarState(key,{schema:99,bundles:[{id:'valid',name:'Actual',items:['study:BUNDLE_STRONG']}]})
   expect(await savedOfflineBundles()).toEqual([])
  }finally{vi.unstubAllGlobals();await writeCalendarState(key,previous)}
 })
 it('validates the entire batch before installation or removal, including unavailable well-formed IDs',async()=>{
  const {pack,zip}=await study(),library=createIndexedDbLibraryRepository(),chapters=createIndexedDbChapterRepository(),fetcher=vi.fn(async()=>new Response(zip as BodyInit)) as unknown as typeof fetch
  const api={getTranslations:async()=>[translation],getBooks:vi.fn(async()=>books),getChapter:vi.fn(async()=>chapter)} as unknown as BibleApi
  const coordinator=createOfflineBundleCoordinator({api,chapters,library,manifest:async()=>[pack],fetcher,cache:cache(),baseUrl:'https://example.test/api'})
  await expect(coordinator.run(['study:BUNDLE_STRONG','study:DOES_NOT_EXIST'],()=>{},new AbortController().signal)).rejects.toThrow('source unavailable')
  expect(fetcher).not.toHaveBeenCalled();expect(api.getChapter).not.toHaveBeenCalled()
  expect((await coordinator.catalog()).find(m=>m.id==='study:BUNDLE_STRONG')?.ready).toBe(false)
  await chapters.put({key:'BUNDLE_TEST:john:1',data:chapter,savedAt:'2026-10-10'})
  await library.putPackage({key:'package:BUNDLE_TEST',translationCode:translation.code,translationName:translation.name,translation,books,totalChapters:1,catalogVersion:'v1',chapterCount:1,approximateBytes:1,downloadedAt:'2026-10-10',complete:true,finished:true})
  for(const invalid of [['bible:BUNDLE_TEST','atlas:wrong'],['bible:BUNDLE_TEST','bible:BUNDLE_TEST']]){
   await expect(coordinator.remove(invalid)).rejects.toThrow('Invalid bundle IDs')
   expect(await library.getPackage('BUNDLE_TEST')).toBeDefined();expect(await chapters.get('BUNDLE_TEST:john:1')).toBeDefined()
  }
  expect(()=>validateBundleIds([])).toThrow();expect(()=>validateBundleIds(Array.from({length:101},(_,i)=>`study:ID${i}`))).toThrow()
  await coordinator.remove(['bible:BUNDLE_TEST'])
 })
})
