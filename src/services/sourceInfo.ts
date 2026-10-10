import {prayerBlocks} from './prayerContent'
export interface SourceInfo { name?:string;shortName?:string;language?:string;author?:string;edition?:string;source?:string;version?:string;updated?:string;capabilities:string[] }
/** Only published, nonempty values are presented. No author/date/licence is inferred from a code. */
export function sourceInfo(value:unknown, version?:string):SourceInfo{
 const input=value&&typeof value==='object'?value as Record<string,unknown>:{}
 const clean=(value:string)=>prayerBlocks(`<span>${value}</span>`).map(b=>b.segments.map(s=>s.text).join('')).join(' ')
 const string=(...keys:string[])=>{const value=keys.map(key=>input[key]).find(v=>typeof v==='string'&&v.trim());const plain=typeof value==='string'?clean(value):undefined;return plain?.trim()?plain:undefined}
 const language=input.language&&typeof input.language==='object'?input.language as Record<string,unknown>:{}
 const capabilities=Array.isArray(input.capabilities)?input.capabilities.filter((v):v is string=>typeof v==='string'&&!!v.trim()):[]
 return{name:string('name','title'),shortName:string('short_name'),language:string('language_code','language')??(typeof language.name==='string'?clean(language.name):typeof language.code==='string'?clean(language.code):undefined),author:string('author','translator','compiler'),edition:string('edition'),source:string('source_url','source','electronic_source'),version:version??string('content_version','version'),updated:string('updated_at','updated'),capabilities}
}
