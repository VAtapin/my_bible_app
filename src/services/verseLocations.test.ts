import { describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/api/client'
import { createVerseLocationApi } from '@/api/verseLocations'
import { resolveVerseLocations } from './verseLocations'
import type { ChapterService } from './chapterService'
describe('verified edition locations',()=>{
 const response={verse_id:401,osis_ref:'Joel.2.28',book_slug:'joel-edition',chapter_number:3,verse_number:28}
 it('accepts an actual module chapter differing from canonical OSIS',async()=>{
  const fetchImpl=vi.fn(async()=>new Response(JSON.stringify({data:[response]})))
  const lookup=createVerseLocationApi({baseUrl:'https://example.test/api',fetcher:fetchImpl})
  expect(await lookup('EDITION',['Joel.2.28'])).toEqual([response]);expect(String(fetchImpl.mock.calls[0]?.[0])).toContain('osis_refs%5B%5D=Joel.2.28')
 })
 it('rejects unrequested or duplicate identities',async()=>{
  for(const data of [[{...response,osis_ref:'Joel.2.29'}],[response,response]]){
   const lookup=createVerseLocationApi({baseUrl:'https://example.test/api',fetcher:async()=>new Response(JSON.stringify({data}))})
   await expect(lookup('EDITION',['Joel.2.28'])).rejects.toMatchObject({kind:'invalid-response'})
  }
 })
 it('offline uses actual saved chapter without a numbering guess and never masks HTTP errors',async()=>{
  const service={listStored:async()=>[{data:{translation:{code:'EDITION'},book:{slug:'joel-edition'},chapter:{number:3},verses:[{id:401,osis_ref:'Joel.2.28',number:28,plain_text:'Published'}]}}]} as unknown as ChapterService
  expect(await resolveVerseLocations('EDITION',['Joel.2.28'],service,async()=>{throw new ApiError('offline','Offline')})).toEqual([response])
  expect(await resolveVerseLocations('EDITION',['Joel.2.29'],service,async()=>{throw new ApiError('offline','Offline')})).toEqual([])
  await expect(resolveVerseLocations('EDITION',['Joel.2.28'],service,async()=>{throw new ApiError('http','Denied',403)})).rejects.toMatchObject({status:403})
 })
})
