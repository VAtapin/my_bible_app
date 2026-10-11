import {describe,it,expect} from 'vitest'
import type {CalendarReading} from '@/api/contracts'
import {calendarReadingTitle} from './calendarLectionary'
const reading=(candidates:{book:string;number:number}[],status='exact-reference-match'):CalendarReading=>({id:'actual',type:'reading',title:'Ин.20:11-18',display_ref:'Ин.20:11-18',passage_ref:'John.20.11',date_rule_type:'pascha-relative',reading:{schemaVersion:1,parseStatus:'parsed',passages:[],lectionary:{status,candidates}}})
describe('calendar lection numbers',()=>{
 it('shows actual source numbers without requiring a translation mapping',()=>{
  expect(calendarReadingTitle(reading([{book:'John',number:64}]),'ru')).toBe('Ин.20:11-18 · зач. 64')
  expect(calendarReadingTitle(reading([{book:'2Cor',number:194}]),'uk')).toContain('зач. 194')
  expect(calendarReadingTitle(reading([{book:'Luke',number:26}]),'de')).toContain('Perikope 26')
 })
 it('deduplicates source variants of the same lection',()=>{
  expect(calendarReadingTitle(reading([{book:'John',number:64},{book:'John',number:64}]),'en')).toContain('Lection 64')
 })
 it('omits missing, ambiguous and invalid numbers instead of guessing',()=>{
  for(const item of [reading([],'unmatched'),reading([{book:'John',number:64}],'ambiguous'),reading([{book:'John',number:64},{book:'Luke',number:64}]),reading([{book:'John',number:0}]),reading([{book:'John',number:1.5}])])expect(calendarReadingTitle(item,'ru')).toBe('Ин.20:11-18')
  const legacy=reading([]);delete legacy.reading;expect(calendarReadingTitle(legacy,'ru')).toBe('Ин.20:11-18')
 })
})
