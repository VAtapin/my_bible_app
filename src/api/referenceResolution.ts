/** Additive v2 coordinates: original addresses never become display addresses implicitly. */
export interface OriginalReference {verse_id:number;osis_ref:string}
export interface ResolvedReference {
  osis_ref:string
  relation:'exact'|'ambiguous'
  cardinality:'one-to-one'|'one-to-many'|'many-to-one'
  evidence_uri:string
  evidence_id:string
  part:null
}
export interface ResolvedReferenceTarget extends ResolvedReference {
  verse_id:number|null
  book_slug:string|null
  chapter_number:number|null
  verse_number:number|null
  reference?:string|null
  text:string|null
}
export interface ReferenceResolution {
  status:'unknown'|'raw'|'ambiguous'|'verified'
  source_profile:string|null
  edition_profile:string|null
  map_id:number|null
  map_version:string|null
  map_sha256:string|null
}
export interface ReferenceSourceResolution {
  source:string
  status:ReferenceResolution['status']
  original_osis_refs:string[]
  map_id:number|null
  map_version:string|null
  map_sha256:string|null
}
export interface ReferenceResolutionEnvelope {schema:2;query_osis_ref:string;sources:ReferenceSourceResolution[]}
const record=(value:unknown):value is Record<string,unknown>=>typeof value==='object'&&value!==null&&!Array.isArray(value)
const id=(value:unknown):value is number=>Number.isSafeInteger(value)&&Number(value)>0
const nullableId=(value:unknown)=>value===null||id(value)
const nullableText=(value:unknown)=>value===null||typeof value==='string'
export const referenceOsis=(value:unknown):value is string=>typeof value==='string'&&/^[A-Za-z0-9]+\.[1-9]\d*\.[1-9]\d*$/u.test(value)
export const referenceHash=(value:unknown):value is string=>typeof value==='string'&&/^[a-f0-9]{64}$/iu.test(value)
function evidenceUri(value:unknown){if(typeof value!=='string')return false;try{const url=new URL(value);return ['https:','http:'].includes(url.protocol)&&Boolean(url.hostname)&&!url.username&&!url.password}catch{return false}}
const status=(value:unknown)=>['unknown','raw','ambiguous','verified'].includes(String(value))
export const isOriginalReference=(value:unknown):value is OriginalReference=>record(value)&&id(value.verse_id)&&referenceOsis(value.osis_ref)
export const isResolvedReference=(value:unknown):value is ResolvedReference=>record(value)&&referenceOsis(value.osis_ref)&&['exact','ambiguous'].includes(String(value.relation))&&['one-to-one','one-to-many','many-to-one'].includes(String(value.cardinality))&&evidenceUri(value.evidence_uri)&&typeof value.evidence_id==='string'&&Boolean(value.evidence_id.trim())&&value.part===null
export const isResolvedReferenceTarget=(value:unknown):value is ResolvedReferenceTarget=>isResolvedReference(value)&&record(value)&&nullableId(value.verse_id)&&nullableText(value.book_slug)&&nullableId(value.chapter_number)&&nullableId(value.verse_number)&&nullableText(value.text)&&(value.reference===undefined||nullableText(value.reference))&&(value.chapter_number===null||Number(value.osis_ref.split('.')[1])===value.chapter_number)&&(value.verse_number===null||Number(value.osis_ref.split('.')[2])===value.verse_number)
export const isReferenceResolution=(value:unknown):value is ReferenceResolution=>record(value)&&status(value.status)&&nullableText(value.source_profile)&&nullableText(value.edition_profile)&&nullableId(value.map_id)&&nullableText(value.map_version)&&(value.map_sha256===null||referenceHash(value.map_sha256))
export const isReferenceResolutionEnvelope=(value:unknown):value is ReferenceResolutionEnvelope=>record(value)&&value.schema===2&&referenceOsis(value.query_osis_ref)&&Array.isArray(value.sources)&&value.sources.every(source=>record(source)&&typeof source.source==='string'&&status(source.status)&&Array.isArray(source.original_osis_refs)&&source.original_osis_refs.every(referenceOsis)&&new Set(source.original_osis_refs).size===source.original_osis_refs.length&&nullableId(source.map_id)&&nullableText(source.map_version)&&(source.map_sha256===null||referenceHash(source.map_sha256)))&&new Set(value.sources.map(source=>source.source)).size===value.sources.length
export const verifiedResolution=(value?:ReferenceResolution):value is ReferenceResolution=>Boolean(value&&isReferenceResolution(value)&&value.status==='verified'&&value.source_profile?.trim()&&value.edition_profile?.trim()&&value.map_id&&value.map_version?.trim()&&referenceHash(value.map_sha256))
export const exactReference=(value:ResolvedReference)=>value.relation==='exact'&&value.cardinality==='one-to-one'&&value.part===null
