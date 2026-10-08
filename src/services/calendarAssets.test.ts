import { describe, expect, it } from 'vitest'
import { bundledCalendarAssetUrl } from './calendarAssets'
import { apiBaseUrl } from '@/config/api'

describe('original BibleDesktop signs available offline', () => {
  it.each(['great', 'vigil', 'polyeleos', 'doxology', 'six-stichera'])('bundles the existing %s Typikon SVG', (name) => {
    expect(bundledCalendarAssetUrl(`/assets/typikon/${name}.svg`)).toBeTruthy()
    expect(bundledCalendarAssetUrl(new URL(`/assets/typikon/${name}.svg`, apiBaseUrl).href)).toBeTruthy()
  })
  it.each(['boiled-no-oil', 'boiled-with-oil', 'dairy-eggs', 'dry-eating', 'fast-no-fish', 'fish', 'memorial', 'strict-fast'])('bundles the existing %s food/memorial marker', (name) => {
    expect(bundledCalendarAssetUrl(`/assets/markers/minimal-dark/${name}.png`)).toBeTruthy()
  })
  it('does not guess unknown markers or resolve another origin', () => {
    expect(bundledCalendarAssetUrl('/assets/typikon/unknown.svg')).toBeUndefined()
    expect(bundledCalendarAssetUrl('https://other.example/assets/typikon/vigil.svg')).toBeUndefined()
    expect(bundledCalendarAssetUrl('/assets/typikon/vigil.svg?modified=1')).toBeUndefined()
  })
})
