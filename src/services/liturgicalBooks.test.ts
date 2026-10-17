import 'fake-indexeddb/auto'
import {IDBFactory} from 'fake-indexeddb'
import {beforeEach,describe,it,expect,vi} from 'vitest'
import {ApiError,isLiturgicalWorkVersion,type BibleApi} from '@/api/client'
import type {LiturgicalWorkSummary,LiturgicalWorkVersion} from '@/api/contracts'
import {createIndexedDbDailyContentRepository} from '@/offline/indexedDbDailyContentRepository'
import {createDailyContentService} from './dailyContentService'

const summary:LiturgicalWorkSummary={id:1,slug:'book-source',title:'Book',collections:['prayer-books'],available_languages:['de'],source_url:null,editions:[{code:'modern',title:'Modern',language:'de',orthography:'modern-de',reader_profile:'full'},{code:'historic',title:'Historic',language:'de',orthography:'historic',reader_profile:'full'}]}
const version:LiturgicalWorkVersion={slug:summary.slug,title:'Book',language:'de',edition:'modern',edition_title:'Modern',orthography:'modern-de',reader_profile:'full',blocks:[{id:'b1',kind:'heading',text:'Beginning'},{id:'b2',kind:'rubric',text:'Read twice'},{id:'b3',kind:'text',text:'Ending'}],credit:'Source',source_url:'https://example.test/book',content_hash:'a'.repeat(64),review_status:'source-verified-complete',completeness:'complete',artifactType:'source-book',bookCompleteness:'whole-published-edition',sourceEditionNotes:['Original note'],bookSections:[{id:'first',title:'First',printed_pages:[2,3],works:[{slug:'prayer-unit'}],startBlockId:'b1',endBlockId:'b3'}]}
describe('whole prayer books and separate editions',()=>{
 beforeEach(()=>{globalThis.indexedDB=new IDBFactory()})
 it('accepts complete source-book status and rejects malformed metadata or invented boundaries',()=>{
  expect(isLiturgicalWorkVersion(version)).toBe(true)
  expect(isLiturgicalWorkVersion({...version,review_status:'unreviewed'})).toBe(false)
  expect(isLiturgicalWorkVersion({...version,sourceEditionNotes:[5]})).toBe(false)
  expect(isLiturgicalWorkVersion({...version,bookSections:[{...version.bookSections![0],startBlockId:'missing'}]})).toBe(false)
 })
 it('caches an unfiltered catalogue with mixed collections in one operation',async()=>{
  const repository=createIndexedDbDailyContentRepository()
  const prayer={...summary,slug:'prayer-unit',collections:['prayers']}
  await repository.putLiturgicalWorks?.('',[summary,prayer])
  expect(await repository.getLiturgicalWorks?.('')).toEqual([summary,prayer])
  expect(await repository.getLiturgicalWork?.('prayer-unit')).toEqual(prayer)
  await expect(repository.putLiturgicalWorks?.('prayers',[summary])).rejects.toMatchObject({kind:'invalid-response'})
 })
 it('keeps book catalogue, full rubrics, section metadata and both same-language editions offline without replacing either',async()=>{
  const repository=createIndexedDbDailyContentRepository()
  const api={getLiturgicalWorks:vi.fn(async()=>[summary]),getLiturgicalWork:vi.fn(async()=>summary),getLiturgicalVersion:vi.fn(async(_slug,_language,edition)=>({...version,edition:edition??'modern',blocks:[...version.blocks,{id:'b4',kind:'text',text:edition??'modern'}]}))} as unknown as BibleApi
  const service=createDailyContentService(api,repository)
  await service.openLiturgicalWorks('prayer-books')
  await service.openLiturgicalVersion(summary.slug,'de','modern');await service.openLiturgicalVersion(summary.slug,'de','historic')
  for(const fn of [api.getLiturgicalWorks,api.getLiturgicalWork!,api.getLiturgicalVersion])vi.mocked(fn).mockRejectedValue(new ApiError('offline','Offline'))
  expect(await service.openLiturgicalWorks('prayer-books')).toEqual([summary])
  for(const edition of ['modern','historic']){
   const saved=await service.openLiturgicalVersion(summary.slug,'de',edition)
   expect(saved.offline).toBe(true);expect(saved.data.blocks.at(-1)?.text).toBe(edition)
   expect(saved.data.bookSections).toEqual(version.bookSections);expect(saved.data.sourceEditionNotes).toEqual(version.sourceEditionNotes)
  }
  vi.mocked(api.getLiturgicalVersion).mockRejectedValue(new ApiError('http','Damaged',409))
  await expect(service.openLiturgicalVersion(summary.slug,'de','modern')).rejects.toMatchObject({status:409})
  vi.mocked(api.getLiturgicalVersion).mockRejectedValue(new ApiError('offline','Offline'))
  await expect(service.openLiturgicalVersion(summary.slug,'de','modern')).rejects.toBeDefined()
  expect((await service.openLiturgicalVersion(summary.slug,'de','historic')).data.edition).toBe('historic')
 })
})
