import 'fake-indexeddb/auto'
import {afterEach,expect,it,vi} from 'vitest'
import {verifyOfflineReference,type VersificationTables} from './offlineVersification'
import {zipSync,strToU8} from 'fflate'
import * as packages from './studyPackages'
import {installedCrossReferences} from './installedCrossReferences'
afterEach(async()=>{vi.restoreAllMocks();await packages.removeStudyPackage('CROSS_REFERENCES')})
const tables:VersificationTables={profiles:[{code:'SOURCE',revision:'v1',sha256:'a'.repeat(64),provenance_uri:'https://primary.test/source'},{code:'EDITION',revision:'v2',sha256:'b'.repeat(64),provenance_uri:'https://primary.test/edition'}],assignments:[{subject_type:'reference_source',subject_code:'ActualSource',profile_code:'SOURCE',evidence_uri:'https://primary.test/assign-source'},{subject_type:'translation',subject_code:'ActualEdition',profile_code:'EDITION',evidence_uri:'https://primary.test/assign-edition'}],sets:[{id:7,source_profile_code:'SOURCE',target_profile_code:'EDITION',version:'reviewed-v1',sha256:'c'.repeat(64),provenance_uri:'https://primary.test/map',verified_at:'2026-10-09T00:00:00Z',reviewer:'Independent reviewer'}]}
const entry=(ref:string,target=ref)=>({map_set_id:7,source_osis_ref:ref,target_osis_ref:target,relation:'exact',evidence_uri:'https://primary.test/verse-evidence',evidence_id:`proof-${ref}`})
it('verifies only explicitly assigned profiles and reviewed identity evidence for both stored OSIS addresses',async()=>{
 expect(await verifyOfflineReference(tables,'ActualSource','ActualEdition','John.3.1','Ps.1.1',async ref=>[entry(ref)])).toEqual({status:'verified',source_profile:'SOURCE',edition_profile:'EDITION',map_version:'reviewed-v1'})
 expect((await verifyOfflineReference(tables,'ActualSource','OtherEdition','John.3.1','Ps.1.1',async ref=>[entry(ref)])).status).toBe('unknown')
 expect((await verifyOfflineReference({...tables,sets:[{...tables.sets[0],reviewer:''}]},'ActualSource','ActualEdition','John.3.1','Ps.1.1',async ref=>[entry(ref)])).status).toBe('raw')
})
it('never translates verse numbers and refuses ambiguity, missing evidence and unreviewed map sets',async()=>{
 expect((await verifyOfflineReference(tables,'ActualSource','ActualEdition','John.3.1','Ps.1.1',async ref=>[entry(ref,ref==='Ps.1.1'?'Ps.2.1':ref)])).status).toBe('raw')
 expect((await verifyOfflineReference(tables,'ActualSource','ActualEdition','John.3.1','Ps.1.1',async ref=>ref==='Ps.1.1'?[entry(ref),entry(ref,'Ps.2.1')]:[entry(ref)])).status).toBe('ambiguous')
 expect((await verifyOfflineReference(tables,'ActualSource','ActualEdition','John.3.1','Ps.1.1',async ref=>[{...entry(ref),evidence_id:''}])).status).toBe('raw')
 expect((await verifyOfflineReference({...tables,profiles:[{...tables.profiles[0],sha256:'bad'},tables.profiles[1]!]},'ActualSource','ActualEdition','John.3.1','Ps.1.1',async ref=>[entry(ref)])).status).toBe('unknown')
 expect((await verifyOfflineReference({...tables,profiles:[{...tables.profiles[0],revision:'0'},tables.profiles[1]!]},'ActualSource','ActualEdition','John.3.1','Ps.1.1',async ref=>[entry(ref)])).status).toBe('unknown')
 expect((await verifyOfflineReference({...tables,profiles:[...tables.profiles,tables.profiles[0]!]},'ActualSource','ActualEdition','John.3.1','Ps.1.1',async ref=>[entry(ref)])).status).toBe('unknown')
})
it('installs real optional JSONL tables and derives the offline status from persisted indices',async()=>{
 const rows=(data:unknown[])=>strToU8(data.map(row=>JSON.stringify(row)).join('\n')),verse=(book:string,chapter:number,id:number)=>({api_id:id,osis_ref:`${book}.${chapter}.1`,book_slug:book.toLowerCase(),book_osis:book,chapter,verse:1})
 const zip=zipSync({'module.json':rows([{schema:1,kind:'cross_references',code:'CROSS_REFERENCES',versification_schema:1}]),'sources.jsonl':rows([{code:'ActualSource',name:'Published source',record_count:1}]),'references.jsonl':rows([{id:'a'.repeat(40),api_id:501,source_code:'ActualSource',source_name:'Published source',source_verse:verse('John',3,101),target_verse:verse('Ps',1,102)}]),'versification_profiles.jsonl':rows(tables.profiles),'versification_assignments.jsonl':rows(tables.assignments),'versification_map_sets.jsonl':rows(tables.sets),'versification_map_entries.jsonl':rows([entry('John.3.1'),entry('Ps.1.1')])})
 const hash=Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',zip)),n=>n.toString(16).padStart(2,'0')).join('')
 await packages.installStudyPackage({id:'CROSS_REFERENCES',kind:'cross_references',version:hash,sha256:hash,bytes:zip.length,url:'/api/offline/packages/CROSS_REFERENCES'},'https://example.test/api',new AbortController().signal,()=>{},vi.fn(async()=>new Response(zip as BodyInit)))
 const scan=vi.spyOn(packages,'packageTable').mockRejectedValue(Error('Persisted index required'))
 const result=await installedCrossReferences(999,'ActualEdition','John.3.1');expect(result?.references[0]?.versification?.status).toBe('verified');expect(result?.references[0]?.target.osis_ref).toBe('Ps.1.1');expect(result?.references[0]?.target.text).toBeNull();expect(scan).not.toHaveBeenCalled()
})
