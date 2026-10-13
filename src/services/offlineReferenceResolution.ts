import type {BibleChapter} from '@/api/contracts'
import type {StudyCrossReference,StudyReferences} from '@/api/verseStudy'
import type {ResolvedReferenceTarget} from '@/api/referenceResolution'
import {isResolvedReference,referenceHash,referenceOsis} from '@/api/referenceResolution'
import type {InstalledStudyPackage} from './studyPackages'
import {studyPackageRows} from './studyPackageLookup'
import {reviewedOfflineMap,type VersificationTables} from './offlineVersification'
import {referenceGroupDigest,referenceTextDigest,type ReferenceDigestEdge} from './referenceDigests'
import {resolvedReadingTarget} from './resolvedReferences'

const object=(value:unknown):value is Record<string,unknown>=>typeof value==='object'&&value!==null&&!Array.isArray(value)
const tables=['versification_profiles.jsonl','versification_assignments.jsonl','versification_map_sets.jsonl','versification_map_entries.jsonl','versification_inverse_entries.jsonl']
type Rows=(table:string,osis?:string)=>Promise<Record<string,unknown>[]>
/** Resolve persisted original edges through the reviewed map; never infer an identity row. */
export async function resolveOfflineReferences(id:number,edition:string,query:string,chapters:BibleChapter[],rows:Rows):Promise<StudyReferences>{
 const data:StudyReferences={verse:{id,osis_ref:query},translation_code:edition,references:[],reference_resolution:{schema:2,query_osis_ref:query,sources:[]}}
 const corpus:VersificationTables={profiles:await rows(tables[0]!),assignments:await rows(tables[1]!),sets:await rows(tables[2]!)}
 const actual=(osis:string)=>{const parts=osis.split('.');const matches=chapters.filter(chapter=>chapter.translation.code===edition&&chapter.chapter.number===Number(parts[1])&&chapter.book.slug.trim()).flatMap(chapter=>chapter.verses.filter(verse=>verse.osis_ref===osis&&verse.number===Number(parts[2])&&Number.isSafeInteger(verse.id)&&verse.id>0).map(verse=>({chapter,verse})));return matches.length===1?matches[0]:undefined}
 const queryText=actual(query);if(!queryText||queryText.verse.id!==id||!referenceOsis(query))return data
 const queryHash=await referenceTextDigest(queryText.verse.plain_text)
 const sources=await rows('sources.jsonl')
 for(const source of sources){if(typeof source.code!=='string')continue;const reviewed=reviewedOfflineMap(corpus,source.code,edition);if(!reviewed)continue
  const {map,sourceProfile,editionProfile}=reviewed,inverse=(await rows(tables[4]!,query)).filter(row=>row.map_set_id===map.id&&row.target_osis_ref===query)
  if(inverse.length!==1||!referenceOsis(inverse[0]?.source_osis_ref))continue
  const original=inverse[0]!.source_osis_ref,fromRows=(await rows(tables[3]!,original)).filter(row=>row.map_set_id===map.id&&row.source_osis_ref===original)
  const from=fromRows[0];if(fromRows.length!==1||!from||from.target_osis_ref!==query||!exact(from)||!sameEvidence(from,inverse[0]!))continue
  const group=(await rows('references.jsonl',original)).filter(row=>row.source_code===source.code)
  const edges:ReferenceDigestEdge[]=[]
  for(const row of group){const target=row.target_verse,metadata=object(row.metadata)?row.metadata:{};if(!object(target)||!referenceOsis(target.osis_ref))continue;edges.push([target.osis_ref,typeof row.type==='string'?row.type:null,typeof metadata.raw_ref==='string'?metadata.raw_ref:null,typeof metadata.legacy_quote_id==='number'?metadata.legacy_quote_id:null])}
  if(edges.length!==group.length)continue
  const groupHash=await referenceGroupDigest(source.code,original,edges)
  data.reference_resolution!.sources.push({source:source.code,status:'verified',original_osis_refs:[original],map_id:Number(map.id),map_version:String(map.version),map_sha256:String(map.sha256)})
  for(const row of group){const rawSource=row.source_verse,rawTarget=row.target_verse;if(!object(rawSource)||!object(rawTarget)||rawSource.osis_ref!==original||!Number.isSafeInteger(rawSource.api_id)||Number(rawSource.api_id)<1||!Number.isSafeInteger(rawTarget.api_id)||Number(rawTarget.api_id)<1||!Number.isSafeInteger(row.api_id)||Number(row.api_id)<1||!referenceOsis(rawTarget.osis_ref))continue
   const originalParts=rawTarget.osis_ref.split('.'),rawMetadata=object(row.metadata)?row.metadata:{}
   const raw:StudyCrossReference={id:Number(row.api_id),source:source.code,type:typeof row.type==='string'?row.type:null,metadata:{...(typeof rawMetadata.legacy_quote_id==='number'?{legacy_quote_id:rawMetadata.legacy_quote_id}:{}),...(typeof row.raw_range==='string'?{raw_ref:row.raw_range}:{})},target:{verse_id:Number(rawTarget.api_id),osis_ref:rawTarget.osis_ref,reference:`${originalParts[0]} ${originalParts[1]}:${originalParts[2]}`,book_slug:String(rawTarget.book_slug),chapter_number:Number(originalParts[1]),verse_number:Number(originalParts[2]),text:null},original_source:{verse_id:Number(rawSource.api_id),osis_ref:original},original_target:{verse_id:Number(rawTarget.api_id),osis_ref:rawTarget.osis_ref},resolved_source:[],resolved_targets:[],resolution:{status:'raw',source_profile:sourceProfile,edition_profile:editionProfile,map_id:Number(map.id),map_version:String(map.version),map_sha256:String(map.sha256)}}
   data.references.push(raw)
   const targetRows=(await rows(tables[3]!,rawTarget.osis_ref)).filter(entry=>entry.map_set_id===map.id&&entry.source_osis_ref===rawTarget.osis_ref),target=targetRows[0]
   raw.resolved_source=[{osis_ref:query,relation:'exact',cardinality:'one-to-one',evidence_uri:String(from.evidence_uri),evidence_id:String(from.evidence_id),part:null}]
   raw.resolved_targets=targetRows.flatMap(entry=>{if(!referenceOsis(entry.target_osis_ref))return [];const value=actual(entry.target_osis_ref),candidate:ResolvedReferenceTarget={osis_ref:entry.target_osis_ref,relation:targetRows.length>1||entry.relation==='ambiguous'?'ambiguous':'exact',cardinality:targetRows.length>1?'one-to-many':'one-to-one',evidence_uri:String(entry.evidence_uri),evidence_id:String(entry.evidence_id),part:null,verse_id:value?.verse.id??null,book_slug:value?.chapter.book.slug??null,chapter_number:value?.chapter.chapter.number??null,verse_number:value?.verse.number??null,reference:value?`${value.chapter.book.name} ${value.chapter.chapter.number}:${value.verse.number}`:null,text:value?.verse.plain_text??null};return isResolvedReference(candidate)?[candidate]:[]})
   if(targetRows.length>1||targetRows.some(entry=>entry.relation==='ambiguous'))raw.resolution!.status='ambiguous'
   if(targetRows.length!==1||!target||!exact(target)||!referenceOsis(target.target_osis_ref))continue
   // Inverse uniqueness also excludes many-to-one target mappings.
   const reverse=(await rows(tables[4]!,target.target_osis_ref)).filter(entry=>entry.map_set_id===map.id&&entry.target_osis_ref===target.target_osis_ref)
   if(reverse.length!==1||reverse[0]!.source_osis_ref!==rawTarget.osis_ref||!sameEvidence(target,reverse[0]!))continue
   const resolved=actual(target.target_osis_ref);if(!resolved?.verse.plain_text.trim()||!resolved.chapter.book.name.trim())continue
   const moved=original!==query||rawTarget.osis_ref!==target.target_osis_ref,targetHash=await referenceTextDigest(resolved.verse.plain_text)
   // A fingerprinted TARGET-only entry cannot authorize an unreviewed SOURCE group.
   if(!digestMatches(from.source_reference_sha256,groupHash,moved||from.target_content_sha256!=null)||!digestMatches(from.target_content_sha256,queryHash,moved)||!digestMatches(target.target_content_sha256,targetHash,moved))continue
   const metadata=object(row.metadata)?row.metadata:{},parts=rawTarget.osis_ref.split('.'),proof=(entry:Record<string,unknown>)=>({osis_ref:String(entry.target_osis_ref),relation:'exact' as const,cardinality:'one-to-one' as const,evidence_uri:String(entry.evidence_uri),evidence_id:String(entry.evidence_id),part:null})
   const reference:StudyCrossReference={id:Number(row.api_id),source:source.code,type:typeof row.type==='string'?row.type:null,metadata:{...(typeof metadata.legacy_quote_id==='number'?{legacy_quote_id:metadata.legacy_quote_id}:{}),...(typeof row.raw_range==='string'?{raw_ref:row.raw_range}:{})},target:{verse_id:Number(rawTarget.api_id),osis_ref:rawTarget.osis_ref,reference:`${parts[0]} ${parts[1]}:${parts[2]}`,book_slug:String(rawTarget.book_slug),chapter_number:Number(parts[1]),verse_number:Number(parts[2]),text:null},original_source:{verse_id:Number(rawSource.api_id),osis_ref:original},original_target:{verse_id:Number(rawTarget.api_id),osis_ref:rawTarget.osis_ref},resolved_source:[proof(from)],resolved_targets:[{...proof(target),verse_id:resolved.verse.id,book_slug:resolved.chapter.book.slug,chapter_number:resolved.chapter.chapter.number,verse_number:resolved.verse.number,reference:`${resolved.chapter.book.name} ${resolved.chapter.chapter.number}:${resolved.verse.number}`,text:resolved.verse.plain_text}],resolution:{status:'verified',source_profile:sourceProfile,edition_profile:editionProfile,map_id:Number(map.id),map_version:String(map.version),map_sha256:String(map.sha256)}}
   Object.assign(raw,reference);raw.readingTarget=resolvedReadingTarget(raw,data)
  }
 }
 return data
}
function exact(row:Record<string,unknown>){return (row.part==null)&&(row.cardinality==null||row.cardinality==='one-to-one')&&isResolvedReference({osis_ref:row.target_osis_ref,relation:row.relation,cardinality:'one-to-one',evidence_uri:row.evidence_uri,evidence_id:row.evidence_id,part:null})&&row.relation==='exact'}
function sameEvidence(a:Record<string,unknown>,b:Record<string,unknown>){return exact(b)&&a.relation===b.relation&&a.evidence_uri===b.evidence_uri&&a.evidence_id===b.evidence_id}
function digestMatches(value:unknown,expected:string,required:boolean){return value==null?!required:referenceHash(value)&&value.toLowerCase()===expected}
export async function installedResolvedReferences(pack:InstalledStudyPackage,id:number,edition:string,query:string,chapters:BibleChapter[]){
 if(!tables.every(table=>pack.files.includes(table)))return
 const cache=new Map<string,Promise<Record<string,unknown>[]>>()
 return resolveOfflineReferences(id,edition,query,chapters,(table,osis)=>{const key=JSON.stringify([table,osis]);let pending=cache.get(key);if(!pending){pending=studyPackageRows(pack,table,{...(osis?{sourceOsis:osis}:{}),limit:Number.MAX_SAFE_INTEGER}).then(result=>result.rows);cache.set(key,pending)}return pending})
}
