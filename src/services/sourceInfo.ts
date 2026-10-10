import {prayerBlocks} from './prayerContent'
export interface SourceInfo { name?:string;shortName?:string;language?:string;author?:string;edition?:string;source?:string;version?:string;updated?:string;capabilities:string[] }
const sourceFields=['name','shortName','language','author','edition','source','version','updated'] as const
function cleanValue(value:unknown):string|undefined{
 if(typeof value!=='string'||!value.trim())return
 const plain=prayerBlocks(`<span>${value}</span>`).map(b=>b.segments.map(s=>s.text).join('')).join(' ')
 return plain.trim()?plain:undefined
}
/** Only published, nonempty values are presented. No author/date/licence is inferred from a code. */
export function sourceInfo(value:unknown, version?:string):SourceInfo{
 const input=value&&typeof value==='object'?value as Record<string,unknown>:{}
 const string=(...keys:string[])=>keys.map(key=>cleanValue(input[key])).find(value=>value!==undefined)
 const language=input.language&&typeof input.language==='object'?input.language as Record<string,unknown>:{}
 const capabilities=Array.isArray(input.capabilities)?input.capabilities.map(cleanValue).filter((value):value is string=>value!==undefined):[]
 return{name:string('name','title'),shortName:string('short_name'),language:string('language_code','language')??cleanValue(language.name)??cleanValue(language.code),author:string('author','translator','compiler'),edition:string('edition'),source:string('source_url','source','electronic_source'),version:cleanValue(version)??string('content_version','version'),updated:string('updated_at','updated'),capabilities}
}
/** Labels and values are paired only when an actual display value exists. */
export function sourceInfoRows(metadata:unknown,version?:string,capabilities?:unknown){
 const info=sourceInfo(metadata,version)
 const rows:Array<{field:typeof sourceFields[number]|'capabilities';value:string}>=[]
 for(const field of sourceFields){const value=info[field];if(value)rows.push({field,value})}
 const actual=(Array.isArray(capabilities)?capabilities.map(cleanValue).filter((value):value is string=>value!==undefined):info.capabilities).join(' · ')
 if(actual)rows.push({field:'capabilities',value:actual})
 return rows
}
