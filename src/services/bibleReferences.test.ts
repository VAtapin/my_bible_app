import { describe, expect, it } from 'vitest'
import { readerTarget } from './bibleReferences'

describe('Bible references', () => {
  it('maps calendar OSIS books to API reader slugs', () => {
    expect(readerTarget('Eph', 1)).toEqual({ book: 'ephesians', chapter: '1' })
    expect(readerTarget('Mark', 8)).toEqual({ book: 'mark', chapter: '8' })
    expect(readerTarget('Ps', 134)).toEqual({ book: 'psalms', chapter: '134' })
    expect(readerTarget('1Cor', 13)).toEqual({ book: '1corinthians', chapter: '13' })
    expect(readerTarget('Song', 2)).toEqual({ book: 'songs', chapter: '2' })
  })

  it('does not invent a link for an unknown book', () => {
    expect(readerTarget('Unknown', 1)).toBeUndefined()
  })
})
