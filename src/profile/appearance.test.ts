import { afterEach, describe, expect, it, vi } from 'vitest'
import { loadAppearance, useAppearance } from './appearance'
import type { KeyValueStorage } from './profileRepository'

describe('appearance', () => {
  afterEach(() => vi.unstubAllGlobals())
  const storage = (value: string | null): KeyValueStorage => ({ getItem: () => value, setItem: () => {}, removeItem: () => {} })
  it('defaults to the blended style for missing and invalid preferences', () => {
    expect(loadAppearance(storage(null))).toBe('classic')
    expect(loadAppearance(storage('unknown'))).toBe('classic')
  })
  it('restores both alternative styles', () => {
    expect(loadAppearance(storage('modern'))).toBe('modern')
    expect(loadAppearance(storage('warm'))).toBe('warm')
  })
  it('persists selection and restores it without depending on control layout', () => {
    const values = new Map<string, string>()
    const localStorage: KeyValueStorage = { getItem: (key) => values.get(key) ?? null, setItem: (key, value) => { values.set(key, value) }, removeItem: (key) => { values.delete(key) } }
    vi.stubGlobal('window', { localStorage })
    const appearance = useAppearance()
    appearance.initialize()
    for (const value of ['modern', 'warm', 'classic'] as const) {
      appearance.setTheme(value)
      expect(appearance.theme.value).toBe(value)
      expect(loadAppearance(localStorage)).toBe(value)
    }
  })
})
