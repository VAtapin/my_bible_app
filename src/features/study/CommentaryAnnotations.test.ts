import {createSSRApp} from 'vue'
import {renderToString} from 'vue/server-renderer'
import {expect,it} from 'vitest'
import CommentaryAnnotations from './CommentaryAnnotations.vue'
import type {CommentaryAnnotationLink} from '@/api/commentaryAnnotations'

const external:CommentaryAnnotationLink={kind:'external',href:'https://example.org/source',label:'External website',status:'raw'}
const bible:CommentaryAnnotationLink={kind:'bible',href:'B: 500 3:16',label:'John 3:16',status:'raw',book_slug:'john',chapter:3,verse:16}
const render=(links:CommentaryAnnotationLink[])=>renderToString(createSSRApp(CommentaryAnnotations,{source:'Commentary',annotations:{source_sha256:'a'.repeat(64),links,media:[]}}))
it('omits the entire annotations section when only external websites are supplied',async()=>{
 const html=await render([external])
 expect(html).not.toContain('<section')
 expect(html).not.toContain('example.org')
 expect(html).not.toContain('External website')
})
it('preserves internal scripture navigation while removing website links and labels',async()=>{
 const html=await render([external,bible])
 expect(html).toContain('John 3:16</button>')
 expect(html).not.toContain('<a ')
 expect(html).not.toContain('example.org')
 expect(html).not.toContain('External website')
})
