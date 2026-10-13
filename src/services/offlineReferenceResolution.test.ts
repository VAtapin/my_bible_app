import {expect,it} from 'vitest'
import type {BibleChapter} from '@/api/contracts'
import {resolveOfflineReferences} from './offlineReferenceResolution'
import {referenceGroupDigest,referenceTextDigest} from './referenceDigests'
import 'fake-indexeddb/auto'
import {afterEach,vi} from 'vitest'
import {zipSync,strToU8} from 'fflate'
import * as packages from './studyPackages'
import {studyPackageRows} from './studyPackageLookup'
afterEach(async()=>{vi.restoreAllMocks();await packages.removeStudyPackage('CROSS_REFERENCES')})

const chapter=(number:number,id:number,text:string):BibleChapter=>({translation:{code:'Edition',name:'Actual edition',short_name:null,language:{code:'ru',name:'Русский'}},book:{slug:'psalms',name:'Псалтирь',short_name:null,chapters_count:151},chapter:{number,verses_count:1},verses:[{id,number:1,osis_ref:`Ps.${number}.1`,text,plain_text:text,has_strong_markup:false}]})
async function fixture(){
 const chapters=[chapter(118,11,'Первый текст'),chapter(117,12,'Второй текст')],groupHash=await referenceGroupDigest('Source','Ps.119.1',[['Ps.118.1','tsk','Ps 118:1',7]])
 const proof={map_set_id:7,relation:'exact',evidence_uri:'https://primary.test/evidence',evidence_id:'review-1'},from={...proof,source_osis_ref:'Ps.119.1',target_osis_ref:'Ps.118.1',source_reference_sha256:groupHash,target_content_sha256:await referenceTextDigest('Первый текст')},target={...proof,source_osis_ref:'Ps.118.1',target_osis_ref:'Ps.117.1',source_reference_sha256:'f'.repeat(64),target_content_sha256:await referenceTextDigest('Второй текст')}
 const files:Record<string,Record<string,unknown>[]>={
 'sources.jsonl':[{code:'Source'}],
 'versification_profiles.jsonl':['Origin','Selected'].map(code=>({code,revision:'v1',sha256:'a'.repeat(64),provenance_uri:'https://primary.test/profile'})),
 'versification_assignments.jsonl':[{subject_type:'reference_source',subject_code:'Source',profile_code:'Origin',evidence_uri:'https://primary.test/source'},{subject_type:'translation',subject_code:'Edition',profile_code:'Selected',evidence_uri:'https://primary.test/edition'}],
 'versification_map_sets.jsonl':[{id:7,source_profile_code:'Origin',target_profile_code:'Selected',version:'v1',sha256:'b'.repeat(64),provenance_uri:'https://primary.test/map',reviewer:'Reviewer',verified_at:'2026-10-13T00:00:00Z'}],
 'versification_map_entries.jsonl':[from,target],
 'versification_inverse_entries.jsonl':[from,target],
 'references.jsonl':[{api_id:501,source_code:'Source',type:'tsk',raw_range:'Ps 118:1',metadata:{legacy_quote_id:7,raw_ref:'Ps 118:1'},source_verse:{api_id:101,osis_ref:'Ps.119.1'},target_verse:{api_id:102,osis_ref:'Ps.118.1',book_slug:'psalms'}}]
 }
 const run=(query='Ps.118.1')=>resolveOfflineReferences(11,'Edition',query,chapters,async(table,osis)=>files[table]!.filter(row=>!osis||(table==='versification_inverse_entries.jsonl'?row.target_osis_ref:table==='references.jsonl'?(row.source_verse as Record<string,unknown>).osis_ref:row.source_osis_ref)===osis))
 return{run,files,chapters,from,target}
}
it('uses inverse source lookup and both text digests; retains original coordinates and ignores a target’s unrelated source-group digest',async()=>{
 const f=await fixture(),result=await f.run(),item=result.references[0]!
 expect(result.verse.osis_ref).toBe('Ps.118.1');expect(item.original_source?.osis_ref).toBe('Ps.119.1');expect(item.target.osis_ref).toBe('Ps.118.1');expect(item.target.text).toBeNull()
 expect(item.readingTarget).toMatchObject({osis_ref:'Ps.117.1',verse_id:12,reference:'Псалтирь 117:1',text:'Второй текст'})
})
it('refuses changed source groups, changed edition text, missing hashes and nonunique inverse addresses',async()=>{
 for(const mutate of [(f:Awaited<ReturnType<typeof fixture>>)=>{f.from.source_reference_sha256='c'.repeat(64)},(f:Awaited<ReturnType<typeof fixture>>)=>{f.chapters[0]!.verses[0]!.plain_text+=' changed'},(f:Awaited<ReturnType<typeof fixture>>)=>{f.target.target_content_sha256=''},(f:Awaited<ReturnType<typeof fixture>>)=>{f.files['versification_inverse_entries.jsonl']!.push({...f.target,source_osis_ref:'Ps.120.1'})}]){const f=await fixture();mutate(f);expect((await f.run()).references.every(row=>!row.readingTarget)).toBe(true)}
})
it('keeps original edges and ambiguous candidates without publishing a reading target',async()=>{
 const f=await fixture();f.files['versification_map_entries.jsonl']!.push({...f.target,target_osis_ref:'Ps.116.1',relation:'ambiguous'})
 const result=await f.run(),item=result.references[0]!
 expect(item.original_target?.osis_ref).toBe('Ps.118.1');expect(item.resolution?.status).toBe('ambiguous');expect(item.resolved_targets?.map(target=>target.osis_ref)).toEqual(['Ps.117.1','Ps.116.1']);expect(item.readingTarget).toBeUndefined()
})
it('does not fall back to an older covered map when the newest reviewed map has no entry',async()=>{
 const f=await fixture();f.files['versification_map_sets.jsonl']!.push({...f.files['versification_map_sets.jsonl']![0],id:8,version:'v2',verified_at:'2026-10-14T00:00:00Z'})
 expect((await f.run()).references).toEqual([])
})
it('cannot reuse a fingerprinted target-only identity entry to admit an unreviewed source group',async()=>{
 const f=await fixture();f.from.target_osis_ref='Ps.119.1';f.target.target_osis_ref='Ps.118.1'
 f.chapters[0]=chapter(119,11,'Первый текст');f.chapters[1]=chapter(118,12,'Второй текст')
 Reflect.deleteProperty(f.from,'source_reference_sha256')
 const result=await f.run('Ps.119.1');expect(result.references[0]?.original_source?.osis_ref).toBe('Ps.119.1');expect(result.references[0]?.readingTarget).toBeUndefined()
 Reflect.deleteProperty(f.from,'target_content_sha256');Reflect.deleteProperty(f.target,'target_content_sha256')
 expect((await f.run('Ps.119.1')).references[0]?.readingTarget?.osis_ref).toBe('Ps.118.1')
})
it('matches independently confirmed backend Unicode/entity and source-group hash fixtures',async()=>{
 expect(await referenceTextDigest('Слово & ещё, конец.')).toBe('6a896d577f3116c1293eaac02a6b5013bb95f6946422af00c90dca5ec2437bea')
 expect(await referenceGroupDigest('legacy_quote','Ps.119.1',[['Ps.119.2','tsk','Ps 119:2',7]])).toBe('13e95c9f384f067a8cc9abfd32ac012241aa447851f4f451fb1c0470bce3970a')
 expect(await referenceGroupDigest('quote.tsk','Ps.119.10',[['Ps.119.1','parallel','Ссылка/α',9],['Gen.1.1','parallel',null,null],['Gen.1.1','parallel',null,null]])).toBe('68f697341f973c4d3f56f94a8632ddc6ef27d8e64d6c32bfd11fd43e07f26b61')
})
it('installs schema2 and selects inverse actual addresses from the persisted index without scanning JSONL',async()=>{
 const f=await fixture(),files=Object.fromEntries(Object.entries(f.files).map(([name,rows])=>[name,strToU8(rows.map(row=>JSON.stringify(row)).join('\n'))]))
 files['module.json']=strToU8(JSON.stringify({schema:1,kind:'cross_references',code:'CROSS_REFERENCES',versification_schema:2}))
 const zip=zipSync(files),sha=[...new Uint8Array(await crypto.subtle.digest('SHA-256',zip))].map(value=>value.toString(16).padStart(2,'0')).join(''),manifest={id:'CROSS_REFERENCES',kind:'cross_references' as const,version:sha,sha256:sha,bytes:zip.length,url:'/api/offline/packages/CROSS_REFERENCES'}
 const pack=await packages.installStudyPackage(manifest,'https://example.test/api',new AbortController().signal,()=>{},vi.fn(async()=>new Response(zip as BodyInit)))
 const scan=vi.spyOn(packages,'packageTable').mockRejectedValue(Error('Unexpected corpus scan'))
 const inverse=await studyPackageRows(pack,'versification_inverse_entries.jsonl',{sourceOsis:'Ps.118.1',limit:10})
 expect(inverse.rows.map(row=>row.source_osis_ref)).toEqual(['Ps.119.1']);expect(scan).not.toHaveBeenCalled()
})
