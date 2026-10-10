import {createSSRApp,ref} from 'vue'
import {renderToString} from 'vue/server-renderer'
import {describe,it,expect,vi} from 'vitest'
import type {BibleChapter} from '@/api/contracts'
import VerseStudyPanel from './VerseStudyPanel.vue'

const calls=vi.hoisted(()=>({tokens:vi.fn(),references:vi.fn().mockResolvedValue({verse:{id:1,osis_ref:'Matt.1.18'},references:[]})}))
vi.mock('@/services/verseStudy',async importOriginal=>({...await importOriginal<typeof import('@/services/verseStudy')>(),verseStudyService:()=>calls}))
vi.mock('@/services/webBibleLibrary',()=>({webBibleBooks:async()=>[]}))
vi.mock('@/profile/referencePreferences',()=>({useReferencePreferences:()=>({settings:ref({inlineSources:null,detailSources:null,detailSort:'canonical'}),initialize(){},setSettings(){}})}))

describe('Strong articles opened from the verse',()=>{
 it('does not fetch or show an unrelated grid of source numbers before a word is selected',async()=>{
   const chapter={translation:{code:'RST_STRONG',name:'Actual source'},book:{name:'Матфея',slug:'matthew'},chapter:{number:1},verses:[{id:1,number:18,osis_ref:'Matt.1.18',text:'Рождество G1083 Иисуса G2424',plain_text:'Рождество Иисуса',has_strong_markup:true}]} as BibleChapter
   const html=await renderToString(createSSRApp(VerseStudyPanel,{chapter,verse:18}))
   expect(calls.tokens).not.toHaveBeenCalled()
   expect(html).not.toContain('strong-numbers')
   expect(html).not.toContain('G1083');expect(html).not.toContain('G2424')
   expect(calls.references).toHaveBeenCalledWith(1,'RST_STRONG','Matt.1.18')
 })
})
