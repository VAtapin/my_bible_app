import type { DictionaryArticle, DictionaryModule, DictionaryPage } from '@/api/dictionaries'
import { installedStudyPackages, studyPackageFile, packageTable } from './studyPackages'
import { installedStudyPackage, studyPackageRows, studyPackageArticle } from './studyPackageLookup'
const numericId = (value:unknown) => { if(typeof value!=='number'||!Number.isSafeInteger(value)||value<=0)throw new Error('Package API alias unavailable');return value }
export async function installedDictionaryModules():Promise<DictionaryModule[]> {
 return (await installedStudyPackages()).filter(p=>p.manifest.kind==='dictionary').map(p=>({code:p.manifest.id,name:String(p.metadata.name??p.manifest.id),language_code:typeof p.metadata.language_code==='string'?p.metadata.language_code:null,kind:Number(p.metadata.media_count)>0?'atlas':Number(p.metadata.entries_count)===0?'word_forms':'articles',content_version:typeof p.metadata.source_archive_sha256==='string'?p.metadata.source_archive_sha256:p.manifest.version,entries_count:Number(p.metadata.entries_count??0),media_count:Number(p.metadata.media_count??0),word_forms_count:Number(p.metadata.word_forms_count??0)}))
}
export async function installedDictionaryEntries(code:string,q:string,offset:number):Promise<DictionaryPage|undefined> {
 const pack=await installedStudyPackage(code);if(pack?.manifest.kind!=='dictionary')return undefined
 const page=await studyPackageRows(pack,'entries.jsonl',{query:q,offset,limit:30})
 return{total:page.total,data:page.rows.map(row=>({id:numericId(row.api_id),key:String(row.id),topic:String(row.topic)}))}
}
export async function installedDictionaryArticle(code:string,key:string):Promise<DictionaryArticle|undefined> {
 const pack=await installedStudyPackage(code);if(pack?.manifest.kind!=='dictionary')return undefined
 const data=await studyPackageArticle(pack,key);if(!data)throw new Error('Installed article unavailable')
 const mediaLinks=await studyPackageRows(pack,'media_links.jsonl',{entry:key,limit:Number.MAX_SAFE_INTEGER}),media:DictionaryArticle['media']=[]
 for(const link of mediaLinks.rows){const row=(await studyPackageRows(pack,'media.jsonl',{id:String(link.media_id),limit:1})).rows[0];if(row){const id=numericId(row.api_id);media.push({id,fragment_id:String(row.fragment_id),url:`/api/dictionaries/${encodeURIComponent(code)}/media/${id}`})}}
 const links:DictionaryArticle['links']=[]
 for(const link of data.links){const target=(await studyPackageRows(pack,'entries.jsonl',{id:String(link.target_id),limit:1})).rows[0];if(target)links.push({label:String(link.label??''),key:String(target.id),topic:String(target.topic)})}
 return{id:numericId(data.article.api_id),key:String(data.article.id),topic:String(data.article.topic),body:String(data.article.body),media,links,references:data.references.map(r=>({book_slug:String(r.book_slug),book_osis:typeof r.book_osis === 'string'?r.book_osis:null,chapter_number:r.chapter===null?null:Number(r.chapter),verse_from:r.verse_from===null?null:Number(r.verse_from),verse_to:r.verse_to===null?null:Number(r.verse_to)}))}
}
export async function installedDictionaryImage(code:string,apiId:number) {
 const pack=await installedStudyPackage(code);if(pack?.manifest.kind!=='dictionary')return undefined
 for await(const row of await packageTable(pack,'media.jsonl'))if(row.api_id===apiId&&typeof row.path==='string'){const file=await studyPackageFile(pack,row.path);return file?new Blob([file],{type:String(row.mime_type)}):undefined}
 return undefined
}
export async function installedDictionaryLookup(q:string,codes:string[]) {
 const modules=(await installedDictionaryModules()).filter(m=>!codes.length||codes.includes(m.code)), query=q.trim().toLocaleLowerCase(),data:{module_code:string;standard_form:string}[]=[]
 for(const module of modules){const pack=await installedStudyPackage(module.code);if(!pack)continue;const rows=await studyPackageRows(pack,'word_forms.jsonl',{variation:query,limit:Number.MAX_SAFE_INTEGER});for(const row of rows.rows)data.push({module_code:module.code,standard_form:String(row.standard_form)})}
 return{data,allInstalled:codes.length>0&&codes.every(code=>modules.some(m=>m.code===code))}
}
export async function installedDictionaryContext(book:string,chapter:number|null,codes:string[],offset:number,first?:number,last?:number,osis?:string):Promise<DictionaryPage|undefined> {
 const modules=(await installedDictionaryModules()).filter(m=>!codes.length||codes.includes(m.code));if(!modules.length||codes.length&&codes.some(code=>!modules.some(m=>m.code===code)))return undefined
 const found=new Map<string,DictionaryPage['data'][number]>()
 for(const module of modules){const pack=await installedStudyPackage(module.code);if(!pack)continue;const refs=await studyPackageRows(pack,'references.jsonl',{book:osis?undefined:book,bookOsis:osis,limit:Number.MAX_SAFE_INTEGER});for(const ref of refs.rows){if(osis&&ref.book_osis!==osis)continue;if(chapter===null?ref.chapter!==null:ref.chapter!==null&&Number(ref.chapter)!==chapter)continue;if(first!==undefined&&ref.verse_from!==null&&(Number(ref.verse_from)>(last??first)||Number(ref.verse_to)<first))continue;const key=String(ref.entry_id),identity=`${module.code}:${key}`;if(found.has(identity))continue;const topic=(await studyPackageRows(pack,'entries.jsonl',{id:key,limit:1})).rows[0];if(topic)found.set(identity,{id:numericId(topic.api_id),key,topic:String(topic.topic),module_code:module.code,module_name:module.name})}}
 const data=[...found.values()];return{total:data.length,data:data.slice(offset,offset+30)}
}
