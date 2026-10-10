import {createSSRApp} from 'vue'
import {renderToString} from 'vue/server-renderer'
import {afterEach,expect,it,vi} from 'vitest'
import type {BibleVerse} from '@/api/contracts'
import {defaultReaderPreferences} from '@/profile/readerPreferences'
import SourceVerseText from './SourceVerseText.vue'
vi.mock('@/offline/personalStudy',()=>({createPersonalStudyRepository:()=>({})}))
const verse={id:1,number:1,osis_ref:'John.1.1',text:'Word',plain_text:'Word',annotations:{status:'available',paragraph_before:false,paragraph_breaks:[],line_breaks:[],headings:[],footnotes:[],added_words:[],emphasis:[],red_letters:[],source:{kind:'mybible',sha256:null},strong_tokens:[{strong_number:'G3056',token_order:1,offset_utf16:0,grammar_code:null,surface_text:'Word'}],features:{headings:'absent',footnotes:'absent',added_words:'absent',paragraphs:'absent'}}} as BibleVerse
afterEach(()=>vi.unstubAllGlobals())
async function render(strongNumbers:boolean){
 vi.stubGlobal('window',{localStorage:{getItem:()=>null,setItem:()=>{}}})
 return renderToString(createSSRApp(SourceVerseText,{verse,code:'RST',marks:[],readOnly:true,preferences:{...defaultReaderPreferences,strongNumbers}}))
}
it('keeps Strong annotations superscript in temporary read-only passages',async()=>{
 const html=await render(true)
 expect(html).toMatch(/<sup[^>]*aria-label="G3056"[^>]*>G3056<\/sup>/)
 expect(html).not.toContain('role="button"')
})
it('respects disabled Strong annotations in temporary passages',async()=>{
 const html=await render(false)
 expect(html).toContain('Word')
 expect(html).not.toContain('G3056')
})
