import { unzip } from 'fflate'
import { isStudyPackage, type StudyPackage } from '@/api/studyPackages'
import { readCalendarState, writeCalendarState } from '@/offline/calendarMedia'
import { openOfflineDatabase, offlineStores } from '@/offline/database'
import { buildStudyPackageIndex,deleteStudyPackageIndex,discardStudyPackageIndex } from './studyPackageIndex'
import {verifyCommentaryImage,commentaryMediaUrl,commentaryImageTypes} from './commentaryMedia'
import {isCommentaryAnnotations} from '@/api/commentaryAnnotations'
export interface InstalledStudyPackage { manifest: StudyPackage; metadata: Record<string, unknown>; files: string[]; installedAt: string }
const indexKey = 'study-packages:installed:v1'
const fileKey = (id: string, version: string, name: string) => `study-package:${id}:${version}:${name}`
const partialKey = (pack: StudyPackage) => `study-package:partial:${pack.id}:${pack.version}`
export async function installedStudyPackages() { return await readCalendarState<InstalledStudyPackage[]>(indexKey) ?? [] }
export async function studyPackageFile(pack: InstalledStudyPackage, name: string) { if(!pack.files.includes(name)) return undefined; return readCalendarState<Blob>(fileKey(pack.manifest.id, pack.manifest.version, name)) }
export async function* packageRows(file: Blob): AsyncGenerator<Record<string, unknown>> {
 const reader = file.stream().getReader(), decoder = new TextDecoder('utf-8', { fatal: true }); let pending = '', processed = 0
 try { while(true) { const value = await reader.read(); pending += decoder.decode(value.value, { stream: !value.done }); let end: number; while((end = pending.indexOf('\n')) >= 0) { const row = pending.slice(0,end).trim(); pending = pending.slice(end+1); if(row) { const parsed: unknown = JSON.parse(row); if(typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) throw new Error('Invalid package row'); yield parsed as Record<string,unknown>; if(++processed % 1000 === 0) await new Promise(resolve => setTimeout(resolve,0)) } } if(pending.length > 20*1024*1024) throw new Error('Oversized package row'); if(value.done) break } if(pending.trim()) { const parsed: unknown = JSON.parse(pending); if(typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) throw new Error('Invalid package row'); yield parsed as Record<string,unknown> } } finally { reader.releaseLock() }
}
export async function packageTable(pack: InstalledStudyPackage, table: string) { const file = await studyPackageFile(pack, table); if(!file) throw new Error('Missing package table'); return packageRows(file) }
const validFile = (name: string) => /^(module\.json|entries\.jsonl|references\.jsonl|sources\.jsonl|versification_profiles\.jsonl|versification_assignments\.jsonl|versification_map_sets\.jsonl|versification_map_entries\.jsonl|links\.jsonl|word_forms\.jsonl|media_links\.jsonl|media\.jsonl|books\.jsonl|lexicons\.jsonl|media\/(?:[a-f0-9]{40}|[a-f0-9]{64})\.(png|jpg|webp|gif))$/.test(name)
export async function decodeStudyPackage(pack: StudyPackage, zip: Uint8Array, maxBytes = 1024*1024*1024): Promise<{ metadata: Record<string,unknown>; files: Record<string,Blob> }> {
 if(!isStudyPackage(pack) || zip.byteLength !== pack.bytes) throw new Error('Package size mismatch')
 const hash = [...new Uint8Array(await crypto.subtle.digest('SHA-256', zip as BufferSource))].map(b=>b.toString(16).padStart(2,'0')).join('')
 if(hash !== pack.sha256) throw new Error('Package checksum mismatch')
 let total = 0, invalid = false
 const extracted = await new Promise<Record<string,Uint8Array>>((resolve,reject) => unzip(zip, { filter: entry => { total += entry.originalSize; if(!validFile(entry.name) || total > maxBytes || entry.originalSize > maxBytes) invalid = true; return !invalid } }, (error,files)=>error ? reject(error) : resolve(files)))
 if(invalid) throw new Error('Invalid package file or insufficient space')
 const metadata: unknown = JSON.parse(new TextDecoder('utf-8',{fatal:true}).decode(extracted['module.json']).trim())
 if(typeof metadata !== 'object' || metadata === null || !('schema' in metadata) || metadata.schema !== 1 || !('code' in metadata) || metadata.code !== pack.id || !('kind' in metadata) || metadata.kind !== pack.kind) throw new Error('Package identity mismatch')
 const required = pack.kind === 'dictionary' ? ['entries.jsonl','references.jsonl','links.jsonl','word_forms.jsonl','media_links.jsonl','media.jsonl'] : pack.kind === 'commentary' ? ['entries.jsonl','books.jsonl'] : pack.kind === 'cross_references' ? ['references.jsonl','sources.jsonl'] : ['entries.jsonl','lexicons.jsonl']
 if(required.some(name=>!(name in extracted))) throw new Error('Incomplete package')
 const files: Record<string,Blob> = {}
 const counts:Record<string,number>={}
 for(const [name,bytes] of Object.entries(extracted)) { files[name] = new Blob([bytes as BlobPart]); if(name.endsWith('.jsonl')) for await(const row of packageRows(files[name]!)) { counts[name]=(counts[name]??0)+1;if(name === 'entries.jsonl' && (typeof row.id !== 'string' || !row.id || (pack.kind === 'dictionary' && (!/^[a-f0-9]{40}$/.test(row.id) || typeof row.topic !== 'string' || typeof row.body !== 'string')) || (pack.kind === 'commentary' && typeof row.body !== 'string') || (pack.kind === 'strong' && (!/^[HG]?\d{1,5}$/.test(row.id) || row.content!==null&&typeof row.content !== 'string')))) throw new Error('Invalid package article') } }
 if(pack.kind==='commentary'){
  const mediaRows=new Map<number,Record<string,unknown>>(),paths=new Set<string>()
  if(files['media.jsonl'])for await(const row of packageRows(files['media.jsonl'])){if(typeof row.id!=='string'||!/^[a-f0-9]{64}$/.test(row.id)||typeof row.api_id!=='number'||!Number.isSafeInteger(row.api_id)||row.api_id<1||typeof row.mime_type!=='string'||typeof row.bytes!=='number'||row.path!==`media/${row.id}.${commentaryImageTypes[row.mime_type]}`||!files[String(row.path)]||mediaRows.has(row.api_id))throw Error('Invalid commentary media record');await verifyCommentaryImage(files[String(row.path)]!,row.id,row.mime_type,row.bytes);mediaRows.set(row.api_id,row);paths.add(String(row.path))}
  for(const path of Object.keys(files).filter(name=>name.startsWith('media/')))if(!paths.has(path))throw Error('Unlinked commentary image')
  for await(const row of packageRows(files['entries.jsonl']!)){if(!isCommentaryAnnotations(row.annotations))continue;for(const item of row.annotations.media.filter(media=>media.status==='resolved')){commentaryMediaUrl(pack.id,item);const image=mediaRows.get(item.media_id!);if(!image||image.id!==item.sha256||image.bytes!==item.bytes||image.mime_type!==item.mime_type)throw Error('Unlinked commentary annotation image')}}
 }
 Object.assign(metadata,{entries_count:counts['entries.jsonl']??0,media_count:counts['media.jsonl']??0,word_forms_count:counts['word_forms.jsonl']??0,references_count:counts['references.jsonl']??0})
 return { metadata: metadata as Record<string,unknown>, files }
}
async function installAtomically(pack: StudyPackage, data: Awaited<ReturnType<typeof decodeStudyPackage>>) {
 const marker: InstalledStudyPackage = { manifest:pack, metadata:data.metadata, files:Object.keys(data.files), installedAt:new Date().toISOString() }
 const db = await openOfflineDatabase()
 try { await new Promise<void>((resolve,reject)=>{
  const tx=db.transaction(offlineStores.state,'readwrite'), store=tx.objectStore(offlineStores.state)
  tx.oncomplete=()=>resolve(); tx.onabort=tx.onerror=()=>reject(tx.error ?? new Error('Storage failed'))
  const request=store.get(indexKey)
  request.onsuccess=()=>{
   const installed:InstalledStudyPackage[]=request.result?.value??[]
   for(const[name,value]of Object.entries(data.files))store.put({key:fileKey(pack.id,pack.version,name),value})
   const previous=installed.find(p=>p.manifest.id===pack.id)
   if(previous && previous.manifest.version!==pack.version){for(const name of previous.files)store.delete(fileKey(previous.manifest.id,previous.manifest.version,name));deleteStudyPackageIndex(store,previous.manifest.id,previous.manifest.version)}
   store.put({key:indexKey,value:[...installed.filter(p=>p.manifest.id!==pack.id),marker]});store.delete(partialKey(pack))
  }
 }) } finally { db.close() }
 return marker
}
export async function installStudyPackage(pack: StudyPackage, baseUrl: string, signal: AbortSignal, onProgress: (bytes:number,total:number)=>void, fetcher: typeof fetch = fetch) {
 if(!isStudyPackage(pack)) throw new Error('Invalid manifest')
 const available = await navigator.storage?.estimate?.(); if(pack.bytes > 1024*1024*1024 || available?.quota && pack.bytes > available.quota-(available.usage??0)) throw new Error('Insufficient space')
 const url=new URL(pack.url,baseUrl); if(url.origin!==new URL(baseUrl).origin)throw new Error('Invalid package URL')
 let partial=await readCalendarState<Blob>(partialKey(pack)), downloaded=partial?.size??0
 if(downloaded>=pack.bytes) { partial=undefined; downloaded=0 }
 const response=await fetcher(url,{signal,headers:downloaded?{Range:`bytes=${downloaded}-`}:{}})
 if(!response.ok)throw new Error(`HTTP ${response.status}`)
 if(downloaded && response.status===206) { if(!response.headers.get('content-range')?.startsWith(`bytes ${downloaded}-`))throw new Error('Invalid resumed response') } else { partial=undefined;downloaded=0 }
 const parts: BlobPart[] = partial?[partial]:[]; let persisted=downloaded
 if(!response.body)throw new Error('Empty package response')
 const reader=response.body.getReader()
 try { while(true) { signal.throwIfAborted();const part=await reader.read();if(part.done)break; downloaded+=part.value.length;if(downloaded>pack.bytes)throw new Error('Package exceeds manifest size');parts.push(part.value as BlobPart);onProgress(downloaded,pack.bytes);if(downloaded-persisted>=4*1024*1024){await writeCalendarState(partialKey(pack),new Blob(parts));persisted=downloaded} } }
 finally { reader.releaseLock(); if(downloaded && downloaded<pack.bytes)await writeCalendarState(partialKey(pack),new Blob(parts)).catch(()=>undefined) }
 signal.throwIfAborted(); const zip = new Uint8Array(await new Blob(parts).arrayBuffer())
 let decoded:Awaited<ReturnType<typeof decodeStudyPackage>>
 try{decoded=await decodeStudyPackage(pack,zip,available?.quota ? Math.min(1024*1024*1024,available.quota-(available.usage??0)) : undefined)}
 catch(error){const db=await openOfflineDatabase();try{const tx=db.transaction(offlineStores.state,'readwrite');tx.objectStore(offlineStores.state).delete(partialKey(pack))}finally{db.close()}throw error}
 try{signal.throwIfAborted();await buildStudyPackageIndex(pack,decoded.files,signal);signal.throwIfAborted();return await installAtomically(pack,decoded)}
 catch(error){if(!(await installedStudyPackages()).some(p=>p.manifest.id===pack.id&&p.manifest.version===pack.version))await discardStudyPackageIndex(pack).catch(()=>undefined);throw error}
}
export async function removeStudyPackage(id:string) {
 const db=await openOfflineDatabase()
 try{await new Promise<void>((resolve,reject)=>{const tx=db.transaction(offlineStores.state,'readwrite'),store=tx.objectStore(offlineStores.state);tx.oncomplete=()=>resolve();tx.onabort=tx.onerror=()=>reject(tx.error??new Error('Storage failed'));const request=store.get(indexKey);request.onsuccess=()=>{const installed:InstalledStudyPackage[]=request.result?.value??[],pack=installed.find(p=>p.manifest.id===id);if(pack){for(const name of pack.files)store.delete(fileKey(pack.manifest.id,pack.manifest.version,name));deleteStudyPackageIndex(store,pack.manifest.id,pack.manifest.version)}store.put({key:indexKey,value:installed.filter(p=>p.manifest.id!==id)})}})}finally{db.close()}
}
