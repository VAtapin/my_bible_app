import { describe, expect, it } from 'vitest'
import { letters } from './letters'

describe('letter catalogue', () => {
  it('uses stable unique ids and complete first-release translations', () => {
    expect(new Set(letters.map((letter) => letter.id)).size).toBe(letters.length)
    expect(letters.every((letter) => letter.name.ru && letter.name.de && letter.meaning.ru && letter.meaning.de)).toBe(true)
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
