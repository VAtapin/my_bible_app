import {expect,it,vi} from 'vitest'
import {prayerTopic,prayerSectionWorks} from './prayerCatalog'
import type {LiturgicalWorkSummary,LiturgicalWorkVersion} from '@/api/contracts'
const work=(slug:string,collections=['prayers']):LiturgicalWorkSummary=>({id:1,slug,title:slug,collections,available_languages:['de'],editions:[],source_url:null})
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
