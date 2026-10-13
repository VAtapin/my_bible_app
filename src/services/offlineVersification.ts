import type {ReferenceVersification} from '@/api/verseStudy'
import type {InstalledStudyPackage} from './studyPackages'
import {studyPackageRows} from './studyPackageLookup'
const files=['versification_profiles.jsonl','versification_assignments.jsonl','versification_map_sets.jsonl','versification_map_entries.jsonl']
const hash=(value:unknown)=>typeof value==='string'&&/^[a-f0-9]{64}$/i.test(value)
const text=(value:unknown):value is string=>typeof value==='string'&&Boolean(value.trim())
function uri(value:unknown){try{const url=new URL(String(value));return ['https:','http:'].includes(url.protocol)&&Boolean(url.hostname)&&!url.username&&!url.password}catch{return false}}
export interface VersificationTables{profiles:Record<string,unknown>[];assignments:Record<string,unknown>[];sets:Record<string,unknown>[]}
function assignedProfile(tables:VersificationTables,type:string,code:string){const assigned=tables.assignments.filter(row=>row.subject_type===type&&row.subject_code===code);if(assigned.length!==1||!uri(assigned[0]!.evidence_uri))return null;const values=tables.profiles.filter(row=>row.code===assigned[0]!.profile_code);if(values.length!==1)return null;const row=values[0]!;return text(row.code)&&uri(row.provenance_uri)&&text(row.revision)&&row.revision!=='0'&&hash(row.sha256)?row.code:null}
export function reviewedOfflineMap(tables:VersificationTables,source:string,edition:string){

 const sourceProfile=assignedProfile(tables,'reference_source',source),editionProfile=assignedProfile(tables,'translation',edition);if(!sourceProfile||!editionProfile)return
 const maps=tables.sets.filter(row=>row.source_profile_code===sourceProfile&&row.target_profile_code===editionProfile&&Number.isSafeInteger(row.id)&&Number(row.id)>0&&uri(row.provenance_uri)&&hash(row.sha256)&&text(row.version)&&text(row.reviewer)&&typeof row.verified_at==='string'&&Number.isFinite(Date.parse(row.verified_at))).sort((a,b)=>Date.parse(String(b.verified_at))-Date.parse(String(a.verified_at))||Number(b.id)-Number(a.id))
 if(maps[0])return{map:maps[0],sourceProfile,editionProfile}
}
export async function verifyOfflineReference(tables:VersificationTables,source:string,edition:string,from:string,to:string,entries:(ref:string)=>Promise<Record<string,unknown>[]>):Promise<ReferenceVersification>{
 const sourceProfile=assignedProfile(tables,'reference_source',source),editionProfile=assignedProfile(tables,'translation',edition),result:ReferenceVersification={status:'unknown',source_profile:sourceProfile,edition_profile:editionProfile,map_version:null};if(!sourceProfile||!editionProfile)return result;result.status='raw'
 const reviewed=reviewedOfflineMap(tables,source,edition)
 if(!reviewed)return result;const map=reviewed.map;result.map_version=String(map.version)
 for(const ref of [from,to]){const candidates=(await entries(ref)).filter(row=>row.map_set_id===map.id&&row.source_osis_ref===ref);if(candidates.length>1||candidates.some(row=>row.relation==='ambiguous')){result.status='ambiguous';return result};const row=candidates[0];if(candidates.length!==1||row?.relation!=='exact'||row.target_osis_ref!==ref||!uri(row.evidence_uri)||!text(row.evidence_id))return result}
 result.status='verified';return result
}
const cache=new Map<string,Promise<(source:string,edition:string,from:string,to:string)=>Promise<ReferenceVersification>>>()
export async function offlineVersification(pack:InstalledStudyPackage):Promise<(source:string,edition:string,from:string,to:string)=>Promise<ReferenceVersification>>{
 if(pack.metadata.versification_schema!==1||!files.every(file=>pack.files.includes(file)))return async():Promise<ReferenceVersification>=>({status:'unknown',source_profile:null,edition_profile:null,map_version:null})
 const key=`${pack.manifest.id}:${pack.manifest.version}`;let pending=cache.get(key)
 if(!pending){pending=(async()=>{const tables:VersificationTables={profiles:(await studyPackageRows(pack,files[0]!,{limit:Number.MAX_SAFE_INTEGER})).rows,assignments:(await studyPackageRows(pack,files[1]!,{limit:Number.MAX_SAFE_INTEGER})).rows,sets:(await studyPackageRows(pack,files[2]!,{limit:Number.MAX_SAFE_INTEGER})).rows},rows=new Map<string,Promise<Record<string,unknown>[]>>()
  return(source:string,edition:string,from:string,to:string)=>verifyOfflineReference(tables,source,edition,from,to,ref=>{let result=rows.get(ref);if(!result){result=studyPackageRows(pack,files[3]!,{sourceOsis:ref,limit:Number.MAX_SAFE_INTEGER}).then(value=>value.rows);rows.set(ref,result);if(rows.size>1000)rows.delete(rows.keys().next().value!)}return result})
 })();cache.set(key,pending);void pending.catch(()=>cache.delete(key));if(cache.size>2)cache.delete(cache.keys().next().value!)}
 return pending
}
