import {describe,expect,it} from 'vitest'
import {boundaryCommentaries,generalCommentary,readerCommentaryBoundaries,finalBoundaryFailure} from './commentaryBoundaries'
import {ApiError} from '@/api/client'
import type {BibleChapter} from '@/api/contracts'
import {isCommentaryEntry} from '@/api/study'
import type {CommentaryEntry} from '@/api/study'
const entry=(id:number,chapter:number,placement?:'before'|'after'|null):CommentaryEntry=>({id,chapter_from:chapter,verse_from:0,chapter_to:null,verse_to:null,placement,title:'Text',author:null,body:'Full source text',module_code:'SOURCE',module_name:'Source',commentary_book_id:1})
describe('general materials at real reading boundaries',()=>{
 it('retries temporary failures but stops access and contract failures',()=>{
  for(const status of [400,401,403,404,409])expect(finalBoundaryFailure(new ApiError('http','error',status))).toBe(true)
  for(const status of [429,500,503])expect(finalBoundaryFailure(new ApiError('http','error',status))).toBe(false)
  expect(finalBoundaryFailure(new ApiError('invalid-response','error'))).toBe(true)
  expect(finalBoundaryFailure(new ApiError('offline','error'))).toBe(false)
 })
 it('places book introductions once at chapter one and book conclusions only at its end',()=>{
  const rows=[entry(1,0,'before'),entry(2,0,'after'),entry(3,0)]
  expect(boundaryCommentaries(rows,1,false,'start').map(e=>e.id)).toEqual([1,3])
  expect(boundaryCommentaries(rows,2,false,'start')).toEqual([])
  expect(boundaryCommentaries(rows,1,false,'start',false)).toEqual([])
  expect(boundaryCommentaries(rows,2,false,'end')).toEqual([])
  expect(boundaryCommentaries(rows,21,true,'end').map(e=>e.id)).toEqual([2])
 })
 it('uses exact chapter boundaries and does not infer placement from a title',()=>{
  const before=entry(1,3),after={...entry(2,3,'after'),chapter_to:4},verse={...entry(3,3,'before'),verse_from:2,title:'Before the chapter'}
  expect(boundaryCommentaries([before,after,verse],3,false,'start')).toEqual([before])
  expect(before.placement).toBeUndefined()
  expect(boundaryCommentaries([before,after,verse],3,false,'end')).toEqual([])
  expect(boundaryCommentaries([before,after,verse],4,false,'end')).toEqual([after])
  expect(generalCommentary(verse)).toBe(false)
 })
 it('deduplicates article identities without merging different sources with identical text',()=>{
  const a=entry(1,1),b={...entry(2,1),module_code:'SECOND'}
  expect(boundaryCommentaries([a,a,b],1,false,'start')).toEqual([a,b])
 })
 it('accepts compatible explicit placement and rejects unknown placement values',()=>{
  for(const placement of [undefined,null,'before','after'] as const)expect(isCommentaryEntry(entry(1,1,placement))).toBe(true)
  expect(isCommentaryEntry({...entry(1,1),placement:'guessed'})).toBe(false)
 })
 it('finds actual canonical chapter transitions within one module chapter',()=>{
  const chapter=(number:number,refs:string[])=>({chapter:{number},book:{chapters_count:150},verses:refs.map(osis_ref=>({osis_ref}))}) as BibleChapter
  const first=chapter(9,['Ps.9.20','Ps.10.1','Ps.10.2']),continuation=chapter(10,['Ps.10.3','Ps.11.1'])
  const alone=readerCommentaryBoundaries([first])
  expect(alone.get('Ps.9.20')?.end?.chapterBoundary).toBe(true)
  expect(alone.get('Ps.10.1')?.start?.chapterBoundary).toBe(true)
  expect(alone.get('Ps.10.2')?.end).toBeUndefined()
  const joined=readerCommentaryBoundaries([first,continuation])
  expect(joined.get('Ps.10.2')?.end).toBeUndefined()
  expect(joined.get('Ps.10.3')?.start).toBeUndefined()
  expect(joined.get('Ps.10.3')?.end?.chapterBoundary).toBe(true)
  expect(joined.get('Ps.11.1')?.start?.chapterBoundary).toBe(true)
  expect(readerCommentaryBoundaries([continuation]).get('Ps.10.3')?.start).toBeUndefined()
 })
})
