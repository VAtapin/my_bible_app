import type { CommentaryAnnotationLink } from '@/api/commentaryAnnotations'
export function commentaryExternalUrl(link:CommentaryAnnotationLink):string|undefined {
 if(link.kind!=='external')return undefined
 try{const url=new URL(link.href);return url.protocol==='https:'&&!url.username&&!url.password?url.href:undefined}catch{return undefined}
}
export function commentaryScriptureReference(link:CommentaryAnnotationLink){return link.kind==='bible'&&link.book_slug&&Number.isSafeInteger(link.chapter)&&Number.isSafeInteger(link.verse)&&link.chapter!>0&&link.verse!>0?{book_slug:link.book_slug,chapter_number:link.chapter!,verse_from:link.verse!,verse_to:link.verse!}:undefined}
