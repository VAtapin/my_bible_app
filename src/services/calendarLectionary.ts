import type {CalendarReading} from '@/api/contracts'
import {calendarLectionaryMessages} from '@/i18n/calendarLectionary'
export function calendarLectionaryNumber(reading:CalendarReading):number|undefined {
 const value=reading.reading?.lectionary
 if(value?.status!=='exact-reference-match'||!Array.isArray(value.candidates)||!value.candidates.length)return
 if(value.candidates.some(c=>!c||typeof c.book!=='string'||!c.book.trim()||!Number.isSafeInteger(c.number)||c.number<=0))return
 const identities=new Set(value.candidates.map(c=>`${c.book}:${c.number}`))
 return identities.size===1?value.candidates[0]?.number:undefined
}
export function calendarReadingTitle(reading:CalendarReading,language:keyof typeof calendarLectionaryMessages):string {
 const title=reading.display_ref||reading.title,number=calendarLectionaryNumber(reading)
 return number===undefined?title:`${title} · ${calendarLectionaryMessages[language]} ${number}`
}
