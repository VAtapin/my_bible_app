import{describe,it,expect}from'vitest'
import{displayReferenceGroups,referenceCopyText}from'./referenceDisplay'
import{normalizeReferencePreferences,toggleReferenceSource}from'@/profile/referencePreferences'
import type{StudyCrossReference}from'@/api/verseStudy'
const ref=(source:string,verse:number):StudyCrossReference=>({id:verse,source,type:'parallel',versification:{status:'verified',source_profile:'source',edition_profile:'edition',map_version:'reviewed-v1'},target:{verse_id:verse,osis_ref:`John.3.${verse}`,reference:`John 3:${verse}`,book_slug:'john',chapter_number:3,verse_number:verse,text:`Verse ${verse}`}})
describe('reference display',()=>{
 it('merges only identical published target ranges and preserves both source names',()=>{
  const groups=displayReferenceGroups([ref('A',1),ref('A',2),ref('B',1),ref('B',2)])
  expect(groups).toHaveLength(1);expect(groups[0]?.source).toBe('A · B');expect(groups[0]?.targets.map(t=>t.verse_number)).toEqual([1,2])
  expect(displayReferenceGroups([ref('A',1),ref('A',2),ref('B',1)])).toHaveLength(2)
 })
 it('keeps normal/detail source settings separate, with empty selection meaning none',()=>{
  const settings=normalizeReferencePreferences({inlineSources:['A','A'],detailSources:['B'],inlineMode:'list'})
  expect(settings.inlineSources).toEqual(['A']);expect(settings.detailSources).toEqual(['B']);expect(displayReferenceGroups([ref('A',1)],[])).toEqual([])
  expect(toggleReferenceSource(null,'A',['A','B'])).toEqual(['B']);expect(toggleReferenceSource([],'A',['A','B'])).toEqual(['A'])
 })
 it('copies every explicitly published verse with source and translation',()=>{
  const group=displayReferenceGroups([ref('A',1),ref('A',2)])[0]!
  expect(referenceCopyText(group,'RST')).toContain('1 Verse 1\n2 Verse 2');expect(referenceCopyText(group,'RST')).toContain('RST · A')
 })
})
