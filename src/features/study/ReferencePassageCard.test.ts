import {createSSRApp} from 'vue'
import {renderToString} from 'vue/server-renderer'
import {it,expect} from 'vitest'
import ReferencePassageCard from './ReferencePassageCard.vue'
import type {ReferenceGroup} from '@/services/verseStudy'
const target={verse_id:1,osis_ref:'Acts.6.13',reference:'Деяния 6:13',book_slug:'acts',chapter_number:6,verse_number:13,text:'Actual saved text',versification:{status:'verified' as const,source_profile:'source',edition_profile:'edition',map_version:'v1'}}
const group:ReferenceGroup={key:'actual',label:'Деяния 6:13',source:'legacy_quote',type:'legacy_quote',targets:[target]}
it('shows a clickable passage and actual confirmed text without machine labels or numbering diagnostics',async()=>{
 const html=await renderToString(createSSRApp(ReferencePassageCard,{group,code:'RST'}))
 expect(html).toContain('passage-link');expect(html).toContain('Деяния 6:13');expect(html).toContain('Actual saved text')
 for(const value of ['legacy_quote','Нумерация','Система источника','Система редакции','Предпросмотр скрыт'])expect(html).not.toContain(value)
})
it('does not expose an unconfirmed passage, stray verse number or unverified preview',async()=>{
 const html=await renderToString(createSSRApp(ReferencePassageCard,{code:'RST',group:{...group,targets:[{...target,versification:{status:'unknown',source_profile:null,edition_profile:null,map_version:null}}]}}))
 expect(html).not.toContain('Деяния');expect(html).not.toContain('Actual saved text');expect(html).not.toContain('<sup>')
})
