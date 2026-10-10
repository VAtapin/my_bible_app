import { openOfflineDatabase,offlineStores,runRequest } from '@/offline/database'
import { readCalendarState,writeCalendarState } from '@/offline/calendarMedia'
import type { StudyPackage } from '@/api/studyPackages'
import type { InstalledStudyPackage } from './studyPackages'
import { packageRows } from './studyPackages'
const marker=(id:string,version:string)=>`study-package-index:${id}:${version}`
const headerKey=(id:string,version:string,table:string)=>`study-package-headers:${id}:${version}:${table}`
const headerCache=new Map<string,Promise<Record<string,unknown>[]>>()
export interface PackageQuery {query?:string;id?:string;entry?:string;book?:string;bookOsis?:string;sourceApiId?:number;sourceOsis?:string;variation?:string;offset?:number;limit?:number}
interface PackageIndexRecord {key:string;value:Record<string,unknown>;[field:string]:unknown}
const record=(value:unknown):value is Record<string,unknown>=>typeof value==='object'&&value!==null
export async function buildStudyPackageIndex(pack:StudyPackage,files:Record<string,Blob>,signal:AbortSignal) {
 if(await readCalendarState(marker(pack.id,pack.version)))return
 const db=await openOfflineDatabase()
 const bookSlugs=new Map<string,Set<string>>()
 async function batch(values:PackageIndexRecord[]) {signal.throwIfAborted();await new Promise<void>((resolve,reject)=>{const tx=db.transaction(offlineStores.state,'readwrite'),store=tx.objectStore(offlineStores.state);tx.oncomplete=()=>resolve();tx.onabort=tx.onerror=()=>reject(tx.error??new Error('Index storage failed'));for(const value of values)store.put(value)})}
 try {
  for(const[name,file]of Object.entries(files)){if(!name.endsWith('.jsonl'))continue;let position=0,pending:PackageIndexRecord[]=[],headers:Record<string,unknown>[]=[]
   for await(const row of packageRows(file)){
    signal.throwIfAborted();const prefix=[pack.id,pack.version,name],value:PackageIndexRecord={key:`study-index:${pack.id}:${pack.version}:${name}:${String(position++).padStart(12,'0')}`,value:row,studyPackage:[pack.id,pack.version],studyTable:prefix}
    if(typeof row.source_osis_ref==='string')value.studySource=[...prefix,row.source_osis_ref]
    if(typeof row.id==='string')value.studyId=[...prefix,row.id]
    const parent=row.entry_id??row.commentary_book_id;if(typeof parent==='string')value.studyParent=[...prefix,parent]
    const alias=row.api_id??row.entry_api_id;if(typeof alias==='number')value.studyApiId=[...prefix,String(alias)]
    if(typeof row.book_slug==='string')value.studyBook=[...prefix,row.book_slug]
    if(typeof row.book_osis==='string'&&typeof row.book_slug==='string'){const values=bookSlugs.get(row.book_osis)??new Set<string>();values.add(row.book_slug);bookSlugs.set(row.book_osis,values)}
    if(typeof row.variation==='string')value.studyVariation=[...prefix,row.variation.normalize('NFC').toLocaleLowerCase()]
    if(record(row.source_verse)){if(typeof row.source_verse.osis_ref==='string')value.studySource=[...prefix,row.source_verse.osis_ref];if(typeof row.source_verse.api_id==='number')value.studySourceId=[...prefix,String(row.source_verse.api_id)]}
    if(name==='entries.jsonl'&&pack.kind==='dictionary'||name==='books.jsonl')headers.push({id:row.id,api_id:row.api_id,topic:row.topic,title:row.title,author:row.author})
    pending.push(value);if(pending.length>=500){await batch(pending);pending=[]}
   }
   if(pending.length)await batch(pending)
   if(headers.length)await writeCalendarState(headerKey(pack.id,pack.version,name),headers)
  }
  signal.throwIfAborted();await writeCalendarState(marker(pack.id,pack.version),{schema:1,bookSlugs:Object.fromEntries([...bookSlugs].map(([osis,slugs])=>[osis,[...slugs]]))})
 }finally{db.close()}
}
export function deleteStudyPackageIndex(store:IDBObjectStore,id:string,version:string) {
 const cursor=store.index('studyPackage').openKeyCursor(IDBKeyRange.only([id,version]))
 cursor.onsuccess=()=>{const row=cursor.result;if(row){store.delete(row.primaryKey);row.continue()}}
 store.delete(marker(id,version))
 for(const table of ['entries.jsonl','books.jsonl']){store.delete(headerKey(id,version,table));headerCache.delete(headerKey(id,version,table))}
}
export async function discardStudyPackageIndex(pack:StudyPackage){const db=await openOfflineDatabase();try{await new Promise<void>((resolve,reject)=>{const tx=db.transaction(offlineStores.state,'readwrite');tx.oncomplete=()=>resolve();tx.onabort=tx.onerror=()=>reject(tx.error??new Error('Index cleanup failed'));deleteStudyPackageIndex(tx.objectStore(offlineStores.state),pack.id,pack.version)})}finally{db.close()}}
export async function indexedStudyPackageRows(pack:InstalledStudyPackage,table:string,options:PackageQuery):Promise<{rows:Record<string,unknown>[];total:number}|undefined> {
 const metadata=await readCalendarState<{schema:number;bookSlugs?:Record<string,string[]>}>(marker(pack.manifest.id,pack.manifest.version));if(!metadata)return undefined
 const aliases=options.bookOsis&&metadata.bookSlugs&&Object.hasOwn(metadata.bookSlugs,options.bookOsis)?metadata.bookSlugs[options.bookOsis]:undefined,book=options.book??(aliases?.length===1?aliases[0]:undefined)
 const db=await openOfflineDatabase(),prefix=[pack.manifest.id,pack.manifest.version,table],offset=options.offset??0,limit=options.limit??30,query=options.query?.normalize('NFC').toLocaleLowerCase().trim()??''
 if(query && !options.id&&!options.entry&&!options.book&&!options.sourceOsis&&options.sourceApiId===undefined&&(table==='entries.jsonl'&&pack.manifest.kind==='dictionary'||table==='books.jsonl')){
  db.close();const key=headerKey(pack.manifest.id,pack.manifest.version,table)
  let pending=headerCache.get(key);if(!pending){pending=readCalendarState<Record<string,unknown>[]>(key).then(value=>value??[]);headerCache.set(key,pending);if(headerCache.size>4)headerCache.delete(headerCache.keys().next().value!)}
  const headers=await pending,matched=headers.filter(row=>[row.topic,row.title,row.author].filter(value=>typeof value==='string').join(' ').normalize('NFC').toLocaleLowerCase().includes(query))
  return{rows:matched.slice(offset,offset+limit),total:matched.length}
 }
 const selection=options.id!==undefined?['studyId',options.id]:options.entry!==undefined?['studyParent',options.entry]:options.sourceOsis!==undefined?['studySource',options.sourceOsis]:options.sourceApiId!==undefined?['studySourceId',String(options.sourceApiId)]:options.variation!==undefined?['studyVariation',options.variation.normalize('NFC').toLocaleLowerCase()]:book!==undefined?['studyBook',book]:['studyTable']
 try {return await new Promise((resolve,reject)=>{const tx=db.transaction(offlineStores.state),index=tx.objectStore(offlineStores.state).index(selection[0]!),cursor=index.openCursor(IDBKeyRange.only(selection.length===1?prefix:[...prefix,selection[1]!]));const rows:Record<string,unknown>[]=[];let total=0
  cursor.onerror=()=>reject(cursor.error??new Error('Package index failed'))
  cursor.onsuccess=()=>{const item=cursor.result;if(!item){resolve({rows,total});return}const row=(item.value as PackageIndexRecord).value,source=record(row.source_verse)?row.source_verse:undefined,target=record(row.target_verse)?row.target_verse:undefined
   const matches=(!options.id||row.id===options.id)&&(!options.entry||row.entry_id===options.entry||row.commentary_book_id===options.entry)&&(!options.book||row.book_slug===options.book)&&(!options.bookOsis||row.book_osis===options.bookOsis)&&(options.sourceOsis===undefined||(source?.osis_ref??row.source_osis_ref)===options.sourceOsis)&&(options.sourceApiId===undefined||source?.api_id===options.sourceApiId)&&(options.variation===undefined||String(row.variation).normalize('NFC').toLocaleLowerCase()===options.variation.normalize('NFC').toLocaleLowerCase())&&(!query||[row.topic,row.title,row.author,row.standard_form,row.variation,row.word,row.id,row.source_code,row.source_name,source?.osis_ref,target?.osis_ref].filter(v=>typeof v==='string').join(' ').normalize('NFC').toLocaleLowerCase().includes(query))
   if(matches){if(total>=offset&&rows.length<limit)rows.push(row);total++}item.continue()
  }
 })}finally{db.close()}
}
