import { describe, it, expect, vi } from 'vitest'
import { matchesVerse, searchSegments, searchVersePage } from './verseSearch'
import type { BibleApi } from '@/api/client'
import { ApiError } from '@/api/client'
import type { ChapterRepository } from '@/offline/chapterRepository'
import {prepareSearchStemming} from './searchStemming'
describe('verse search semantics', () => {
  it('separates exact forms, fragments and contiguous word phrases without dropping accents', () => {
    expect(matchesVerse('Бог дал богатство', 'бог', 'exact')).toBe(true)
    expect(matchesVerse('богатство', 'бог', 'exact')).toBe(false)
    expect(matchesVerse('богатство', 'бог', 'partial')).toBe(true)
    expect(matchesVerse('Бог, дал мир', 'бог дал', 'phrase')).toBe(true)
    expect(matchesVerse('Бог людям дал мир', 'бог дал', 'phrase')).toBe(false)
    expect(matchesVerse('всё', 'все', 'exact')).toBe(false)
    expect(matchesVerse('λόγος', 'λογος', 'exact')).toBe(false)
  })
  it('checks full Strong tokens, never numeric fragments', () => {
    expect(matchesVerse('', 'h430', 'strong', 'Бог H430')).toBe(true)
    expect(matchesVerse('', 'H43', 'strong', 'Бог H430')).toBe(false)
    expect(matchesVerse('', '430', 'strong', 'Бог H430')).toBe(false)
  })
  it('returns plain highlight segments without treating input as HTML', () => {
    expect(searchSegments('<script>Бог</script> богатство', 'бог', 'exact').filter(s => s.match)).toEqual([{text: 'Бог', match: true}])
  })
  it('requests scoped pages and removes approximate server fallback from exact results', async () => {
    const item = {verse_id: 1, snippet: 'богатство', text: 'богатство'}
    const api = {searchVerses: vi.fn().mockResolvedValue({results: [item]})} as unknown as BibleApi
    const result = await searchVersePage(api, {} as ChapterRepository, 'RST', 'бог', {match:'exact', scope:'new', offset:50})
    expect(result.results).toEqual([])
    expect(result.next).toBe(51)
    expect(api.searchVerses).toHaveBeenCalledWith('бог','RST',{match:'partial',scope:'new',offset:50,limit:50})
  })
  it('does not hide HTTP failure with cached text', async () => {
    const api = {searchVerses: vi.fn().mockRejectedValue(new ApiError('http','Forbidden',403))} as unknown as BibleApi
    await expect(searchVersePage(api, {} as ChapterRepository, 'RST','бог',{match:'exact',scope:'all',offset:0})).rejects.toMatchObject({status:403})
  })
  it('uses the language-aware server mode for word forms, without pretending fragments are morphology', async () => {
    const item={verse_id:1,snippet:'Богу'}
    const api={searchVerses:vi.fn().mockResolvedValue({results:[item]})} as unknown as BibleApi
    expect((await searchVersePage(api,{} as ChapterRepository,'RST','Бог',{match:'morphology',scope:'all',offset:0})).results).toEqual([item])
    expect(api.searchVerses).toHaveBeenCalledWith('Бог','RST',{match:'all_words',scope:'all',offset:0,limit:50})
  })
  it('filters a selected book by canonical identity without assuming edition slugs match', async () => {
    const items=[{verse_id:1,snippet:'Бог',text:'Бог',book:{slug:'source-john',osis_code:'John'}},{verse_id:2,snippet:'Бог',text:'Бог',book:{slug:'acts',osis_code:'Acts'}}]
    const api={searchVerses:vi.fn().mockResolvedValue({results:items})} as unknown as BibleApi
    expect((await searchVersePage(api,{} as ChapterRepository,'RST','Бог',{match:'exact',scope:'all',book:'John',offset:0})).results).toEqual([items[0]])
  })
  it('highlights inflections using Snowball even when the server supplies no marked segments',async()=>{
    await prepareSearchStemming()
    expect(searchSegments('Бога, Богу, богатство','Бог','morphology','ru').filter(part=>part.match).map(part=>part.text)).toEqual(['Бога','Богу'])
    expect(matchesVerse('Богу','Бог','morphology','Богу','ru')).toBe(true)
    expect(matchesVerse('богатство','Бог','morphology','богатство','ru')).toBe(false)
    expect(matchesVerse('всё','все','morphology','всё','ru')).toBe(true)
  })
  it('searches cached inflections without the network and reports that the corpus is local',async()=>{
    const api={searchVerses:vi.fn().mockRejectedValue(new ApiError('offline','No network'))} as unknown as BibleApi
    const data={translation:{code:'RST',language:{code:'ru'}},book:{slug:'john',name:'Иоанна'},chapter:{number:3},verses:[{id:1,number:16,osis_ref:'John.3.16',plain_text:'Слава Богу',text:'Слава Богу',has_strong_markup:false}]}
    const chapters={list:vi.fn().mockResolvedValue([{data}])} as unknown as ChapterRepository
    const page=await searchVersePage(api,chapters,'RST','Бог',{match:'morphology',scope:'all',offset:0})
    expect(page.local).toBe(true)
    expect(page.results[0]?.verse_number).toBe(16)
    expect(page.more).toBe(false)
  })
})
