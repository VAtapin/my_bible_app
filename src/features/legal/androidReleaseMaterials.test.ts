import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'
// @ts-expect-error Static build utility is not browser runtime code.
import { androidPrivacy, androidPrivacyHtml } from '../../../scripts/android-privacy.mjs'

const listing = JSON.parse(readFileSync(new URL('../../../mobile/play/listings.json', import.meta.url), 'utf8'))
const checklist = JSON.parse(readFileSync(new URL('../../../mobile/play/submission-checklist.json', import.meta.url), 'utf8'))

describe('native Android publication material', () => {
  it.each(['ru', 'de', 'uk', 'en'])('has a complete crawler-readable Android policy in %s', (language) => {
    const html = androidPrivacyHtml(language)
    expect(html).toContain(`<html lang="${language}">`)
    expect(html).toContain('com.bibledesktop.myapp')
    expect(html).toContain('https://atapin.de/impressum')
    expect(html).toContain('Volodymyr Atapin')
    expect(html).toContain('mailto:atapin@gmail.com')
    expect(html).toContain('noBackupFilesDir')
    expect(html).toContain('2026-10-09')
    expect(html).not.toContain('<script')
    expect(html).not.toContain('/assets/app')
    expect(androidPrivacy.locales[language].sections).toHaveLength(7)
  })
  it.each(['ru-RU', 'de-DE', 'uk', 'en-US'])('fits Play listing limits in %s without claiming full translated content', (locale) => {
    const text = listing.locales[locale]
    expect(text.title.length).toBeLessThanOrEqual(30)
    expect(text.shortDescription.length).toBeLessThanOrEqual(80)
    expect(text.fullDescription.length).toBeGreaterThan(700)
    expect(text.fullDescription.length).toBeLessThanOrEqual(4000)
  })
  it('does not mistake preparation for submission or approve an unsupported no-data claim', () => {
    expect(listing.status).toBe('prepared-not-submitted')
    expect(checklist.decisions.firstPublication).toBe(true)
    expect(checklist.dataSafetyEvidence.noDataClaimApproved).toBe(false)
    expect(checklist.contentRights.status).toBe('owner-confirmation-required-before-publication')
    expect(listing.supportEmail).toBe(androidPrivacy.email)
  })
  it('preserves the standalone Android policy outside SPA navigation fallback', () => {
    const config = readFileSync(new URL('../../../vite.config.ts', import.meta.url), 'utf8')
    expect(config).toContain('navigateFallbackDenylist: [/^\\/android\\/privacy(?:\\/|$)/]')
  })
  it.each(['ru', 'de', 'uk', 'en'])('has a correctly sized store feature PNG in %s', (language) => {
    const png = readFileSync(new URL(`../../../mobile/play/assets/feature-${language}.png`, import.meta.url))
    expect(png.subarray(0, 8).toString('hex')).toBe('89504e470d0a1a0a')
    expect(png.readUInt32BE(16)).toBe(1024)
    expect(png.readUInt32BE(20)).toBe(500)
  })
  it('has a square store icon without an alpha channel', () => {
    const png = readFileSync(new URL('../../../mobile/play/assets/icon-512.png', import.meta.url))
    expect(png.readUInt32BE(16)).toBe(512)
    expect(png.readUInt32BE(20)).toBe(512)
    expect(png[25]).toBe(2)
  })
})
