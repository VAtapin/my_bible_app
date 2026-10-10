import { expect,it,vi } from 'vitest'
const mocks=vi.hoisted(()=>({canonical:vi.fn(async()=> 'john'),fetch:vi.fn(async(_book:string,_chapter:number,_sources:string[],offset:number)=>({total:3,entries:offset===0?[{id:1,chapter_from:3,verse_from:1,chapter_to:3,verse_to:5,body:'First'},{id:2,chapter_from:3,verse_from:6,chapter_to:4,verse_to:2,body:'Cross chapter'}]:[{id:1,chapter_from:3,verse_from:1,chapter_to:3,verse_to:5,body:'First duplicate'}]}))}))
vi.mock('./studyService',()=>({createStudyService:()=>({canonicalSlug:mocks.canonical,commentaries:mocks.fetch})}))
vi.mock('./studyPackages',()=>({installedStudyPackages:async()=>[]}))
import { inlineCommentaries } from './inlineCommentaries'
it('coalesces visible verse requests, pages and deduplicates actual overlapping article IDs',async()=>{
 const rows=await Promise.all(Array.from({length:20},(_,i)=>inlineCommentaries(`John.3.${i+1}`,'test',['published'])))
 expect(mocks.canonical).toHaveBeenCalledTimes(1);expect(mocks.fetch).toHaveBeenCalledTimes(2)
 expect(rows[0]?.map(r=>r.id)).toEqual([1]);expect(rows[5]?.map(r=>r.id)).toEqual([2]);expect(rows[19]?.map(r=>r.id)).toEqual([2])
 expect(await inlineCommentaries('John.3.1','test',[])).toEqual([])
})
