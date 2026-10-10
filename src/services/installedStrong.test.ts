import { describe,expect,it,vi } from 'vitest'
import { installedStrongArticle,verseStudyService } from './verseStudy'
import type { VerseStudyApi } from '@/api/verseStudy'
import { sourceStudyStrongTokens } from './strongMarkup'
import type { BibleVerse } from '@/api/contracts'
const data={entries:[{id:'H430',lexicon_code:'HEB',word:'אלהים',transliteration:'elohim',pronunciation:null,content:'Complete text'},{id:'G430',lexicon_code:'GREEK',word:'Other',transliteration:null,pronunciation:null,content:'Greek text'}],lexicons:[{code:'HEB',name:'Hebrew source',language:'ru'},{code:'GREEK',name:'Greek source',language:'en'}]}
describe('installed Strong source dispatch',()=>{
 it('opens an installed article from validated annotation Strong despite bare raw identifiers and offline token API',async()=>{
  const verse:BibleVerse={id:1,number:1,osis_ref:'Gen.1.1',text:'God<S>430</S>',plain_text:'God',has_strong_markup:true,annotations:{status:'available',paragraph_before:null,paragraph_breaks:[],line_breaks:[],headings:[],footnotes:[],added_words:[],emphasis:[],red_letters:[],strong_tokens:[{strong_number:'H430',token_order:7,offset_utf16:3,grammar_code:'N',surface_text:'God'}],source:{kind:'mybible',sha256:'a'.repeat(64)},features:{headings:'absent',footnotes:'absent',added_words:'absent',paragraphs:'absent'}}}
  const network=vi.fn().mockRejectedValue(new Error('Offline'))
  const service=verseStudyService({strong:network,tokens:network} as unknown as VerseStudyApi,{read:async()=>undefined,write:async()=>undefined},async()=>data)
  const tokens=sourceStudyStrongTokens(verse)
  expect(tokens).toEqual([{strong_number:'H430',token_order:7,grammar_code:'N',surface_text:'God'}])
  expect((await service.strong(tokens[0]!.strong_number,verse.id)).content).toBe('Complete text')
  expect(network).not.toHaveBeenCalled()
  expect(sourceStudyStrongTokens({...verse,annotations:{...verse.annotations!,source:{kind:'mybible',sha256:'bad'}}})).toEqual([])
 })
 it('preserves complete content, explicit H/G number and lexicon identity',()=>{expect(installedStrongArticle('H430',data)).toEqual({number:'H430',word:'אלהים',transliteration:'elohim',pronunciation:null,content:'Complete text',lexicon:{code:'HEB',name:'Hebrew source',language:'ru'}})})
 it('never substitutes Hebrew/Greek or silently changes an explicitly selected source',()=>{expect(()=>installedStrongArticle('H431',data)).toThrow();expect(()=>installedStrongArticle('H430',data,'GREEK')).toThrow()})
 it('reads the full installed lexicon without issuing a network request',async()=>{const network=vi.fn();const service=verseStudyService({strong:network} as unknown as VerseStudyApi,{read:async()=>undefined,write:async()=>undefined},async()=>data);expect((await service.strong('H430')).content).toBe('Complete text');expect(network).not.toHaveBeenCalled()})
})
