import 'fake-indexeddb/auto'
import {describe,expect,it,vi} from 'vitest'
import {createBibleApi} from './client'
import {createVerseStudyApi} from './verseStudy'
import {guardedReferences,referenceGroups} from '@/services/verseStudy'
import {createStudyPackageApi} from './studyPackages'
import {installStudyPackage} from '@/services/studyPackages'
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
 async function acceptsBoundedPsalmRemap(edition:string){
  const original=(await references('Ps.119.2')).references.filter(item=>item.source==='legacy_quote')
  const value=await references('Ps.118.2',edition),items=value.references.filter(item=>item.source==='legacy_quote')
  expect(value.reference_resolution?.sources.find(item=>item.source==='legacy_quote')?.original_osis_refs).toEqual(['Ps.119.2'])
  expect(items.map(item=>item.id).sort((a,b)=>a-b)).toEqual(original.map(item=>item.id).sort((a,b)=>a-b))
  const verified=items.filter(item=>item.readingTarget),raw=items.filter(item=>!item.readingTarget)
  expect(items).toHaveLength(14);expect(verified).toHaveLength(3);expect(raw).toHaveLength(11)
  for(const item of verified){
   const target=item.readingTarget!
   expect(item.original_source?.osis_ref).toBe('Ps.119.2')
   expect(item.original_target?.osis_ref).toMatch(/^Ps\.119\./)
   expect(target.osis_ref).toBe(item.original_target?.osis_ref.replace(/^Ps\.119\./,'Ps.118.'))
   expect(item.resolution?.status).toBe('verified')
   const chapter=await bible.getChapter(edition,target.book_slug,target.chapter_number)
   const verse=chapter.verses.find(verse=>verse.id===target.verse_id&&verse.osis_ref===target.osis_ref)
   expect(verse?.plain_text).toBe(target.text)
  }
  for(const item of raw){expect(item.original_source?.osis_ref).toBe('Ps.119.2');expect(item.target.text).toBeNull();expect(item.resolution?.status).not.toBe('verified')}
  const held=await references('Ps.118.5',edition)
  expect(held.references.filter(item=>item.source==='legacy_quote').every(item=>!item.readingTarget&&item.target.text===null)).toBe(true)
 }
 it.skipIf(!process.env.REFERENCE_MAP_RST)('accepts only proved RST edition Ps119 to Ps118 links and keeps the rest raw',async()=>{
  await acceptsBoundedPsalmRemap('BQ_MYBIBLE_RST_9827BA0EF963')
 },60000)
 it.skipIf(!process.env.REFERENCE_MAP_RST_STRONG)('accepts the separately proved default Russian edition without borrowing RST+ verse hashes',async()=>{
  await acceptsBoundedPsalmRemap('BQ_RUSSIAN_RST_STRONG')
 },60000)
 it.skipIf(!process.env.REFERENCE_MAP_CU)('accepts only proved CSL-PNM Ps119 to Ps118 links and keeps the rest raw',async()=>{
  await acceptsBoundedPsalmRemap('BQ_CSL_PNM')
 },60000)
 it.skipIf(!process.env.REFERENCE_MAP_PACKAGE)('installs the actual published package and resolves the same links from saved chapters without network',async()=>{
  const mark=(stage:string)=>process.stderr.write(`[reference-package] ${stage}\n`)
  const online=await references('Ps.119.1'),targets=online.references.filter(item=>item.source==='legacy_quote').map(item=>item.readingTarget!)
  const source=(await bible.getVerseLocations!(code,['Ps.119.1']))[0]!,repository=createIndexedDbChapterRepository(),service=createChapterService(bible,repository)
  const addresses=new Map([[`${source.book_slug}:${source.chapter_number}`,{book:source.book_slug,chapter:source.chapter_number}]])
  for(const target of targets)addresses.set(`${target.book_slug}:${target.chapter_number}`,{book:target.book_slug,chapter:target.chapter_number})
  try{
   for(const address of addresses.values())await service.download(code,address.book,address.chapter)
   mark(`saved ${addresses.size} actual chapters`)
   const manifest=await createStudyPackageApi(options).manifest(),pack=manifest.packages.find(item=>item.id==='CROSS_REFERENCES');expect(pack).toBeDefined()
   mark(`installing ${pack!.bytes} bytes, SHA ${pack!.sha256}`)
   let lastProgress=0
   const installed=await installStudyPackage(pack!,options.baseUrl,new AbortController().signal,(bytes)=>{if(bytes-lastProgress>=5_000_000){mark(`downloaded ${bytes} bytes`);lastProgress=bytes}});expect(installed.metadata.versification_schema).toBe(2)
   mark('installed and indexed')
   const network=vi.spyOn(bibleApi,'getChapter').mockRejectedValue(Error('Offline acceptance must not fetch a chapter'))
   const offline=guardedReferences((await installedCrossReferences(source.verse_id,code,'Ps.119.1'))!)
   expect(offline.references.filter(item=>item.source==='legacy_quote').map(item=>({id:item.id,osis:item.readingTarget?.osis_ref,text:item.readingTarget?.text})).sort((a,b)=>a.id-b.id)).toEqual(online.references.filter(item=>item.source==='legacy_quote').map(item=>({id:item.id,osis:item.readingTarget?.osis_ref,text:item.readingTarget?.text})).sort((a,b)=>a.id-b.id))
   expect(network).not.toHaveBeenCalled()
   mark('offline result matches online')
  }finally{
   vi.restoreAllMocks()
   // fake-indexeddb is scoped to this isolated Vitest worker; its large index
   // disappears with the worker, so teardown need not enumerate every row.
  }
 },600000)
})
