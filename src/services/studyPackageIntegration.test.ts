import 'fake-indexeddb/auto'
import { afterEach,describe,expect,it,vi } from 'vitest'
import { zipSync,strToU8 } from 'fflate'
import { installStudyPackage,installedStudyPackages,removeStudyPackage } from './studyPackages'
import { createDictionaryService } from './dictionaryService'
import { installedDictionaryImage } from './installedDictionaries'
import { readCalendarState,writeCalendarState } from '@/offline/calendarMedia'
import type { StudyPackage } from '@/api/studyPackages'
import type { DictionaryApi } from '@/api/dictionaries'
const topic='a'.repeat(40), image='b'.repeat(40)
async function source(body='Original',code='MAPS'){
 const entries={id:topic,api_id:17,topic:'Jerusalem',body,order:1},references={entry_id:topic,entry_api_id:17,book_slug:'john',book_osis:'John',chapter:3,verse_from:1,verse_to:5},media={id:image,api_id:19,fragment_id:'map',mime_type:'image/png',path:`media/${image}.png`}
 const rows=(value:unknown)=>strToU8(JSON.stringify(value)+'\n')
 const png=Uint8Array.from(atob('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aRZkAAAAASUVORK5CYII='),c=>c.charCodeAt(0))
 const zip=zipSync({'module.json':rows({schema:1,kind:'dictionary',code,api_id:7,name:'Actual source',language_code:'ru',source_archive_sha256:'v1'}),'entries.jsonl':rows(entries),'references.jsonl':rows(references),'links.jsonl':strToU8(''),'word_forms.jsonl':rows({standard_form:'Бог',variation:'богом'}),'media_links.jsonl':rows({entry_id:topic,media_id:image,media_api_id:19}),'media.jsonl':rows(media),[`media/${image}.png`]:png})
 const sha256=[...new Uint8Array(await crypto.subtle.digest('SHA-256',zip))].map(b=>b.toString(16).padStart(2,'0')).join(''),pack:StudyPackage={id:code,kind:'dictionary',version:sha256,sha256,bytes:zip.length,url:`/api/offline/packages/${code}`};return{zip,pack,png}
}
const fetchZip=(zip:Uint8Array)=>vi.fn(async()=>new Response(zip as BodyInit,{headers:{'Content-Type':'application/zip'}})) as unknown as typeof fetch
afterEach(async()=>{await removeStudyPackage('MAPS');await removeStudyPackage('RESUME')})
describe('installed study materials end to end',()=>{
 it('installs verified ZIP and reads aliases, article, word forms, context and image with networking disabled',async()=>{
  const {zip,pack,png}=await source();await installStudyPackage(pack,'https://example.test/api',new AbortController().signal,()=>{},fetchZip(zip))
  const network=vi.fn().mockRejectedValue(new Error('Network must not run')), service=createDictionaryService({modules:network,entries:network,article:network,lookup:network,context:network,verse:network} as unknown as DictionaryApi)
  expect((await service.entries('MAPS')).data[0]).toMatchObject({id:17,key:topic})
  expect((await service.article('MAPS',topic)).body).toBe('Original')
  expect(await service.lookup('Богом',['MAPS'])).toEqual([{module_code:'MAPS',standard_form:'Бог'}])
  expect((await service.verseAt(901,'john',3,2,['MAPS'],0,'John')).data).toHaveLength(1)
  expect((await service.verseAt(902,'john',3,6,['MAPS'],0,'John')).data).toHaveLength(0)
  expect(new Uint8Array(await (await installedDictionaryImage('MAPS',19))!.arrayBuffer())).toEqual(png)
  expect(network).not.toHaveBeenCalled()
 })
 it('keeps previous installed text and personal data on corrupt update, then atomically switches to the verified new version',async()=>{
  const original=await source();await installStudyPackage(original.pack,'https://example.test/api',new AbortController().signal,()=>{},fetchZip(original.zip));await writeCalendarState('study-test:personal-note','Keep me')
  const updated=await source('Updated'),bad=updated.zip.slice();bad[10]^=1
  await expect(installStudyPackage(updated.pack,'https://example.test/api',new AbortController().signal,()=>{},fetchZip(bad))).rejects.toThrow('checksum')
  expect((await installedStudyPackages()).find(p=>p.manifest.id==='MAPS')?.manifest.version).toBe(original.pack.version)
  await installStudyPackage(updated.pack,'https://example.test/api',new AbortController().signal,()=>{},fetchZip(updated.zip))
  expect((await installedStudyPackages()).find(p=>p.manifest.id==='MAPS')?.manifest.version).toBe(updated.pack.version)
  expect(await readCalendarState('study-test:personal-note')).toBe('Keep me')
  expect(await readCalendarState(`study-package:MAPS:${original.pack.version}:entries.jsonl`)).toBeUndefined()
 })
 it('never marks cancelled content installed and resumes its persisted bytes with a verified HTTP range',async()=>{
  const {zip,pack}=await source('Resumable','RESUME'),half=Math.floor(zip.length/2),controller=new AbortController()
  const firstFetch=vi.fn(async()=>new Response(new ReadableStream({start(stream){stream.enqueue(zip.slice(0,half));stream.close()}}))) as unknown as typeof fetch
  await expect(installStudyPackage(pack,'https://example.test/api',controller.signal,()=>controller.abort(),firstFetch)).rejects.toThrow()
  expect((await installedStudyPackages()).some(p=>p.manifest.id==='RESUME')).toBe(false)
  const resume=vi.fn(async(_:RequestInfo|URL,init?:RequestInit)=>{expect((init?.headers as Record<string,string>).Range).toBe(`bytes=${half}-`);return new Response(zip.slice(half) as BodyInit,{status:206,headers:{'Content-Range':`bytes ${half}-${zip.length-1}/${zip.length}`}})}) as unknown as typeof fetch
  await installStudyPackage(pack,'https://example.test/api',new AbortController().signal,()=>{},resume)
  expect((await installedStudyPackages()).some(p=>p.manifest.id==='RESUME')).toBe(true)
 })
})
