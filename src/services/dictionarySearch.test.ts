import 'fake-indexeddb/auto'
import { afterEach,expect,it,vi } from 'vitest'
import { zipSync,strToU8 } from 'fflate'
import { installStudyPackage,removeStudyPackage } from './studyPackages'
import { searchInstalledDictionaries } from './dictionarySearch'
afterEach(async()=>{await removeStudyPackage('SEARCH_A');await removeStudyPackage('SEARCH_B')})
async function install(code:string,name:string,topics:string[]) {
 const zip=zipSync({'module.json':strToU8(JSON.stringify({schema:1,kind:'dictionary',code,name})), 'entries.jsonl':strToU8(topics.map((topic,index)=>JSON.stringify({id:String(index).padStart(40,'a'),api_id:100+index,topic,body:'Full text',order:index})).join('\n')),...Object.fromEntries(['references','links','word_forms','media_links','media'].map(table=>[`${table}.jsonl`,strToU8('')]))})
 const hash=Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',zip)),n=>n.toString(16).padStart(2,'0')).join('')
 await installStudyPackage({id:code,kind:'dictionary',version:hash,sha256:hash,bytes:zip.length,url:`/api/offline/packages/${code}`},'https://example.test/api',new AbortController().signal,()=>{},vi.fn(async()=>new Response(zip as BodyInit)) as typeof fetch)
}
it('searches compact installed headers across sources with exact aliases, counts and page boundary',async()=>{
 await install('SEARCH_A','A published source',['Jerusalem ancient','Other']);await install('SEARCH_B','B published source',['Jerusalem modern','JERUSALEM map'])
 const page=await searchInstalledDictionaries('Jerusalem',['SEARCH_A','SEARCH_B'],1,1)
 expect(page.total).toBe(3);expect(page.data).toEqual([{id:100,key:'a'.repeat(39)+'0',topic:'Jerusalem modern',module_code:'SEARCH_B',module_name:'B published source'}])
 expect((await searchInstalledDictionaries('jerusalem',['SEARCH_B'],1,1)).data[0]?.id).toBe(101)
 expect((await searchInstalledDictionaries('jerusalem',['SEARCH_A'],8,1)).data).toEqual([])
})
