import { expect, it } from 'vitest'
import { dictionaryBrowse, dictionaryCatalogQuery } from './dictionaryNavigation'

it('opens the catalog by default while preserving existing search links', () => {
  expect(dictionaryBrowse({}).catalog).toBe(true)
  expect(dictionaryBrowse({ q: 'абие', mode: 'word' })).toMatchObject({ catalog: false, q: 'абие', mode: 'word' })
  expect(dictionaryBrowse({ mode: 'meaning' }).catalog).toBe(false)
  expect(dictionaryBrowse({ view: 'search' }).catalog).toBe(false)
})

it('browses a source section without a lingering word or meaning filter', () => {
  const query = dictionaryCatalogQuery(36)
  expect(query).toEqual({ view: 'catalog', letter: '36' })
  expect(dictionaryBrowse({ ...query, q: 'вода', mode: 'meaning' })).toEqual({ catalog: true, q: '', mode: 'word', letter: 36 })
  expect(dictionaryBrowse({ view: 'catalog', letter: '37' }).letter).toBeUndefined()
  expect(dictionaryCatalogQuery()).toEqual({ view: 'catalog' })
})
