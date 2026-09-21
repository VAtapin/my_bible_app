import { describe, expect, it } from 'vitest'
import { decomposeSlavonicNumeral, toSlavonicClockValue, toSlavonicNumeral } from './slavonicNumerals'

describe('Church Slavonic numerals', () => {
  it('uses the reversed order for values from eleven through nineteen', () => {
    expect(toSlavonicNumeral(10)).toBe('і҃')
    expect(toSlavonicNumeral(12)).toBe('ві҃')
    expect(toSlavonicNumeral(19)).toBe('ѳі҃')
  })

  it('composes tens and units in descending order outside the second decade', () => {
    expect(toSlavonicNumeral(21)).toBe('ка҃')
    expect(toSlavonicNumeral(59)).toBe('нѳ҃')
    expect(toSlavonicNumeral(999)).toBe('цчѳ҃')
  })

  it('shows the absence of a positional zero in the clock', () => {
    expect(toSlavonicClockValue(0, 0, 0)).toEqual({ hours: '—', minutes: '—', seconds: '—' })
    expect(toSlavonicClockValue(14, 5, 12)).toEqual({ hours: 'ді҃', minutes: 'є҃', seconds: 'ві҃' })
  })

  it('returns the visible construction steps in their written order', () => {
    expect(decomposeSlavonicNumeral(12)).toEqual([{ value: 2, glyph: 'в' }, { value: 10, glyph: 'і' }])
    expect(decomposeSlavonicNumeral(742)).toEqual([{ value: 700, glyph: 'ѱ' }, { value: 40, glyph: 'м' }, { value: 2, glyph: 'в' }])
  })

  it('rejects unsupported values', () => {
    expect(() => toSlavonicNumeral(0)).toThrow(RangeError)
    expect(() => toSlavonicNumeral(1000)).toThrow(RangeError)
  })
})
