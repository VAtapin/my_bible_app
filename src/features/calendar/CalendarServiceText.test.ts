import {describe,it,expect} from 'vitest'
import {createSSRApp,h} from 'vue'
import {renderToString} from 'vue/server-renderer'
import CalendarServiceText from './CalendarServiceText.vue'
import type {CalendarTextMetadata} from '@/api/automaticCalendar'
const metadata:CalendarTextMetadata={familyId:'work:actual',workSlug:'actual',language:'cu',orthography:'traditional',edition:'actual-edition',sourceKind:'liturgical-corpus',selectedTextId:17,contentHash:'a'.repeat(64),availableEditions:[],selection:'preferred',missingReason:null}
async function render(item:CalendarTextMetadata&{text:string}){return renderToString(createSSRApp({render:()=>h(CalendarServiceText,{item})}))}
describe('actual calendar text rendering',()=>{
 it('renders unchanged source scripts with per-item fonts and no manual override control',async()=>{
  const traditional=await render({...metadata,text:'Сла́ва ѻ҆ц҃ꙋ̀'}),civil=await render({...metadata,language:'cu-civil',orthography:'civil-accented',text:'Слава Отцу'}),german=await render({...metadata,language:'de',orthography:'civil',text:'Original Deutsch'})
  expect(traditional).toContain('slavonic-unicode');expect(traditional).toContain('Сла́ва ѻ҆ц҃ꙋ̀');expect(traditional).not.toContain('<select')
  expect(civil).toContain('slavonic-civil');expect(civil).toContain('Слава Отцу');expect(civil).not.toContain('slavonic-unicode')
  expect(german).toContain('lang="de"');expect(german).toContain('Original Deutsch');expect(german).not.toContain('slavonic-unicode')
 })
 it('renders explicit missing status without a source body or invented edition',async()=>{
  const html=await render({...metadata,selection:'missing',language:null,edition:null,orthography:null,sourceKind:null,selectedTextId:null,contentHash:null,missingReason:'published-edition-unavailable',text:''})
  expect(html).toContain('role="status"');expect(html).not.toContain('actual-edition');expect(html).not.toContain('slavonic-unicode')
 })
})
