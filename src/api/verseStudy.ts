import { createApiRequest, type ApiClientOptions } from './client'

export interface ReferenceVersification {status:'unknown'|'raw'|'ambiguous'|'verified';source_profile:string|null;edition_profile:string|null;map_version:string|null}
export interface StudyReferenceTarget { verse_id:number;osis_ref:string;reference:string;book_slug:string;chapter_number:number;verse_number:number;text:string|null;versification?:ReferenceVersification }
export interface StudyCrossReference { id:number;type?:string|null;source?:string|null;metadata?:{legacy_quote_id?:number;raw_ref?:string}|null;target:StudyReferenceTarget;versification?:ReferenceVersification }
export interface StudyReferences { verse:{id:number;osis_ref:string};translation_code:string;references:StudyCrossReference[] }
export interface StudyStrongToken { strong_number:string;token_order?:number;surface_text?:string|null;grammar_code?:string|null }
export interface StudyStrongTokens { verse:{id:number;osis_ref:string};tokens:StudyStrongToken[] }
export interface StudyStrongEntry { number:string;canonical_number?:string|null;scope?:'H'|'G'|null;word:string|null;transliteration:string|null;pronunciation?:string|null;content:string|null;lexicon:{code?:string;name:string;language:string} }
const record=(v:unknown):v is Record<string,unknown>=>typeof v==='object'&&v!==null
const id=(v:unknown):v is number=>Number.isSafeInteger(v)&&Number(v)>0
const nullableText=(v:unknown)=>v===null||typeof v==='string'
const optionalText=(v:unknown)=>v===undefined||nullableText(v)
export const isReferenceVersification=(v:unknown):v is ReferenceVersification=>record(v)&&['unknown','raw','ambiguous','verified'].includes(String(v.status))&&nullableText(v.source_profile)&&nullableText(v.edition_profile)&&nullableText(v.map_version)
export function referenceVersification(value?:ReferenceVersification):ReferenceVersification{return value&&isReferenceVersification(value)?value:{status:'unknown',source_profile:null,edition_profile:null,map_version:null}}
export function verifiedReference(value?:ReferenceVersification):boolean{const status=referenceVersification(value);return status.status==='verified'&&Boolean(status.source_profile&&status.edition_profile&&status.map_version)}
export const isReferenceTarget=(v:unknown):v is StudyReferenceTarget=>record(v)&&id(v.verse_id)&&typeof v.osis_ref==='string'&&typeof v.reference==='string'&&typeof v.book_slug==='string'&&id(v.chapter_number)&&id(v.verse_number)&&nullableText(v.text)&&new RegExp(`^[A-Za-z0-9]+\\.${v.chapter_number}\\.${v.verse_number}$`,'u').test(v.osis_ref)
const isReference=(v:unknown):v is StudyCrossReference=>record(v)&&id(v.id)&&optionalText(v.type)&&optionalText(v.source)&&isReferenceTarget(v.target)&&(v.versification===undefined||isReferenceVersification(v.versification))&&(v.metadata===undefined||v.metadata===null||record(v.metadata)&&(v.metadata.legacy_quote_id===undefined||id(v.metadata.legacy_quote_id))&&(v.metadata.raw_ref===undefined||typeof v.metadata.raw_ref==='string'))
const verse=(v:unknown)=>record(v)&&id(v.id)&&typeof v.osis_ref==='string'&&/^[A-Za-z0-9]+\.\d+\.\d+$/u.test(v.osis_ref)
const envelope=<T>(check:(v:unknown)=>v is T)=>(v:unknown):v is {data:T}=>record(v)&&check(v.data)
export function createVerseStudyApi(options:ApiClientOptions){
  const request=createApiRequest(options)
  return {
    async references(verseId:number,code:string){
      if(!id(verseId)||!code)throw new Error('Invalid passage')
      return(await request(`/verses/${verseId}/cross-references?${new URLSearchParams({translation:code})}`,envelope((v):v is StudyReferences=>record(v)&&verse(v.verse)&&record(v.verse)&&v.verse.id===verseId&&v.translation_code===code&&Array.isArray(v.references)&&v.references.every(isReference)))).data
    },
    async tokens(verseId:number,code:string){
      if(!id(verseId)||!code)throw new Error('Invalid passage')
      return(await request(`/verses/${verseId}/strong-tokens?${new URLSearchParams({translation:code})}`,envelope((v):v is StudyStrongTokens=>record(v)&&verse(v.verse)&&record(v.verse)&&v.verse.id===verseId&&Array.isArray(v.tokens)&&v.tokens.every(t=>record(t)&&typeof t.strong_number==='string'&&/^[HG]?\d{1,5}$/iu.test(t.strong_number))))).data
    },
    async strong(number:string,verseId?:number){
      if(!/^[HG]\d{1,5}$/iu.test(number)||Number(number.slice(1))<1||verseId!==undefined&&!id(verseId))throw new Error('Explicit Hebrew/Greek Strong number required')
      const params=verseId?`?${new URLSearchParams({verse:String(verseId)})}`:''
      return(await request(`/strong/${number.toUpperCase()}${params}`,envelope((v):v is StudyStrongEntry=>record(v)&&typeof v.number==='string'&&optionalText(v.canonical_number)&&(v.canonical_number===undefined||v.canonical_number===null||v.canonical_number===number.toUpperCase())&&(v.scope===undefined||v.scope===null||v.scope===number[0]!.toUpperCase())&&nullableText(v.word)&&nullableText(v.transliteration)&&nullableText(v.content)&&record(v.lexicon)&&typeof v.lexicon.name==='string'&&typeof v.lexicon.language==='string'))).data
    },
  }
}
export type VerseStudyApi=ReturnType<typeof createVerseStudyApi>
