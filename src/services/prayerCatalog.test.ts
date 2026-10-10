import 'fake-indexeddb/auto'
import {IDBFactory} from 'fake-indexeddb'
import {beforeEach,describe,it,expect,vi} from 'vitest'
import {ApiError,type BibleApi} from '@/api/client'
import type {PrayerDetail,PrayerCatalog,PrayerSummary,LiturgicalWorkSummary,LiturgicalWorkVersion} from '@/api/contracts'
import {createIndexedDbDailyContentRepository} from '@/offline/indexedDbDailyContentRepository'
import {createIndexedDbLibraryRepository} from '@/offline/indexedDbLibraryRepository'
import {offlineStores,openOfflineDatabase} from '@/offline/database'
import {createDailyContentService} from './dailyContentService'
import {uniquePrayerCards,prayerLanguages,prayerInGroup,prayerCardTarget} from './prayerCatalog'

const legacy:PrayerDetail={id:1,title:'Правило',short_title:null,language_code:'ru',category:'morning',liturgy_key:null,intro:'Описание правила',body:'Прежний обрывок',source_url:null,sections:[]}
function reviewed(language='cu-civil',revision='a'.repeat(64)):PrayerDetail{return{...legacy,language_code:language,canonical_slug:'prayer-morning-rule',liturgical_work_id:101,group:'rules',groups:['rules'],available_languages:['cu-civil','ru'],completeness:'complete',review_status:'source-verified',content_revision:revision,catalog_visible:true,body:'Первая молитва\n\nПовтор\n\nПовтор\n\nПоследняя молитва',plain_text:'Первая молитва\n\nПовтор\n\nПовтор\n\nПоследняя молитва'}}
function summary(data=reviewed()):PrayerSummary{const{body:_body,plain_text:_plain,source_url:_url,sections:_sections,...item}=data;return{...item,excerpt:item.intro}}
function catalog(data=[summary()]):PrayerCatalog{return{catalog_version:2,data,groups:{short:'Короткие молитвы',rules:'Правила',occasions:'На разные случаи',initial:'Начальные'},external_sources:[{language:'de',title:'External source',url:'https://orthodoxia.de/gebete/gebetbuch',availability:'external-only',offline_available:false}]}}
const metadata:LiturgicalWorkSummary={id:101,slug:'prayer-morning-rule',title:'Правило',collections:['prayers'],available_languages:['cu-civil','ru'],source_url:null,editions:[{code:'verified-civil',title:'Гражданская редакция',language:'cu-civil',orthography:'civil-accented',reader_profile:'full'}],legacy_slugs:['prayer-1','prayer-9'],intro:'Описание отдельно',prayer_group:'rules',prayer_groups:['rules'],completeness:'complete',content_revision:'a'.repeat(64)+':'+ 'b'.repeat(64)}
function version(language='cu-civil',revision='a'.repeat(64)):LiturgicalWorkVersion{return{slug:metadata.slug,title:'Правило',language,edition:'verified-civil',edition_title:'Редакция',orthography:'civil-accented',reader_profile:'full',blocks:[{id:'body',kind:'paragraph',text:reviewed().body}],credit:'Проверенный источник',source_url:'https://example.test/source',content_hash:revision,review_status:'prayer-reviewed',completeness:'complete'}}
function setup(){
  const repository=createIndexedDbDailyContentRepository()
  const api={getPrayerCatalog:vi.fn(async()=>catalog()),getPrayers:vi.fn(async()=>[summary()]),getPrayer:vi.fn(async()=>reviewed()),getLiturgicalWork:vi.fn(async()=>metadata),getLiturgicalVersion:vi.fn(async()=>version())} as unknown as BibleApi
  return{api,repository,service:createDailyContentService(api,repository)}
}
async function corruptState(key:string,value:unknown){const db=await openOfflineDatabase();try{await new Promise<void>((resolve,reject)=>{const tx=db.transaction(offlineStores.state,'readwrite');tx.oncomplete=()=>resolve();tx.onerror=()=>reject(tx.error);tx.objectStore(offlineStores.state).put({key,data:value,savedAt:'now'})})}finally{db.close()}}
describe('reviewed prayer catalogue and durable editions',()=>{
  beforeEach(()=>{globalThis.indexedDB=new IDBFactory()})
  it('has 21 unique work cards and four published groups, with multi-group uses kept on one card',()=>{
    const entries=Array.from({length:21},(_,index)=>({...summary(),id:index+1,canonical_slug:`actual-${index}`,liturgical_work_id:index+101,groups:index===0?['short','occasions']:['rules'],group:index===0?'short':'rules'}))
    const cards=uniquePrayerCards([...entries,{...entries[0]!,id:99,title:'Название применения'}])
    expect(cards).toHaveLength(21);expect(Object.keys(catalog(cards).groups!)).toHaveLength(4)
    expect(cards.filter(item=>prayerInGroup(item,'occasions'))).toHaveLength(1)
    expect(prayerLanguages(cards[0]!)).toEqual(['cu-civil','ru'])
    expect(prayerCardTarget(cards[0]!,'cu-civil')).toEqual({path:'/liturgical/actual-0/cu-civil'})
    expect(()=>prayerCardTarget(cards[0]!,'de')).toThrow()
    expect(uniquePrayerCards([{...summary(),canonical_slug:undefined,id:1},{...summary(),canonical_slug:undefined,id:2}])).toHaveLength(2)
  })
  it('requests only the RU compatibility catalogue and keeps DE external-only without offline editions',async()=>{
    const{api,service}=setup();const result=await service.openPrayerCatalog()
    expect(api.getPrayerCatalog).toHaveBeenCalledWith('ru');expect(api.getPrayers).not.toHaveBeenCalled()
    expect(result.data.data.filter(item=>prayerLanguages(item).includes('de'))).toEqual([])
    expect(result.data.external_sources?.[0]).toMatchObject({availability:'external-only',offline_available:false})
  })
  it('v2 migration prunes incomplete and stale actual editions but keeps different-language revisions and personal bookmarks',async()=>{
    const{api,repository,service}=setup()
    await repository.putPrayer({key:'1',savedAt:'old',data:legacy})
    const ru={...reviewed('ru','b'.repeat(64)),id:9}
    await repository.putPrayer({key:'9',savedAt:'now',data:ru})
    await repository.putPrayer({key:'1',savedAt:'now',data:reviewed()})
    const library=createIndexedDbLibraryRepository()
    await library.putBookmark({key:'personal',translationCode:'RST',translationName:'RST',bookSlug:'genesis',bookName:'Бытие',chapter:1,verse:1,text:'Личная закладка',createdAt:'now'})
    vi.mocked(api.getPrayerCatalog!).mockResolvedValue(catalog([summary(reviewed('cu-civil','c'.repeat(64)))]))
    await service.openPrayerCatalog()
    expect(await repository.getPrayer(1)).toBeUndefined()
    expect((await repository.getPrayer(9,'ru'))?.data.content_revision).toBe('b'.repeat(64))
    expect((await library.listBookmarks())[0]?.key).toBe('personal')
  })
  it('detail-first reviewed response purges old copies and a later v1 rollback cannot render a truncated body',async()=>{
    const{api,repository,service}=setup()
    await repository.putPrayer({key:'8',savedAt:'old',data:{...legacy,id:8}})
    expect((await service.openPrayer(1)).data.body).toBe(reviewed().body)
    expect(await repository.isReviewedPrayerCatalog!()).toBe(true)
    expect(await repository.getPrayer(8)).toBeUndefined()
    vi.mocked(api.getPrayer).mockResolvedValue(legacy)
    await expect(createDailyContentService(api,repository).openPrayer(1)).rejects.toMatchObject({kind:'invalid-response'})
    vi.mocked(api.getPrayerCatalog!).mockResolvedValue({data:[{...legacy,excerpt:null}]})
    await expect(service.openPrayerCatalog()).rejects.toMatchObject({kind:'invalid-response'})
    expect((await repository.getPrayer(1))?.data.body).toBe(reviewed().body)
  })
  it('complete rules and canonical aliases reopen offline, but HTTP/contract failures never show cached bodies',async()=>{
    const{api,repository,service}=setup()
    await service.openPrayerCatalog();const opened=await service.openLiturgicalVersion('prayer-9','cu-civil')
    expect(api.getLiturgicalVersion).toHaveBeenCalledWith(metadata.slug,'cu-civil',undefined)
    expect(opened.metadata?.intro).toBe('Описание отдельно');expect(opened.data.blocks[0]!.text).toBe(reviewed().body)
    vi.mocked(api.getLiturgicalWork!).mockRejectedValue(new ApiError('offline','offline'))
    vi.mocked(api.getLiturgicalVersion).mockRejectedValue(new ApiError('offline','offline'))
    expect((await createDailyContentService(api,repository).openLiturgicalVersion('prayer-9','cu-civil')).offline).toBe(true)
    for(const error of [new ApiError('http','closed',404),new ApiError('http','damaged',409),new ApiError('http','denied',403),new ApiError('invalid-response','wrong DTO')]){
      vi.mocked(api.getLiturgicalVersion).mockRejectedValue(error)
      await expect(service.openLiturgicalVersion('prayer-9','cu-civil')).rejects.toBe(error)
    }
  })
  it('a changed catalogue revision invalidates a canonical body even through its lingering legacy alias',async()=>{
    const{api,repository,service}=setup();await service.openPrayerCatalog();await service.openLiturgicalVersion('prayer-9','cu-civil')
    vi.mocked(api.getPrayerCatalog!).mockResolvedValue(catalog([summary(reviewed('cu-civil','c'.repeat(64)))]));await service.openPrayerCatalog()
    expect(await repository.getLiturgicalVersion!('prayer-9','cu-civil')).toBeUndefined()
    vi.mocked(api.getLiturgicalWork!).mockRejectedValue(new ApiError('offline','offline'));vi.mocked(api.getLiturgicalVersion).mockRejectedValue(new ApiError('offline','offline'))
    await expect(service.openLiturgicalVersion('prayer-9','cu-civil')).rejects.toMatchObject({kind:'offline'})
  })
  it('malformed cached v2 catalogue fails closed offline and an online valid catalogue repairs it',async()=>{
    const{api,repository,service}=setup();await service.openPrayerCatalog()
    await corruptState('prayer-catalog',{...catalog(),data:[{...summary(),content_revision:'not-a-hash'}]})
    vi.mocked(api.getPrayerCatalog!).mockRejectedValue(new ApiError('offline','offline'))
    expect(await repository.getPrayerCatalog!()).toBeUndefined()
    await expect(service.openPrayerCatalog()).rejects.toMatchObject({kind:'invalid-response'})
    vi.mocked(api.getPrayerCatalog!).mockResolvedValue(catalog())
    expect((await service.openPrayerCatalog()).data.catalog_version).toBe(2)
  })
  it('legacy v1 remains readable before any reviewed proof and never guesses a canonical identity',async()=>{
    const{api,repository,service}=setup();vi.mocked(api.getPrayerCatalog!).mockResolvedValue({data:[{...legacy,excerpt:null}]});vi.mocked(api.getPrayer).mockResolvedValue(legacy)
    expect((await service.openPrayerCatalog()).data.catalog_version).toBeUndefined()
    await service.openPrayer(1);vi.mocked(api.getPrayer).mockRejectedValue(new ApiError('offline','offline'))
    expect((await createDailyContentService(api,repository).openPrayer(1)).data).toEqual(legacy)
    expect(prayerCardTarget({...legacy,excerpt:null},'')).toEqual({path:'/prayers/1',query:undefined})
  })
  it('rejects wrong canonical resolver and wrong edition source identities without a cache fallback',async()=>{
    const{api,repository,service}=setup()
    vi.mocked(api.getLiturgicalWork!).mockResolvedValue({...metadata,slug:'different-work',legacy_slugs:[]})
    await expect(service.openLiturgicalVersion('prayer-9','cu-civil')).rejects.toMatchObject({kind:'invalid-response'})
    expect(api.getLiturgicalVersion).not.toHaveBeenCalled()
    vi.mocked(api.getLiturgicalWork!).mockResolvedValue(metadata)
    vi.mocked(api.getLiturgicalVersion).mockResolvedValue({...version(),slug:'different-work'})
    await expect(service.openLiturgicalVersion('prayer-9','cu-civil')).rejects.toMatchObject({kind:'invalid-response'})
    expect(await repository.getLiturgicalVersion!('prayer-9','cu-civil')).toBeUndefined()
  })
  it('a version-first reviewed reader also prevents a later legacy truncated edition from rendering',async()=>{
    const{api,repository,service}=setup();await service.openLiturgicalVersion('prayer-9','cu-civil')
    expect(await repository.isReviewedPrayerCatalog!()).toBe(true)
    vi.mocked(api.getLiturgicalVersion).mockResolvedValue({...version(),completeness:undefined,content_hash:'old-v1',review_status:'legacy',blocks:[{id:'old',kind:'paragraph',text:'Обрывок'}]})
    await expect(createDailyContentService(api,repository).openLiturgicalVersion('prayer-9','cu-civil')).rejects.toMatchObject({kind:'invalid-response'})
    expect((await repository.getLiturgicalVersion!('prayer-9','cu-civil'))?.data.blocks[0]?.text).toBe(reviewed().body)
  })
  it('does not store a malformed modern body and invalid cached editions can be repaired online',async()=>{
    const{api,repository,service}=setup()
    vi.mocked(api.getPrayer).mockResolvedValue({...reviewed(),plain_text:null})
    await expect(service.openPrayer(1)).rejects.toMatchObject({kind:'invalid-response'})
    expect(await repository.isReviewedPrayerCatalog!()).toBe(false)
    await service.openLiturgicalVersion('prayer-9','cu-civil')
    await corruptState(`prayer-version:${metadata.slug}:cu-civil:verified-civil`,{...version(),language:'cu',orthography:'civil'})
    expect(await repository.getLiturgicalVersion!('prayer-9','cu-civil')).toBeUndefined()
    expect((await service.openLiturgicalVersion('prayer-9','cu-civil')).data.content_hash).toBe('a'.repeat(64))
  })
  it('a definitive missing legacy numeric link cannot return its old fragment on a later offline request',async()=>{
    const{api,repository,service}=setup()
    await repository.putPrayer({key:'1',savedAt:'old',data:legacy})
    vi.mocked(api.getPrayer).mockRejectedValue(new ApiError('http','missing',404))
    await expect(service.openPrayer(1)).rejects.toMatchObject({status:404})
    vi.mocked(api.getPrayer).mockRejectedValue(new ApiError('offline','offline'))
    await expect(createDailyContentService(api,repository).openPrayer(1)).rejects.toMatchObject({kind:'offline'})
    await expect(service.openPrayer(1,'ru')).rejects.toMatchObject({kind:'offline'})
    vi.mocked(api.getPrayer).mockResolvedValue(legacy)
    await service.openPrayer(1)
    vi.mocked(api.getPrayer).mockRejectedValue(new ApiError('offline','offline'))
    expect((await service.openPrayer(1)).data.body).toBe(legacy.body)
  })
  it('a rejected CU edition is blocked through default and explicit aliases but a missing DE edition keeps CU intact',async()=>{
    const{api,repository,service}=setup()
    await service.openLiturgicalVersion('prayer-9','cu-civil')
    await expect(service.openLiturgicalVersion('prayer-9','de')).rejects.toMatchObject({status:404})
    vi.mocked(api.getLiturgicalWork!).mockRejectedValue(new ApiError('offline','offline'))
    vi.mocked(api.getLiturgicalVersion).mockRejectedValue(new ApiError('offline','offline'))
    expect((await service.openLiturgicalVersion('prayer-9','cu-civil')).data.blocks[0]?.text).toBe(reviewed().body)
    vi.mocked(api.getLiturgicalVersion).mockRejectedValue(new ApiError('http','damaged',409))
    await expect(service.openLiturgicalVersion('prayer-9','cu-civil')).rejects.toMatchObject({status:409})
    vi.mocked(api.getLiturgicalVersion).mockRejectedValue(new ApiError('offline','offline'))
    await expect(createDailyContentService(api,repository).openLiturgicalVersion(metadata.slug,'cu-civil','verified-civil')).rejects.toMatchObject({kind:'offline'})
    await expect(service.openLiturgicalVersion('prayer-9','cu-civil')).rejects.toMatchObject({kind:'offline'})
  })
  it('keeps explicitly hidden reviewed full texts and proven same-edition versions while removing absent visible cards',async()=>{
    const{api,repository,service}=setup()
    const hidden={...reviewed(),catalog_visible:false}
    vi.mocked(api.getPrayer).mockResolvedValue(hidden)
    await service.openPrayer(1)
    await service.openLiturgicalVersion('prayer-9','cu-civil')
    const visible={...reviewed('ru','b'.repeat(64)),id:9,canonical_slug:'previously-visible',liturgical_work_id:109}
    await repository.putPrayer({key:'9',savedAt:'now',data:visible})
    vi.mocked(api.getPrayerCatalog!).mockResolvedValue(catalog([]))
    await service.openPrayerCatalog()
    expect(await repository.getPrayer(9,'ru')).toBeUndefined()
    expect((await repository.getPrayer(1))?.data.body).toBe(hidden.body)
    vi.mocked(api.getPrayer).mockRejectedValue(new ApiError('offline','offline'))
    vi.mocked(api.getLiturgicalWork!).mockRejectedValue(new ApiError('offline','offline'))
    vi.mocked(api.getLiturgicalVersion).mockRejectedValue(new ApiError('offline','offline'))
    expect((await service.openPrayer(1)).data.catalog_visible).toBe(false)
    expect((await service.openLiturgicalVersion('prayer-9','cu-civil')).data.content_hash).toBe(hidden.content_revision)
    expect((await repository.getPrayerCatalog!())?.data.data).toEqual([])
  })
})
