import 'fake-indexeddb/auto'
import { describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/api/client'
import { isReferenceTarget, type VerseStudyApi, type StudyCrossReference } from '@/api/verseStudy'
import { explicitStrong, referenceGroups, verseStudyService } from './verseStudy'

const reference=(verse:number,source='A',book='John',chapter=3):StudyCrossReference=>({id:verse,source,type:'parallel',metadata:{legacy_quote_id:1,raw_ref:'published'},target:{verse_id:verse,osis_ref:`${book}.${chapter}.${verse}`,reference:`${book} ${chapter}:${verse}`,book_slug:book.toLowerCase(),chapter_number:chapter,verse_number:verse,text:'Text'}})
describe('source-aware verse study',()=>{
 it('groups published consecutive verses while retaining gaps and independent sources',()=>{
  const groups=referenceGroups([reference(3),reference(1),reference(2),reference(2),reference(5),reference(1,'B'),reference(1,'A','Gen',1)],{Gen:1,John:43})
  expect(groups.map(g=>g.targets.map(t=>t.osis_ref))).toEqual([['Gen.1.1'],['John.3.1','John.3.2','John.3.3'],['John.3.1'],['John.3.5']])
  expect(groups[1]!.label).toBe('John 3:1–3');expect(groups[2]!.source).toBe('B')
 })
 it('rejects contradictory target metadata and never guesses a bare Strong prefix',()=>{
  expect(isReferenceTarget(reference(1).target)).toBe(true)
  expect(isReferenceTarget({...reference(1).target,chapter_number:4})).toBe(false)
  expect(explicitStrong('7225')).toBeUndefined();expect(explicitStrong('7225','old')).toBe('H7225');expect(explicitStrong('G00025')).toBe('G25');expect(explicitStrong('H0')).toBeUndefined()
 })
 it('restores references only for the exact edition after connection failure',async()=>{
  const records=new Map<string,unknown>(),read=vi.fn().mockResolvedValue({verse:{id:7,osis_ref:'John.3.1'},references:[reference(1)]})
  const service=verseStudyService({references:read} as unknown as VerseStudyApi,{read:async<T>(key:string)=>records.get(key) as T|undefined,write:async(key,value)=>{records.set(key,value)}})
  await service.references(7,'RST');read.mockRejectedValue(new ApiError('offline','Disconnected'))
  expect((await service.references(7,'RST')).verse.id).toBe(7);await expect(service.references(7,'DE')).rejects.toThrow('Disconnected')
  read.mockRejectedValue(new ApiError('http','Denied',403));await expect(service.references(7,'RST')).rejects.toMatchObject({status:403})
 })
})
