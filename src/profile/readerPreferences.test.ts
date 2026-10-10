import { describe,it,expect } from 'vitest'
import { defaultReaderPreferences,effectiveReaderPreferences,normalizeReaderPreferences,loadReaderPreferences } from './readerPreferences'
import {loadFavoriteTranslations} from './favoriteTranslations'
describe('reader preferences',()=>{
  it('clean mode restores the actual prior settings without overwriting them',()=>{
    const p={...defaultReaderPreferences,verseNumbers:false,strongNumbers:true,clean:true}
    expect(effectiveReaderPreferences(p).strongNumbers).toBe(false)
    expect(effectiveReaderPreferences({...p,clean:false}).strongNumbers).toBe(true)
    expect(p.verseNumbers).toBe(false)
  })
  it('validates persisted types, clamps readable typography and leaves input opt-in',()=>{
    const p=normalizeReaderPreferences({fontSize:999,lineHeight:.1,night:'yes',tapPaging:true})
    expect(p.fontSize).toBe(36);expect(p.lineHeight).toBe(1.2);expect(p.night).toBe(false);expect(p.tapPaging).toBe(true);expect(p.swipeBooks).toBe(false)
    expect(normalizeReaderPreferences({fontSize:NaN}).fontSize).toBe(19)
    expect(loadReaderPreferences({getItem:()=>'{',setItem:()=>{},removeItem:()=>{}})).toEqual(defaultReaderPreferences)
  })
  it('deduplicates favorite edition identities and ignores invalid saved items',()=>{
    expect(loadFavoriteTranslations({getItem:()=> '["RST","RST",3,"KJV"]',setItem:()=>{},removeItem:()=>{}})).toEqual(['RST','KJV'])
  })
})
