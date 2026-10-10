import {expect,it} from 'vitest'
import {isCommentaryAnnotations} from '@/api/commentaryAnnotations'
import {commentaryExternalUrl,commentaryScriptureReference} from './commentaryAnnotations'
it('keeps genuine MyBible source targets and unresolved INCLUDE metadata without inventing IDs or images',()=>{
 const value={source_sha256:'a'.repeat(64),links:[{kind:'bible',href:'B: 500 3:16',label:'John',status:'raw',book_slug:'john',chapter:3,verse:16}],media:[{fragment_id:'drawing-1',module:'source',textual:'false',status:'unresolved'}]}
 expect(isCommentaryAnnotations(value)).toBe(true);expect(commentaryScriptureReference(value.links[0] as never)).toEqual({book_slug:'john',chapter_number:3,verse_from:16,verse_to:16})
 expect(commentaryScriptureReference({kind:'commentary',href:'C: 12',label:'Comment',status:'raw'})).toBeUndefined()
 expect(isCommentaryAnnotations({...value,source_sha256:'invalid'})).toBe(false)
 const malformed={...value.links[0],chapter:0,verse:0};expect(isCommentaryAnnotations({...value,links:[malformed]})).toBe(true);expect(commentaryScriptureReference(malformed as never)).toBeUndefined()
})
it('allows only genuine HTTPS external URLs and never executes unknown or local targets',()=>{
 const link=(href:string)=>({kind:'external' as const,href,label:'Source',status:'raw'})
 expect(commentaryExternalUrl(link('https://example.org/article'))).toBe('https://example.org/article')
 for(const href of ['http://example.org','javascript:alert(1)','file:///etc/passwd','https://user:password@example.org'])expect(commentaryExternalUrl(link(href))).toBeUndefined()
 expect(commentaryExternalUrl({...link('https://example.org'),kind:'unknown'})).toBeUndefined()
})
