import 'fake-indexeddb/auto'
import { afterEach,describe,expect,it,vi } from 'vitest'
import { zipSync,strToU8 } from 'fflate'
import * as packages from './studyPackages'
import { installedCrossReferences } from './installedCrossReferences'
import type { StudyPackage } from '@/api/studyPackages'
afterEach(async()=>{vi.restoreAllMocks();await packages.removeStudyPackage('CROSS_REFERENCES')},30000)
describe('persisted large cross-reference index',()=>{
 it('coalesces concurrent verse lookups through the persisted source index without re-reading the JSONL corpus',async()=>{
  const rows=Array.from({length:2000},(_,index)=>{const verse=Math.floor(index/10)%36+1;return{id:index.toString(16).padStart(40,'0'),api_id:index+100000,type:'parallel',source_code:'SOURCE',source_name:null,source_verse:{api_id:2000+verse,osis_ref:`John.3.${verse}`,book_slug:'john',book_osis:'John',chapter:3,verse},target_verse:{api_id:3000+index%6,osis_ref:`Ps.1.${index%6+1}`,book_slug:'psalms',book_osis:'Ps',chapter:1,verse:index%6+1},raw_range:null,metadata:null}})
  const zip=zipSync({'module.json':strToU8('{"schema":1,"kind":"cross_references","code":"CROSS_REFERENCES"}\n'),'sources.jsonl':strToU8('{"code":"SOURCE","name":null,"record_count":2000}\n'),'references.jsonl':strToU8(rows.map(row=>JSON.stringify(row)).join('\n')+'\n')})
  const sha256=[...new Uint8Array(await crypto.subtle.digest('SHA-256',zip))].map(b=>b.toString(16).padStart(2,'0')).join(''),pack:StudyPackage={id:'CROSS_REFERENCES',kind:'cross_references',version:sha256,sha256,bytes:zip.length,url:'/api/offline/packages/CROSS_REFERENCES'}
  await packages.installStudyPackage(pack,'https://example.test/api',new AbortController().signal,()=>{},vi.fn(async()=>new Response(zip as BodyInit)) as unknown as typeof fetch)
  const readCorpus=vi.spyOn(packages,'packageTable').mockRejectedValue(new Error('Must use persisted source index'))
  const results=await Promise.all(Array.from({length:20},(_,index)=>installedCrossReferences(9000+index,'RST',`John.3.${index+1}`)))
  expect(results.every(result=>result&&result.references.length>0)).toBe(true)
  expect(results[0]!.verse).toEqual({id:9000,osis_ref:'John.3.1'})
  expect(results[0]!.references.every(ref=>ref.source==='SOURCE'&&ref.target.text===null)).toBe(true)
  expect(readCorpus).not.toHaveBeenCalled()
 },30000)
})
