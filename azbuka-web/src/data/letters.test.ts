import { describe, expect, it } from 'vitest'
import { letters } from './letters'
import { letterExampleById, letterExampleIds } from './letterExamples'

describe('letter catalogue', () => {
  it('uses stable unique ids and complete first-release translations', () => {
    expect(new Set(letters.map((letter) => letter.id)).size).toBe(letters.length)
    expect(letters.every((letter) => letter.name.ru && letter.name.de && letter.meaning.ru && letter.meaning.de)).toBe(true)
  })

  it('provides a contextual example containing every target glyph', () => {
    expect(new Set(letterExampleIds).size).toBe(letters.length)
    for (const letter of letters) {
      const example = letterExampleById(letter.id)
      expect(example, `missing example for ${letter.id}`).toBeDefined()
      expect(example!.text.toLocaleLowerCase('ru')).toContain(letter.glyph.toLocaleLowerCase('ru'))
      expect(example!.translation.ru).toBeTruthy()
      expect(example!.translation.de).toBeTruthy()
      expect(example!.source.ru).toBeTruthy()
      expect(example!.source.de).toBeTruthy()
    }
  })

  it('contains each canonical Cyrillic numeral value through 900', () => {
    const values = letters
      .map((letter) => letter.numericValue)
      .filter((value): value is number => value !== null)
      .sort((left, right) => left - right)

    expect(values).toEqual([
      1, 2, 3, 4, 5, 6, 7, 8, 9,
      10, 20, 30, 40, 50, 60, 70, 80, 90,
      100, 200, 300, 400, 500, 600, 700, 800, 900
    ])
  })
})
