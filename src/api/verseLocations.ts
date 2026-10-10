import { createApiRequest, type ApiClientOptions } from './client'
export interface VerseLocation { verse_id:number;osis_ref:string;book_slug:string;chapter_number:number;verse_number:number }
const pattern=/^[A-Za-z0-9]+\.[1-9]\d*\.[1-9]\d*$/u
export function createVerseLocationApi(options:ApiClientOptions){
 const request=createApiRequest(options)
 return async(code:string,osis:string[]):Promise<VerseLocation[]>=>{
  if(!code||!osis.length||osis.length>200||new Set(osis).size!==osis.length||osis.some(ref=>!pattern.test(ref)))throw new Error('Invalid canonical references')
  const query=new URLSearchParams();osis.forEach(ref=>query.append('osis_refs[]',ref))
  const result=await request(`/translations/${encodeURIComponent(code)}/verse-locations?${query}`,(v):v is {data:VerseLocation[]}=>{
   if(typeof v!=='object'||v===null||!('data'in v)||!Array.isArray(v.data))return false
   return v.data.every(r=>typeof r==='object'&&r!==null&&Number.isSafeInteger(r.verse_id)&&r.verse_id>0&&osis.includes(r.osis_ref)&&typeof r.book_slug==='string'&&r.book_slug&&Number.isSafeInteger(r.chapter_number)&&r.chapter_number>0&&Number.isSafeInteger(r.verse_number)&&r.verse_number>0)&&new Set(v.data.map(r=>r.osis_ref)).size===v.data.length
  });return result.data
 }
}
