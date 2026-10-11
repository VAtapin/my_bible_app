import {describe,it,expect} from 'vitest'
import type {BibleChapter,BibleVerse,CalendarReading} from '@/api/contracts'
import {calendarFocus,decodeCalendarFocus,calendarVerseFocused,calendarVerseBoundary} from './calendarReadingFocus'
import {calendarReadingLink} from './calendarPresentation'
function verse(osis_ref:string):BibleVerse{return{id:1,number:Number(osis_ref.split('.')[2]),osis_ref,text:osis_ref,plain_text:osis_ref,has_strong_markup:false}}
function chapter(number:number,refs:string[]):BibleChapter{return{translation:{code:'actual',name:'Actual',short_name:null,language:{code:'ru',name:'Русский'}},book:{slug:'actual-john',name:'John',short_name:null,chapters_count:10},chapter:{number,verses_count:refs.length},verses:refs.map(verse)}}
const reading={title:'Published reading',display_ref:'John 3:2–4:1; John 4:3–4',reading:{schemaVersion:1,parseStatus:'parsed',passages:[{book:'John',start:{chapter:3,verse:2},end:{chapter:4,verse:1}},{book:'John',start:{chapter:4,verse:3},end:{chapter:4,verse:4}}]}} as CalendarReading
describe('whole day-reading focus',()=>{
 it('carries all published parts through the home link and highlights both chapters while leaving gaps and adjacent verses out',()=>{
   const link=calendarReadingLink(reading,'Published title · № 17')!,focus=decodeCalendarFocus(link.query.calendarReading)!
   expect(focus.title).toBe('Published title · № 17');expect(focus.passages).toHaveLength(2)
   for(const ref of ['John.3.2','John.3.99','John.4.1','John.4.3','John.4.4'])expect(calendarVerseFocused(focus,verse(ref))).toBe(true)
   for(const ref of ['John.3.1','John.4.2','John.4.5','Luke.3.2'])expect(calendarVerseFocused(focus,verse(ref))).toBe(false)
 })
 it('marks exact starts and ends using canonical OSIS despite different source module chapter numbering',()=>{
   const focus=calendarFocus(reading)!,source=chapter(8,['John.3.2','John.3.3','John.4.1'])
   expect(calendarVerseBoundary(focus,source,source.verses[0]!)).toEqual({start:true,end:false})
   expect(calendarVerseBoundary(focus,source,source.verses[1]!)).toEqual({start:false,end:false})
   expect(calendarVerseBoundary(focus,source,source.verses[2]!)).toEqual({start:false,end:true})
 })
 it('finds the actual whole-chapter end with shuffled/prepended loaded modules, and waits when the end is not yet known',()=>{
   const focus=calendarFocus({...reading,reading:{schemaVersion:1,parseStatus:'parsed',passages:[{book:'John',start:{chapter:3,verse:null},end:{chapter:3,verse:null}}]}})!
   const first=chapter(7,['John.3.1','John.3.2']),last=chapter(8,['John.3.3']),next=chapter(9,['John.4.1'])
   expect(calendarVerseBoundary(focus,last,last.verses[0]!,[first,last])).toEqual({start:false,end:false})
   expect(calendarVerseBoundary(focus,last,last.verses[0]!,[next,last,first])).toEqual({start:false,end:true})
   expect(calendarVerseBoundary(focus,first,first.verses[1]!,[next,last,first]).end).toBe(false)
 })
 it('a direct reader URL without a reading, or malformed query, cannot reopen an old focus',()=>{
   for(const value of [undefined,null,['old'],'{}','not JSON',JSON.stringify({title:'bad',passages:[null]})])expect(decodeCalendarFocus(value)).toBeUndefined()
   expect(calendarVerseFocused(undefined,verse('John.3.2'))).toBe(false)
   expect(calendarVerseBoundary(undefined,chapter(1,['John.3.2']),verse('John.3.2'))).toEqual({start:false,end:false})
 })
})
