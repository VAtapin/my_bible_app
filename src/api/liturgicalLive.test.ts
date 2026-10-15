import {describe,it,expect} from 'vitest'
import {createBibleApi} from './client'
import {prayerSectionWorks,prayerTopic,prayerTopics} from '@/services/prayerCatalog'

/** Opt-in acceptance against an isolated official import, never a synthetic response server. */
describe.skipIf(!process.env.PRAYER_BOOK_API)('actual full prayer-book API',()=>{
 const api=createBibleApi({baseUrl:process.env.PRAYER_BOOK_API!})
 it('keeps every published book unit reachable through the thematic catalogue',async()=>{
  const works=await api.getLiturgicalWorks('')
  const expected=new Set<string>()
  const result=await prayerSectionWorks(works,async book=>{
   const edition=book.editions.find(item=>item.language===book.available_languages[0])!
   const version=await api.getLiturgicalVersion(book.slug,edition.language,edition.code)
   for(const section of version.bookSections??[])for(const unit of section.works)expected.add(unit.slug)
   return version
  },slug=>api.getLiturgicalWork!(slug))
  expect(expected.size).toBeGreaterThan(0)
  for(const slug of expected)expect(result.some(work=>work.slug===slug),slug).toBe(true)
  expect(result.some(work=>work.collections.includes('prayer-books'))).toBe(false)
  for(const work of result)expect(prayerTopics).toContain(prayerTopic(work.slug,'',work.collections,work.prayer_groups))
 },120000)
 it('opens the current complete book with all source-bound section IDs',async()=>{
  const slug='book-orthodoxia-gebetbuch',edition='prayer-de-owner-permission-full-gebetbuch-20261013'
  const work=await api.getLiturgicalWork!(slug)
  expect(work.collections).toContain('prayer-books');expect(work.available_languages).toContain('de')
  const book=await api.getLiturgicalVersion(slug,'de',edition)
  expect(book.blocks).toHaveLength(1391);expect(book.bookSections).toHaveLength(19)
  expect(book.review_status).toBe('source-verified-complete')
  expect(book.sourceEditionNotes?.length).toBeGreaterThan(0)
  for(const section of book.bookSections!){
   expect(book.blocks.some(block=>block.id===section.startBlockId)).toBe(true)
   expect(book.blocks.some(block=>block.id===section.endBlockId)).toBe(true)
  }
 })
 it('accepts the internal-book catalogue marker and discovers German-only families through the unfiltered collection',async()=>{
  const catalog=await api.getPrayerCatalog!('ru')
  expect(catalog.external_sources?.some(source=>source.availability==='full-book-internal')).toBe(true)
  const works=await api.getLiturgicalWorks('prayers')
  const germanOnly=works.find(work=>work.available_languages.includes('de')&&!work.available_languages.some(code=>code.startsWith('cu')||code==='ru'))
  expect(germanOnly).toBeDefined()
  const edition=germanOnly!.editions.find(item=>item.language==='de')!
  const body=await api.getLiturgicalVersion(germanOnly!.slug,'de',edition.code)
  expect(body.blocks.some(block=>block.text.trim())).toBe(true)
  expect(body.language).toBe('de')
 })
})
