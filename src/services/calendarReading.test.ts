import {describe,it,expect,vi} from 'vitest'
import type {BibleChapter,CalendarReading,CalendarReadingPassage} from '@/api/contracts'
import type {ChapterService} from './chapterService'
import {loadCalendarReading,parsedCalendarPassages} from './calendarReading'

function chapter(book:string,moduleChapter:number,refs:string[]):BibleChapter{return{
 translation:{code:'ACTUAL',name:'Source',short_name:null,language:{code:'en',name:'English'}},book:{slug:book.toLowerCase(),name:book,short_name:null,chapters_count:10},chapter:{number:moduleChapter,verses_count:refs.length},
 verses:refs.map((osis_ref,index)=>({id:moduleChapter*100+index,number:Number(osis_ref.split('.')[2]),osis_ref,text:osis_ref,plain_text:osis_ref,has_strong_markup:false})),
}}
function reading(passages:CalendarReadingPassage[]):CalendarReading{return{id:'published',type:'gospel',title:'Reading',display_ref:'Published references',passage_ref:'',date_rule_type:'',reading:{schemaVersion:1,parseStatus:'parsed',passages}}}
const service={} as ChapterService
describe('all published calendar reading parts',()=>{
 it('loads every part across actual module chapter boundaries and excludes verses outside the published range',async()=>{
   const first=chapter('John',7,['John.3.15','John.3.16']),middle=chapter('John',8,['John.3.17','John.4.1']),last=chapter('John',9,['John.4.2','John.4.3']),other=chapter('Acts',2,['Acts.1.1','Acts.1.2'])
   const resolve=vi.fn(async(_code:string,ref:string)=>[first,last,other].find(c=>c.verses.some(v=>v.osis_ref===ref))!)
   const read=vi.fn(async()=>middle)
   const value=await loadCalendarReading(reading([{book:'John',start:{chapter:3,verse:16},end:{chapter:4,verse:2}},{book:'Acts',start:{chapter:1,verse:1},end:{chapter:1,verse:1}}]),'ACTUAL',service,resolve,read)
   expect(value).toHaveLength(2)
   expect(value[0]!.chapters.map(c=>c.chapter.number)).toEqual([7,8,9])
   expect(value.flatMap(p=>p.chapters.flatMap(c=>c.verses.map(v=>v.osis_ref)))).toEqual(['John.3.16','John.3.17','John.4.1','John.4.2','Acts.1.1'])
   expect(read).toHaveBeenCalledExactlyOnceWith(service,'ACTUAL','john',8)
 })
 it('keeps a whole canonical chapter when it crosses module boundaries without guessing a final verse number',async()=>{
   const first=chapter('John',7,['John.5.1','John.5.2']),next=chapter('John',8,['John.5.3','John.6.1'])
   const read=vi.fn(async()=>next)
   const value=await loadCalendarReading(reading([{book:'John',start:{chapter:5,verse:null},end:{chapter:5,verse:null}}]),'ACTUAL',service,async()=>first,read)
   expect(value[0]!.chapters.flatMap(c=>c.verses.map(v=>v.osis_ref))).toEqual(['John.5.1','John.5.2','John.5.3'])
   expect(read).toHaveBeenCalledTimes(1)
 })
 it('rejects unknown/reversed references without constructing a substitute passage',async()=>{
   const data=reading([{book:'John',start:{chapter:3,verse:16},end:{chapter:3,verse:15}}])
   expect(parsedCalendarPassages(data)).toBeUndefined()
   expect(parsedCalendarPassages({...data,reading:{...data.reading!,parseStatus:'unknown'}})).toBeUndefined()
   expect(parsedCalendarPassages({...data,reading:{...data.reading!,passages:[null] as unknown as CalendarReadingPassage[]}})).toBeUndefined()
   const resolve=vi.fn()
   await expect(loadCalendarReading(data,'ACTUAL',service,resolve)).rejects.toThrow('Unparsed')
   expect(resolve).not.toHaveBeenCalled()
 })
 it('rejects a returned edition or missing actual endpoint and stops queued continuation reads when closed',async()=>{
   const data=reading([{book:'John',start:{chapter:5,verse:null},end:{chapter:5,verse:null}}]),first=chapter('John',7,['John.5.1'])
   await expect(loadCalendarReading(data,'ACTUAL',service,async()=>({...first,translation:{...first.translation,code:'OTHER'}}))).rejects.toThrow('Conflicting')
   await expect(loadCalendarReading(data,'ACTUAL',service,async()=>chapter('John',7,['John.5.2']))).rejects.toThrow('Conflicting')
   const controller=new AbortController(),read=vi.fn()
   await expect(loadCalendarReading(data,'ACTUAL',service,async()=>{controller.abort();return first},read,controller.signal)).rejects.toMatchObject({name:'AbortError'})
   expect(read).not.toHaveBeenCalled()
 })
})
