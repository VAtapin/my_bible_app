import { describe, expect, it } from 'vitest'
import { loadAppearance } from './appearance'
import type { KeyValueStorage } from './profileRepository'

describe('appearance', () => {
  const storage = (value: string | null): KeyValueStorage => ({ getItem: () => value, setItem: () => {}, removeItem: () => {} })
  it('defaults to the blended style for missing and invalid preferences', () => {
    expect(loadAppearance(storage(null))).toBe('classic')
    expect(loadAppearance(storage('unknown'))).toBe('classic')
  })
  it('restores both alternative styles', () => {
    expect(loadAppearance(storage('modern'))).toBe('modern')
    expect(loadAppearance(storage('warm'))).toBe('warm')
  })
})
