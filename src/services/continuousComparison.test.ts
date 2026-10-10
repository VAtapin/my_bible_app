import {describe,it,expect,vi} from 'vitest'
import type {BibleChapter} from '@/api/contracts'
import type {ChapterService} from './chapterService'
import {loadPairedContinuation} from './continuousReading'
import {ApiError} from '@/api/client'
import type{BibleApi}from'@/api/client'
const chapter=(code:string,book:string,number=3,osis='John'):BibleChapter=>({translation:{code,name:code,short_name:null,language:{code:'ru',name:'Russian'}},book:{slug:book,name:book,short_name:null,chapters_count:21},chapter:{number,verses_count:1},verses:[{id:1,number:1,osis_ref:`${osis}.${number}.1`,text:'Text',plain_text:'Text',has_strong_markup:false}]})
describe('continuous comparison',()=>{
 it('loads both actual edition book slugs and the same validated canonical chapter',async()=>{
  const download=vi.fn(async(code:string,slug:string,n:number)=>chapter(code,slug,n));const service={download}as unknown as ChapterService
  const frame=await loadPairedContinuation(chapter('A','john-a'),chapter('B','john-b'),4,service)
  expect(download.mock.calls).toEqual([['A','john-a',4],['B','john-b',4]])
  expect(frame.primary.verses[0]?.osis_ref).toBe('John.4.1');expect(frame.secondary?.book.slug).toBe('john-b')
 })
 it('represents an unavailable target chapter explicitly and never fetches a guessed one',async()=>{
  const download=vi.fn(async(code:string,slug:string,n:number)=>chapter(code,slug,n));const service={download}as unknown as ChapterService
  const secondary=chapter('B','john-b');secondary.book.chapters_count=3
  expect((await loadPairedContinuation(chapter('A','john-a'),secondary,4,service)).secondary).toBeUndefined()
  expect(download).toHaveBeenCalledTimes(1)
 })
 it('rejects wrong canonical references instead of matching equal verse numbers',async()=>{
  const service={download:vi.fn(async(code:string,slug:string,n:number)=>chapter(code,slug,n,'Acts'))}as unknown as ChapterService
  await expect(loadPairedContinuation(chapter('A','john-a'),chapter('B','john-b'),4,service)).rejects.toThrow('references')
  await expect(loadPairedContinuation(chapter('A','john-a'),chapter('B','john-b',3,'Acts'),4,service)).rejects.toThrow('canonical books')
 })
 it('does not replace rejected access with a missing-target message or cached text',async()=>{
  const readOffline=vi.fn();const service={download:vi.fn().mockRejectedValue(new ApiError('http','Forbidden',403)),readOffline}as unknown as ChapterService
  await expect(loadPairedContinuation(chapter('A','john-a'),chapter('B','john-b'),4,service)).rejects.toMatchObject({status:403})
  expect(readOffline).not.toHaveBeenCalled()
 })
 it('uses explicitly resolved edition chapters when module numbering differs',async()=>{
  const primary=chapter('A','joel-a',3,'Joel'),secondary=chapter('B','joel-b',2,'Joel')
  const target=chapter('B','joel-b',3,'Joel');target.verses[0]!.osis_ref='Joel.4.1'
  const service={download:vi.fn(async(code:string,slug:string,n:number)=>code==='A'?chapter(code,slug,n,'Joel'):target)}as unknown as ChapterService
  const api={getVerseLocations:vi.fn().mockResolvedValue([{verse_id:1,osis_ref:'Joel.4.1',book_slug:'joel-b',chapter_number:3,verse_number:1}])}as unknown as BibleApi
  const frame=await loadPairedContinuation(primary,secondary,4,service,api)
  expect(frame.primary.chapter.number).toBe(4);expect(frame.secondary?.chapter.number).toBe(3)
  expect(frame.primary.verses[0]?.osis_ref).toBe(frame.secondary?.verses[0]?.osis_ref)
 })
})
