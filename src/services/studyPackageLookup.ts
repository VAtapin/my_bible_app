import { installedStudyPackages, packageTable, studyPackageFile, type InstalledStudyPackage } from './studyPackages'
import { indexedStudyPackageRows,type PackageQuery } from './studyPackageIndex'
export async function installedStudyPackage(code: string) { return (await installedStudyPackages()).find(pack => pack.manifest.id === code) }
export async function studyPackageRows(pack: InstalledStudyPackage, table: string, options: PackageQuery = {}) {
 const indexed=await indexedStudyPackageRows(pack,table,options);if(indexed)return indexed
 const rows: Record<string,unknown>[] = []; let total = 0
 const query = options.query?.normalize('NFC').toLocaleLowerCase().trim() ?? '', offset = options.offset ?? 0, limit = options.limit ?? 30
 for await(const row of await packageTable(pack,table)) {
  if(options.id && row.id!==options.id)continue
  if(options.entry && row.entry_id!==options.entry && row.commentary_book_id!==options.entry)continue
  if(options.book && row.book_slug!==options.book)continue
  if(options.bookOsis && row.book_osis!==options.bookOsis)continue
  const source=typeof row.source_verse==='object'&&row.source_verse!==null?row.source_verse as Record<string,unknown>:undefined,target=typeof row.target_verse==='object'&&row.target_verse!==null?row.target_verse as Record<string,unknown>:undefined
  if(options.sourceApiId!==undefined && source?.api_id!==options.sourceApiId)continue
  if(options.sourceOsis!==undefined && (source?.osis_ref??row.source_osis_ref)!==options.sourceOsis)continue
  if(options.variation!==undefined && String(row.variation).normalize('NFC').toLocaleLowerCase()!==options.variation.normalize('NFC').toLocaleLowerCase())continue
  if(query && ![row.topic,row.title,row.author,row.standard_form,row.variation,row.word,row.id,row.source_code,row.source_name,source?.osis_ref,target?.osis_ref].filter(v=>typeof v==='string').join(' ').normalize('NFC').toLocaleLowerCase().includes(query))continue
  if(total>=offset && rows.length<limit)rows.push(row);total++
 }
 return { rows,total }
}
export async function installedStrongEntries(number:string) { const pack=await installedStudyPackage('STRONG');if(!pack)return undefined; const entries=await studyPackageRows(pack,'entries.jsonl',{id:number,limit:Number.MAX_SAFE_INTEGER}),lexicons=await studyPackageRows(pack,'lexicons.jsonl',{limit:Number.MAX_SAFE_INTEGER});return{entries:entries.rows,lexicons:lexicons.rows} }
export interface PackageArticle {article:Record<string,unknown>;links:Record<string,unknown>[];references:Record<string,unknown>[];media:{id:string;file:Blob}[]}
export async function studyPackageArticle(pack: InstalledStudyPackage, key:string,lexiconCode?:string):Promise<PackageArticle|undefined> {
 if(pack.manifest.kind==='cross_references'){const row=(await studyPackageRows(pack,'references.jsonl',{id:key,limit:1})).rows[0];if(!row)return undefined;const references=[row.source_verse,row.target_verse].filter((value):value is Record<string,unknown>=>typeof value==='object'&&value!==null).map(ref=>({book_slug:ref.book_slug,book_osis:ref.book_osis,chapter:ref.chapter,verse_from:ref.verse,verse_to:ref.verse}));return{article:{...row,title:references.map(ref=>`${ref.book_osis} ${ref.chapter}:${ref.verse_from}`).join(' → '),body:String(row.raw_range??'')},links:[] as Record<string,unknown>[],references,media:[] as {id:string;file:Blob}[]}}
 const candidates=(await studyPackageRows(pack,'entries.jsonl',{id:key,limit:Number.MAX_SAFE_INTEGER})).rows,article=candidates.find(row=>!lexiconCode||row.lexicon_code===lexiconCode);if(!article)return undefined
 if(pack.manifest.kind!=='dictionary')return {article,links:[],references:[],media:[]}
 const links=(await studyPackageRows(pack,'links.jsonl',{entry:key,limit:Number.MAX_SAFE_INTEGER})).rows, references=(await studyPackageRows(pack,'references.jsonl',{entry:key,limit:Number.MAX_SAFE_INTEGER})).rows, mediaLinks=(await studyPackageRows(pack,'media_links.jsonl',{entry:key,limit:Number.MAX_SAFE_INTEGER})).rows
 const media: {id:string;file:Blob}[]=[]
 for(const link of mediaLinks) { const item=(await studyPackageRows(pack,'media.jsonl',{id:String(link.media_id),limit:1})).rows[0];if(item && typeof item.path==='string'){const file=await studyPackageFile(pack,item.path);if(file)media.push({id:String(item.id),file})} }
 return {article,links,references,media}
}
