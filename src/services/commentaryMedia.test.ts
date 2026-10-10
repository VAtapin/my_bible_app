import 'fake-indexeddb/auto'
import {afterEach,expect,it,vi} from 'vitest'
import {strToU8,zipSync} from 'fflate'
import {installStudyPackage,removeStudyPackage} from './studyPackages'
import {loadCommentaryImage,commentaryMediaUrl} from './commentaryMedia'
import {installedStudyArticle} from './installedStudyLibrary'
import type {CommentaryAnnotationMedia} from '@/api/commentaryAnnotations'
import {writeCalendarState} from '@/offline/calendarMedia'
const png=Uint8Array.from(atob('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aRZkAAAAASUVORK5CYII='),c=>c.charCodeAt(0))
const sha=async(bytes:Uint8Array)=>Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',bytes)),n=>n.toString(16).padStart(2,'0')).join('')
async function source(corrupt=false,owner='COMMENT_MEDIA'){
 const hash=await sha(png),media:CommentaryAnnotationMedia={status:'resolved',src:'actual.png',alt:'Actual image',media_id:71,url:`/api/commentary-modules/${owner}/media/71`,sha256:hash,mime_type:'image/png',bytes:png.length},actual=png.slice();if(corrupt)actual[15]^=1
 const rows=(data:unknown)=>strToU8(JSON.stringify(data)+'\n'),zip=zipSync({'module.json':rows({schema:1,kind:'commentary',code:'COMMENT_MEDIA',name:'Real module'}),'books.jsonl':rows({id:'actual-book',api_id:901,title:'Actual book',order:0}),'entries.jsonl':rows({id:'section-key',api_id:911,commentary_book_id:'actual-book',commentary_book_api_id:901,book_slug:'john',book_osis:'John',chapter_from:3,verse_from:16,body:'Complete section body',order:0,annotations:{source_sha256:'a'.repeat(64),links:[],media:[media]}}),'media.jsonl':rows({id:hash,api_id:71,mime_type:'image/png',bytes:png.length,width:1,height:1,path:`media/${hash}.png`}),[`media/${hash}.png`]:actual}),version=await sha(zip)
 return{media,zip,pack:{id:'COMMENT_MEDIA',kind:'commentary' as const,version,sha256:version,bytes:zip.length,url:'/api/offline/packages/COMMENT_MEDIA'}}
}
const fetchZip=(zip:Uint8Array)=>vi.fn(async()=>new Response(zip as BodyInit)) as typeof fetch
afterEach(async()=>removeStudyPackage('COMMENT_MEDIA'))
it('renders the full installed book body and loads verified companion PNG without networking',async()=>{
 const data=await source();await installStudyPackage(data.pack,'https://example.test/api',new AbortController().signal,()=>{},fetchZip(data.zip))
 const article=await installedStudyArticle(901,911);expect(article?.body).toBe('Complete section body');expect(article?.annotations?.media[0]).toEqual(data.media)
 const network=vi.fn().mockRejectedValue(Error('Unexpected network'));const file=await loadCommentaryImage('COMMENT_MEDIA',data.media,'https://example.test/api',network)
 expect(file.type).toBe('image/png');expect(new Uint8Array(await file.arrayBuffer())).toEqual(png);expect(network).not.toHaveBeenCalled()
 expect(()=>commentaryMediaUrl('OTHER',data.media,'https://example.test/api')).toThrow('owner')
})
it('rejects a valid ZIP with corrupted image or a cross-module annotation before installation',async()=>{
 const bad=await source(true);await expect(installStudyPackage(bad.pack,'https://example.test/api',new AbortController().signal,()=>{},fetchZip(bad.zip))).rejects.toThrow('checksum')
 const wrong=await source(false,'OTHER');await expect(installStudyPackage(wrong.pack,'https://example.test/api',new AbortController().signal,()=>{},fetchZip(wrong.zip))).rejects.toThrow('owner')
})
it('checks the online endpoint, MIME and checksum, then reads the saved image with networking disabled',async()=>{
 const {media}=await source(),online={...media,url:'/api/commentary-modules/ONLINE_COMMENT/media/71'},fetcher=vi.fn(async()=>new Response(png,{headers:{'content-type':'image/png'}}))
 try{await loadCommentaryImage('ONLINE_COMMENT',online,'https://example.test/api',fetcher);expect(fetcher).toHaveBeenCalledWith('https://example.test/api/commentary-modules/ONLINE_COMMENT/media/71',{redirect:'error'})
  const offline=vi.fn().mockRejectedValue(Error('No network'));expect((await loadCommentaryImage('ONLINE_COMMENT',online,'https://example.test/api',offline)).size).toBe(png.length);expect(offline).not.toHaveBeenCalled()
  await expect(loadCommentaryImage('BAD_MIME',{...media,url:'/api/commentary-modules/BAD_MIME/media/71'},'https://example.test/api',vi.fn(async()=>new Response(png,{headers:{'content-type':'text/html'}})))).rejects.toThrow('response')
  await expect(loadCommentaryImage('BAD_HASH',{...media,url:'/api/commentary-modules/BAD_HASH/media/71',sha256:'b'.repeat(64)},'https://example.test/api',fetcher)).rejects.toThrow('checksum')
 }finally{await writeCalendarState(`commentary:image:ONLINE_COMMENT:${media.sha256}`,undefined)}
})
