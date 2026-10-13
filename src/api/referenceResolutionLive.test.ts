import 'fake-indexeddb/auto'
import {describe,expect,it,vi} from 'vitest'
import {createBibleApi} from './client'
import {createVerseStudyApi} from './verseStudy'
import {guardedReferences,referenceGroups} from '@/services/verseStudy'
import {createStudyPackageApi} from './studyPackages'
import {installStudyPackage,removeStudyPackage} from '@/services/studyPackages'
import {createIndexedDbChapterRepository} from '@/offline/indexedDbChapterRepository'
import {createChapterService} from '@/services/chapterService'
import {installedCrossReferences} from '@/services/installedCrossReferences'
import {bibleApi} from '@/api'

/** Explicit read-only acceptance after the scoped map is published; no default production requests. */
describe.skipIf(!process.env.REFERENCE_MAP_API)('published scoped reference resolution',()=>{
 const options={baseUrl:process.env.REFERENCE_MAP_API!,timeoutMs:30000},bible=createBibleApi(options),study=createVerseStudyApi(options)
 const code='BQ_MYBIBLE_KJV_343776B4C6F1'
 async function references(osis:string,edition=code){const locations=await bible.getVerseLocations!(edition,[osis]);expect(locations).toHaveLength(1);return guardedReferences(await study.references(locations[0]!.verse_id,edition))}
 it('shows the actual text of all 36 admitted links for the published Ps119:1 group',async()=>{
  const value=await references('Ps.119.1'),items=value.references.filter(item=>item.source==='legacy_quote')
  expect(value.reference_resolution?.schema).toBe(2);expect(value.verse.osis_ref).toBe('Ps.119.1');expect(items).toHaveLength(36)
  for(const item of items){expect(item.original_source?.osis_ref).toBe('Ps.119.1');expect(item.original_target?.osis_ref).toBe(item.target.osis_ref);expect(item.readingTarget?.text?.trim()).toBeTruthy();expect(item.readingTarget?.reference?.trim()).toBeTruthy();expect(item.resolution?.status).toBe('verified')}
  expect(referenceGroups(items).every(group=>group.targets.every(target=>target.text?.trim()))).toBe(true)
 },60000)
 it('does not promote a held group or assign the scoped English map to the Russian edition',async()=>{
  for(const [osis,edition] of [['Ps.119.5',code],['Ps.119.1','BQ_RUSSIAN_RST_STRONG']] as const){const value=await references(osis,edition);expect(value.references.filter(item=>item.source==='legacy_quote').every(item=>item.readingTarget===undefined&&item.target.text===null)).toBe(true)}
 },60000)
 it.skipIf(!process.env.REFERENCE_MAP_PACKAGE)('installs the actual published package and resolves the same links from saved chapters without network',async()=>{
  const online=await references('Ps.119.1'),targets=online.references.filter(item=>item.source==='legacy_quote').map(item=>item.readingTarget!)
  const source=(await bible.getVerseLocations!(code,['Ps.119.1']))[0]!,repository=createIndexedDbChapterRepository(),service=createChapterService(bible,repository)
  const addresses=new Map([[`${source.book_slug}:${source.chapter_number}`,{book:source.book_slug,chapter:source.chapter_number}]])
  for(const target of targets)addresses.set(`${target.book_slug}:${target.chapter_number}`,{book:target.book_slug,chapter:target.chapter_number})
  try{
   for(const address of addresses.values())await service.download(code,address.book,address.chapter)
   const manifest=await createStudyPackageApi(options).manifest(),pack=manifest.packages.find(item=>item.id==='CROSS_REFERENCES');expect(pack).toBeDefined()
   const installed=await installStudyPackage(pack!,options.baseUrl,new AbortController().signal,()=>{});expect(installed.metadata.versification_schema).toBe(2)
   const network=vi.spyOn(bibleApi,'getChapter').mockRejectedValue(Error('Offline acceptance must not fetch a chapter'))
   const offline=guardedReferences((await installedCrossReferences(source.verse_id,code,'Ps.119.1'))!)
   expect(offline.references.filter(item=>item.source==='legacy_quote').map(item=>({id:item.id,osis:item.readingTarget?.osis_ref,text:item.readingTarget?.text})).sort((a,b)=>a.id-b.id)).toEqual(online.references.filter(item=>item.source==='legacy_quote').map(item=>({id:item.id,osis:item.readingTarget?.osis_ref,text:item.readingTarget?.text})).sort((a,b)=>a.id-b.id))
   expect(network).not.toHaveBeenCalled()
  }finally{vi.restoreAllMocks();await removeStudyPackage('CROSS_REFERENCES');for(const saved of await repository.list())await repository.delete(saved.key)}
 },300000)
})
