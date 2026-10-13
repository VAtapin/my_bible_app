import {expect,it} from 'vitest'
import type {StudyReferences} from '@/api/verseStudy'
import {createVerseStudyApi} from '@/api/verseStudy'
import {guardedReferences,referenceGroups} from './verseStudy'
const sha='a'.repeat(64)
function mapped():StudyReferences {
  const relation={relation:'exact' as const,cardinality:'one-to-one' as const,evidence_uri:'https://primary.test/map',evidence_id:'whole-verse-proof',part:null}
  return {verse:{id:11801,osis_ref:'Ps.118.1'},translation_code:'ACTUAL_EDITION',reference_resolution:{schema:2,query_osis_ref:'Ps.118.1',sources:[{source:'RAW_QUOTE',status:'verified',original_osis_refs:['Ps.119.1'],map_id:7,map_version:'scoped-v1',map_sha256:sha}]},references:[{
    id:10,source:'RAW_QUOTE',target:{verse_id:11902,osis_ref:'Ps.119.2',reference:'Псалтирь 119:2',book_slug:'psalms',chapter_number:119,verse_number:2,text:'Stale original preview'},
    original_source:{verse_id:11901,osis_ref:'Ps.119.1'},original_target:{verse_id:11902,osis_ref:'Ps.119.2'},resolved_source:[{...relation,osis_ref:'Ps.118.1'}],
    resolved_targets:[{...relation,verse_id:11802,osis_ref:'Ps.118.2',reference:'Псалтирь 118:2',book_slug:'psalms',chapter_number:118,verse_number:2,text:'Actual admitted edition text'}],
    resolution:{status:'verified',source_profile:'RAW_QUOTE_SNAPSHOT',edition_profile:'ACTUAL_PUBLISHED_EDITION',map_id:7,map_version:'scoped-v1',map_sha256:sha},
  }]}
}
it('reads and labels the resolved verse while retaining original coordinates through cache serialization',()=>{
  for(const value of [mapped(),JSON.parse(JSON.stringify(mapped())) as StudyReferences]){
    const result=guardedReferences(value),reference=result.references[0]!,group=referenceGroups(result.references)[0]!
    expect(reference.original_source?.osis_ref).toBe('Ps.119.1')
    expect(reference.target.osis_ref).toBe('Ps.119.2');expect(reference.target.text).toBeNull()
    expect(group.label).toBe('Псалтирь 118:2');expect(group.targets[0]?.verse_id).toBe(11802)
    expect(group.targets[0]?.text).toBe('Actual admitted edition text')
  }
})
it('never promotes missing, stale, ambiguous, partial or contradictory mapping evidence',()=>{
  const changes:Array<(value:StudyReferences)=>void>=[
    value=>{value.reference_resolution!.query_osis_ref='Ps.118.3'},
    value=>{value.reference_resolution!.sources[0]!.map_sha256='b'.repeat(64)},
    value=>{value.references[0]!.resolution!.map_id=null},
    value=>{value.references[0]!.resolution!.status='ambiguous'},
    value=>{value.references[0]!.resolved_source![0]!.osis_ref='Ps.118.3'},
    value=>{value.references[0]!.resolved_targets![0]!.cardinality='one-to-many'},
    value=>{value.references[0]!.resolved_targets![0]!.reference=null},
    value=>{value.references[0]!.resolved_targets![0]!.text=''},
    value=>{value.references[0]!.resolved_targets![0]!.chapter_number=119},
    value=>{value.references[0]!.original_target!.verse_id=1},
    value=>{value.references[0]!.resolved_targets![0]!.evidence_uri='https://user:secret@primary.test/'},
    value=>{value.references[0]!.resolved_targets!.push({...value.references[0]!.resolved_targets![0]!,osis_ref:'Ps.118.3',verse_number:3})},
  ]
  for(const change of changes){const value=mapped();change(value);const result=guardedReferences(value);expect(result.references[0]?.readingTarget).toBeUndefined();expect(referenceGroups(result.references)[0]?.targets[0]?.text).toBeNull()}
})
it('accepts additive HTTP v2 and rejects a mismatched query or a malformed resolved coordinate',async()=>{
  const value=mapped(),api=createVerseStudyApi({baseUrl:'https://example.test/api',fetcher:async()=>new Response(JSON.stringify({data:value}))})
  expect((await api.references(11801,'ACTUAL_EDITION')).reference_resolution?.schema).toBe(2)
  value.reference_resolution!.query_osis_ref='Ps.118.3'
  await expect(api.references(11801,'ACTUAL_EDITION')).rejects.toMatchObject({kind:'invalid-response'})
  value.reference_resolution!.query_osis_ref='Ps.118.1';value.references[0]!.resolved_targets![0]!.verse_number=3
  await expect(api.references(11801,'ACTUAL_EDITION')).rejects.toMatchObject({kind:'invalid-response'})
})
