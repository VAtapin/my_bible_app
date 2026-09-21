import { describe, expect, it } from 'vitest'
import { toSlavonicClockValue, toSlavonicNumeral } from './slavonicNumerals'

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
    expect(toSlavonicClockValue(0, 0)).toEqual({ hours: '—', minutes: '—' })
    expect(toSlavonicClockValue(14, 5)).toEqual({ hours: 'ді҃', minutes: 'є҃' })
  })

  it('rejects unsupported values', () => {
    expect(() => toSlavonicNumeral(0)).toThrow(RangeError)
    expect(() => toSlavonicNumeral(1000)).toThrow(RangeError)
  })
})
