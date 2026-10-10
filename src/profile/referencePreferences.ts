import {ref} from 'vue'
import type {KeyValueStorage} from './profileRepository'
export interface ReferencePreferences {inlineMode:'compact'|'list';inlineSources:string[]|null;detailSources:string[]|null;detailSort:'canonical'|'source'}
const key='bible-desktop:reference-preferences:v1'
export const defaultReferencePreferences:ReferencePreferences={inlineMode:'compact',inlineSources:null,detailSources:null,detailSort:'canonical'}
export function normalizeReferencePreferences(value:unknown):ReferencePreferences {
 const result={...defaultReferencePreferences};if(!value||typeof value!=='object')return result
 const source=value as Record<string,unknown>
 if(source.inlineMode==='list')result.inlineMode='list'
 if(source.detailSort==='source')result.detailSort='source'
 for(const field of ['inlineSources','detailSources']as const){const values=source[field];if(Array.isArray(values)&&values.every(value=>typeof value==='string'&&value.length<=200))result[field]=[...new Set(values)].slice(0,100)}
 return result
}
export function loadReferencePreferences(storage:KeyValueStorage){try{return normalizeReferencePreferences(JSON.parse(storage.getItem(key)||'null'))}catch{return{...defaultReferencePreferences}}}
const settings=ref<ReferencePreferences>({...defaultReferencePreferences});let initialized=false
export function useReferencePreferences(){function initialize(){if(!initialized){settings.value=loadReferencePreferences(localStorage);initialized=true}};function setSettings(value:ReferencePreferences){settings.value=normalizeReferencePreferences(value);localStorage.setItem(key,JSON.stringify(settings.value))};return{settings,initialize,setSettings}}
export function toggleReferenceSource(selected:string[]|null,source:string,all:string[]){const values=selected??all;return values.includes(source)?values.filter(value=>value!==source):[...values,source]}
