import 'fake-indexeddb/auto'
import { IDBFactory } from 'fake-indexeddb'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import { ApiError } from '@/api/client'
import { createIndexedDbChapterRepository } from '@/offline/indexedDbChapterRepository'
import { createIndexedDbLibraryRepository } from '@/offline/indexedDbLibraryRepository'
import { chapterKey } from '@/offline/chapterRepository'
import { createChapterService } from './chapterService'
import { readWebChapter,webBibleBooks } from './webBibleLibrary'
import { searchVersePage } from './verseSearch'
import type { BibleApi } from '@/api/client'
import type { BibleChapter, TranslationSummary } from '@/api/contracts'
import type { ChapterRepository, StoredChapter } from '@/offline/chapterRepository'
import type { LibraryRepository, OfflinePackage, ReadingLocation, Bookmark } from '@/offline/libraryRepository'
import { createCatalogVersion, createOfflinePackageService } from './offlinePackageService'

const translation: TranslationSummary = {
  code: 'RST', name: 'Синодальный', short_name: 'RST', language: { code: 'ru', name: 'Русский' },
  canon_code: 'bible', has_old_testament: true, has_new_testament: true,
  has_apocrypha: false, has_strong: false, is_default: true,
}

function chapter(number: number): BibleChapter {
  return {
    translation: { code: 'RST', name: 'Синодальный', short_name: 'RST', language: { code: 'ru', name: 'Русский' } },
    book: { slug: 'genesis', name: 'Бытие', short_name: 'Быт.', chapters_count: 2 },
    chapter: { number, verses_count: 1 },
    verses: [{ id: number, number: 1, osis_ref: `Gen.${number}.1`, text: 'Текст', plain_text: 'Текст', has_strong_markup: false }],
  }
}

describe('offline package service', () => {
  it('marks a package complete only after every chapter is stored', async () => {
    const storedChapters: StoredChapter[] = []
    let storedPackage: OfflinePackage | undefined
    const chapters: ChapterRepository = {
      get: vi.fn(),
      put: vi.fn(async (value) => { storedChapters.push(value) }),
      list: vi.fn(async () => storedChapters),
      delete: vi.fn(),
    }
    const library: LibraryRepository = {
      getReadingLocation: vi.fn(async () => undefined),
      saveReadingLocation: vi.fn(async (_value: ReadingLocation) => undefined),
      listBookmarks: vi.fn(async () => [] as Bookmark[]),
      putBookmark: vi.fn(), deleteBookmark: vi.fn(),
      getPackage: vi.fn(async () => storedPackage),
      listPackages: vi.fn(async () => storedPackage ? [storedPackage] : []),
      putPackage: vi.fn(async (value) => { storedPackage = value }),
      deletePackage: vi.fn(),
    }
    const api = {
      getTranslations: vi.fn(),
      getBooks: vi.fn(async () => [{ slug: 'genesis', name: 'Бытие', short_name: null, order: 1, chapters_count: 2 }]),
      getChapter: vi.fn(async (_translation: string, _book: string, number: number) => chapter(number)),
      getPrayers: vi.fn(), getPrayer: vi.fn(), getCalendarDay: vi.fn(),
    } satisfies BibleApi
    const progress = vi.fn()

    const result = await createOfflinePackageService(api, chapters, library).download(translation, progress)

    expect(storedChapters).toHaveLength(2)
    expect(result.chapterCount).toBe(2)
    expect(library.putPackage).toHaveBeenLastCalledWith(expect.objectContaining({ finished: true, complete: true, chapterCount: 2 }))
    expect(progress).toHaveBeenLastCalledWith(expect.objectContaining({ current: 2, total: 2 }))
  })

  it('builds a stable catalog version from book slugs and chapter counts', () => {
    expect(createCatalogVersion([
      { slug: 'genesis', name: 'Бытие', short_name: null, order: 1, chapters_count: 50 },
      { slug: 'exodus', name: 'Исход', short_name: null, order: 2, chapters_count: 40 },
    ])).toBe('catalog-v1:genesis:50|exodus:40')
  })
})

describe('whole Bible packages with durable IndexedDB checkpoints',()=>{
  beforeEach(()=>{globalThis.indexedDB=new IDBFactory()})
  function setup(){
    const chapters=createIndexedDbChapterRepository(),library=createIndexedDbLibraryRepository()
    const books=[{...chapter(1).book,order:1,canonical_book:{osis_code:'Gen',testament:'old',is_deuterocanonical:false}}]
    const api={getBooks:vi.fn(async()=>books),getChapter:vi.fn(async(_code:string,_slug:string,number:number)=>chapter(number)),
      searchVerses:vi.fn().mockRejectedValue(new ApiError('offline','Offline'))} as unknown as BibleApi
    return{chapters,library,api,service:createOfflinePackageService(api,chapters,library)}
  }
  it('reads every downloaded chapter and searches the full translation after reopening offline',async()=>{
    const{api,service}=setup()
    expect((await service.download(translation,()=>{})).complete).toBe(true)
    const chapters=createIndexedDbChapterRepository(),library=createIndexedDbLibraryRepository()
    vi.mocked(api.getBooks).mockRejectedValue(new ApiError('offline','Offline'))
    vi.mocked(api.getChapter).mockRejectedValue(new ApiError('offline','Offline'))
    const books=await webBibleBooks(api,translation.code)
    const reader=createChapterService(api,chapters)
    for(const number of [1,2])expect((await readWebChapter(reader,'RST','genesis',number)).chapter.number).toBe(number)
    expect(await searchVersePage(api,chapters,'RST','Текст',{match:'exact',scope:'old',offset:0,bookMetadata:books})).toMatchObject({local:true,total:2,more:false})
    expect(await createOfflinePackageService(api,chapters,library).inspect('RST')).toMatchObject({stored:{complete:true},refreshing:false,missingChapters:[],totalChapters:2})
  })
  it('resumes cancellation from committed chapters instead of downloading them again',async()=>{
    const{api,chapters,library,service}=setup(),controller=new AbortController()
    await expect(service.download(translation,p=>{if(p.current===1)controller.abort()},controller.signal)).rejects.toMatchObject({name:'AbortError'})
    expect(await library.getPackage('RST')).toMatchObject({chapterCount:1,complete:false,finished:false})
    expect((await createOfflinePackageService(api,chapters,library).auditStored('RST')).stored?.complete).toBe(false)
    vi.mocked(api.getChapter).mockClear()
    expect((await createOfflinePackageService(api,chapters,library).download(translation,()=>{})).complete).toBe(true)
    expect(api.getChapter).toHaveBeenCalledTimes(1)
    expect(api.getChapter).toHaveBeenCalledWith('RST','genesis',2)
  })
  it('never marks storage failure ready and resumes only the uncommitted chapter',async()=>{
    const{api,chapters,library}=setup()
    const failed={...chapters,put:vi.fn(async(value:StoredChapter)=>{if(value.data.chapter.number===2)throw new DOMException('Full','QuotaExceededError');await chapters.put(value)})}
    await expect(createOfflinePackageService(api,failed,library).download(translation,()=>{})).rejects.toMatchObject({name:'QuotaExceededError'})
    expect(await library.getPackage('RST')).toMatchObject({chapterCount:1,complete:false,finished:false})
    expect(await chapters.get(chapterKey('RST','genesis',2))).toBeUndefined()
    vi.mocked(api.getChapter).mockClear()
    expect((await createOfflinePackageService(api,chapters,library).download(translation,()=>{})).complete).toBe(true)
    expect(api.getChapter).toHaveBeenCalledTimes(1)
  })
  it('requires the final durable completion marker even if all chapter files were committed',async()=>{
    const{api,chapters,library,service}=setup()
    const quota={...library,putPackage:vi.fn(async(value:OfflinePackage)=>{if(value.complete)throw new DOMException('Full','QuotaExceededError');await library.putPackage(value)})}
    await expect(createOfflinePackageService(api,chapters,quota).download(translation,()=>{})).rejects.toMatchObject({name:'QuotaExceededError'})
    expect(await library.getPackage('RST')).toMatchObject({chapterCount:2,finished:false,complete:false})
    expect((await service.auditStored('RST')).stored?.complete).toBe(false)
    vi.mocked(api.getChapter).mockClear()
    expect((await service.download(translation,()=>{})).complete).toBe(true)
    expect(api.getChapter).not.toHaveBeenCalled()
  })
  it('retries retryable source errors without losing committed chapters',async()=>{
    const{api,service}=setup();let rejected=false
    vi.mocked(api.getChapter).mockImplementation(async(_code,_book,number)=>{
      if(number===2&&!rejected){rejected=true;throw new ApiError('http','Too many requests',429,10)}
      return chapter(number)
    })
    expect((await service.download(translation,()=>{})).complete).toBe(true)
    expect(api.getChapter).toHaveBeenCalledTimes(3)
    expect(vi.mocked(api.getChapter).mock.calls.map(call=>call[2])).toEqual([1,2,2])
  })
  it('force refresh fetches and stores actual new text and validated annotations',async()=>{
    const{api,chapters,library,service}=setup()
    await service.download(translation,()=>{})
    vi.mocked(api.getChapter).mockClear().mockImplementation(async(_code,_book,number)=>({...chapter(number),verses:[{...chapter(number).verses[0]!,text:'Новый Бог<S>430</S>',plain_text:'Новый Бог',markup_format:'mybible',annotations:{status:'available',paragraph_before:null,paragraph_breaks:[],line_breaks:[],headings:[],footnotes:[],added_words:[],emphasis:[],red_letters:[],strong_tokens:[{strong_number:'H430',token_order:0,offset_utf16:9,grammar_code:null,surface_text:null}],source:{kind:'mybible',sha256:'b'.repeat(64)},features:{headings:'absent',footnotes:'absent',added_words:'absent',paragraphs:'absent'}}}]}))
    const result=await service.download(translation,()=>{},undefined,{forceRefresh:true})
    expect(result).toMatchObject({complete:true,refreshing:false,finished:true})
    expect(result.refresh).toBeUndefined();expect(api.getChapter).toHaveBeenCalledTimes(2)
    const cached=(await chapters.get(chapterKey('RST','genesis',1)))!.data
    expect(cached.verses[0]).toMatchObject({plain_text:'Новый Бог',annotations:{strong_tokens:[{strong_number:'H430'}]}})
    expect((await library.getPackage('RST'))?.refresh).toBeUndefined()
  })
  it('keeps old chapters readable on failed update, but durable refreshing state prevents full-ready',async()=>{
    const{api,chapters,library,service}=setup()
    await service.download(translation,()=>{})
    vi.mocked(api.getChapter).mockImplementation(async(_code,_slug,number)=>{
      if(number===2)throw new ApiError('http','Forbidden',403)
      return{...chapter(number),verses:[{...chapter(number).verses[0]!,plain_text:'Новый текст',text:'Новый текст'}]}
    })
    await expect(service.download(translation,()=>{},undefined,{forceRefresh:true})).rejects.toMatchObject({status:403})
    const saved=await library.getPackage('RST')
    expect(saved).toMatchObject({complete:true,refreshing:true,refresh:{pending:[chapterKey('RST','genesis',2)]}})
    expect((await chapters.get(chapterKey('RST','genesis',2)))!.data.verses[0]?.plain_text).toBe('Текст')
    expect((await service.auditStored('RST')).stored?.complete).toBe(false)
    vi.mocked(api.getChapter).mockClear().mockImplementation(async(_code,_slug,number)=>chapter(number))
    expect((await createOfflinePackageService(api,chapters,library).download(translation,()=>{})).complete).toBe(true)
    expect(api.getChapter).toHaveBeenCalledTimes(1);expect(api.getChapter).toHaveBeenCalledWith('RST','genesis',2)
    expect((await chapters.get(chapterKey('RST','genesis',1)))!.data.verses[0]?.plain_text).toBe('Новый текст')
  })
  it('does not replace a good old chapter with an empty source during refresh',async()=>{
    const{api,chapters,library,service}=setup()
    await service.download(translation,()=>{})
    vi.mocked(api.getChapter).mockImplementation(async(_code,_slug,number)=>number===1?{...chapter(1),chapter:{number:1,verses_count:0},verses:[]}:chapter(number))
    const result=await service.download(translation,()=>{},undefined,{forceRefresh:true})
    expect(result).toMatchObject({complete:false,finished:false,refreshing:true,refresh:{pending:[chapterKey('RST','genesis',1)],unavailable:['Бытие 1']}})
    expect((await chapters.get(chapterKey('RST','genesis',1)))!.data.verses[0]?.plain_text).toBe('Текст')
    expect((await library.getPackage('RST'))?.refreshing).toBe(true)
    expect((await service.auditStored('RST')).stored?.complete).toBe(false)
  })
  it('restarts every chapter when a published revision changes during an interrupted refresh',async()=>{
    const{api,chapters,library,service}=setup()
    const revisionA={...translation,content_revision:'revision-A',offline_size_estimate_bytes:1234}
    await service.download(revisionA,()=>{})
    const original=await library.getPackage('RST')
    const revisionB={...translation,name:'Редакция B',content_revision:'revision-B',offline_size_estimate_bytes:2345}
    vi.mocked(api.getChapter).mockImplementation(async(_code,_slug,number)=>({...chapter(number),verses:[{...chapter(number).verses[0]!,text:'Текст B',plain_text:'Текст B'}]}))
    const cancelled=new AbortController()
    await expect(service.download(revisionB,p=>{if(p.current===1)cancelled.abort()},cancelled.signal,{forceRefresh:true})).rejects.toMatchObject({name:'AbortError'})
    const interrupted=await library.getPackage('RST')
    expect(interrupted).toMatchObject({translation:revisionA,approximateBytes:original!.approximateBytes,refreshing:true,
      refresh:{translation:revisionB,contentRevision:'revision-B',pending:[chapterKey('RST','genesis',2)]}})
    const revisionC={...translation,name:'Редакция C',content_revision:'revision-C',offline_size_estimate_bytes:3456}
    vi.mocked(api.getChapter).mockClear().mockImplementation(async(_code,_slug,number)=>({...chapter(number),verses:[{...chapter(number).verses[0]!,text:'Текст C',plain_text:'Текст C'}]}))
    const writes=vi.spyOn(library,'putPackage')
    const complete=await service.download(revisionC,()=>{})
    expect(writes.mock.calls[0]![0]).toMatchObject({translation:revisionA,refresh:{translation:revisionC,contentRevision:'revision-C',pending:[chapterKey('RST','genesis',1),chapterKey('RST','genesis',2)]}})
    expect(writes.mock.calls[0]![0].refresh!.startedAt).not.toBe(interrupted!.refresh!.startedAt)
    expect(vi.mocked(api.getChapter).mock.calls.map(call=>call[2])).toEqual([1,2])
    expect(complete).toMatchObject({complete:true,refreshing:false,translation:revisionC,translationName:'Редакция C'})
    for(const number of [1,2])expect((await chapters.get(chapterKey('RST','genesis',number)))!.data.verses[0]!.plain_text).toBe('Текст C')
  })
  it('resumes a matching revision and retains the metadata captured by that refresh pass',async()=>{
    const{api,library,service}=setup()
    await service.download({...translation,content_revision:'revision-A'},()=>{})
    const target={...translation,name:'Редакция B',content_revision:'revision-B',offline_size_estimate_bytes:2345}
    const cancelled=new AbortController()
    await expect(service.download(target,p=>{if(p.current===1)cancelled.abort()},cancelled.signal,{forceRefresh:true})).rejects.toMatchObject({name:'AbortError'})
    const startedAt=(await library.getPackage('RST'))!.refresh!.startedAt
    vi.mocked(api.getChapter).mockClear()
    const writes=vi.spyOn(library,'putPackage')
    const complete=await service.download({...target,name:'Изменённое имя',offline_size_estimate_bytes:9999},()=>{})
    expect(writes.mock.calls[0]![0].refresh).toMatchObject({startedAt,translation:target,contentRevision:'revision-B'})
    expect(vi.mocked(api.getChapter).mock.calls.map(call=>call[2])).toEqual([2])
    expect(complete.translation).toEqual(target)
  })
  it('reports real source-empty and missing verses without declaring a complete package',async()=>{
    const{api,service}=setup()
    vi.mocked(api.getChapter).mockImplementation(async(_code,_slug,number)=>number===1?{...chapter(1),chapter:{number:1,verses_count:0},verses:[]}:
      {...chapter(2),chapter:{number:2,verses_count:2},verses:[chapter(2).verses[0]!,{...chapter(2).verses[0]!,id:3,number:2,osis_ref:'Gen.2.2',text:'',plain_text:''}]})
    expect(await service.download(translation,()=>{})).toMatchObject({complete:false,finished:true,chapterCount:1,unavailable:['Бытие 1'],missingVerses:['Gen.2.2']})
    expect((await service.auditStored('RST')).stored?.complete).toBe(false)
  })
  it('audits deleted files, and falls back to saved books only for offline/timeout, never HTTP errors',async()=>{
    const{api,chapters,service}=setup();await service.download(translation,()=>{})
    await chapters.delete(chapterKey('RST','genesis',2))
    expect(await service.auditStored('RST')).toMatchObject({stored:{complete:false,chapterCount:1},missingChapters:[chapterKey('RST','genesis',2)]})
    vi.mocked(api.getBooks).mockRejectedValue(new ApiError('timeout','Timeout'))
    expect(await service.inspect('RST')).toMatchObject({totalChapters:2,stored:{complete:false}})
    vi.mocked(api.getBooks).mockRejectedValue(new ApiError('http','Forbidden',403))
    await expect(service.inspect('RST')).rejects.toMatchObject({status:403})
  })
})
