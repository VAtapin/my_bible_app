import {expect,it,vi} from 'vitest'
import {prayerTopic,prayerSectionWorks,preferredPrayerLanguage,prayerLanguageChoices,prayerCatalogWorks,prayerWorkCards} from './prayerCatalog'
import type {LiturgicalWorkSummary,LiturgicalWorkVersion} from '@/api/contracts'
const work=(slug:string,collections=['prayers']):LiturgicalWorkSummary=>({id:1,slug,title:slug,collections,available_languages:['de'],editions:[],source_url:null})
it('starts with the interface language while preserving an explicitly different prayer choice',()=>{
 expect(preferredPrayerLanguage('de',['de','ru','cu','cu-civil'])).toBe('de')
 expect(preferredPrayerLanguage('de',['ru'])).toBe('ru')
 expect(preferredPrayerLanguage('de',[])).toBe('de')
 expect(prayerLanguageChoices('ru',['ru','cu','ru'])).toEqual(['ru','cu'])
 expect(prayerLanguageChoices('de',undefined)).toEqual(['de'])
})
it('lists prayer and worship units without source books or duplicate German editions',()=>{
 const prayer={...work('prayer-morning-rule'),editions:[
  {code:'historic',title:'Maltzew',language:'de',orthography:'historic-1911',reader_profile:'full'},
  {code:'modern',title:'Orthodoxia',language:'de',orthography:'modern-de',reader_profile:'full'},
 ]}
 const works=[prayer,work('whole-book',['prayer-books']),work('calendar-hymn',['calendar-reference']),
  {...work('russian-canon',['canons']),available_languages:['ru'],editions:[{code:'ru',title:'Russian',language:'ru',orthography:'modern',reader_profile:'full'}]},
  {...work('church-slavonic-hours',['horologion-laity']),available_languages:['cu'],editions:[{code:'cu',title:'Church Slavonic',language:'cu',orthography:'traditional',reader_profile:'full'}]},
  {...work('whole-prayer-book',['prayers','prayer-books']),available_languages:['ru'],editions:[{code:'book',title:'Book',language:'ru',orthography:'modern',reader_profile:'full'}]}]
 expect(prayerCatalogWorks(works).map(work=>work.slug)).toEqual(['prayer-morning-rule','russian-canon','church-slavonic-hours'])
 expect(prayerWorkCards(works,'de').map(card=>[card.work.slug,card.edition?.code])).toEqual([['prayer-morning-rule','modern']])
 expect(prayerWorkCards(works,'de','maltzew').map(card=>card.edition?.code)).toEqual(['historic'])
 expect(prayerWorkCards(works,'').map(card=>card.edition?.code)).toEqual(['modern','ru','cu'])
 expect(prayerWorkCards(works,'ru').map(card=>card.work.slug)).toEqual(['russian-canon'])
 expect(prayerWorkCards(works,'cu').map(card=>card.work.slug)).toEqual(['church-slavonic-hours'])
})
it('groups individual prayers and full rules by occasion without guessing from translated titles',()=>{
 for(const slug of ['prayer-morning-rule','prayer-morning-1','prayer-morning-trinity'])expect(prayerTopic(slug)).toBe('morning')
 for(const slug of ['prayer-evening-rule','prayer-evening-1','prayer-before-sleep'])expect(prayerTopic(slug)).toBe('evening')
 expect(prayerTopic('prayer-before-evening-meal')).toBe('meals')
 expect(prayerTopic('prayer-before-study')).toBe('study')
 expect(prayerTopic('prayer-before-work')).toBe('work')
 expect(prayerTopic('prayer-communion-rule')).toBe('communion')
 expect(prayerTopic('kanon-pokayannyj')).toBe('canons')
 expect(prayerTopic('hymn-feast')).toBe('hymns')
})
it('preserves every indexed unit across books including hidden prayers without book cards or duplicates',async()=>{
 const books=[work('book-a',['prayer-books']),work('book-b',['prayer-books'])]
 const openWork=vi.fn(async(slug:string)=>work(slug))
 const result=await prayerSectionWorks([...books,work('prayer-morning-rule')],async book=>({bookSections:[{id:book.slug,title:'Section',printed_pages:[],works:[{slug:'prayer-morning-rule'},{slug:'prayer-morning-1'},{slug:book.slug+'-unit'}]}]} as LiturgicalWorkVersion),openWork)
 expect(result.map(w=>w.slug)).toEqual(['prayer-morning-rule','prayer-morning-1','book-a-unit','book-b-unit'])
 expect(openWork).toHaveBeenCalledTimes(3)
})
it('reports unavailable units instead of silently dropping book content',async()=>{
 await expect(prayerSectionWorks([work('book',['prayer-books'])],async()=>({bookSections:[{works:[{slug:'missing'}]}]} as LiturgicalWorkVersion),async()=>{throw new Error('Unavailable')})).rejects.toThrow('Unavailable')
})
