import type { StudyReferences,StudyCrossReference } from '@/api/verseStudy'
import { installedStudyPackage,studyPackageRows } from './studyPackageLookup'
import { createChapterService } from './chapterService'
import { createIndexedDbChapterRepository } from '@/offline/indexedDbChapterRepository'
import { bibleApi } from '@/api'
import {offlineVersification} from './offlineVersification'
const record=(value:unknown):value is Record<string,unknown>=>typeof value==='object'&&value!==null
export async function installedCrossReferences(id:number,code:string,actualOsis?:string):Promise<StudyReferences|undefined> {
 const pack=await installedStudyPackage('CROSS_REFERENCES');if(pack?.manifest.kind!=='cross_references')return undefined
 const service=createChapterService(bibleApi,createIndexedDbChapterRepository()),stored=await service.listStored(),chapters=stored.map(row=>row.data).filter(chapter=>chapter.translation.code===code),current=chapters.flatMap(chapter=>chapter.verses).find(verse=>verse.id===id)
 const requested=actualOsis??current?.osis_ref;if(requested&&!/^[A-Za-z0-9]+\.[1-9]\d*\.[1-9]\d*$/.test(requested))throw new Error('Invalid canonical reference')
 const data=await studyPackageRows(pack,'references.jsonl',{...(requested?{sourceOsis:requested}:{sourceApiId:id}),limit:Number.MAX_SAFE_INTEGER}),references:StudyCrossReference[]=[],verify=await offlineVersification(pack);let sourceOsis=requested??''
 for(const row of data.rows){const source=row.source_verse,target=row.target_verse;if(!record(source)||!record(target)||typeof source.osis_ref!=='string'||typeof target.osis_ref!=='string'||typeof target.api_id!=='number'||typeof row.api_id!=='number'||requested&&source.osis_ref!==requested)throw new Error('Invalid installed reference');sourceOsis=source.osis_ref
  const versification=await verify(String(row.source_code??''),code,source.osis_ref,target.osis_ref)
  let text:string|null=null,book=String(target.book_slug),chapter=Number(target.chapter),number=Number(target.verse)
  const value=chapters.find(chapter=>chapter.verses.some(verse=>verse.osis_ref===target.osis_ref)),verse=value?.verses.find(verse=>verse.osis_ref===target.osis_ref)
  if(value&&verse?.plain_text.trim()){if(versification.status==='verified')text=verse.plain_text;book=value.book.slug;chapter=value.chapter.number;number=verse.number}
  const metadata=record(row.metadata)?row.metadata:undefined
  references.push({id:row.api_id,type:typeof row.type==='string'?row.type:null,source:typeof row.source_name==='string'?row.source_name:String(row.source_code??''),versification,metadata:{...(typeof metadata?.legacy_quote_id==='number'?{legacy_quote_id:metadata.legacy_quote_id}:{}),...(typeof row.raw_range==='string'?{raw_ref:row.raw_range}:{})},target:{verse_id:target.api_id,osis_ref:target.osis_ref,reference:`${target.book_osis??String(target.osis_ref).split('.')[0]} ${chapter}:${number}`,book_slug:book,chapter_number:chapter,verse_number:number,text,versification}})
 }
 if(!sourceOsis)return undefined
 return{verse:{id,osis_ref:sourceOsis},translation_code:code,references}
}
