import { describe, expect, it } from 'vitest'
import type { CalendarEvent, CalendarReading } from '@/api/contracts'
import { calendarEvents, calendarReadingLink, fastingNote,calendarMealMark } from './calendarPresentation'

describe('shared calendar presentation', () => {
  it('uses published meal colors rather than Russian words to identify multilingual food-rule indicators',()=>{
    expect(calendarMealMark({foodLabel:'Kein Fasten',fastingColor:'#ffffff'})).toBeUndefined()
    expect(calendarMealMark({foodLabel:'Fisch erlaubt',fastingColor:'#CCE9F3'})).toEqual({label:'Fisch erlaubt',color:'#cce9f3'})
    expect(calendarMealMark({foodLabel:'поста нет',fastingColor:'#cce9f3'})?.label).toBe('поста нет')
    expect(calendarMealMark({foodLabel:' ',fastingColor:'#cce9f3'})).toBeUndefined()
    expect(calendarMealMark({foodLabel:'Unknown rule',fastingColor:'#123456'})).toBeUndefined()
  })
  it('does not repeat BibleDesktop rules in the separate commemoration list', () => {
    const event: CalendarEvent = { id: 'memory', name: 'Memory', is_icon_commemoration: false, is_fasting: false }
    const rule = { ...event, id: 'rule', name: 'Rule', type_code: null }
    expect(calendarEvents([event, rule], 'all', [{ id: 'rule' }])).toEqual([event])
  })
  it('keeps fasting separate and applies the same event filter on both screens', () => {
    const ordinary: CalendarEvent = { id: 'ordinary', name: 'Memory', is_icon_commemoration: false, is_fasting: false }
    const icon = { ...ordinary, id: 'icon', is_icon_commemoration: true }
    const fasting = { ...ordinary, id: 'fast', is_fasting: true }
    expect(calendarEvents([ordinary, icon, fasting], 'major')).toEqual([icon])
    expect(calendarEvents([ordinary, icon, fasting], 'all')).toEqual([ordinary, icon])
    expect(fastingNote({ ...fasting, metadata: { meal_note: 'Meal note' } })).toBe('Meal note')
    expect(fastingNote(fasting)).toBe('Memory')
  })
  it('links parsed readings to the internal reader and leaves unknown references unlinked', () => {
    const reading: CalendarReading = { id: 'reading', type: 'gospel', title: 'Gospel', display_ref: 'Лк.4:1-15', passage_ref: '', date_rule_type: '', reading: { schemaVersion: 1, parseStatus: 'parsed', passages: [{ book: 'Luke', start: { chapter: 4, verse: 1 }, end: { chapter: 4, verse: 15 } }] } }
    expect(calendarReadingLink(reading)).toEqual({ path: '/reader', query: { book: 'luke', chapter: '4', verse: '1' } })
    expect(calendarReadingLink({ ...reading, reading: undefined })).toBeUndefined()
  })
})
