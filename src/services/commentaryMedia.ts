import type {CommentaryAnnotationMedia} from '@/api/commentaryAnnotations'
import {apiBaseUrl} from '@/config/api'
import {installedStudyPackage,studyPackageRows} from './studyPackageLookup'
import {studyPackageFile} from './studyPackages'
import {readCalendarState,writeCalendarState} from '@/offline/calendarMedia'
export const commentaryImageTypes:Record<string,string>={'image/png':'png','image/jpeg':'jpg','image/webp':'webp','image/gif':'gif'}
export function commentaryMediaUrl(code:string,media:CommentaryAnnotationMedia,base=apiBaseUrl){
 if(!/^[A-Za-z0-9][A-Za-z0-9_.-]*$/.test(code)||media.status!=='resolved'||!Number.isSafeInteger(media.media_id)||media.media_id!<=0||typeof media.sha256!=='string'||!/^[a-f0-9]{64}$/.test(media.sha256)||!Number.isSafeInteger(media.bytes)||media.bytes!<1||media.bytes!>10*1024*1024||!media.mime_type||!commentaryImageTypes[media.mime_type]||!media.url)throw Error('Invalid commentary media')
 const origin=new URL(base,globalThis.location?.origin??'https://bible-desktop.com'),url=new URL(media.url,origin)
 if(url.origin!==origin.origin||url.pathname!==`/api/commentary-modules/${code}/media/${media.media_id}`||url.search||url.hash||url.username||url.password)throw Error('Wrong commentary media owner')
 return url.href
}
export async function verifyCommentaryImage(file:Blob,sha:string,mime:string,size:number){
 if(file.size!==size||size<1||size>10*1024*1024||!commentaryImageTypes[mime])throw Error('Invalid commentary image size/type')
 const bytes=new Uint8Array(await file.arrayBuffer()),text=(first:number,last:number)=>String.fromCharCode(...bytes.slice(first,last))
 const matches=mime==='image/png'?[137,80,78,71,13,10,26,10].every((n,i)=>bytes[i]===n):mime==='image/jpeg'?bytes[0]===255&&bytes[1]===216&&bytes[2]===255:mime==='image/gif'?['GIF87a','GIF89a'].includes(text(0,6)):text(0,4)==='RIFF'&&text(8,12)==='WEBP'
 const hash=[...new Uint8Array(await crypto.subtle.digest('SHA-256',bytes))].map(n=>n.toString(16).padStart(2,'0')).join('')
 if(!matches||hash!==sha)throw Error('Commentary image checksum/type mismatch')
 return new Blob([bytes],{type:mime})
}
export async function loadCommentaryImage(code:string,media:CommentaryAnnotationMedia,base=apiBaseUrl,fetcher:typeof fetch=fetch){
 const url=commentaryMediaUrl(code,media,base),key=`commentary:image:${code}:${media.sha256}`,pack=await installedStudyPackage(code)
 if(pack){if(pack.manifest.kind!=='commentary')throw Error('Wrong package kind');const row=(await studyPackageRows(pack,'media.jsonl',{id:media.sha256,limit:1})).rows[0];if(!row||row.api_id!==media.media_id||row.bytes!==media.bytes||row.mime_type!==media.mime_type)throw Error('Installed image alias unavailable');const file=await studyPackageFile(pack,String(row.path));if(!file)throw Error('Installed image missing');return verifyCommentaryImage(file,media.sha256!,media.mime_type!,media.bytes!)}
 const saved=await readCalendarState<Blob>(key).catch(()=>undefined);if(saved)return verifyCommentaryImage(saved,media.sha256!,media.mime_type!,media.bytes!)
 const response=await fetcher(url,{redirect:'error'});if(!response.ok||response.headers.get('content-type')?.split(';')[0]!==media.mime_type)throw Error('Commentary image response invalid')
 const reader=response.body?.getReader();if(!reader)throw Error('Commentary image stream missing');const chunks:BlobPart[]=[];let count=0
 try{while(true){const part=await reader.read();if(part.done)break;count+=part.value.byteLength;if(count>media.bytes!)throw Error('Commentary image too large');chunks.push(part.value as BlobPart)}}finally{await reader.cancel().catch(()=>undefined)}
 const file=await verifyCommentaryImage(new Blob(chunks),media.sha256!,media.mime_type!,media.bytes!);await writeCalendarState(key,file).catch(()=>undefined);return file
}
