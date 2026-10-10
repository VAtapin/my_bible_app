import {describe,it,expect}from'vitest'
import {dictionaryLookupLink,dictionaryLookupQuery}from'./dictionaryLookup'
describe('dictionary word lookup',()=>{
 it('forwards the real selected text as a route query, preserving accents and multiple words',()=>{
  expect(dictionaryLookupLink('  λόγος  ')).toEqual({path:'/dictionaries',query:{q:'λόγος'}})
  expect(dictionaryLookupQuery('Слава Богу')).toBe('Слава Богу')
 })
 it('does not silently truncate a long passage or replace an empty selection with another word',()=>{
  expect(dictionaryLookupLink('  ')).toBeUndefined();expect(dictionaryLookupQuery('a'.repeat(121))).toBeUndefined()
  expect(dictionaryLookupQuery('a'.repeat(120))).toHaveLength(120)
  expect(dictionaryLookupQuery(undefined)).toBeUndefined();expect(dictionaryLookupQuery('Бог\0')).toBeUndefined()
 })
})
