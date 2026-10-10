import type {CalendarServicePlan} from './contracts'
export const automaticCalendarPolicy='calendar-language-v1'
export interface CalendarEdition {selectedTextId:number|null;language:string;orthography:string;edition:string|null;sourceKind:'liturgical-corpus'|'calendar-engine'}
export interface CalendarTextMetadata {familyId:string|null;workSlug:string|null;language:string|null;orthography:string|null;edition:string|null;sourceKind:CalendarEdition['sourceKind']|null;selectedTextId:number|null;contentHash:string|null;availableEditions:CalendarEdition[];selection:'preferred'|'fallback'|'missing';missingReason:string|null}
export interface AutomaticCalendarPlan extends CalendarServicePlan {schemaVersion:2;textPolicy:typeof automaticCalendarPolicy;calendarLanguage:string;assignments:(CalendarServicePlan['assignments'][number]&CalendarTextMetadata)[];expansions:(CalendarServicePlan['expansions'][number]&CalendarTextMetadata)[]}
const record=(v:unknown):v is Record<string,unknown>=>!!v&&typeof v==='object'&&!Array.isArray(v)
const nullableString=(v:unknown)=>v===null||typeof v==='string'&&!!v
export const calendarTextPriority=(language:string)=>[...new Set(language==='ru'?['cu','cu-civil','ru']:language==='uk'?['cu','cu-civil','uk','ru']:[language,'cu','cu-civil','ru'])]
function edition(v:unknown):v is CalendarEdition{
 if(!record(v)||typeof v.language!=='string'||!v.language||typeof v.orthography!=='string'||!v.orthography||!nullableString(v.edition))return false
 if(v.language==='cu'&&v.orthography!=='traditional'||v.language==='cu-civil'&&!['civil','civil-accented'].includes(v.orthography))return false
 return v.sourceKind==='calendar-engine'?v.edition===null&&v.selectedTextId===null:v.sourceKind==='liturgical-corpus'&&typeof v.edition==='string'&&Number.isSafeInteger(v.selectedTextId)&&Number(v.selectedTextId)>0
}
function text(v:unknown,language:string):boolean{
 if(!record(v)||typeof v.title!=='string'||typeof v.text!=='string'||!nullableString(v.familyId)||!nullableString(v.workSlug)||!Array.isArray(v.availableEditions)||!v.availableEditions.every(edition)||!nullableString(v.missingReason))return false
 const available=v.availableEditions as CalendarEdition[],priority=calendarTextPriority(language),selected=priority.find(code=>available.some(e=>e.language===code))
 if(v.selection==='missing')return !selected&&v.text===''&&v.language===null&&v.edition===null&&v.orthography===null&&v.sourceKind===null&&v.selectedTextId===null&&v.contentHash===null&&typeof v.missingReason==='string'
 if(!edition(v)||!v.text.trim()||v.language!==selected||v.selection!==(selected===priority[0]?'preferred':'fallback')||v.missingReason!==null||typeof v.contentHash!=='string'||!/^[a-f0-9]{64}$/.test(v.contentHash))return false
 if(!v.availableEditions.some(e=>e.language===v.language&&e.orthography===v.orthography&&e.edition===v.edition&&e.selectedTextId===v.selectedTextId&&e.sourceKind===v.sourceKind))return false
 return v.sourceKind==='liturgical-corpus'?typeof v.workSlug==='string'&&v.familyId===`work:${v.workSlug}`:v.workSlug===null&&typeof v.id==='string'&&v.familyId===`calendar-expansion:${v.id}`
}
export function isAutomaticCalendarPlan(v:unknown,date:string,language:string):v is AutomaticCalendarPlan{
 return record(v)&&v.schemaVersion===2&&v.textPolicy===automaticCalendarPolicy&&v.calendarLanguage===language&&v.date===date&&v.textLanguage==='mixed'
  &&Array.isArray(v.assignments)&&v.assignments.every(a=>record(a)&&(typeof a.textId==='string'||typeof a.textId==='number')&&typeof a.slot==='string'&&text(a,language))
  &&Array.isArray(v.expansions)&&v.expansions.every(a=>record(a)&&typeof a.id==='string'&&text(a,language))
}
