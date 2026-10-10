import {describe,it,expect,vi} from 'vitest'
import {createBibleApi} from './client'
describe('search API contract',()=>{
 it('sends explicit forms and canonical book slug, preserving count and fallback metadata',async()=>{
  const payload={results:[],match:'forms',total:0,has_more:false,forms_fallback:['COPT']}
  const fetcher=vi.fn<typeof fetch>().mockResolvedValue(new Response(JSON.stringify({data:payload})))
  const api=createBibleApi({baseUrl:'https://example.test/api',fetcher})
  expect(await api.searchVerses('Бог','RST',{match:'forms',book:'john',offset:50,limit:50})).toEqual(payload)
  const url=new URL(String(fetcher.mock.calls[0]![0]));expect(url.searchParams.get('book')).toBe('john');expect(url.searchParams.get('match')).toBe('forms')
 })
 it.each([{total:-1},{has_more:'yes'},{forms_fallback:{}},{match:'unknown'}])('rejects malformed search metadata %j',async(extra)=>{
  const api=createBibleApi({baseUrl:'https://example.test/api',fetcher:vi.fn<typeof fetch>().mockResolvedValue(new Response(JSON.stringify({data:{results:[],...extra}})))})
  await expect(api.searchVerses('Бог','RST')).rejects.toMatchObject({kind:'invalid-response'})
 })
 it('accepts a deployed legacy result without pretending count metadata exists',async()=>{
  const api=createBibleApi({baseUrl:'https://example.test/api',fetcher:vi.fn<typeof fetch>().mockResolvedValue(new Response(JSON.stringify({data:{results:[]}})))})
  expect(await api.searchVerses('Бог','RST')).toEqual({results:[]})
 })
})
