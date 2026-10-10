import 'fake-indexeddb/auto'
import{describe,it,expect,vi}from'vitest'
import type{BibleChapter}from'@/api/contracts'
import type{BibleApi}from'@/api/client'
import type{ChapterService}from'./chapterService'
import{loadComparisonChapters,comparisonFrameRows,compareVerses}from'./bibleComparison'
function chapter(code:string,number:number,refs:string[]):BibleChapter{return{translation:{code,name:code,short_name:null,language:{code:'en',name:'English'}},book:{slug:'psalms',name:'Psalms',short_name:null,chapters_count:150},chapter:{number,verses_count:refs.length},verses:refs.map((osis_ref,index)=>({id:number*100+index,number:index+1,osis_ref,text:osis_ref,plain_text:osis_ref,has_strong_markup:false}))}}
describe('actual comparison module chapters',()=>{
 it('loads all mapped chapters and keeps the chapter for secondary actions',async()=>{
  const primary=chapter('A',2,['Ps.2.1','Ps.2.2']),first=chapter('B',3,['Ps.2.1']),second=chapter('B',4,['Ps.2.2','Ps.3.1'])
  const api={getVerseLocations:vi.fn(async()=>[first,second].flatMap(c=>c.verses.filter(v=>primary.verses.some(p=>p.osis_ref===v.osis_ref)).map(v=>({verse_id:v.id,osis_ref:v.osis_ref,book_slug:c.book.slug,chapter_number:c.chapter.number,verse_number:v.number}))))}as unknown as BibleApi
  const service={readOnline:vi.fn(async(_code:string,_book:string,num:number)=>num===3?first:second)}as unknown as ChapterService
  const loaded=await loadComparisonChapters(primary,'B',api,service)
  expect(loaded).toEqual([first,second]);expect(service.readOnline).toHaveBeenCalledTimes(2)
  const rows=compareVerses(primary,loaded);expect(rows[1]?.secondaryChapter).toBe(second);expect(rows[1]?.secondary?.id).toBe(400)
 })
 it('deduplicates overlapping secondary chapters, reassigns primary refs, and sorts chapter before verse',()=>{
  const p1=chapter('A',1,['Ps.2.10']),p2=chapter('A',2,['Ps.3.1']),s=chapter('B',7,['Ps.2.10','Ps.3.1','Ps.3.2'])
  const rows=comparisonFrameRows([{primary:p1,secondary:s},{primary:p2,secondary:s}])
  expect(rows.map(r=>r.reference)).toEqual(['Ps.2.10','Ps.3.1','Ps.3.2']);expect(rows[1]?.frame.primary).toBe(p2);expect(rows[1]?.primary).toBe(p2.verses[0]);expect(rows[2]?.secondaryChapter).toBe(s)
 })
 it('rejects conflicting API verse ids instead of assigning a guessed source',async()=>{
  const p=chapter('A',1,['Ps.1.1']),s=chapter('B',9,['Ps.1.1'])
  const api={getVerseLocations:async()=>[{verse_id:123,osis_ref:'Ps.1.1',book_slug:'psalms',chapter_number:9,verse_number:1}]}as unknown as BibleApi
  await expect(loadComparisonChapters(p,'B',api,{readOnline:async()=>s}as unknown as ChapterService)).rejects.toThrow('Conflicting')
 })
})
