import {expect,it,vi} from 'vitest'
import {createSlavonicDictionary,dictionaryModule,isDictionaryEntry} from './slavonicDictionary'
const entry={key:'a'.repeat(40),headword:'А҆́зъ',civil:'Азъ',definition:'Полное значение.\nВторая строка.',body_format:'plain-text',content_hash:'b'.repeat(64),source_chapter:1,source_url:'https://azbyka.ru/otechnik/Spravochniki/polnyj-pravoslavnyj-tserkovnoslavjanskij-slovar/1'}
it('sends distinct word and meaning modes with original Unicode, source letter and pagination',async()=>{
 const calls:string[]=[]
 const api=createSlavonicDictionary(vi.fn(async(url)=>{calls.push(String(url));const params=new URL(String(url)).searchParams;return new Response(JSON.stringify({data:[entry],total:48,offset:Number(params.get('offset')),limit:30,content_version:'c'.repeat(64),module_code:dictionaryModule}))}) as typeof fetch)
 expect((await api.search('А҆́зъ','word',0,1)).data[0]?.definition).toBe(entry.definition)
 await api.search('начало','meaning',30)
 expect(new URL(calls[0]!).searchParams.get('q')).toBe('А҆́зъ')
 expect(new URL(calls[0]!).searchParams.get('letter')).toBe('1')
 expect(new URL(calls[1]!).searchParams.get('mode')).toBe('meaning')
 expect(new URL(calls[1]!).searchParams.get('offset')).toBe('30')
})
it('reads direct articles from the existing dictionary endpoint and keeps full plain text',async()=>{
 const {key}=entry,lexical=entry
 const api=createSlavonicDictionary(vi.fn(async()=>new Response(JSON.stringify({data:{key,module_code:dictionaryModule,lexical}}))) as typeof fetch)
 expect(await api.article(key)).toEqual(entry)
})
it('uses the actual source alphabet rather than guessing modern Russian letters',async()=>{
 const data=[{chapter:36,label:'Ѵ',count:8}]
 const api=createSlavonicDictionary(vi.fn(async()=>new Response(JSON.stringify({module_code:dictionaryModule,data}))) as typeof fetch)
 expect(await api.alphabet()).toEqual(data)
})
it('rejects untrusted source links, HTML format, mismatched modules and wrong article identities',async()=>{
 expect(isDictionaryEntry({...entry,source_url:'javascript:alert(1)'})).toBe(false)
 expect(isDictionaryEntry({...entry,body_format:'html'})).toBe(false)
 const api=createSlavonicDictionary(vi.fn(async()=>new Response(JSON.stringify({data:{key:'d'.repeat(40),module_code:dictionaryModule,lexical:entry}}))) as typeof fetch)
 await expect(api.article(entry.key)).rejects.toMatchObject({kind:'contract'})
})
it('distinguishes rate limits, access errors and network interruption',async()=>{
 const rateFetch=vi.fn(async()=>new Response('',{status:429,headers:{'Retry-After':'60'}})),rate=createSlavonicDictionary(rateFetch as typeof fetch)
 await expect(rate.search('слово','word')).rejects.toMatchObject({kind:'http',status:429,retryAfter:60})
 await expect(rate.search('снова','word')).rejects.toMatchObject({kind:'http',status:429})
 expect(rateFetch).toHaveBeenCalledTimes(1)
 const denied=createSlavonicDictionary(vi.fn(async()=>new Response('',{status:403})) as typeof fetch)
 await expect(denied.search('слово','word')).rejects.toMatchObject({kind:'http',status:403})
 const interrupted=createSlavonicDictionary(vi.fn(async()=>{throw new TypeError('offline')}) as typeof fetch)
 await expect(interrupted.search('слово','word')).rejects.toMatchObject({kind:'network'})
})
it('cancels an obsolete request when the user changes the search or leaves the screen',async()=>{
 const controller=new AbortController()
 let received:AbortSignal|undefined
 const api=createSlavonicDictionary(vi.fn(async(_url,options)=>new Promise<Response>((_resolve,reject)=>{
  received=options?.signal as AbortSignal
  received.addEventListener('abort',()=>reject(new DOMException('Aborted','AbortError')),{once:true})
 })) as typeof fetch)
 const pending=api.search('слово','word',0,undefined,controller.signal)
 controller.abort()
 await expect(pending).rejects.toMatchObject({kind:'network'})
 expect(received?.aborted).toBe(true)
})
