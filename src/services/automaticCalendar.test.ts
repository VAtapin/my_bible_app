import 'fake-indexeddb/auto'
import {describe,it,expect,vi} from 'vitest'
import {createBibleApi,ApiError,type BibleApi} from '@/api/client'
import {automaticCalendarPolicy,isAutomaticCalendarPlan,calendarTextPriority,type AutomaticCalendarPlan} from '@/api/automaticCalendar'
import {createCalendarContentService,automaticCalendarServiceKey,calendarServiceKey,horizonKey} from './calendarContent'
import {createIndexedDbDailyContentRepository} from '@/offline/indexedDbDailyContentRepository'
import {writeCalendarState} from '@/offline/calendarMedia'
const date='2026-10-06'
function item(id:string,language:string,orthography=language==='cu'?'traditional':'civil-accented'){
 const candidate={selectedTextId:17,language,orthography,edition:`actual-${id}-${language}`,sourceKind:'liturgical-corpus' as const}
 return{textId:id,slot:'troparion-of-day',title:'Same title',text:`Original ${id} source text`,insert:false,rubric:'Original rubric',familyId:`work:${id}`,workSlug:id,...candidate,contentHash:'a'.repeat(64),availableEditions:[candidate],selection:'preferred' as 'preferred'|'fallback',missingReason:null}
}
function plan(language='ru'):AutomaticCalendarPlan{return{date,calendarLanguage:language,schemaVersion:2,textPolicy:automaticCalendarPolicy,textLanguage:'mixed',assignments:[item('first','cu'),{...item('second','cu-civil'),selection:'fallback'}],expansions:[]}}
describe('automatic calendar source editions',()=>{
 it('requests original calendar language and additive policy and accepts independently selected actual scripts',async()=>{
  const fetcher=vi.fn(async()=>Response.json({data:plan()})),api=createBibleApi({baseUrl:'https://example.test/api',fetcher})
  const data=await api.getAutomaticCalendarService!(date,'ru')
  const url=new URL(String(fetcher.mock.calls[0]?.[0]));expect(url.searchParams.get('calendar_lang')).toBe('ru');expect(url.searchParams.get('text_policy')).toBe(automaticCalendarPolicy);expect(url.searchParams.has('lang')).toBe(false)
  expect(data.assignments.map(i=>i.language)).toEqual(['cu','cu-civil']);expect(data.assignments[1]?.insert).toBe(false)
  expect(calendarTextPriority('ru')).toEqual(['cu','cu-civil','ru']);expect(calendarTextPriority('uk')).toEqual(['cu','cu-civil','uk','ru']);expect(calendarTextPriority('de')).toEqual(['de','cu','cu-civil','ru']);expect(calendarTextPriority('en')).toEqual(['en','cu','cu-civil','ru'])
 })
 it('rejects legacy, wrong actual orthography, wrong-family and lower-priority selection rather than mislabelling text',()=>{
  expect(isAutomaticCalendarPlan({date,textLanguage:'cu',assignments:[],expansions:[]},date,'ru')).toBe(false)
  const source=plan();expect(isAutomaticCalendarPlan(source,date,'ru')).toBe(true)
  expect(isAutomaticCalendarPlan({...source,assignments:[{...source.assignments[0],orthography:'civil'}]},date,'ru')).toBe(false)
  expect(isAutomaticCalendarPlan({...source,assignments:[{...source.assignments[0],familyId:'work:another'}]},date,'ru')).toBe(false)
  const german=plan('de');german.assignments[0]!.selection='fallback';german.assignments[0]!.availableEditions.push({...item('first','de').availableEditions[0]!})
  expect(isAutomaticCalendarPlan(german,date,'de')).toBe(false)
 })
 it('uses only same-policy same-original-language cache on offline failure; access and contract failures never fall back',async()=>{
  const data=plan('en');data.assignments.forEach(i=>i.selection='fallback')
  const getAutomaticCalendarService=vi.fn(async()=>data),api={getAutomaticCalendarService} as unknown as BibleApi,service=createCalendarContentService(api,createIndexedDbDailyContentRepository())
  expect((await service.openAutomaticService(date,'en')).offline).toBe(false)
  await writeCalendarState(calendarServiceKey(date,'cu-civil'),{date,textLanguage:'cu-civil',assignments:[],expansions:[]})
  getAutomaticCalendarService.mockRejectedValue(new ApiError('offline','offline'))
  expect((await service.openAutomaticService(date,'en')).data.calendarLanguage).toBe('en')
  await expect(service.openAutomaticService(date,'ru')).rejects.toMatchObject({kind:'offline'})
  expect(automaticCalendarServiceKey(date,'en')).not.toBe(automaticCalendarServiceKey(date,'ru'));expect(horizonKey('en')).not.toBe(horizonKey('ru'))
  for(const error of [new ApiError('http','denied',403),new ApiError('http','unreviewed',409),new ApiError('invalid-response','old contract')]){getAutomaticCalendarService.mockRejectedValue(error);await expect(service.openAutomaticService(date,'en')).rejects.toBe(error)}
 })
 it('keeps missing text explicitly unavailable instead of substituting another same-title family',()=>{
  const source=plan(),missing={...item('missing','ru'),text:'',language:null,edition:null,orthography:null,selectedTextId:null,sourceKind:null,contentHash:null,availableEditions:[],selection:'missing',missingReason:'published-edition-unavailable'}
  expect(isAutomaticCalendarPlan({...source,assignments:[...source.assignments,missing]},date,'ru')).toBe(true)
  expect(isAutomaticCalendarPlan({...source,assignments:[{...missing,text:'Borrowed from another text'}]},date,'ru')).toBe(false)
 })
})
